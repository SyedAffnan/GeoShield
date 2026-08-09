package com.geoshield.historicaldata.ingestion;

public enum HistoricalDataset {
    MORTH_ROAD_ACCIDENTS_2024("MoRTH Road Accidents in India 2024", 2024),
    NCRB_CRIME_IN_INDIA_2023_TABLE_13A_2("NCRB Crime in India 2023 Ch.13A", 2023);

    private final String source;
    private final int sourceYear;

    HistoricalDataset(String source, int sourceYear) {
        this.source = source;
        this.sourceYear = sourceYear;
    }

    public String source() { return source; }
    public int sourceYear() { return sourceYear; }
}
