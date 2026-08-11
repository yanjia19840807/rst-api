package com.cmacgm.gbs.rst.api.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTOs exchanged with the Python rst-forecast API (snake_case JSON).
 */
public final class ForecastApiModels {

    private ForecastApiModels() {
    }

    /** History month sent to SARIMAX. */
    public record MonthlyActual(
            @JsonProperty("date_month") LocalDate dateMonth,
            @JsonProperty("actual_volume") BigDecimal actualVolume,
            @JsonProperty("work_days") int workDays,
            @JsonProperty("weekend_days") int weekendDays,
            @JsonProperty("commercial_ratio") BigDecimal commercialRatio) {
    }

    /** Future month exogenous features. */
    public record MonthlyFuture(
            @JsonProperty("date_month") LocalDate dateMonth,
            @JsonProperty("work_days") int workDays,
            @JsonProperty("weekend_days") int weekendDays,
            @JsonProperty("commercial_ratio") BigDecimal commercialRatio) {
    }

    /** Request body for POST /api/v1/forecasts/monthly. */
    public record MonthlyForecastRequest(
            List<MonthlyActual> history,
            List<MonthlyFuture> future,
            @JsonProperty("confidence_level") double confidenceLevel) {
    }

    /** One monthly forecast point from Python. */
    public record ForecastPointDto(
            @JsonProperty("date_month") LocalDate dateMonth,
            BigDecimal forecast,
            BigDecimal lower,
            BigDecimal upper) {
    }

    /** History day sent to daily SARIMAX. */
    public record DailyActual(
            LocalDate date,
            @JsonProperty("actual_volume") BigDecimal actualVolume,
            @JsonProperty("is_working_day") boolean isWorkingDay,
            @JsonProperty("is_holiday") boolean isHoliday,
            @JsonProperty("commercial_ratio") BigDecimal commercialRatio) {
    }

    /** Future day exogenous features. */
    public record DailyFuture(
            LocalDate date,
            @JsonProperty("is_working_day") boolean isWorkingDay,
            @JsonProperty("is_holiday") boolean isHoliday,
            @JsonProperty("commercial_ratio") BigDecimal commercialRatio) {
    }

    /** Request body for POST /api/v1/forecasts/daily. */
    public record DailyForecastRequest(
            List<DailyActual> history,
            List<DailyFuture> future,
            @JsonProperty("confidence_level") double confidenceLevel) {
    }

    /** One daily forecast point from Python. */
    public record DailyForecastPointDto(
            LocalDate date,
            BigDecimal forecast,
            BigDecimal lower,
            BigDecimal upper) {
    }

    /** Model metadata from Python. */
    public record ModelMetadata(
            String name,
            String version,
            List<Integer> order,
            @JsonProperty("seasonal_order") List<Integer> seasonalOrder,
            @JsonProperty("training_start") LocalDate trainingStart,
            @JsonProperty("training_end") LocalDate trainingEnd,
            @JsonProperty("observation_count") int observationCount) {
    }

    /** Response body from POST /api/v1/forecasts/monthly. */
    public record MonthlyForecastResponse(
            List<ForecastPointDto> forecasts,
            ModelMetadata model,
            @JsonProperty("confidence_level") double confidenceLevel,
            @JsonProperty("duration_ms") int durationMs) {
    }

    /** Response body from POST /api/v1/forecasts/daily. */
    public record DailyForecastResponse(
            List<DailyForecastPointDto> forecasts,
            ModelMetadata model,
            @JsonProperty("confidence_level") double confidenceLevel,
            @JsonProperty("duration_ms") int durationMs) {
    }
}
