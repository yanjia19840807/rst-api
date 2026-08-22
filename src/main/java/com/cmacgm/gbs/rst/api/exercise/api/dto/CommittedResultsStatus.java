package com.cmacgm.gbs.rst.api.exercise.api.dto;

/**
 * How many scenarios on an Exercise currently have saved Forecast / Simulation snapshots.
 */
public record CommittedResultsStatus(int scenarioCount) {
}
