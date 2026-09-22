package com.geoshield.sos.service;

import com.geoshield.common.exception.ConflictException;
import com.geoshield.identity.entity.Role;
import com.geoshield.identity.entity.User;
import com.geoshield.identity.entity.UserRole;
import com.geoshield.identity.repository.RoleRepository;
import com.geoshield.identity.repository.UserRepository;
import com.geoshield.sos.dto.CreateSosRequest;
import com.geoshield.sos.dto.SosResponse;
import com.geoshield.sos.entity.SosRequest;
import com.geoshield.sos.entity.SosStatus;
import com.geoshield.sos.repository.SosRequestRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real MySQL database integration and concurrency test infrastructure for SOS persistence,
 * SELECT FOR UPDATE pessimistic locking, and unique constraint guarantees.
 *
 * <p>Tagged with {@code @Tag("integration")} and enabled conditionally via
 * {@code @EnabledIfEnvironmentVariable(named = "GEOSHIELD_DB_PASSWORD", matches = ".+")}.
 * Crucially, this does NOT use Mockito to fake persistence; it runs against the live target
 * database using real multithreaded transactions to verify that SELECT FOR UPDATE serializes
 * concurrent creation and prevents duplicate active SOS for the same tourist.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "geoshield.jwt.secret=integration-test-secret-at-least-32-bytes-long-123456"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GEOSHIELD_DB_PASSWORD", matches = ".+")
class SosPersistenceIntegrationTest {

    private static final List<SosStatus> ACTIVE_STATUSES = List.of(
            SosStatus.PENDING,
            SosStatus.ACKNOWLEDGED,
            SosStatus.RESPONDING
    );

    @Autowired
    private SosRequestRepository sosRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SosTransactionHelper sosTransactionHelper;

    private User testTourist;

    private UserRole getOrCreateTouristRole() {
        return roleRepository.findByName(Role.TOURIST)
                .orElseGet(() -> roleRepository.save(new UserRole(Role.TOURIST)));
    }

    @AfterEach
    void tearDown() {
        if (testTourist != null && testTourist.getId() != null) {
            sosRequestRepository.findAllByUserIdOrderByCreatedAtDesc(testTourist.getId())
                    .forEach(s -> sosRequestRepository.delete(s));
            userRepository.delete(testTourist);
            testTourist = null;
        }
    }

    @Test
    @DisplayName("clientRequestId unique constraint prevents duplicate SOS insertion in real database")
    void clientRequestId_uniqueConstraint_preventsDuplicateInsertion() {
        testTourist = new User("tourist_int_" + UUID.randomUUID().toString().substring(0, 8),
                "tourist_int_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "hash", "Tourist Integration", "+919876543299", getOrCreateTouristRole());
        testTourist = userRepository.saveAndFlush(testTourist);

        UUID clientRequestId = UUID.randomUUID();
        SosRequest first = new SosRequest(testTourist, new BigDecimal("12.9716"), new BigDecimal("77.5946"), SosStatus.PENDING, clientRequestId);
        sosRequestRepository.saveAndFlush(first);

        SosRequest duplicate = new SosRequest(testTourist, new BigDecimal("12.9720"), new BigDecimal("77.5950"), SosStatus.PENDING, clientRequestId);
        assertThatThrownBy(() -> sosRequestRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("concurrent createSos for same tourist: SELECT FOR UPDATE serializes transactions, allowing exactly 1 active SOS")
    void concurrentCreateSos_forSameTourist_allowsOnlyOneActiveSos() throws Exception {
        testTourist = new User("tourist_conc_" + UUID.randomUUID().toString().substring(0, 8),
                "tourist_conc_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "hash", "Tourist Concurrent", "+919876543298", getOrCreateTouristRole());
        testTourist = userRepository.saveAndFlush(testTourist);

        final UUID touristId = testTourist.getId();
        final int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        // Two distinct clientRequestIds targeting the same tourist
        UUID reqId1 = UUID.randomUUID();
        UUID reqId2 = UUID.randomUUID();

        Callable<SosResponse> task1 = () -> {
            startLatch.await();
            return sosTransactionHelper.createSosInTransaction(
                    touristId,
                    new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), reqId1)
            );
        };

        Callable<SosResponse> task2 = () -> {
            startLatch.await();
            return sosTransactionHelper.createSosInTransaction(
                    touristId,
                    new CreateSosRequest(new BigDecimal("12.9718"), new BigDecimal("77.5948"), reqId2)
            );
        };

        List<Future<SosResponse>> futures = new ArrayList<>();
        futures.add(executor.submit(task1));
        futures.add(executor.submit(task2));

        // Fire both threads simultaneously
        startLatch.countDown();

        int successCount = 0;
        int conflictCount = 0;

        for (Future<SosResponse> future : futures) {
            try {
                SosResponse resp = future.get(10, TimeUnit.SECONDS);
                assertThat(resp).isNotNull();
                assertThat(resp.status()).isEqualTo(SosStatus.PENDING);
                successCount++;
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ConflictException) {
                    assertThat(e.getCause().getMessage())
                            .contains("An active SOS alert already exists for this tourist");
                    conflictCount++;
                } else {
                    throw e;
                }
            }
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Exactly one concurrent thread must succeed, and one must receive ConflictException
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);

        // Verify database state: exactly one active SOS exists for this tourist
        List<SosRequest> allUserSos = sosRequestRepository.findAllByUserIdOrderByCreatedAtDesc(touristId);
        List<SosRequest> activeInDb = allUserSos.stream()
                .filter(s -> ACTIVE_STATUSES.contains(s.getStatus()))
                .toList();
        assertThat(activeInDb).hasSize(1);
    }

    @Test
    @DisplayName("concurrent createSos with same clientRequestId: idempotency guarantees single creation")
    void concurrentCreateSos_withSameClientRequestId_isIdempotent() throws Exception {
        testTourist = new User("tourist_idem_" + UUID.randomUUID().toString().substring(0, 8),
                "tourist_idem_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                "hash", "Tourist Idempotent", "+919876543297", getOrCreateTouristRole());
        testTourist = userRepository.saveAndFlush(testTourist);

        final UUID touristId = testTourist.getId();
        final UUID sharedRequestId = UUID.randomUUID();
        final int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<SosResponse> task = () -> {
            startLatch.await();
            return sosTransactionHelper.createSosInTransaction(
                    touristId,
                    new CreateSosRequest(new BigDecimal("12.9716"), new BigDecimal("77.5946"), sharedRequestId)
            );
        };

        Future<SosResponse> f1 = executor.submit(task);
        Future<SosResponse> f2 = executor.submit(task);

        startLatch.countDown();

        SosResponse resp1 = f1.get(10, TimeUnit.SECONDS);
        SosResponse resp2 = f2.get(10, TimeUnit.SECONDS);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        // Both must return the same SOS alert
        assertThat(resp1).isNotNull();
        assertThat(resp2).isNotNull();
        assertThat(resp1.sosId()).isEqualTo(resp2.sosId());
        assertThat(resp1.clientRequestId()).isEqualTo(sharedRequestId);

        // Database must contain exactly one active record
        List<SosRequest> allUserSos = sosRequestRepository.findAllByUserIdOrderByCreatedAtDesc(touristId);
        List<SosRequest> activeInDb = allUserSos.stream()
                .filter(s -> ACTIVE_STATUSES.contains(s.getStatus()))
                .toList();
        assertThat(activeInDb).hasSize(1);
    }
}
