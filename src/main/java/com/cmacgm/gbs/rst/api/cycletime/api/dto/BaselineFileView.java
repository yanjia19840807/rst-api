package com.cmacgm.gbs.rst.api.cycletime.api.dto;

import java.util.UUID;

/**
 * Support file metadata on a MANUAL baseline.
 */
public record BaselineFileView(
        UUID id,
        String fileName,
        String mimeType,
        Long sizeBytes,
        String webUrl,
        int displayOrder) {
}
