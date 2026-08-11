package com.cmacgm.gbs.rst.api.forecast;

import java.net.http.HttpClient;
import java.time.Duration;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.DailyForecastRequest;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.DailyForecastResponse;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.MonthlyForecastRequest;
import com.cmacgm.gbs.rst.api.forecast.ForecastApiModels.MonthlyForecastResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for the Python rst-forecast service (Spring RestClient + Jackson 3).
 */
@Component
public class ForecastClient {

    private final RestClient restClient;

    /**
     * Creates the forecast client.
     *
     * @param properties forecast configuration
     * @param jsonMapper Spring Boot Jackson 3 {@link JsonMapper} bean
     */
    public ForecastClient(ForecastProperties properties, JsonMapper jsonMapper) {
        // HTTP/1.1 is required for uvicorn; JDK HttpClient otherwise prefers HTTP/2.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(Math.max(1, properties.connectTimeoutMs())))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, properties.readTimeoutMs())));

        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.baseUrl()))
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(jsonMapper)))
                .build();
    }

    /**
     * Calls POST /api/v1/forecasts/monthly.
     *
     * @param request monthly forecast payload
     * @return forecast response
     */
    public MonthlyForecastResponse forecastMonthly(MonthlyForecastRequest request) {
        return postForecast("/api/v1/forecasts/monthly", request, MonthlyForecastResponse.class);
    }

    /**
     * Calls POST /api/v1/forecasts/daily.
     *
     * @param request daily forecast payload
     * @return forecast response
     */
    public DailyForecastResponse forecastDaily(DailyForecastRequest request) {
        return postForecast("/api/v1/forecasts/daily", request, DailyForecastResponse.class);
    }

    private <T> T postForecast(String path, Object request, Class<T> responseType) {
        try {
            T response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "forecast-empty-response",
                        "Forecast service returned an empty response.");
            }
            if (response instanceof MonthlyForecastResponse monthly
                    && (monthly.forecasts() == null || monthly.forecasts().isEmpty())) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "forecast-empty-response",
                        "Forecast service returned an empty response.");
            }
            if (response instanceof DailyForecastResponse daily
                    && (daily.forecasts() == null || daily.forecasts().isEmpty())) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "forecast-empty-response",
                        "Forecast service returned an empty response.");
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "forecast-service-error",
                    "Forecast service rejected the request: HTTP "
                            + ex.getStatusCode().value()
                            + " "
                            + truncate(ex.getResponseBodyAsString()));
        } catch (RestClientException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "forecast-service-unreachable",
                    "Forecast service is unreachable: " + ex.getMessage());
        }
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String truncate(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String trimmed = body.trim().replace('\n', ' ');
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240) + "…";
    }
}
