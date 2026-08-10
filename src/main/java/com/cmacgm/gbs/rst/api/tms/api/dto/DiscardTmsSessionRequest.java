package com.cmacgm.gbs.rst.api.tms.api.dto;

import jakarta.validation.constraints.Size;

/** Discard body; reason is optional (UI confirm-only delete). */
public record DiscardTmsSessionRequest(@Size(max = 500) String reason) {
}
