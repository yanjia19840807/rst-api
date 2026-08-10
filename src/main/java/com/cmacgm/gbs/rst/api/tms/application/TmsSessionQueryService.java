package com.cmacgm.gbs.rst.api.tms.application;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.common.paging.PageResponse;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSessionResponse;
import com.cmacgm.gbs.rst.api.tms.api.dto.TmsSummaryResponse;
import com.cmacgm.gbs.rst.api.tms.domain.TmsSessionStatus;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionRepository;
import com.cmacgm.gbs.rst.api.tms.persistence.TmsSessionSpecification;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TmsSessionQueryService {

    private final TmsSessionRepository sessionRepository;
    private final Clock clock;

    public TmsSessionQueryService(TmsSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TmsSessionResponse current(UUID userId) {
        var now = clock.instant();
        return sessionRepository.findFirstByUserIdAndStatusIn(
                        userId, Set.of(TmsSessionStatus.RUNNING, TmsSessionStatus.PAUSED))
                .map(session -> TmsSessionResponse.from(session, now))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public TmsSessionResponse get(UUID userId, String sessionNo) {
        var now = clock.instant();
        return sessionRepository.findBySessionNoAndUserId(sessionNo, userId)
                .map(session -> TmsSessionResponse.from(session, now))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "tms-session-not-found",
                        "The TMS session was not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<TmsSessionResponse> sessions(
            UUID userId,
            String status,
            String sessionNo,
            String reference,
            String query,
            LocalDate dateFrom,
            LocalDate dateTo,
            int page,
            int pageSize) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-date-range",
                    "dateFrom cannot be after dateTo.");
        }
        TmsSessionStatus resolvedStatus = parseStatus(status);
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        var pageable = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "startedAt"));
        var result = sessionRepository.findAll(
                TmsSessionSpecification.filtered(
                        userId,
                        resolvedStatus,
                        sessionNo,
                        reference,
                        query,
                        dateFrom,
                        dateTo),
                pageable);
        var now = clock.instant();
        return PageResponse.from(result, session -> TmsSessionResponse.from(session, now));
    }

    @Transactional(readOnly = true)
    public TmsSummaryResponse summary(UUID userId) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        var from = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        var to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TmsSummaryResponse(
                sessionRepository.countByUserIdAndStatusAndEndedAtGreaterThanEqualAndEndedAtLessThan(
                        userId,
                        TmsSessionStatus.COMPLETED,
                        from,
                        to),
                sessionRepository.sumVolume(
                        userId,
                        TmsSessionStatus.COMPLETED,
                        from,
                        to),
                sessionRepository.countByUserIdAndStatus(userId, TmsSessionStatus.PAUSED));
    }

    private static TmsSessionStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TmsSessionStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "invalid-session-status",
                    "Unsupported TMS session status: " + value);
        }
    }
}
