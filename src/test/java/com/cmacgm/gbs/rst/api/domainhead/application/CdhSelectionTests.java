package com.cmacgm.gbs.rst.api.domainhead.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.workflow.application.WorkflowRouter;
import org.junit.jupiter.api.Test;

class CdhSelectionTests {

    private static final WorkflowRouter.RoutedStep ADA =
            new WorkflowRouter.RoutedStep("POS-1", "DH001", "Ada");
    private static final WorkflowRouter.RoutedStep BEN =
            new WorkflowRouter.RoutedStep("POS-2", "DH002", "Ben");

    @Test
    void singleCandidateIsUsedWithoutAChoice() {
        WorkflowRouter.RoutedStep chosen = CdhSelection.choose(List.of(ADA), null);

        assertThat(chosen).isEqualTo(ADA);
    }

    @Test
    void severalCandidatesRequireAChoice() {
        assertThatThrownBy(() -> CdhSelection.choose(List.of(ADA, BEN), " "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Select one handler");
    }

    @Test
    void choiceMustBeOneOfTheConfiguredHandlers() {
        assertThatThrownBy(() -> CdhSelection.choose(List.of(ADA, BEN), "POS-9"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void explicitChoiceSelectsThatHandler() {
        assertThat(CdhSelection.choose(List.of(ADA, BEN), "POS-2")).isEqualTo(BEN);
    }
}
