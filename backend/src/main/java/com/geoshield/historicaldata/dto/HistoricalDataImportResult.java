package com.geoshield.historicaldata.dto;

public record HistoricalDataImportResult(String source, int recordsRead, int recordsImported, int recordsSkipped) { }
