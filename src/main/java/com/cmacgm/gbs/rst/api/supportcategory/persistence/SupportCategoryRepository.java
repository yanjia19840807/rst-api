package com.cmacgm.gbs.rst.api.supportcategory.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.supportcategory.domain.SupportCategory;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for Production Support categories. */
public interface SupportCategoryRepository extends JpaRepository<SupportCategory, UUID> {

    List<SupportCategory> findByDeletedAtIsNullOrderByDisplayOrderAscNameAsc();

    Optional<SupportCategory> findByIdAndDeletedAtIsNull(UUID id);
}
