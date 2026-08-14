package com.cmacgm.gbs.rst.api.submission.api.dto;

import java.util.UUID;

/**
 * Submit request payload.
 */
public record SubmitRequest(String remarks, UUID requestId) {
}
