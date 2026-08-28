package com.cmacgm.gbs.rst.api.delegation.api;

import java.util.List;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.delegation.api.dto.CreateDelegationRequest;
import com.cmacgm.gbs.rst.api.delegation.api.dto.DelegationCandidateView;
import com.cmacgm.gbs.rst.api.delegation.api.dto.DelegationView;
import com.cmacgm.gbs.rst.api.delegation.application.DelegationService;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grant, revoke, and list identity delegations.
 */
@RestController
@RequestMapping("/api/v1/delegations")
public class DelegationController {

    private final DelegationService delegations;

    /**
     * @param delegations service
     */
    public DelegationController(DelegationService delegations) {
        this.delegations = delegations;
    }

    /**
     * Creates a delegation from the signed-in user to {@code delegateCcgid}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','MANAGER','CDH','LTH')")
    public DelegationView create(
            @AuthenticationPrincipal RstPrincipal principal,
            @Valid @RequestBody CreateDelegationRequest request) {
        return delegations.create(principal, request);
    }

    /**
     * Revokes an open grant issued by the signed-in user.
     */
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','MANAGER','CDH','LTH')")
    public DelegationView revoke(
            @AuthenticationPrincipal RstPrincipal principal,
            @PathVariable UUID id) {
        return delegations.revoke(principal, id);
    }

    /**
     * Delegations the signed-in user has granted.
     */
    @GetMapping("/granted")
    public List<DelegationView> granted(@AuthenticationPrincipal RstPrincipal principal) {
        return delegations.granted(principal);
    }

    /**
     * Delegations the signed-in user has received.
     */
    @GetMapping("/received")
    public List<DelegationView> received(@AuthenticationPrincipal RstPrincipal principal) {
        return delegations.received(principal);
    }

    /**
     * Timesheet people the grantor may pick.
     */
    @GetMapping("/candidates")
    @PreAuthorize("hasAnyRole('AGENT','SUPERVISOR','MANAGER','CDH','LTH')")
    public PageResponse<DelegationCandidateView> candidates(
            @AuthenticationPrincipal RstPrincipal principal,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return delegations.candidates(principal, q, page, pageSize);
    }
}
