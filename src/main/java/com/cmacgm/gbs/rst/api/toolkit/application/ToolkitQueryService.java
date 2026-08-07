package com.cmacgm.gbs.rst.api.toolkit.application;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.toolkit.api.ToolkitResponse;
import com.cmacgm.gbs.rst.api.toolkit.persistence.ToolkitRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolkitQueryService {

    private final ToolkitRepository toolkitRepository;
    private final TimesheetReadService timesheet;

    public ToolkitQueryService(
            ToolkitRepository toolkitRepository, TimesheetReadService timesheet) {
        this.toolkitRepository = toolkitRepository;
        this.timesheet = timesheet;
    }

    @Transactional(readOnly = true)
    public List<ToolkitResponse> agentToolkits(String ccgid) {
        return toolkitRepository.findAvailableToAgent(ccgid).stream()
                .map(ToolkitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ToolkitResponse> supervisorToolkits(String ccgid) {
        var positions = timesheet.supervisorHierarchy(ccgid).stream()
                .map(candidate -> candidate.supervisorPositionId())
                .distinct()
                .toList();
        return positions.stream()
                .flatMap(position -> toolkitRepository
                        .findBySupervisorPositionIdAndDeletedAtIsNullOrderByName(position)
                        .stream())
                .filter(toolkit -> timesheet.supervisorOwnsScope(
                        ccgid,
                        toolkit.getSupervisorPositionId(),
                        toolkit.getPrimaryPl3Code()))
                .map(ToolkitResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ToolkitResponse detail(String ccgid, UUID id) {
        var toolkit = toolkitRepository.findActiveById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "toolkit-not-found", "The Toolkit was not found."));
        boolean allowed = timesheet.agentCanUse(
                ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code())
                || timesheet.supervisorOwnsScope(
                        ccgid, toolkit.getSupervisorPositionId(), toolkit.getPrimaryPl3Code());
        if (!allowed) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "toolkit-out-of-scope",
                    "The Toolkit is outside the current Timesheet scope.");
        }
        return ToolkitResponse.from(toolkit);
    }
}
