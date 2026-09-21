package com.geoshield.notification.service;

import com.geoshield.notification.entity.SachetAlert;
import java.io.StringReader;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Robust, hardened parser for NDMA SACHET Common Alerting Protocol (CAP 1.2) XML feeds.
 * Includes complete XXE and entity expansion defenses.
 */
@Component
public class SachetCapXmlParser {

    private final DocumentBuilderFactory factory;

    public SachetCapXmlParser() {
        try {
            factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure secure XML parser for SACHET CAP", e);
        }
    }

    /**
     * Parses a CAP 1.2 XML string into a SachetAlert entity.
     * Throws IllegalArgumentException on invalid or malformed XML.
     */
    public SachetAlert parse(String capXml) {
        if (capXml == null || capXml.isBlank()) {
            throw new IllegalArgumentException("CAP XML payload must not be null or empty");
        }

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(capXml)));
            Element alertElem = doc.getDocumentElement();

            if (!"alert".equalsIgnoreCase(alertElem.getLocalName()) && !"alert".equalsIgnoreCase(alertElem.getTagName())) {
                throw new IllegalArgumentException("Root element must be <alert>");
            }

            String identifier = getRequiredChildText(alertElem, "identifier");
            String sender = getRequiredChildText(alertElem, "sender");
            String sentStr = getRequiredChildText(alertElem, "sent");
            Instant sentAt = parseTimestamp(sentStr);
            String status = getRequiredChildText(alertElem, "status");
            String msgType = getRequiredChildText(alertElem, "msgType");
            String references = getOptionalChildText(alertElem, "references");

            Element infoElem = getFirstChildElement(alertElem, "info");
            if (infoElem == null) {
                throw new IllegalArgumentException("CAP alert must contain at least one <info> block");
            }

            String category = getRequiredChildText(infoElem, "category");
            String event = getRequiredChildText(infoElem, "event");
            String urgency = getRequiredChildText(infoElem, "urgency");
            String severity = getRequiredChildText(infoElem, "severity");
            String certainty = getRequiredChildText(infoElem, "certainty");

            String effectiveStr = getOptionalChildText(infoElem, "effective");
            Instant effectiveAt = effectiveStr != null ? parseTimestamp(effectiveStr) : sentAt;

            String expiresStr = getRequiredChildText(infoElem, "expires");
            Instant expiresAt = parseTimestamp(expiresStr);

            String headline = getOptionalChildText(infoElem, "headline");
            String description = getOptionalChildText(infoElem, "description");
            String instruction = getOptionalChildText(infoElem, "instruction");

            List<Element> areaElements = getChildElements(infoElem, "area");
            if (areaElements.isEmpty()) {
                throw new IllegalArgumentException("CAP alert <info> must contain at least one <area> block");
            }

            List<String> areaDescs = new java.util.ArrayList<>();
            List<String> normalizedGeometries = new java.util.ArrayList<>();

            for (Element areaElem : areaElements) {
                String aDesc = getOptionalChildText(areaElem, "areaDesc");
                if (aDesc != null && !aDesc.isBlank()) {
                    areaDescs.add(aDesc.trim());
                }

                // Process all circles in this area
                List<Element> circles = getChildElements(areaElem, "circle");
                for (Element circleElem : circles) {
                    String raw = circleElem.getTextContent();
                    if (raw != null && !raw.isBlank()) {
                        normalizedGeometries.add("CIRCLE:" + normalizeCircle(raw));
                    }
                }

                // Process all polygons in this area
                List<Element> polygons = getChildElements(areaElem, "polygon");
                for (Element polygonElem : polygons) {
                    String raw = polygonElem.getTextContent();
                    if (raw != null && !raw.isBlank()) {
                        normalizedGeometries.add("POLYGON:" + normalizePolygon(raw));
                    }
                }
            }

            if (normalizedGeometries.isEmpty()) {
                throw new IllegalArgumentException("CAP alert <area> must contain at least one <circle> or <polygon>");
            }

            String geometryType;
            String geometryData;

            if (normalizedGeometries.size() == 1) {
                String single = normalizedGeometries.get(0);
                if (single.startsWith("CIRCLE:")) {
                    geometryType = "CIRCLE";
                    geometryData = single.substring(7);
                } else {
                    geometryType = "POLYGON";
                    geometryData = single.substring(8);
                }
            } else {
                geometryType = "MULTI";
                geometryData = String.join(";", normalizedGeometries);
            }

            String combinedAreaDesc = areaDescs.isEmpty() ? null : String.join("; ", areaDescs);
            if (combinedAreaDesc != null && combinedAreaDesc.length() > 500) {
                combinedAreaDesc = combinedAreaDesc.substring(0, 497) + "...";
            }

            boolean isSynthetic = "Test".equalsIgnoreCase(status)
                    || "Exercise".equalsIgnoreCase(status)
                    || identifier.toUpperCase().startsWith("TEST-")
                    || identifier.toUpperCase().startsWith("SYNTHETIC-");

            return new SachetAlert(
                    identifier,
                    sender,
                    sentAt,
                    status,
                    msgType,
                    references,
                    category,
                    event,
                    urgency,
                    severity,
                    certainty,
                    effectiveAt,
                    expiresAt,
                    headline,
                    description,
                    instruction,
                    combinedAreaDesc,
                    geometryType,
                    geometryData,
                    isSynthetic,
                    "Cancel".equalsIgnoreCase(msgType)
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse CAP 1.2 XML payload: " + e.getMessage(), e);
        }
    }

    private String normalizeCircle(String rawCircle) {
        if (rawCircle == null || rawCircle.isBlank()) {
            throw new IllegalArgumentException("CAP <circle> must not be empty");
        }
        String trimmed = rawCircle.trim();
        double lat;
        double lon;
        double radiusKm;

        String normalized;
        // Try standard CAP space-separated form: "latitude,longitude radius"
        String[] spaceTokens = trimmed.split("\\s+");
        if (spaceTokens.length == 2) {
            String[] coords = spaceTokens[0].split(",");
            if (coords.length != 2) {
                throw new IllegalArgumentException("Malformed CAP circle coordinates: " + spaceTokens[0]);
            }
            try {
                lat = Double.parseDouble(coords[0].trim());
                lon = Double.parseDouble(coords[1].trim());
                radiusKm = Double.parseDouble(spaceTokens[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Non-numeric CAP circle values in: " + trimmed, e);
            }
            normalized = coords[0].trim() + "," + coords[1].trim() + "," + spaceTokens[1].trim();
        } else {
            // Support comma-separated synthetic/test form: "latitude,longitude,radius"
            String[] commaTokens = trimmed.split(",");
            if (commaTokens.length == 3) {
                try {
                    lat = Double.parseDouble(commaTokens[0].trim());
                    lon = Double.parseDouble(commaTokens[1].trim());
                    radiusKm = Double.parseDouble(commaTokens[2].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Non-numeric CAP circle values in: " + trimmed, e);
                }
                normalized = commaTokens[0].trim() + "," + commaTokens[1].trim() + "," + commaTokens[2].trim();
            } else {
                throw new IllegalArgumentException("Malformed CAP circle geometry: " + trimmed);
            }
        }

        validateLatitude(lat);
        validateLongitude(lon);
        if (radiusKm <= 0.0) {
            throw new IllegalArgumentException("CAP circle radius must be greater than 0: " + radiusKm);
        }

        return normalized;
    }

    private String normalizePolygon(String rawPolygon) {
        if (rawPolygon == null || rawPolygon.isBlank()) {
            throw new IllegalArgumentException("CAP <polygon> must not be empty");
        }
        String[] pairs = rawPolygon.trim().split("\\s+");
        if (pairs.length < 3) {
            throw new IllegalArgumentException("CAP <polygon> must contain at least 3 coordinate pairs, found: " + pairs.length);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pairs.length; i++) {
            String[] coords = pairs[i].split(",");
            if (coords.length != 2) {
                throw new IllegalArgumentException("Malformed CAP polygon coordinate pair: " + pairs[i]);
            }
            double lat;
            double lon;
            try {
                lat = Double.parseDouble(coords[0].trim());
                lon = Double.parseDouble(coords[1].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Non-numeric CAP polygon coordinate: " + pairs[i], e);
            }
            validateLatitude(lat);
            validateLongitude(lon);
            if (i > 0) sb.append(" ");
            sb.append(coords[0].trim()).append(",").append(coords[1].trim());
        }
        return sb.toString();
    }

    private void validateLatitude(double lat) {
        if (Double.isNaN(lat) || Double.isInfinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude out of range [-90, 90]: " + lat);
        }
    }

    private void validateLongitude(double lon) {
        if (Double.isNaN(lon) || Double.isInfinite(lon) || lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Longitude out of range [-180, 180]: " + lon);
        }
    }

    private java.util.List<Element> getChildElements(Element parent, String localName) {
        java.util.List<Element> elements = new java.util.ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String ln = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (localName.equalsIgnoreCase(ln) || el.getTagName().equalsIgnoreCase(localName)
                        || el.getTagName().endsWith(":" + localName)) {
                    elements.add(el);
                }
            }
        }
        return elements;
    }

    private Instant parseTimestamp(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e1) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e2) {
                throw new IllegalArgumentException("Invalid ISO 8601 timestamp: " + text);
            }
        }
    }

    private String getRequiredChildText(Element parent, String localName) {
        String text = getOptionalChildText(parent, localName);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Missing required CAP element: <" + localName + ">");
        }
        return text;
    }

    private String getOptionalChildText(Element parent, String localName) {
        Element child = getFirstChildElement(parent, localName);
        if (child != null) {
            String text = child.getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    private Element getFirstChildElement(Element parent, String localName) {
        NodeList list = parent.getElementsByTagNameNS("*", localName);
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        list = parent.getElementsByTagName(localName);
        if (list.getLength() > 0) {
            return (Element) list.item(0);
        }
        return null;
    }
}
