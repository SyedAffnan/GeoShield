package com.geoshield.risk.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Verifies the bundled boundary resource loads from the classpath and matches MoRTH naming. */
class StateBoundaryIndexTest {
    /**
     * The exact 36 State/UT {@code geographicUnit} values the MoRTH Annexure-4 import
     * produces. The derived boundary resource must agree with these character for
     * character, because HistoricalRiskFeatureService matches on the name.
     */
    private static final Set<String> MORTH_STATE_UT_UNITS = Set.of(
            "Andaman and Nicobar Islands", "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar",
            "Chandigarh", "Chhattisgarh", "Dadra and Nagar Haveli and Daman and Diu", "Delhi", "Goa",
            "Gujarat", "Haryana", "Himachal Pradesh", "Jammu and Kashmir", "Jharkhand", "Karnataka",
            "Kerala", "Ladakh", "Lakshadweep", "Madhya Pradesh", "Maharashtra", "Manipur", "Meghalaya",
            "Mizoram", "Nagaland", "Odisha", "Puducherry", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu",
            "Telangana", "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal");

    private static StateBoundaryIndex index;

    @BeforeAll
    static void loadOnce() {
        index = new StateBoundaryIndex();
    }

    @Test
    void loadsEveryStateUtPolygonFromTheBundledClasspathResource() {
        assertEquals(36, index.stateCount());
        assertTrue(index.totalVertexCount() > 100_000,
                "expected a detailed boundary set, got " + index.totalVertexCount() + " vertices");
    }

    @Test
    void everyLoadedStateNameIsExactlyAMorthGeographicUnit() {
        Set<String> loaded = new TreeSet<>(index.stateNames());
        assertEquals(new TreeSet<>(MORTH_STATE_UT_UNITS), loaded,
                "boundary state_name values must match MoRTH geographicUnit values exactly");
    }

    @Test
    void appliesTheFourExplicitStateNameNormalizations() {
        List<String> names = index.stateNames();
        // The MoRTH-canonical spellings must be present...
        assertTrue(names.contains("Andaman and Nicobar Islands"));
        assertTrue(names.contains("Arunachal Pradesh"));
        assertTrue(names.contains("Dadra and Nagar Haveli and Daman and Diu"));
        assertTrue(names.contains("Jammu and Kashmir"));
        // ...and no raw Survey of India spelling may survive.
        assertTrue(names.stream().noneMatch(name -> name.contains("&")), "no '&' spelling may remain");
        assertTrue(names.stream().noneMatch(name -> name.contains("Arunanchal")), "source misspelling must be fixed");
        assertTrue(names.stream().noneMatch(name -> name.contains("Havelli")), "source misspelling must be fixed");
        assertTrue(names.stream().noneMatch(name -> name.equals("Andaman and Nicobar Island")), "must be plural");
    }

    @Test
    void reportsAClearErrorWhenTheBoundaryResourceIsAbsent() {
        var failure = assertThrows(IllegalStateException.class, () -> new StateBoundaryIndex("geo/does-not-exist.geojson"));
        assertTrue(failure.getMessage().contains("missing from the classpath"));
    }

    @Test
    void resolvesUsingClasspathResourceWithoutAnyAbsoluteFilesystemPath() throws IOException {
        // The configured location must be classpath-relative: no drive letter, no leading slash.
        assertTrue(StateBoundaryIndex.RESOURCE_PATH.matches("[A-Za-z0-9._/-]+"));
        assertTrue(!StateBoundaryIndex.RESOURCE_PATH.startsWith("/") && !StateBoundaryIndex.RESOURCE_PATH.contains(":"));

        // And no main source or configuration file may hard-code a Windows absolute path,
        // so the application can never depend on a personal D: drive location.
        Path mainSources = Path.of("src", "main");
        assertTrue(Files.isDirectory(mainSources), "expected to run from the backend module directory");
        try (Stream<Path> files = Files.walk(mainSources)) {
            List<String> offenders = files.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".java") || name.endsWith(".yml")
                                || name.endsWith(".yaml") || name.endsWith(".properties");
                    })
                    .filter(StateBoundaryIndexTest::containsWindowsAbsolutePath)
                    .map(Path::toString)
                    .toList();
            assertTrue(offenders.isEmpty(), "hard-coded absolute paths found in: " + offenders);
        }
    }

    private static boolean containsWindowsAbsolutePath(Path path) {
        try {
            return Files.readString(path).matches("(?s).*\\b[A-Za-z]:\\\\.*");
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }
}
