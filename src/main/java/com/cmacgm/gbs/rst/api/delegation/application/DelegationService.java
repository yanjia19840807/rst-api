package com.cmacgm.gbs.rst.api.delegation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.delegation.api.dto.CreateDelegationRequest;
import com.cmacgm.gbs.rst.api.delegation.api.dto.DelegationCandidateView;
import com.cmacgm.gbs.rst.api.delegation.api.dto.DelegationView;
import com.cmacgm.gbs.rst.api.delegation.domain.Delegation;
import com.cmacgm.gbs.rst.api.delegation.domain.DelegationStatus;
import com.cmacgm.gbs.rst.api.delegation.persistence.DelegationRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetReadService;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetPerson;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grant, revoke, list, and activate identity delegations.
 */
@Service
public class DelegationService {

    private static final List<DelegationStatus> OPEN =
            List.of(DelegationStatus.PENDING, DelegationStatus.ACTIVE);

    private final DelegationRepository delegations;
    private final TimesheetReadService timesheet;
    private final Clock clock;

    /**
     * @param delegations store
     * @param timesheet person names
     * @param clock time
     */
    public DelegationService(
            DelegationRepository delegations, TimesheetReadService timesheet, Clock clock) {
        this.delegations = delegations;
        this.timesheet = timesheet;
        this.clock = clock;
    }

