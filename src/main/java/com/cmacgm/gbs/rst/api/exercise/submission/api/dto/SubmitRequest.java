package com.cmacgm.gbs.rst.api.exercise.submission.api.dto;

import java.util.UUID;

/**
 * Submit request payload.
 */
public record SubmitRequest(String remarks, UUID requestId, Boolean scopeAcknowledged) {
}
