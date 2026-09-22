package com.geoshield.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.repository.UserRepository;
import com.geoshield.identity.service.IdentityService;
import com.geoshield.sos.dto.CreateSosRequest;
import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.entity.SosRequest;
import com.geoshield.sos.entity.SosStatus;
import com.geoshield.sos.mapper.SosMapper;
import com.geoshield.sos.repository.SosRequestRepository;
import com.geoshield.sos.service.SosServiceImpl;
import com.geoshield.sos.service.SosTransactionHelper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Mocked in-process service-layer microbenchmark measuring JVM control-flow latency
 * across SOS creation, idempotency resolution, conflict recovery, and cancellation dispatch.
 *
 * <p><strong>Scope Note:</strong> This test isolates Java service logic and MapStruct DTO mapping in-memory.
 * It does NOT measure real MySQL persistence, InnoDB row locking, duplicate-key race conditions across
 * physical connections, or network transmission. The authoritative evidence for real MySQL persistence,
 * transactions, and duplicate-key concurrency protection is provided by the Step 3 integration suite
 * ({@code SosPersistenceIntegrationTest}).
 */
public class SosReliabilityBenchmarkTest {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 500;

    private static final List<SosStatus> ACTIVE_STATUSES = List.of(
            SosStatus.PENDING,
            SosStatus.ACKNOWLEDGED,
            SosStatus.RESPONDING
    );

    private SosServiceImpl sosService;
    private SosTransactionHelper transactionHelper;
    private UserRepository userRepository;
    private SosRequestRepository sosRequestRepository;
    private IdentityService identityService;
    private SosMapper sosMapper;

    private UUID touristId;
    private User tourist;

    @BeforeEach
    void setUp() {
        touristId = UUID.randomUUID();
        tourist = new User("tourist_bench", "bench@example.com", "hash", "Bench Tourist", "+919876543210", new UserRole(Role.TOURIST));
        ReflectionTestUtils.setField(tourist, "id", touristId);

        userRepository = mock(UserRepository.class);
        sosRequestRepository = mock(SosRequestRepository.class);
        identityService = mock(IdentityService.class);
        sosMapper = mock(SosMapper.class);

        when(userRepository.findByIdForUpdate(touristId)).thenReturn(Optional.of(tourist));
        when(identityService.getUserById(touristId)).thenReturn(tourist);

        transactionHelper = new SosTransactionHelper(userRepository, sosRequestRepository, sosMapper);
        sosService = new SosServiceImpl(sosRequestRepository, identityService, sosMapper, transactionHelper);
    }

