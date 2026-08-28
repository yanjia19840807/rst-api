package com.cmacgm.gbs.rst.api.mail.persistence;

import java.util.List;
import java.util.Optional;

import com.cmacgm.gbs.rst.api.mail.domain.SsoProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SSO directory used for mail.
 */
public interface SsoProfileRepository extends JpaRepository<SsoProfile, String> {

    /**
     * Latest LTH seen for a Center.
     *
     * @param role LTH
     * @param center toolkit center
     * @return newest first
     */
    List<SsoProfile> findByRoleAndCenterIgnoreCaseOrderBySeenAtDesc(String role, String center);

    /**
     * @param role product role
     * @return profiles
     */
    List<SsoProfile> findByRole(String role);

    /**
     * @param ccgid identity
     * @return profile
     */
    Optional<SsoProfile> findByCcgidIgnoreCase(String ccgid);
}