    /**
     * A authorizes B.
     *
     * @param principal real signed-in user (must not already be acting)
     * @param request payload
     * @return created row
     */
    @Transactional
    public DelegationView create(RstPrincipal principal, CreateDelegationRequest request) {
        requireGrantor(principal);
        Instant now = clock.instant();
        Instant validFrom = request.validFrom();
        Instant validUntil = request.validUntil();
        if (!validUntil.isAfter(validFrom)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-delegation-range",
                    "validUntil must be after validFrom.");
        }
        if (!validUntil.isAfter(now)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-delegation-range",
                    "validUntil must be in the future.");
        }
        String delegateCcgid = request.delegateCcgid().trim().toUpperCase(Locale.ROOT);
        if (delegateCcgid.equalsIgnoreCase(principal.realCcgid())) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "cannot-delegate-self",
                    "You cannot delegate to yourself.");
        }
        String center = requireCenter(principal);
        TimesheetPerson delegate = timesheet.findActivePerson(delegateCcgid)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "delegate-not-found",
                        "The selected person is not in the active Timesheet snapshot."));
        if (!timesheet.personInCenter(delegateCcgid, center)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "delegate-center-mismatch",
                    "You can only delegate to someone in your Center.");
        }
        if (!delegations.findOpenPair(principal.realCcgid(), delegateCcgid, OPEN).isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "delegation-already-open",
                    "An open delegation to this person already exists.");
        }
        if (hasOpenReceived(principal.realCcgid(), now)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "delegation-chain-forbidden",
                    "You cannot grant a delegation while someone has authorized you to act for them.");
        }
        if (hasOpenGranted(delegateCcgid, now)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "delegation-chain-forbidden",
                    "This person has already granted their access to someone else.");
        }
        Delegation row = Delegation.create(
                principal.realCcgid(),
                principal.displayName(),
                delegateCcgid,
                delegate.getName(),
                principal.roles(),
                principal.center(),
                validFrom,
                validUntil,
                now);
        row.refresh(now);
        return DelegationView.from(delegations.save(row));
    }

    /**
     * A revokes an open grant.
     *
     * @param principal real signed-in user
     * @param id delegation id
     * @return updated row
     */
    @Transactional
    public DelegationView revoke(RstPrincipal principal, UUID id) {
        requireGrantor(principal);
        Instant now = clock.instant();
        Delegation row = requireOwnedOpen(principal.realCcgid(), id, now);
        row.revoke(now);
        return DelegationView.from(delegations.save(row));
    }

    /**
     * Grants issued by the real user.
     *
     * @param principal caller
     * @return rows
     */
    @Transactional
    public List<DelegationView> granted(RstPrincipal principal) {
        requireSignedIn(principal);
        Instant now = clock.instant();
        return refreshAll(delegations.findGrantedBy(principal.realCcgid()), now).stream()
                .map(DelegationView::from)
                .toList();
    }

    /**
     * Grants received by the real user.
     *
     * @param principal caller
     * @return rows
     */
    @Transactional
    public List<DelegationView> received(RstPrincipal principal) {
        requireSignedIn(principal);
        Instant now = clock.instant();
        return refreshAll(delegations.findReceivedBy(principal.realCcgid()), now).stream()
                .map(DelegationView::from)
                .toList();
    }

    /**
     * People in the grantor's Center, excluding A.
     *
     * @param principal grantor
     * @param q name or CCGID
     * @param page 1-based page
     * @param pageSize page size
     * @return candidates
     */
    @Transactional(readOnly = true)
    public PageResponse<DelegationCandidateView> candidates(
            RstPrincipal principal, String q, int page, int pageSize) {
        requireGrantor(principal);
        return mapCandidates(principal, q, page, pageSize);
    }

    /**
     * Loads a usable delegation for the signed-in delegate and refreshes status.
     *
     * @param id delegation id
     * @param delegateCcgid real signed-in B
     * @return active row
     */
    @Transactional
    public Delegation requireUsable(UUID id, String delegateCcgid) {
        Instant now = clock.instant();
        Delegation row = delegations.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.FORBIDDEN,
                        "delegation-inactive",
                        "Delegation ended. You are back to your own account."));
        if (row.refresh(now)) {
            delegations.save(row);
        }
        if (!row.getDelegateCcgid().equalsIgnoreCase(delegateCcgid) || !row.isUsable(now)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "delegation-inactive",
                    "Delegation ended. You are back to your own account.");
        }
        return row;
    }

    private PageResponse<DelegationCandidateView> mapCandidates(
            RstPrincipal principal, String q, int page, int pageSize) {
        String center = requireCenter(principal);
        String self = principal.realCcgid();
        PageResponse<TimesheetReadService.CenterPerson> source = timesheet.peopleInCenter(center, q, page, pageSize);
        List<DelegationCandidateView> items = source.items().stream()
                .filter(person -> !self.equalsIgnoreCase(person.ccgid()))
                .map(person -> new DelegationCandidateView(person.ccgid(), person.name(), center))
                .toList();
        return new PageResponse<>(items, source.page(), source.pageSize(), source.total(), source.totalPages());
    }

    private static String requireCenter(RstPrincipal principal) {
        String center = principal.center();
        if (center == null || center.isBlank()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "identity-center-missing",
                    "Current identity has no Center.");
        }
        return center.trim();
    }

    private boolean hasOpenReceived(String ccgid, Instant now) {
        return refreshAll(delegations.findReceivedBy(ccgid), now).stream()
                .anyMatch(row -> row.getStatus().isOpen());
    }

    private boolean hasOpenGranted(String ccgid, Instant now) {
        return refreshAll(delegations.findGrantedBy(ccgid), now).stream()
                .anyMatch(row -> row.getStatus().isOpen());
    }

    private List<Delegation> refreshAll(List<Delegation> rows, Instant now) {
        for (Delegation row : rows) {
            if (row.refresh(now)) {
                delegations.save(row);
            }
        }
        return rows;
    }

    private Delegation requireOwnedOpen(String delegatorCcgid, UUID id, Instant now) {
        Delegation row = delegations.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "delegation-not-found", "The delegation was not found."));
        if (row.refresh(now)) {
            delegations.save(row);
        }
        if (!row.getDelegatorCcgid().equalsIgnoreCase(delegatorCcgid)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "delegation-not-owner",
                    "Only the delegator can revoke this authorization.");
        }
        if (!row.getStatus().isOpen()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "delegation-not-open",
                    "This delegation has already ended.");
        }
        return row;
    }

    private static void requireGrantor(RstPrincipal principal) {
        requireRealUser(principal);
        if (!principal.canGrantDelegation()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "delegation-grant-forbidden",
                    "This role cannot grant a delegation.");
        }
    }

    private static void requireRealUser(RstPrincipal principal) {
        requireSignedIn(principal);
        if (principal.isDelegated()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "delegation-write-forbidden",
                    "Delegation cannot be managed while acting as another user.");
        }
    }

    private static void requireSignedIn(RstPrincipal principal) {
        if (principal == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "unauthenticated", "Authentication is required.");
        }
    }
}
