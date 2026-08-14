package com.cmacgm.gbs.rst.api.scenario.api.dto;

/**
 * Monthly + daily forecast results created together.
 */
public record ForecastBundleView(ForecastView monthly, ForecastView daily) {
}