    @Test
    @DisplayName("Step 6E: Benchmark SOS Service-Level Idempotency and Cancellation Dispatch")
    void benchmarkSosReliabilityAndPerformance() {
        UUID initialClientRequestId = UUID.randomUUID();
        CreateSosRequest normalRequest = new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), initialClientRequestId);
        SosRequest persistedSos = new SosRequest(tourist, normalRequest.latitude(), normalRequest.longitude(), SosStatus.PENDING, initialClientRequestId);
        UUID sosId = UUID.randomUUID();
        ReflectionTestUtils.setField(persistedSos, "id", sosId);

        SosResponse pendingResponse = new SosResponse(
                sosId, touristId, "tourist_bench", "Bench Tourist", "+919876543210",
                normalRequest.latitude(), normalRequest.longitude(), SosStatus.PENDING, null, initialClientRequestId, Instant.now());
        SosResponse cancelledResponse = new SosResponse(
                sosId, touristId, "tourist_bench", "Bench Tourist", "+919876543210",
                normalRequest.latitude(), normalRequest.longitude(), SosStatus.CANCELLED, null, initialClientRequestId, Instant.now());

        when(sosMapper.toResponse(any(SosRequest.class))).thenAnswer(inv -> {
            SosRequest req = inv.getArgument(0);
            return req.getStatus() == SosStatus.CANCELLED ? cancelledResponse : pendingResponse;
        });

        when(sosRequestRepository.findByClientRequestId(initialClientRequestId)).thenReturn(Optional.empty());
        when(sosRequestRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(touristId, ACTIVE_STATUSES))
                .thenReturn(Optional.empty());
        when(sosRequestRepository.saveAndFlush(any(SosRequest.class))).thenReturn(persistedSos);
        when(sosRequestRepository.findById(any(UUID.class))).thenAnswer(inv -> {
            SosRequest req = new SosRequest(tourist, normalRequest.latitude(), normalRequest.longitude(), SosStatus.PENDING, initialClientRequestId);
            ReflectionTestUtils.setField(req, "id", inv.getArgument(0));
            return Optional.of(req);
        });
        when(sosRequestRepository.save(any(SosRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sosRequestRepository.findByClientRequestIdWithUser(initialClientRequestId)).thenReturn(Optional.of(persistedSos));

        // 1. Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            sosService.createSos(touristId, normalRequest);
            sosService.cancelSos(touristId, sosId);
        }

        // 2. Measure Normal SOS Creation Latency
        List<Long> creationNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            SosResponse resp = sosService.createSos(touristId, normalRequest);
            long elapsed = System.nanoTime() - start;
            creationNanos.add(elapsed);
            assertThat(resp.status()).isEqualTo(SosStatus.PENDING);
        }

        // 3. Measure Duplicate SOS Attempt Latency (Idempotency Hit)
        when(sosRequestRepository.findByClientRequestId(initialClientRequestId)).thenReturn(Optional.of(persistedSos));
        List<Long> duplicateNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            SosResponse resp = sosService.createSos(touristId, normalRequest);
            long elapsed = System.nanoTime() - start;
            duplicateNanos.add(elapsed);
            assertThat(resp.clientRequestId()).isEqualTo(initialClientRequestId);
        }

        // 4. Measure Recovery Path Latency (Concurrent race conflict recovery)
        List<Long> recoveryNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            SosResponse resp = transactionHelper.resolveExistingSosAfterConflict(initialClientRequestId, touristId);
            long elapsed = System.nanoTime() - start;
            recoveryNanos.add(elapsed);
            assertThat(resp.clientRequestId()).isEqualTo(initialClientRequestId);
        }

        // 5. Measure Cancellation Latency
        List<Long> cancellationNanos = new ArrayList<>(BENCHMARK_ITERATIONS);
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            SosResponse resp = sosService.cancelSos(touristId, sosId);
            long elapsed = System.nanoTime() - start;
            cancellationNanos.add(elapsed);
            assertThat(resp.status()).isEqualTo(SosStatus.CANCELLED);
        }

        // Print Benchmark Statistics
        printStats("1. Mocked Service SOS Creation Dispatch", creationNanos);
        printStats("2. Mocked Duplicate SOS Idempotency Lookup", duplicateNanos);
        printStats("3. Mocked Conflict Recovery Dispatch", recoveryNanos);
        printStats("4. Mocked SOS Cancellation Dispatch", cancellationNanos);
    }

    /**
     * Prints statistical summary for the sample.
     * Percentile convention: floor-index percentile convention (index = (int)(n * percentile))
     * on the sorted sample of size n=500 (e.g., p95 at index 475, p99 at index 495).
     */
    private void printStats(String name, List<Long> nanosList) {
        Collections.sort(nanosList);
        int n = nanosList.size();
        long min = nanosList.get(0);
        long max = nanosList.get(n - 1);
        double avg = nanosList.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long median = nanosList.get(n / 2);
        long p95 = nanosList.get((int) (n * 0.95));
        long p99 = nanosList.get((int) (n * 0.99));

        System.out.printf("BENCHMARK [%s] - Iterations: %d (Floor Index: p95=%d, p99=%d)%n",
                name, n, (int) (n * 0.95), (int) (n * 0.99));
        System.out.printf("  Min:    %.3f ms (%d ns)%n", min / 1_000_000.0, min);
        System.out.printf("  Max:    %.3f ms (%d ns)%n", max / 1_000_000.0, max);
        System.out.printf("  Avg:    %.3f ms (%.1f ns)%n", avg / 1_000_000.0, avg);
        System.out.printf("  Median: %.3f ms (%d ns)%n", median / 1_000_000.0, median);
        System.out.printf("  p95:    %.3f ms (%d ns)%n", p95 / 1_000_000.0, p95);
        System.out.printf("  p99:    %.3f ms (%d ns)%n", p99 / 1_000_000.0, p99);
    }
}
