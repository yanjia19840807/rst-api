package com.cmacgm.gbs.rst.api.domainhead.application;

import java.util.List;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import org.springframework.http.HttpStatus;

/**
 * Picks the single Domain Head who will own the next review step.
 */
public final class CdhSelection {

    private CdhSelection() {
    }

    /**
     * Chooses one live candidate. A single candidate is used when the caller
     * does not pass a position. Several candidates require an explicit choice.
     *
     * @param live configured people still in the Center
     * @param requestedPositionId position chosen in the confirm dialog, if any
     * @return the handler to assign
     */
    public static WorkflowRouter.RoutedStep choose(
            List<WorkflowRouter.RoutedStep> live, String requestedPositionId) {
        if (live == null || live.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "domain-head-not-configured",
                    "Domain Head is not configured for this Center and Domain.");
        }
        String requested = requestedPositionId == null || requestedPositionId.isBlank()
                ? null
                : requestedPositionId.trim();
        if (live.size() == 1 && requested == null) {
            return live.get(0);
        }
        if (requested == null) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "next-handler-required",
                    "Select one handler for the next step.");
        }
        for (WorkflowRouter.RoutedStep step : live) {
            if (requested.equals(step.positionId())) {
                return step;
            }
        }
        throw new ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "next-handler-invalid",
                "Selected handler is not configured for the next step.");
    }
}
