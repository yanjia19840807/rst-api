package com.cmacgm.gbs.rst.api.exercise.scenario.application;

/**
 * Which saved scenario results an Associated Data or period change invalidates.
 * Forecast levels are MONTHLY and DAILY. Simulation run types are MONTHLY_SIZING, DAILY, and SLOT.
 */
public enum ResultClearScope {
    /** Per-slot Volume or Slot Period. Slot simulation only. */
    SLOT,
    /** Daily Volume. Daily forecast and Daily sizing. */
    DAILY,
    /** Monthly Volume. Monthly forecast, Monthly sizing, and Daily sizing (commercial ratio). */
    MONTHLY,
    /** Calendar or Sizing Month. Monthly and Daily forecast and sizing. Slot stays. */
    SIZING,
    /** Production Support. Monthly sizing only (Support FTE). */
    SUPPORT,
    /** Team Setup or Cycle Time. Forecasts, both sizings, and Slot simulation. */
    ALL;

    public boolean dropsRun(String runType) {
        return switch (this) {
            case SLOT -> "SLOT".equals(runType);
            case DAILY -> "DAILY".equals(runType);
            case MONTHLY, SIZING -> "MONTHLY_SIZING".equals(runType) || "DAILY".equals(runType);
            case SUPPORT -> "MONTHLY_SIZING".equals(runType);
            case ALL -> true;
        };
    }

    public boolean dropsForecast(String forecastLevel) {
        return switch (this) {
            case SLOT, SUPPORT -> false;
            case DAILY -> "DAILY".equals(forecastLevel);
            case MONTHLY -> "MONTHLY".equals(forecastLevel);
            case SIZING, ALL -> true;
        };
    }

    /**
     * Notice prefix is parsed by the web client. Keep the leading clause stable.
     */
    public String notice(int count) {
        String scenarios = count + " scenario(s)";
        return switch (this) {
            case SLOT -> "Cleared saved Slot Simulation for " + scenarios + ".";
            case DAILY -> "Cleared saved Daily forecast and Daily sizing for " + scenarios
                    + ". Monthly forecast, Monthly sizing, and Slot simulation were kept.";
            case MONTHLY -> "Cleared saved Monthly forecast, Monthly sizing, and Daily sizing for "
                    + scenarios
                    + ". Daily forecast and Slot simulation were kept.";
            case SIZING -> "Cleared saved Monthly and Daily forecast and sizing for " + scenarios
                    + ". Slot simulation was kept.";
            case SUPPORT -> "Cleared saved Monthly sizing for " + scenarios
                    + ". Forecasts, Daily sizing, and Slot simulation were kept.";
            case ALL -> "Cleared saved Monthly and Daily forecast and sizing, and Slot simulation for "
                    + scenarios
                    + ".";
        };
    }
}
