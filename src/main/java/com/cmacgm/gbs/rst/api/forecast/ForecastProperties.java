package com.cmacgm.gbs.rst.api.forecast;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Python rst-forecast service integration.
 *
 * @param baseUrl forecast service base URL
 * @param connectTimeoutMs connect timeout
 * @param readTimeoutMs read timeout
 * @param enabled when false, forecast endpoints return forecast-disabled
 * @param confidenceLevel prediction interval level
 */
@ConfigurationProperties(prefix = "rst.forecast")
public record ForecastProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        boolean enabled,
        double confidenceLevel) {

    /**
     * Defaults for local development.
     *
     * @return properties
     */
    public static ForecastProperties defaults() {
        return new ForecastProperties(
                "http://localhost:8000",
                3_000,
                60_000,
                true,
                0.95);
    }
}
