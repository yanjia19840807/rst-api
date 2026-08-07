package com.cmacgm.gbs.rst.api.official.persistence;

import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.official.domain.OfficialPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for Official Packages. */
public interface OfficialPackageRepository extends JpaRepository<OfficialPackage, UUID> {

    /**
     * Finds the current Official Package for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return optional current package
     */
    Optional<OfficialPackage> findByExerciseIdAndCurrentTrue(UUID exerciseId);

    /**
     * Returns the max package version for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return max version or empty
     */
    @Query("select max(p.packageVersion) from OfficialPackage p where p.exerciseId = :exerciseId")
    Optional<Integer> findMaxPackageVersion(@Param("exerciseId") UUID exerciseId);
}
