package com.cmacgm.gbs.rst.api.timesheet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.security.RstRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class TimesheetSharePointSyncTests {

    @Mock
    private TimesheetSyncService syncService;

    private TimesheetSyncAdminService admin;

    @BeforeEach
    void setUp() {
        admin = new TimesheetSyncAdminService(
                null, null, null, syncService, null, null, Clock.systemUTC(), null);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsLocalTransformationHead() {
        assertThatThrownBy(() -> admin.startSharePointSync(principal(RstRoles.LOCAL_TRANSFORMATION_HEAD)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("limited to ADMIN");
        assertThat(admin.sharePointSyncStatus(principal(RstRoles.ADMIN)).running()).isFalse();
    }

    @Test
    void runsDailyThenMonthlyAsTheSignedInUser() throws Exception {
        CountDownLatch both = new CountDownLatch(2);
        AtomicReference<Object> seenPrincipal = new AtomicReference<>();
        when(syncService.syncFromSharePoint(anyString())).thenAnswer(invocation -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            seenPrincipal.set(authentication == null ? null : authentication.getPrincipal());
            both.countDown();
            return List.of();
        });
        RstPrincipal adminUser = principal(RstRoles.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminUser, null, List.of()));

        assertThat(admin.startSharePointSync(adminUser).running()).isTrue();

        assertThat(both.await(5, TimeUnit.SECONDS)).isTrue();
        waitUntilStopped(adminUser);
        assertThat(seenPrincipal.get()).isEqualTo(adminUser);
    }

    @Test
    void rejectsASecondStartWhileTheFirstIsRunning() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(syncService.syncFromSharePoint(anyString())).thenAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of();
        });
        RstPrincipal adminUser = principal(RstRoles.ADMIN);
        admin.startSharePointSync(adminUser);
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> admin.startSharePointSync(adminUser))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already running");

        release.countDown();
        waitUntilStopped(adminUser);
    }

    private void waitUntilStopped(RstPrincipal principal) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!admin.sharePointSyncStatus(principal).running()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("SharePoint sync still running");
    }

    private static RstPrincipal principal(String role) {
        return new RstPrincipal(role + "-1", role, role.toLowerCase() + "@cma-cgm.com", Set.of(role), Set.of(), "SHA");
    }
}
