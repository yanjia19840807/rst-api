package com.cmacgm.gbs.rst.api.approval.api.dto;

import java.util.UUID;

/**
 * Approve request payload.
 */
public record ApproveRequest(String comments, UUID requestId) {
}
