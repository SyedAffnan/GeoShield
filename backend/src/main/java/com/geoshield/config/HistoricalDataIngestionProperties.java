package com.geoshield.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "geoshield.historical-data.ingestion")
public class HistoricalDataIngestionProperties {
    private boolean enabled;
    private Path morthSource;
    private Path ncrbSource;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Path getMorthSource() { return morthSource; }
    public void setMorthSource(Path morthSource) { this.morthSource = morthSource; }
    public Path getNcrbSource() { return ncrbSource; }
    public void setNcrbSource(Path ncrbSource) { this.ncrbSource = ncrbSource; }
}
