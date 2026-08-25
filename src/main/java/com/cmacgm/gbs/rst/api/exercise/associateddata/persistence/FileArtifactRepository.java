package com.cmacgm.gbs.rst.api.exercise.associateddata.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.exercise.associateddata.domain.FileArtifact;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for file artifacts. */
public interface FileArtifactRepository extends JpaRepository<FileArtifact, UUID> {
}
