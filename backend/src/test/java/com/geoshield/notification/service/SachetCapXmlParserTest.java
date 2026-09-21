package com.geoshield.notification.service;

import com.geoshield.notification.entity.SachetAlert;
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
}
