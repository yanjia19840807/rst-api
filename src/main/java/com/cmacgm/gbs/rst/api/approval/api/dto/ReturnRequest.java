package com.cmacgm.gbs.rst.api.approval.api.dto;

import java.util.UUID;

/**
 * Return request payload.
 */
public record ReturnRequest(String comments, UUID requestId) {
}
