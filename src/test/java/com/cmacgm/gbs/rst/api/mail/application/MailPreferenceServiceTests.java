package com.cmacgm.gbs.rst.api.mail.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.mail.domain.MailPreference;
import com.cmacgm.gbs.rst.api.mail.domain.MailType;
import com.cmacgm.gbs.rst.api.mail.persistence.MailPreferenceRepository;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import org.junit.jupiter.api.Test;

class MailPreferenceServiceTests {

    @Test
    void missingRowMeansEnabled() {
        MailPreferenceService service = new MailPreferenceService(emptyRepo(), ccgid -> null);

        assertThat(service.isEnabled("S1", MailType.APPROVAL_REQUESTED)).isTrue();
    }

    @Test
    void savedOffIsHonored() {
        MailPreference row = MailPreference.of("S1", MailType.APPROVAL_REQUESTED.id(), false);
        MailPreferenceService service = new MailPreferenceService(repoWith(row), ccgid -> "s1@timesheet.local");

        assertThat(service.isEnabled("s1", MailType.APPROVAL_REQUESTED)).isFalse();
    }

    @Test
    void supervisorDefaultsAllOutcomeTypesOn() {
        MailPreferenceService service = new MailPreferenceService(emptyRepo(), ccgid -> "s1@timesheet.local");
        RstPrincipal principal = new RstPrincipal(
                "S1", "Yang", "s1@dev.local", Set.of("SUPERVISOR"), Set.of(), "KL");

        MailPreferenceService.PreferenceView view = service.current(principal);

        assertThat(view.email()).isEqualTo("s1@timesheet.local");
        assertThat(view.emailMissing()).isFalse();
        assertThat(view.types()).extracting(MailPreferenceService.TypeView::enabled).containsOnly(true);
        assertThat(view.types()).extracting(MailPreferenceService.TypeView::id)
                .containsExactly("submission.outcome");
    }

    @Test
    void missingTimesheetEmailIsFlagged() {
        MailPreferenceService service = new MailPreferenceService(emptyRepo(), ccgid -> null);
        RstPrincipal principal = new RstPrincipal(
                "S1", "Yang", "s1@dev.local", Set.of("SUPERVISOR"), Set.of(), "KL");

        MailPreferenceService.PreferenceView view = service.current(principal);

        assertThat(view.email()).isEmpty();
        assertThat(view.emailMissing()).isTrue();
    }

    @Test
    void delegatedCallerCannotChangePreferences() {
        MailPreferenceService service = new MailPreferenceService(emptyRepo(), ccgid -> null);
        RstPrincipal principal = new RstPrincipal(
                "S1",
                "Yang",
                "s1@dev.local",
                Set.of("SUPERVISOR"),
                Set.of(),
                "KL",
                "A1",
                "Actor",
                UUID.randomUUID());

        assertThatThrownBy(() -> service.current(principal)).isInstanceOf(ApiException.class);
    }

    private static MailPreferenceRepository emptyRepo() {
        return repoWith();
    }

    private static MailPreferenceRepository repoWith(MailPreference... rows) {
        List<MailPreference> store = new ArrayList<>(List.of(rows));
        return (MailPreferenceRepository) Proxy.newProxyInstance(
                MailPreferenceRepository.class.getClassLoader(),
                new Class<?>[] {MailPreferenceRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> {
                        MailPreference.Pk id = (MailPreference.Pk) args[0];
                        yield store.stream()
                                .filter(row -> row.getCcgid().equalsIgnoreCase(id.getCcgid())
                                        && row.getMailType().equals(id.getMailType()))
                                .findFirst();
                    }
                    case "findByIdCcgidIgnoreCase" -> {
                        String ccgid = (String) args[0];
                        yield store.stream()
                                .filter(row -> row.getCcgid().equalsIgnoreCase(ccgid))
                                .toList();
                    }
                    case "save" -> {
                        MailPreference incoming = (MailPreference) args[0];
                        store.removeIf(row ->
                                row.getCcgid().equalsIgnoreCase(incoming.getCcgid())
                                        && row.getMailType().equals(incoming.getMailType()));
                        store.add(incoming);
                        yield incoming;
                    }
                    default -> throw new AssertionError("Unexpected " + method.getName());
                });
    }
}
