package com.cmacgm.gbs.rst.api.identity.persistence;

import java.util.Optional;
import java.util.UUID;

import com.cmacgm.gbs.rst.api.identity.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for application users.
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Finds an active user by id.
     *
     * @param id user id
     * @return optional active user
     */
    Optional<AppUser> findByIdAndActiveTrue(UUID id);

    /**
     * Finds an active user by CCGID.
     *
     * @param ccgid corporate identity
     * @return optional active user
     */
    Optional<AppUser> findByCcgidAndActiveTrue(String ccgid);
}
