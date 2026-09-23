package com.geoshield.notification.service;

import com.geoshield.notification.entity.SachetAlert;
import java.time.Instant;
import java.util.TimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SachetCapXmlParserTest {

    private SachetCapXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new SachetCapXmlParser();
    }

    @Test
    @DisplayName("Successfully parse valid CAP 1.2 XML with circular area geometry")
    void parseValidCapCircularXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>NDMA-2026-CYC-0042</identifier>
                  <sender>imd_alert@sachet.ndma.gov.in</sender>
                  <sent>2026-09-19T06:30:00+05:30</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Severe Cyclonic Storm</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <effective>2026-09-19T06:30:00+05:30</effective>
                    <expires>2026-09-20T18:00:00+05:30</expires>
                    <headline>Cyclone Warning for Coastal Odisha</headline>
                    <description>Extremely severe cyclonic storm approaching coast with heavy rain.</description>
                    <instruction>Move to designated cyclone shelters immediately. Avoid coastal roads.</instruction>
                    <area>
                      <areaDesc>Coastal Puri and Jagatsinghpur districts</areaDesc>
                      <circle>19.8135,85.8312,50.0</circle>
                    </area>
                  </info>
                </alert>
                """;

        SachetAlert alert = parser.parse(xml);

        assertNotNull(alert);
        assertEquals("NDMA-2026-CYC-0042", alert.getIdentifier());
        assertEquals("imd_alert@sachet.ndma.gov.in", alert.getSender());
        assertEquals("Actual", alert.getStatus());
        assertEquals("Alert", alert.getMsgType());
        assertEquals("Met", alert.getCategory());
        assertEquals("Severe Cyclonic Storm", alert.getEvent());
        assertEquals("Immediate", alert.getUrgency());
        assertEquals("Extreme", alert.getSeverity());
        assertEquals("Observed", alert.getCertainty());
        assertEquals("CIRCLE", alert.getGeometryType());
        assertEquals("19.8135,85.8312,50.0", alert.getGeometryData());
        assertEquals("Cyclone Warning for Coastal Odisha", alert.getHeadline());
        assertFalse(alert.isSynthetic());
        assertFalse(alert.isCancelled());
    }

    @Test
    @DisplayName("Successfully parse valid CAP 1.2 XML with polygon area geometry and synthetic marker")
    void parseValidCapPolygonSyntheticXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>TEST-CAP-FLD-001</identifier>
                  <sender>test_admin@geoshield.org</sender>
                  <sent>2026-09-20T10:00:00Z</sent>
                  <status>Test</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Safety</category>
                    <event>Flash Flood Warning</event>
                    <urgency>Expected</urgency>
                    <severity>Severe</severity>
                    <certainty>Likely</certainty>
                    <expires>2026-09-21T10:00:00Z</expires>
                    <headline>[TEST FIXTURE] Simulated Flash Flood</headline>
                    <instruction>Evacuate low lying areas.</instruction>
                    <area>
                      <areaDesc>Simulated Flood Polygon</areaDesc>
                      <polygon>12.90,77.50 12.90,77.70 13.10,77.70 13.10,77.50 12.90,77.50</polygon>
                    </area>
                  </info>
                </alert>
                """;

        SachetAlert alert = parser.parse(xml);

        assertNotNull(alert);
        assertEquals("TEST-CAP-FLD-001", alert.getIdentifier());
        assertTrue(alert.isSynthetic(), "Test/Synthetic identifiers must have isSynthetic=true");
        assertEquals("POLYGON", alert.getGeometryType());
        assertEquals("12.90,77.50 12.90,77.70 13.10,77.70 13.10,77.50 12.90,77.50", alert.getGeometryData());
    }

    @Test
    @DisplayName("Reject XML containing DOCTYPE entity expansion (XXE attack protection)")
    void rejectXxePayload() {
        String xxeXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE alert [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>&xxe;</identifier>
                  <sender>attacker@evil.com</sender>
                  <sent>2026-09-19T06:30:00Z</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Exploit</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-20T18:00:00Z</expires>
                    <area>
                      <circle>0,0,10</circle>
                    </area>
                  </info>
                </alert>
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(xxeXml),
                "Parser must disallow DOCTYPE declaration to prevent XXE");
    }

    @Test
    @DisplayName("Successfully parse CAP 1.2 XML with standard space-separated circle geometry (lat,lon radius)")
    void parseStandardSpaceSeparatedCircle() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>CAP-SPACE-CIRCLE-01</identifier>
                  <sender>imd@sachet.gov.in</sender>
                  <sent>2026-09-20T10:00:00Z</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Cyclone</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-21T10:00:00Z</expires>
                    <area>
                      <circle>19.8135,85.8312 25.5</circle>
                    </area>
                  </info>
                </alert>
                """;

        SachetAlert alert = parser.parse(xml);
        assertNotNull(alert);
        assertEquals("CIRCLE", alert.getGeometryType());
        assertEquals("19.8135,85.8312,25.5", alert.getGeometryData());
    }

    @Test
    @DisplayName("Reject invalid circle geometries (out-of-range coordinates or negative radius)")
    void rejectInvalidCircleGeometries() {
        String invalidLatXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>INVALID-LAT</identifier>
                  <sender>imd@sachet.gov.in</sender>
                  <sent>2026-09-20T10:00:00Z</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Test</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-21T10:00:00Z</expires>
                    <area><circle>95.0,85.0 20.0</circle></area>
                  </info>
                </alert>
                """;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(invalidLatXml));

        String negativeRadiusXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>INVALID-RAD</identifier>
                  <sender>imd@sachet.gov.in</sender>
                  <sent>2026-09-20T10:00:00Z</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Test</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-21T10:00:00Z</expires>
                    <area><circle>19.0,85.0 -5.0</circle></area>
                  </info>
                </alert>
                """;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(negativeRadiusXml));
    }

    @Test
    @DisplayName("Successfully parse multi-area CAP XML combining multiple areas into MULTI geometry")
    void parseMultiAreaXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>MULTI-AREA-01</identifier>
                  <sender>ndma@sachet.gov.in</sender>
                  <sent>2026-09-20T10:00:00Z</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Severe Cyclone</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-21T10:00:00Z</expires>
                    <area>
                      <areaDesc>Puri Coastal Zone</areaDesc>
                      <circle>19.81,85.83 30.0</circle>
                    </area>
                    <area>
                      <areaDesc>Jagatsinghpur Inundation Zone</areaDesc>
                      <polygon>19.90,86.10 19.90,86.30 20.10,86.30 20.10,86.10 19.90,86.10</polygon>
                    </area>
                  </info>
                </alert>
                """;

        SachetAlert alert = parser.parse(xml);
        assertNotNull(alert);
        assertEquals("MULTI", alert.getGeometryType());
        assertTrue(alert.getGeometryData().contains("CIRCLE:19.81,85.83,30.0"));
        assertTrue(alert.getGeometryData().contains("POLYGON:19.90,86.10 19.90,86.30 20.10,86.30 20.10,86.10 19.90,86.10"));
        assertTrue(alert.getAreaDesc().contains("Puri Coastal Zone"));
        assertTrue(alert.getAreaDesc().contains("Jagatsinghpur Inundation Zone"));
    }

    @Test
    @DisplayName("Reject XML containing polygon with fewer than 3 vertices")
    void rejectDegeneratePolygon() {
        String degeneratePolygonXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>DEGENERATE-POLY</identifier>
                  <sender>test@geoshield.org</sender>
                  <sent>2026-09-20T10:00:00Z</sent>
                  <status>Test</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Safety</category>
                    <event>Test</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-21T10:00:00Z</expires>
                    <area>
                      <polygon>12.0,77.0 12.5,77.5</polygon>
                    </area>
                  </info>
                </alert>
                """;

        assertThrows(IllegalArgumentException.class, () -> parser.parse(degeneratePolygonXml));
    }

    @Test
    @DisplayName("Reject null, empty, or malformed XML")
    void rejectMalformedXml() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("<not-alert></not-alert>"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("<alert>incomplete</alert>"));
    }

    @Test
    @DisplayName("Successfully parse client-generated CAP 1.2 XML broadcast with escaped entities")
    void parseClientGeneratedCapXml() {
        String clientXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>NDMA-2026-TEST &amp; 01</identifier>
                  <sender>admin &lt;ops&gt;@sachet.gov.in</sender>
                  <sent>2026-09-23T14:30:00Z</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Severe Thunderstorm &amp; Gale</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <effective>2026-09-23T14:30:00Z</effective>
                    <expires>2026-09-24T18:00:00Z</expires>
                    <headline>Thunderstorm &quot;Red Alert&quot;</headline>
                    <description>Heavy squalls &amp; lightning.</description>
                    <instruction>Take shelter &amp; avoid trees.</instruction>
                    <area>
                      <areaDesc>Zone A &amp; B</areaDesc>
                      <circle>11.0168,76.9558 15.0</circle>
                    </area>
                  </info>
                </alert>
                """;

        SachetAlert alert = parser.parse(clientXml);
        assertNotNull(alert);
        assertEquals("NDMA-2026-TEST & 01", alert.getIdentifier());
        assertEquals("admin <ops>@sachet.gov.in", alert.getSender());
        assertEquals("Severe Thunderstorm & Gale", alert.getEvent());
        assertEquals("Extreme", alert.getSeverity());
        assertEquals("Immediate", alert.getUrgency());
        assertEquals("CIRCLE", alert.getGeometryType());
        assertEquals("11.0168,76.9558,15.0", alert.getGeometryData());
        assertEquals("Thunderstorm \"Red Alert\"", alert.getHeadline());
        assertEquals("Take shelter & avoid trees.", alert.getInstruction());
    }

    @Test
    @DisplayName("Successfully parse client-generated CAP 1.2 Cancel XML broadcast with references")
    void parseClientGeneratedCancelCapXml() {
        String cancelXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>CANCEL-NDMA-2026-CYC-0042</identifier>
                  <sender>imd_alert@sachet.ndma.gov.in</sender>
                  <sent>2026-09-23T15:00:00Z</sent>
                  <status>Actual</status>
                  <msgType>Cancel</msgType>
                  <references>imd_alert@sachet.ndma.gov.in,NDMA-2026-CYC-0042,2026-09-23T10:00:00Z</references>
                  <info>
                    <category>Met</category>
                    <event>Severe Cyclonic Storm</event>
                    <urgency>Past</urgency>
                    <severity>Minor</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-23T15:05:00Z</expires>
                    <headline>Cancellation of NDMA-2026-CYC-0042</headline>
                    <description>Administrative cancellation of broadcast NDMA-2026-CYC-0042</description>
                    <area>
                      <areaDesc>Puri and Jagatsinghpur</areaDesc>
                      <circle>0.0,0.0 0.1</circle>
                    </area>
                  </info>
                </alert>
                """;

        SachetAlert alert = parser.parse(cancelXml);
        assertNotNull(alert);
        assertEquals("CANCEL-NDMA-2026-CYC-0042", alert.getIdentifier());
        assertEquals("Cancel", alert.getMsgType());
        assertTrue(alert.isCancelled());
        assertEquals("imd_alert@sachet.ndma.gov.in,NDMA-2026-CYC-0042,2026-09-23T10:00:00Z", alert.getReferencesIdentifier());
    }

    @Test
    @DisplayName("Parse timestamps with UTC 'Z' designation accurately to Instant")
    void parseTimestampWithUtcZ() {
        Instant expected = Instant.parse("2026-09-20T10:00:00Z");
        assertEquals(expected, parser.parseTimestamp("2026-09-20T10:00:00Z"));
        assertEquals(expected, parser.parseTimestamp("  2026-09-20T10:00:00Z  "));

        Instant withFraction = Instant.parse("2026-09-20T10:00:00.123Z");
        assertEquals(withFraction, parser.parseTimestamp("2026-09-20T10:00:00.123Z"));
    }

    @Test
    @DisplayName("Parse timestamps with explicit timezone offsets accurately without system timezone dependency")
    void parseTimestampWithExplicitOffsets() {
        // Indian Standard Time (+05:30)
        Instant istExpected = Instant.parse("2026-09-19T01:00:00Z");
        assertEquals(istExpected, parser.parseTimestamp("2026-09-19T06:30:00+05:30"));
        assertEquals(istExpected, parser.parseTimestamp("2026-09-19T06:30:00+0530"));

        // UTC explicit offset (+00:00)
        Instant utcExpected = Instant.parse("2026-09-19T06:30:00Z");
        assertEquals(utcExpected, parser.parseTimestamp("2026-09-19T06:30:00+00:00"));

        // US Eastern Daylight Time (-04:00)
        Instant edtExpected = Instant.parse("2026-09-19T10:30:00Z");
        assertEquals(edtExpected, parser.parseTimestamp("2026-09-19T06:30:00-04:00"));
    }

    @Test
    @DisplayName("Strictly reject timestamps lacking explicit timezone offset with IllegalArgumentException")
    void rejectTimestampWithoutTimezoneOrOffset() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parser.parseTimestamp("2026-09-20T10:00:00"));
        assertTrue(ex.getMessage().contains("Timestamps must include an explicit timezone offset"));

        assertThrows(IllegalArgumentException.class,
                () -> parser.parseTimestamp("2026-09-20T10:00:00.500"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseTimestamp(null));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseTimestamp("   "));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseTimestamp("not-a-timestamp"));
    }

    @Test
    @DisplayName("Reject entire CAP XML alert if timestamp lacks timezone offset")
    void rejectAlertXmlWithTimezonelessTimestamp() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
                  <identifier>NO-TZ-001</identifier>
                  <sender>ops@sachet.gov.in</sender>
                  <sent>2026-09-20T10:00:00</sent>
                  <status>Actual</status>
                  <msgType>Alert</msgType>
                  <info>
                    <category>Met</category>
                    <event>Storm</event>
                    <urgency>Immediate</urgency>
                    <severity>Extreme</severity>
                    <certainty>Observed</certainty>
                    <expires>2026-09-21T10:00:00Z</expires>
                    <area><circle>11.0,76.0 10.0</circle></area>
                  </info>
                </alert>
                """;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> parser.parse(xml));
        assertTrue(ex.getMessage().contains("Timestamps must include an explicit timezone offset"));
    }

    @Test
    @DisplayName("Prove timestamp parsing is 100% deterministic regardless of JVM/machine timezone")
    void timestampParsingIsDeterministicAcrossMachineTimezones() {
        TimeZone originalTz = TimeZone.getDefault();
        String[] probeTimeZones = {
                "UTC",
                "Asia/Kolkata",
                "America/New_York",
                "Pacific/Honolulu",
                "Europe/London",
                "Australia/Sydney",
                "Asia/Tokyo"
        };

        try {
            for (String tzId : probeTimeZones) {
                TimeZone.setDefault(TimeZone.getTimeZone(tzId));

                // 1. Z timestamp parses identically in all machine timezones
                Instant zResult = parser.parseTimestamp("2026-09-20T10:00:00Z");
                assertEquals(Instant.parse("2026-09-20T10:00:00Z"), zResult,
                        "Mismatch under machine timezone: " + tzId);

                // 2. Explicit +05:30 offset parses identically in all machine timezones
                Instant istResult = parser.parseTimestamp("2026-09-19T06:30:00+05:30");
                assertEquals(Instant.parse("2026-09-19T01:00:00Z"), istResult,
                        "Mismatch under machine timezone: " + tzId);

                // 3. Explicit -05:00 offset parses identically in all machine timezones
                Instant estResult = parser.parseTimestamp("2026-09-19T06:30:00-05:00");
                assertEquals(Instant.parse("2026-09-19T11:30:00Z"), estResult,
                        "Mismatch under machine timezone: " + tzId);

                // 4. Timezone-less timestamp is rejected consistently under every machine timezone
                assertThrows(IllegalArgumentException.class,
                        () -> parser.parseTimestamp("2026-09-20T10:00:00"),
                        "Timezone-less timestamp must be rejected under machine timezone: " + tzId);
            }
        } finally {
            TimeZone.setDefault(originalTz);
        }
    }
}
