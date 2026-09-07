package com.cmacgm.gbs.rst.api.exercise.associateddata.api.dto;

import java.util.List;

/**
 * Dry-run of a Monthly or Daily Excel import: overlay plan, no writes.
 */
public record VolumeSeriesImportPreviewView(
        String grain,
        int fileRowCount,
        List<String> overwritten,
        List<String> added,
        List<String> kept) {
}
