package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.timesheet.application.TimesheetSyncAdminService.AlertConfig;
import com.cmacgm.gbs.rst.api.timesheet.domain.TimesheetSyncAlert;
import com.cmacgm.gbs.rst.api.timesheet.persistence.TimesheetSyncAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimesheetSyncAlertAdminTests {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");

    private TimesheetSyncAlert stored;
    private TimesheetSyncAdminService admin;

    @BeforeEach
    void setUp() {
        stored = TimesheetSyncAlert.disabled();
        TimesheetSyncAlertRepository alerts = proxy(TimesheetSyncAlertRepository.class, (proxy, method, args) ->
                switch (method.getName()) {
                    case "findById" -> Optional.of(stored);
                    case "save" -> {
                        stored = (TimesheetSyncAlert) args[0];
                        yield stored;
                    }
                    default -> throw new AssertionError("Unexpected " + method.getName());
                });
        admin = new TimesheetSyncAdminService(
                null,
                null,
                alerts,
                null,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesNormalizedRecipients() {
        AlertConfig saved = admin.saveAlert(
                principal(),
                new AlertConfig(true, List.of("a@cma-cgm.com", " A@cma-cgm.com ", "b@cma-cgm.com")));
        assertThat(saved.enabled()).isTrue();
        assertThat(saved.recipients()).containsExactly("a@cma-cgm.com", "b@cma-cgm.com");
        assertThat(stored.getUpdatedByCcgid()).isEqualTo("S0001");
        assertThat(stored.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsEnabledWithoutRecipients() {
        assertThatThrownBy(() -> admin.saveAlert(principal(), new AlertConfig(true, List.of())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("At least one recipient");
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(() -> admin.saveAlert(principal(), new AlertConfig(false, List.of("not-an-email"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid email");
    }

    private static RstPrincipal principal() {
        return new RstPrincipal("S0001", "LTH", "lth@cma-cgm.com", Set.of("LTH"), Set.of(), "SHA");
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
