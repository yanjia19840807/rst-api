package com.cmacgm.gbs.rst.api.delegation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.delegation.api.dto.AssignDelegationRequest;
import com.cmacgm.gbs.rst.api.delegation.api.dto.CreateDelegationRequest;
import com.cmacgm.gbs.rst.api.delegation.api.dto.DelegationCandidateView;
import com.cmacgm.gbs.rst.api.delegation.api.dto.DelegationView;
import com.cmacgm.gbs.rst.api.delegation.api.dto.PositionAssignmentView;
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
    public List<DelegationView> create(RstPrincipal principal, CreateDelegationRequest request) {
        requireGrantor(principal);
        List<String> positions = timesheet.heldPositionIds(principal.realCcgid());
        if (positions.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "delegation-not-own-position",
                    "You have no position to delegate.");
        }
        return coverPositions(
                principal, positions, request.delegateCcgids(), request.validFrom(), request.validUntil());
    }

    /**
     * A parent covers the selected child position and every other direct child
     * position held by the same occupant. Every role on each position is included.
     *
     * @param principal parent occupant
     * @param request selected child position and delegates
     * @return created rows
     */
    @Transactional
    public List<DelegationView> assign(RstPrincipal principal, AssignDelegationRequest request) {
        requireGrantor(principal);
        List<String> positions = assignablePositionsForSelection(
                principal.realCcgid(), request.positionId().trim());
        if (positions.isEmpty()) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "delegation-not-direct-child",
                    "You can only assign a direct child position.");
        }
        return coverPositions(
                principal, positions, request.delegateCcgids(), request.validFrom(), request.validUntil());
    }

    /**
     * Positions the caller occupies, with their open coverage.
     *
     * @param principal caller
     * @return rows
     */
    @Transactional
    public List<PositionAssignmentView> seats(RstPrincipal principal) {
        requireSignedIn(principal);
        return positionViews(timesheet.heldPositionIds(principal.realCcgid()));
    }

    private List<DelegationView> coverPositions(
            RstPrincipal principal,
            List<String> positionIds,
            List<String> delegateCcgids,
            Instant validFrom,
            Instant validUntil) {
        List<DelegationView> created = new ArrayList<>();
        for (String positionId : positionIds) {
            created.addAll(coverPosition(
                    principal, positionId, delegateCcgids, validFrom, validUntil, true));
        }
        if (created.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "delegation-already-open",
                    "An open assignment to this person already exists for the position.");
        }
        return created;
    }

    private List<String> assignablePositionsForSelection(String grantorCcgid, String selectedPositionId) {
        if (!timesheet.isDirectParentOf(grantorCcgid, selectedPositionId)) {
            return List.of();
        }
        var occupant = timesheet.occupant(selectedPositionId);
        if (occupant == null
                || occupant.ccgid() == null
                || occupant.ccgid().isBlank()
                || occupant.ccgid().equalsIgnoreCase(selectedPositionId)) {
            return List.of(selectedPositionId);
        }
        Set<String> held = new LinkedHashSet<>(timesheet.heldPositionIds(occupant.ccgid()));
        return timesheet.directChildPositionIds(grantorCcgid).stream()
                .filter(held::contains)
                .toList();
    }

    private List<DelegationView> coverPosition(
            RstPrincipal principal,
            String positionId,
            List<String> delegateCcgids,
            Instant validFrom,
            Instant validUntil,
            boolean skipExisting) {
        Instant now = clock.instant();
        requireOpenRange(validFrom, validUntil, now);
        if (hasOpenReceived(principal.realCcgid(), now)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "delegation-chain-forbidden",
                    "You cannot delegate a position while you are a delegate for someone else.");
        }
        List<String> roles = timesheet.roleTypesOfPosition(positionId);
        if (roles.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "delegation-position-missing",
                    "That position is not in the ACTIVE Daily snapshot.");
        }
        String center = requireCenter(principal);
        var occupant = timesheet.occupant(positionId);
        String occupantCcgid = occupant != null && occupant.ccgid() != null && !occupant.ccgid().isBlank()
                ? occupant.ccgid()
                : positionId;
        String occupantName = occupant != null && occupant.name() != null && !occupant.name().isBlank()
                ? occupant.name()
                : positionId;
        Set<String> delegates = new LinkedHashSet<>();
        for (String raw : delegateCcgids) {
            if (raw != null && !raw.isBlank()) {
                delegates.add(raw.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (delegates.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "delegate-not-found",
                    "Select at least one person.");
        }
        List<DelegationView> created = new ArrayList<>();
        for (String delegateCcgid : delegates) {
            if (delegateCcgid.equalsIgnoreCase(occupantCcgid)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "cannot-delegate-self",
                        "The current occupant cannot cover their own position.");
            }
            TimesheetPerson delegate = timesheet.findActivePerson(delegateCcgid)
                    .orElseThrow(() -> new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "delegate-not-found",
                            "The selected person is not in the active Timesheet snapshot."));
            if (!timesheet.personInCenter(delegateCcgid, center)) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "delegate-center-mismatch",
                        "You can only assign someone in your Center.");
            }
            if (!delegations.findOpenPositionDelegate(positionId, delegateCcgid, OPEN).isEmpty()) {
                if (skipExisting) {
                    continue;
                }
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "delegation-already-open",
                        "An open assignment to this person already exists for the position.");
            }
            if (hasOpenGranted(delegateCcgid, now)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "delegation-chain-forbidden",
                        "This person has already granted their access to someone else.");
            }
            Delegation row = Delegation.assignPosition(
                    positionId,
                    Set.copyOf(roles),
                    occupantCcgid,
                    occupantName,
                    delegateCcgid,
                    delegate.getName(),
                    center,
                    principal.realCcgid(),
                    principal.displayName(),
                    validFrom,
                    validUntil,
                    now);
            row.refresh(now);
            created.add(DelegationView.from(delegations.save(row)));
        }
        return created;
    }

    /**
     * Direct child positions and their open coverage.
     *
     * @param principal parent occupant
     * @return rows
     */
    @Transactional
    public List<PositionAssignmentView> assignments(RstPrincipal principal) {
        requireSignedIn(principal);
        return positionViews(timesheet.directChildPositionIds(principal.realCcgid()));
    }

    private List<PositionAssignmentView> positionViews(List<String> positionIds) {
        if (positionIds.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        var coverage = refreshAll(delegations.findBySubjectPositionIdIn(positionIds), now);
        List<PositionAssignmentView> rows = new ArrayList<>();
        for (String positionId : positionIds) {
            var occupant = timesheet.occupant(positionId);
            List<DelegationView> open = coverage.stream()
                    .filter(row -> positionId.equals(row.getSubjectPositionId()) && row.getStatus().isOpen())
                    .map(DelegationView::from)
                    .toList();
            rows.add(new PositionAssignmentView(
                    positionId,
                    timesheet.roleTypesOfPosition(positionId),
                    occupant == null ? null : occupant.name(),
                    occupant == null ? null : occupant.ccgid(),
                    open));
        }
        return rows;
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
     * People in the grantor's Center, including the caller.
     * Whether the caller may be chosen is checked when the form is submitted.
     *
     * @param principal grantor
     * @param q name / email / CCGID
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
        PageResponse<TimesheetReadService.CenterPerson> source = timesheet.peopleInCenter(center, q, page, pageSize);
        List<DelegationCandidateView> items = source.items().stream()
                .map(person -> new DelegationCandidateView(
                        person.ccgid(), person.name(), center, person.email()))
                .toList();
        return new PageResponse<>(items, source.page(), source.pageSize(), source.total(), source.totalPages());
    }

    private String requireCenter(RstPrincipal principal) {
        String center = principal.center();
        if (center != null && !center.isBlank()) {
            return center.trim();
        }
        return timesheet.findActiveCenter(principal.realCcgid())
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "identity-center-missing",
                        "Current identity has no Center."));
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
        boolean owner = row.getDelegatorCcgid().equalsIgnoreCase(delegatorCcgid)
                || (row.getAssignedByCcgid() != null
                        && row.getAssignedByCcgid().equalsIgnoreCase(delegatorCcgid));
        if (!owner) {
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

    private static void requireOpenRange(Instant validFrom, Instant validUntil, Instant now) {
        if (validFrom != null && validUntil != null && !validUntil.isAfter(validFrom)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-delegation-range",
                    "validUntil must be after validFrom.");
        }
        if (validUntil != null && !validUntil.isAfter(now)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-delegation-range",
                    "validUntil must be in the future.");
        }
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
