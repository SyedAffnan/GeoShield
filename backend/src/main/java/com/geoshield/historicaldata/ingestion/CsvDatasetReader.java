package com.geoshield.historicaldata.ingestion;

import com.geoshield.common.exception.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CsvDatasetReader {
    private CsvDatasetReader() { }

    static List<Map<String, String>> read(Path sourceFile) {
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            throw new ValidationException("Historical dataset file does not exist: " + sourceFile);
        }
        try (var lines = Files.lines(sourceFile)) {
            List<String> csvLines = lines.filter(line -> !line.isBlank()).toList();
            if (csvLines.size() < 2) {
                throw new ValidationException("Historical dataset must contain a header and at least one data row");
            }
            List<String> headers = parse(csvLines.getFirst());
            List<Map<String, String>> rows = new ArrayList<>();
            for (int lineNumber = 1; lineNumber < csvLines.size(); lineNumber++) {
                List<String> values = parse(csvLines.get(lineNumber));
                if (values.size() != headers.size()) {
                    throw new ValidationException("Malformed CSV row " + (lineNumber + 1));
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int column = 0; column < headers.size(); column++) {
                    row.put(headers.get(column).trim(), values.get(column).trim());
                }
                rows.add(row);
            }
            return rows;
        } catch (IOException exception) {
            throw new ValidationException("Unable to read historical dataset: " + sourceFile);
        }
    }

    private static List<String> parse(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append(character);
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(character);
            }
        }
        if (quoted) {
            throw new ValidationException("Malformed CSV quoting");
        }
        values.add(value.toString());
        return values;
    }
}
