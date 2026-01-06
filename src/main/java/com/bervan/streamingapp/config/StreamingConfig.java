package com.bervan.streamingapp.config;

import com.bervan.logging.JsonLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class StreamingConfig {
    private final JsonLogger log = JsonLogger.getLogger(getClass(), "streaming");

    private final StreamingConfigLoader streamingConfigLoader;

    public StreamingConfig(StreamingConfigLoader streamingConfigLoader) {
        this.streamingConfigLoader = streamingConfigLoader;
    }

    @Bean
    public Map<String, ProductionData> streamingProductionData() {
        return streamingConfigLoader.getStringProductionDataMap();
    }
}
