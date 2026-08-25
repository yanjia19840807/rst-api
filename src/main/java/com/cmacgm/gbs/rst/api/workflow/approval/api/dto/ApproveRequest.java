package com.cmacgm.gbs.rst.api.workflow.approval.api.dto;

import java.util.UUID;

/**
 * Approve request payload.
 */
public record ApproveRequest(String comments, UUID requestId) {
}
