package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cmacgm.gbs.rst.api.associateddata.domain.DataImportBatch;

/** Persistence for volume/holiday import batches. */
public interface DataImportBatchRepository extends JpaRepository<DataImportBatch, UUID> {
}
