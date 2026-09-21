package com.geoshield.notification.service;

import com.geoshield.notification.dto.SachetAlertSummary;
import com.geoshield.notification.entity.SachetAlert;
import com.geoshield.notification.repository.SachetAlertRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SachetAlertServiceTest {

    @Mock
    private SachetAlertRepository alertRepository;

    private SachetCapXmlParser xmlParser;
    private SachetAlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        xmlParser = new SachetCapXmlParser();
        alertService = new SachetAlertServiceImpl(alertRepository, xmlParser);
    }

    @Test
    @DisplayName("Ingest new CAP 1.2 XML alert and persist to repository")
    void ingestNewAlert() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>TEST-CAP-001</identifier>
                  <sender>admin@geoshield.test</sender>
                  <sent>2026-09-20T10:00:00Z</sent>
                  <status>Test</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Severe Cyclonic Storm</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-20T20:00:00Z</expires>
                    <headline>Test Cyclone Alert</headline>
                    <area>
                      <circle>19.80,85.80,30.0</circle>
                    </area>
                  </info>
                </alert>
                """;

        when(alertRepository.findByIdentifierAndSender("TEST-CAP-001", "admin@geoshield.test"))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(SachetAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SachetAlertSummary summary = alertService.ingestCapXml(xml);

        assertNotNull(summary);
        assertEquals("TEST-CAP-001", summary.identifier());
        assertEquals("Extreme", summary.severity());
        assertEquals("Immediate", summary.urgency());
        assertTrue(summary.isSynthetic());
        verify(alertRepository).save(any(SachetAlert.class));
    }

    @Test
    @DisplayName("Deduplicate and update existing alert with same identifier and sender")
    void updateExistingAlert() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>TEST-CAP-001</identifier>
                  <sender>admin@geoshield.test</sender>
                  <sent>2026-09-20T11:00:00Z</sent>
                  <status>Test</status>
                  <msgType>Update</msgType>
                  <info>
                    <category>Met</category>
                    <event>Severe Cyclonic Storm</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-20T22:00:00Z</expires>
                    <headline>Updated Cyclone Track</headline>
                    <area>
                      <circle>19.80,85.80,40.0</circle>
                    </area>
                  </info>
                </alert>
                """;

        SachetAlert existing = new SachetAlert(
                "TEST-CAP-001", "admin@geoshield.test", Instant.now(), "Test", "Alert",
                null, "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS), "Old Headline",
                null, null, null, "CIRCLE", "19.80,85.80,30.0", true, false
        );

        when(alertRepository.findByIdentifierAndSender("TEST-CAP-001", "admin@geoshield.test"))
                .thenReturn(Optional.of(existing));
        when(alertRepository.save(existing)).thenReturn(existing);

        SachetAlertSummary summary = alertService.ingestCapXml(xml);

        assertNotNull(summary);
        assertEquals("Updated Cyclone Track", existing.getHeadline());
        assertEquals("19.80,85.80,40.0", existing.getGeometryData());
        verify(alertRepository).save(existing);
    }

    @Test
    @DisplayName("Find applicable alert when coordinates are within circle geometry and unexpired")
    void findApplicableAlertInsideCircle() {
        Instant now = Instant.now();
        SachetAlert alert = new SachetAlert(
                "TEST-CAP-PURI", "admin@geoshield.test", now.minus(1, ChronoUnit.HOURS),
                "Test", "Alert", null, "Met", "Cyclone", "Immediate", "Extreme", "Observed",
                now.minus(1, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS),
                "Puri Cyclone", null, "Take shelter", "Puri District", "CIRCLE",
                "19.80,85.80,25.0", true, false
        );

        when(alertRepository.findActiveAlerts(now)).thenReturn(List.of(alert));

        // Coordinate inside Puri circle (~5 km away from 19.80, 85.80)
        Optional<SachetAlertSummary> insideOpt = alertService.findApplicableActiveAlert(19.82, 85.82, now);
        assertTrue(insideOpt.isPresent());
        assertEquals("TEST-CAP-PURI", insideOpt.get().identifier());

        // Coordinate outside Puri circle (> 100 km away)
        Optional<SachetAlertSummary> outsideOpt = alertService.findApplicableActiveAlert(21.00, 86.50, now);
        assertTrue(outsideOpt.isEmpty(), "Out-of-area coordinate must not match alert");
    }

    @Test
    @DisplayName("Alert does not match if expired or cancelled even if returned by repository (non-vacuous check)")
    void doNotMatchExpiredOrCancelled() {
        Instant now = Instant.now();
        SachetAlert cancelledAlert = new SachetAlert(
                "TEST-CANCELLED", "admin@geoshield.test", now.minus(1, ChronoUnit.HOURS),
                "Actual", "Alert", null, "Met", "Cyclone", "Immediate", "Extreme", "Observed",
                now.minus(1, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS),
                "Cancelled Cyclone", null, "Take shelter", "Puri District", "CIRCLE",
                "19.80,85.80,25.0", false, true // cancelled = true
        );

        SachetAlert expiredAlert = new SachetAlert(
                "TEST-EXPIRED", "admin@geoshield.test", now.minus(3, ChronoUnit.HOURS),
                "Actual", "Alert", null, "Met", "Cyclone", "Immediate", "Extreme", "Observed",
                now.minus(3, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS), // expired
                "Expired Cyclone", null, "Take shelter", "Puri District", "CIRCLE",
                "19.80,85.80,25.0", false, false
        );

        when(alertRepository.findActiveAlerts(now)).thenReturn(List.of(cancelledAlert, expiredAlert));

        Optional<SachetAlertSummary> result = alertService.findApplicableActiveAlert(19.80, 85.80, now);
        assertTrue(result.isEmpty(), "Cancelled and expired alerts must be filtered out by the service");
    }

    @Test
    @DisplayName("Cancel referenced alerts when incoming alert specifies CAP <references> (P1)")
    void cancelReferencedAlertsViaCapReferences() {
        String cancelXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>NDMA-CANCEL-001</identifier>
                  <sender>imd_alert@sachet.ndma.gov.in</sender>
                  <sent>2026-09-20T12:00:00Z</sent>
                  <status>Actual</status>
                  <msgType>Cancel</msgType>
                  <references>imd_alert@sachet.ndma.gov.in,NDMA-2026-CYC-0042,2026-09-19T06:30:00+05:30</references>
                  <info>
                    <category>Met</category>
                    <event>Severe Cyclonic Storm</event>
                    <urgency>Past</urgency>
                    <severity>Minor</severity>
                    <certainty>Unlikely</certainty>
                    <expires>2026-09-20T13:00:00Z</expires>
                    <headline>Cyclone Warning Cancelled</headline>
                    <area><circle>19.80,85.80,25.0</circle></area>
                  </info>
                </alert>
                """;

        SachetAlert activeAlert = new SachetAlert(
                "NDMA-2026-CYC-0042", "imd_alert@sachet.ndma.gov.in", Instant.now().minus(2, ChronoUnit.HOURS),
                "Actual", "Alert", null, "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now().minus(2, ChronoUnit.HOURS), Instant.now().plus(4, ChronoUnit.HOURS),
                "Cyclone Warning", null, null, null, "CIRCLE", "19.80,85.80,25.0", false, false
        );

        when(alertRepository.findByIdentifierAndSender("NDMA-2026-CYC-0042", "imd_alert@sachet.ndma.gov.in"))
                .thenReturn(Optional.of(activeAlert));
        when(alertRepository.findByIdentifierAndSender("NDMA-CANCEL-001", "imd_alert@sachet.ndma.gov.in"))
                .thenReturn(Optional.empty());
        when(alertRepository.save(any(SachetAlert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertService.ingestCapXml(cancelXml);

        assertTrue(activeAlert.isCancelled(), "Target referenced alert must be marked cancelled");
        verify(alertRepository).save(activeAlert);
    }

    @Test
    @DisplayName("Monotonic cancellation: subsequent update cannot uncancel a cancelled alert (P2)")
    void monotonicCancellationPreservedOnUpdate() {
        String updateXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>TEST-CAP-001</identifier>
                  <sender>admin@geoshield.test</sender>
                  <sent>2026-09-20T12:00:00Z</sent>
                  <status>Test</status>
                  <msgType>Update</msgType>
                  <info>
                    <category>Met</category>
                    <event>Severe Cyclonic Storm</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-20T22:00:00Z</expires>
                    <headline>Attempted Uncancel Update</headline>
                    <area><circle>19.80,85.80,30.0</circle></area>
                  </info>
                </alert>
                """;

        SachetAlert cancelledAlert = new SachetAlert(
                "TEST-CAP-001", "admin@geoshield.test", Instant.now(), "Test", "Cancel",
                null, "Met", "Severe Cyclonic Storm", "Immediate", "Extreme", "Observed",
                Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS), "Cancelled Alert",
                null, null, null, "CIRCLE", "19.80,85.80,30.0", true, true // cancelled = true
        );

        when(alertRepository.findByIdentifierAndSender("TEST-CAP-001", "admin@geoshield.test"))
                .thenReturn(Optional.of(cancelledAlert));
        when(alertRepository.save(cancelledAlert)).thenReturn(cancelledAlert);

        alertService.ingestCapXml(updateXml);

        assertTrue(cancelledAlert.isCancelled(), "Alert must remain cancelled (monotonic cancellation)");
    }

    @Test
    @DisplayName("Status gating: only Actual, or Test/Exercise when synthetic, can qualify for override (P3, P12)")
    void statusGatingRules() {
        // Actual status: qualifies if Extreme/Immediate
        SachetAlertSummary actualExtreme = new SachetAlertSummary(
                null, "ID-ACTUAL", "sender", Instant.now(), "Met", "Cyclone",
                "Immediate", "Extreme", "Observed", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                "Headline", "Desc", "Instruction", "Area", false, "Actual", "Alert"
        );
        assertTrue(alertService.isQualifyingSevereAlert(actualExtreme), "Actual status must qualify");

        // Test status with isSynthetic=true: qualifies
        SachetAlertSummary testSynthetic = new SachetAlertSummary(
                null, "ID-TEST-SYN", "sender", Instant.now(), "Met", "Cyclone",
                "Immediate", "Extreme", "Observed", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                "Headline", "Desc", "Instruction", "Area", true, "Test", "Alert"
        );
        assertTrue(alertService.isQualifyingSevereAlert(testSynthetic), "Test status with isSynthetic=true must qualify");

        // Test status with isSynthetic=false: does NOT qualify
        SachetAlertSummary testNonSynthetic = new SachetAlertSummary(
                null, "ID-TEST-NONSYN", "sender", Instant.now(), "Met", "Cyclone",
                "Immediate", "Extreme", "Observed", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                "Headline", "Desc", "Instruction", "Area", false, "Test", "Alert"
        );
        assertFalse(alertService.isQualifyingSevereAlert(testNonSynthetic), "Test status without synthetic flag must NOT qualify");

        // Draft or System status: NEVER qualifies
        SachetAlertSummary draftExtreme = new SachetAlertSummary(
                null, "ID-DRAFT", "sender", Instant.now(), "Met", "Cyclone",
                "Immediate", "Extreme", "Observed", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                "Headline", "Desc", "Instruction", "Area", false, "Draft", "Alert"
        );
        assertFalse(alertService.isQualifyingSevereAlert(draftExtreme), "Draft status must NOT qualify");

        SachetAlertSummary systemExtreme = new SachetAlertSummary(
                null, "ID-SYSTEM", "sender", Instant.now(), "Met", "Cyclone",
                "Immediate", "Extreme", "Observed", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                "Headline", "Desc", "Instruction", "Area", false, "System", "Alert"
        );
        assertFalse(alertService.isQualifyingSevereAlert(systemExtreme), "System status must NOT qualify");
    }

    @Test
    @DisplayName("Multi-area alert matches when user coordinate is within any component (P4)")
    void multiAreaAlertMatching() {
        Instant now = Instant.now();
        // Multi geometry: CIRCLE:19.80,85.80,20.0;POLYGON:12.90,77.50 12.90,77.70 13.10,77.70 13.10,77.50 12.90,77.50
        String multiGeometry = "CIRCLE:19.80,85.80,20.0;POLYGON:12.90,77.50 12.90,77.70 13.10,77.70 13.10,77.50 12.90,77.50";
        SachetAlert multiAlert = new SachetAlert(
                "MULTI-01", "sender@sachet.gov.in", now.minus(1, ChronoUnit.HOURS),
                "Actual", "Alert", null, "Met", "Severe Cyclone", "Immediate", "Extreme", "Observed",
                now.minus(1, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS),
                "Multi Headline", null, "Shelter", "Multi Area", "MULTI",
                multiGeometry, false, false
        );

        when(alertRepository.findActiveAlerts(now)).thenReturn(List.of(multiAlert));

        // Inside the circle component
        assertTrue(alertService.findApplicableActiveAlert(19.82, 85.82, now).isPresent());

        // Inside the polygon component
        assertTrue(alertService.findApplicableActiveAlert(13.00, 77.60, now).isPresent());

        // Outside both components
        assertTrue(alertService.findApplicableActiveAlert(28.61, 77.20, now).isEmpty());
    }

    @Test
    @DisplayName("Point-in-polygon matching on edge and vertex (P8)")
    void pointInPolygonEdgeAndVertex() {
        Instant now = Instant.now();
        String polygonGeom = "12.00,77.00 12.00,78.00 13.00,78.00 13.00,77.00 12.00,77.00";
        SachetAlert polyAlert = new SachetAlert(
                "POLY-01", "sender@sachet.gov.in", now.minus(1, ChronoUnit.HOURS),
                "Actual", "Alert", null, "Met", "Flood", "Immediate", "Extreme", "Observed",
                now.minus(1, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS),
                "Poly Headline", null, "Evacuate", "Poly Area", "POLYGON",
                polygonGeom, false, false
        );

        when(alertRepository.findActiveAlerts(now)).thenReturn(List.of(polyAlert));

        // Exactly on a vertex (12.00, 77.00)
        assertTrue(alertService.findApplicableActiveAlert(12.00, 77.00, now).isPresent(), "Vertex must match");

        // Exactly on an edge (12.00, 77.50)
        assertTrue(alertService.findApplicableActiveAlert(12.00, 77.50, now).isPresent(), "Edge must match");

        // Inside polygon (12.50, 77.50)
        assertTrue(alertService.findApplicableActiveAlert(12.50, 77.50, now).isPresent(), "Interior must match");

        // Outside polygon (14.00, 79.00)
        assertTrue(alertService.findApplicableActiveAlert(14.00, 79.00, now).isEmpty(), "Exterior must not match");
    }

    @Test
    @DisplayName("Alert at exact expiresAt boundary (now == expiresAt) must be treated as expired (effectiveAt <= now < expiresAt)")
    void exactExpiresAtBoundaryTreatedAsExpired() {
        Instant effectiveAt = Instant.parse("2026-09-20T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-20T12:00:00Z");

        SachetAlert alert = new SachetAlert(
                "TEST-EXPIRY-001", "admin@geoshield.test", effectiveAt,
                "Actual", "Alert", null, "Met", "Severe Cyclone", "Immediate", "Extreme", "Observed",
                effectiveAt, expiresAt,
                "Cyclone Warning", null, "Evacuate", "Puri Area", "CIRCLE",
                "19.80,85.80,25.0", false, false
        );

        // 1. One millisecond before expiresAt: now < expiresAt -> active and matches
        Instant justBeforeExpiry = expiresAt.minusMillis(1);
        when(alertRepository.findActiveAlerts(justBeforeExpiry)).thenReturn(List.of(alert));
        Optional<SachetAlertSummary> beforeExpiryOpt = alertService.findApplicableActiveAlert(19.80, 85.80, justBeforeExpiry);
        assertTrue(beforeExpiryOpt.isPresent(), "Alert must be active when now < expiresAt");

        // 2. Exactly at expiresAt (now == expiresAt) -> MUST be treated as expired
        Instant exactExpiry = expiresAt;
        // Even if repository returned it, the service stream filter must strictly enforce now < expiresAt
        when(alertRepository.findActiveAlerts(exactExpiry)).thenReturn(List.of(alert));
        Optional<SachetAlertSummary> exactExpiryOpt = alertService.findApplicableActiveAlert(19.80, 85.80, exactExpiry);
        assertTrue(exactExpiryOpt.isEmpty(), "Alert must be treated as expired when now == expiresAt");

        // 3. One millisecond after expiresAt: now > expiresAt -> expired
        Instant justAfterExpiry = expiresAt.plusMillis(1);
        when(alertRepository.findActiveAlerts(justAfterExpiry)).thenReturn(List.of(alert));
        Optional<SachetAlertSummary> afterExpiryOpt = alertService.findApplicableActiveAlert(19.80, 85.80, justAfterExpiry);
        assertTrue(afterExpiryOpt.isEmpty(), "Alert must be treated as expired when now > expiresAt");
    }

    @Test
    @DisplayName("Alert at exact effectiveAt boundary (now == effectiveAt) is active if now < expiresAt")
    void exactEffectiveAtBoundaryIsActive() {
        Instant effectiveAt = Instant.parse("2026-09-20T10:00:00Z");
        Instant expiresAt = Instant.parse("2026-09-20T12:00:00Z");

        SachetAlert alert = new SachetAlert(
                "TEST-EFFECTIVE-001", "admin@geoshield.test", effectiveAt,
                "Actual", "Alert", null, "Met", "Severe Cyclone", "Immediate", "Extreme", "Observed",
                effectiveAt, expiresAt,
                "Cyclone Warning", null, "Evacuate", "Puri Area", "CIRCLE",
                "19.80,85.80,25.0", false, false
        );

        // At exact effectiveAt: effectiveAt <= now < expiresAt is satisfied
        when(alertRepository.findActiveAlerts(effectiveAt)).thenReturn(List.of(alert));
        Optional<SachetAlertSummary> effectiveOpt = alertService.findApplicableActiveAlert(19.80, 85.80, effectiveAt);
        assertTrue(effectiveOpt.isPresent(), "Alert must be active when now == effectiveAt");

        // One millisecond before effectiveAt: not yet active
        Instant beforeEffective = effectiveAt.minusMillis(1);
        when(alertRepository.findActiveAlerts(beforeEffective)).thenReturn(List.of(alert));
        Optional<SachetAlertSummary> beforeEffectiveOpt = alertService.findApplicableActiveAlert(19.80, 85.80, beforeEffective);
        assertTrue(beforeEffectiveOpt.isEmpty(), "Alert must NOT be active before effectiveAt");
    }

    @Test
    @DisplayName("Verify qualifying severe alert logic matches approved Option A specification")
    void verifyQualifyingSevereAlertRules() {
        SachetAlertSummary extremeImmediate = createSummary("Extreme", "Immediate");
        SachetAlertSummary severeExpected = createSummary("Severe", "Expected");
        SachetAlertSummary moderateImmediate = createSummary("Moderate", "Immediate");
        SachetAlertSummary minorExpected = createSummary("Minor", "Expected");
        SachetAlertSummary severeFuture = createSummary("Severe", "Future");

        assertTrue(alertService.isQualifyingSevereAlert(extremeImmediate), "Extreme/Immediate must qualify for override");
        assertTrue(alertService.isQualifyingSevereAlert(severeExpected), "Severe/Expected must qualify for override");
        assertFalse(alertService.isQualifyingSevereAlert(moderateImmediate), "Moderate alerts are advisories, must NOT qualify for override");
        assertFalse(alertService.isQualifyingSevereAlert(minorExpected), "Minor alerts are advisories, must NOT qualify for override");
        assertFalse(alertService.isQualifyingSevereAlert(severeFuture), "Future urgency does not trigger immediate emergency override");
    }

    private SachetAlertSummary createSummary(String severity, String urgency) {
        return new SachetAlertSummary(
                null, "ID-1", "sender", Instant.now(), "Met", "Event",
                urgency, severity, "Observed", Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                "Headline", "Desc", "Instruction", "Area", false, "Actual", "Alert"
        );
    }
}
