package com.cmacgm.gbs.rst.api.official.persistence;

import java.util.Collection;
import java.util.List;
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
     * Finds all Official Packages for an Exercise (including non-current / returned).
     *
     * @param exerciseId Exercise id
     * @return packages
     */
    List<OfficialPackage> findByExerciseId(UUID exerciseId);

    /**
     * Finds all Official Packages for the given Exercises.
     *
     * @param exerciseIds Exercise ids
     * @return packages
     */
    List<OfficialPackage> findByExerciseIdIn(Collection<UUID> exerciseIds);

    /**
     * Returns the max package version for an Exercise.
     *
     * @param exerciseId Exercise id
     * @return max version or empty
     */
    @Query("select max(p.packageVersion) from OfficialPackage p where p.exerciseId = :exerciseId")
    Optional<Integer> findMaxPackageVersion(@Param("exerciseId") UUID exerciseId);
}
