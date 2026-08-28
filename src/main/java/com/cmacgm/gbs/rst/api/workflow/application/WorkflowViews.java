package com.cmacgm.gbs.rst.api.workflow.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.cmacgm.gbs.rst.api.workflow.api.dto.ActionView;
import com.cmacgm.gbs.rst.api.workflow.api.dto.StepView;
import com.cmacgm.gbs.rst.api.workflow.domain.ActorStatus;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessInstance;
import com.cmacgm.gbs.rst.api.workflow.domain.ProcessTask;
import com.cmacgm.gbs.rst.api.workflow.domain.TaskActor;
import org.springframework.stereotype.Component;

/**
 * Maps the process aggregate onto the existing Step / Action API shapes.
 */
@Component
public class WorkflowViews {

    /**
     * One row per review task. Assignee is the pending actor, else the first actor.
     *
     * @param instance process
     * @param displayNames ccgid → name
     * @return step views
     */
    public List<StepView> steps(ProcessInstance instance, Map<String, String> displayNames) {
        return instance.getTasks().stream()
                .filter(task -> task.getNode().isReview())
                .map(task -> toStep(task, displayNames))
                .toList();
    }

    /**
     * History events from actors that have a public action type.
     *
     * @param instance process
     * @param displayNames ccgid → name
     * @return action views
     */
    public List<ActionView> actions(ProcessInstance instance, Map<String, String> displayNames) {
        return instance.getTasks().stream()
                .flatMap(task -> task.getActors().stream().map(actor -> toAction(task, actor, displayNames)))
                .filter(action -> action != null)
                .sorted(Comparator.comparing(ActionView::actionAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static StepView toStep(ProcessTask task, Map<String, String> displayNames) {
        TaskActor actor = task.findAnyPendingActor()
                .or(() -> task.getActors().stream().findFirst())
                .orElse(null);
        String ccgid = actor == null ? null : actor.getCcgid();
        String positionId = actor == null ? null : actor.getPositionId();
        return new StepView(
                task.getNodeOrder(),
                task.getNode().roleCode(),
                ccgid,
                positionId,
                ccgid == null ? null : displayNames.get(ccgid),
                task.getStatus().name());
    }

    private static ActionView toAction(
            ProcessTask task, TaskActor actor, Map<String, String> displayNames) {
        if (!actor.getStatus().isHistory()) {
            return null;
        }
        return new ActionView(
                task.getNodeOrder(),
                actor.getStatus().name(),
                actor.getCcgid(),
                task.getNode().roleCode(),
                actor.handlerDisplayName(displayNames),
                actor.getComments(),
                actor.getActedAt(),
                actor.getRequestId());
    }
}
