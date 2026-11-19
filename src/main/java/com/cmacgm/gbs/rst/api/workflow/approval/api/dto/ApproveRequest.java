package com.cmacgm.gbs.rst.api.workflow.approval.api.dto;

import java.util.UUID;

/**
 * Approve request payload.
 *
 * @param comments optional decision comment
 * @param requestId idempotency key
 * @param nextPositionId handler chosen when the next step has several candidates
 */
public record ApproveRequest(String comments, UUID requestId, String nextPositionId) {
}
