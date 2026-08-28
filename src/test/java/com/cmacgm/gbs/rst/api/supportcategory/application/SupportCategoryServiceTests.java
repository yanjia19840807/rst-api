package com.cmacgm.gbs.rst.api.supportcategory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.security.RstPrincipal;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.CreateSupportCategoryRequest;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.ReorderSupportCategoriesRequest;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.SupportCategoryAdminRow;
import com.cmacgm.gbs.rst.api.supportcategory.api.dto.UpdateSupportCategoryRequest;
import com.cmacgm.gbs.rst.api.supportcategory.domain.SupportCategory;
import com.cmacgm.gbs.rst.api.supportcategory.persistence.SupportCategoryRepository;
import org.junit.jupiter.api.Test;

class SupportCategoryServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-28T10:00:00Z");

    @Test
    void createAppendsActiveCategoryAndRejectsDuplicateName() {
        List<SupportCategory> store = new ArrayList<>();
        store.add(SupportCategory.create("Communication", 1, "S1", NOW));
        SupportCategoryService service = service(store);

        SupportCategoryAdminRow created = service.create(admin(), new CreateSupportCategoryRequest("  Tooling  "));

        assertThat(created.name()).isEqualTo("Tooling");
        assertThat(created.status()).isEqualTo(SupportCategory.STATUS_ACTIVE);
        assertThat(created.displayOrder()).isEqualTo(2);
        assertThatThrownBy(() -> service.create(admin(), new CreateSupportCategoryRequest("tooling")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateRenamesAndSwapsOrder() {
        List<SupportCategory> store = new ArrayList<>();
        SupportCategory first = SupportCategory.create("Communication", 1, "S1", NOW);
        SupportCategory second = SupportCategory.create("Reporting", 2, "S1", NOW);
        store.add(first);
        store.add(second);
        SupportCategoryService service = service(store);

        SupportCategoryAdminRow moved = service.update(
                second.getId(),
                admin(),
                new UpdateSupportCategoryRequest("Weekly Reporting", SupportCategory.STATUS_ACTIVE, 1));

        assertThat(moved.name()).isEqualTo("Weekly Reporting");
        assertThat(moved.displayOrder()).isEqualTo(1);
        assertThat(first.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void updateRejectsUnknownStatus() {
        List<SupportCategory> store = new ArrayList<>();
        SupportCategory category = SupportCategory.create("Training", 1, "S1", NOW);
        store.add(category);
        SupportCategoryService service = service(store);

        assertThatThrownBy(() -> service.update(
                        category.getId(),
                        admin(),
                        new UpdateSupportCategoryRequest("Training", "ARCHIVED", 1)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ACTIVE or INACTIVE");
    }

    @Test
    void listAdminIncludesInactive() {
        List<SupportCategory> store = new ArrayList<>();
        SupportCategory category = SupportCategory.create("Training", 1, "S1", NOW);
        category.setStatus(SupportCategory.STATUS_INACTIVE, "S1", NOW);
        store.add(category);
        SupportCategoryService service = service(store);

        assertThat(service.listActive()).isEmpty();
        assertThat(service.listAdmin()).extracting(SupportCategoryAdminRow::name).containsExactly("Training");
    }

    @Test
    void reorderWritesOneBasedDisplayOrder() {
        List<SupportCategory> store = new ArrayList<>();
        SupportCategory first = SupportCategory.create("Communication", 1, "S1", NOW);
        SupportCategory second = SupportCategory.create("Reporting", 2, "S1", NOW);
        SupportCategory third = SupportCategory.create("Training", 3, "S1", NOW);
        store.add(first);
        store.add(second);
        store.add(third);
        SupportCategoryService service = service(store);

        List<SupportCategoryAdminRow> ordered = service.reorder(
                admin(),
                new ReorderSupportCategoriesRequest(List.of(third.getId(), first.getId(), second.getId())));

        assertThat(ordered).extracting(SupportCategoryAdminRow::name)
                .containsExactly("Training", "Communication", "Reporting");
        assertThat(ordered).extracting(SupportCategoryAdminRow::displayOrder).containsExactly(1, 2, 3);
    }

    @Test
    void softDeleteHidesCategoryFromAdminList() {
        List<SupportCategory> store = new ArrayList<>();
        SupportCategory category = SupportCategory.create("Training", 1, "S1", NOW);
        store.add(category);
        SupportCategoryService service = service(store);

        service.softDelete(category.getId(), admin());

        assertThat(category.getDeletedAt()).isNotNull();
        assertThat(service.listAdmin()).isEmpty();
        assertThat(service.listActive()).isEmpty();

        SupportCategoryAdminRow recreated =
                service.create(admin(), new CreateSupportCategoryRequest("Training"));
        assertThat(recreated.name()).isEqualTo("Training");
    }

    @Test
    void resolveForWriteKeepsSoftDeletedCategoryOnExistingRow() {
        List<SupportCategory> store = new ArrayList<>();
        SupportCategory category = SupportCategory.create("Training", 1, "S1", NOW);
        store.add(category);
        SupportCategoryService service = service(store);
        service.softDelete(category.getId(), admin());

        SupportCategoryService.ResolvedCategory kept =
                service.resolveForWrite(category.getId(), category.getId());
        assertThat(kept.categoryName()).isEqualTo("Training");

        assertThatThrownBy(() -> service.resolveForWrite(category.getId(), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    private static SupportCategoryService service(List<SupportCategory> store) {
        return new SupportCategoryService(repo(store), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RstPrincipal admin() {
        return new RstPrincipal("ADMIN001", "Admin", "admin@timesheet.local", Set.of("ADMIN"), Set.of(), null);
    }

    private static SupportCategoryRepository repo(List<SupportCategory> store) {
        return (SupportCategoryRepository) Proxy.newProxyInstance(
                SupportCategoryRepository.class.getClassLoader(),
                new Class<?>[] {SupportCategoryRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc" -> store.stream()
                            .filter(item -> item.getDeletedAt() == null)
                            .sorted(Comparator.comparingInt(SupportCategory::getDisplayOrder)
                                    .thenComparing(SupportCategory::getName, String.CASE_INSENSITIVE_ORDER))
                            .toList();
                    case "findByIdAndDeletedAtIsNull" -> store.stream()
                            .filter(item -> item.getDeletedAt() == null && item.getId().equals(args[0]))
                            .findFirst();
                    case "findByDeletedAtIsNullAndNameIgnoreCase" -> {
                        String name = ((String) args[0]).toLowerCase();
                        yield store.stream()
                                .filter(item -> item.getDeletedAt() == null
                                        && item.getName().toLowerCase().equals(name))
                                .findFirst();
                    }
                    case "save" -> {
                        SupportCategory incoming = (SupportCategory) args[0];
                        store.removeIf(item -> item.getId().equals(incoming.getId()));
                        store.add(incoming);
                        yield incoming;
                    }
                    case "findById" -> store.stream()
                            .filter(item -> item.getId().equals(args[0]))
                            .findFirst();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
