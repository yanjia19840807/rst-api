package com.cmacgm.gbs.rst.api.mail.persistence;

import java.util.List;

import com.cmacgm.gbs.rst.api.mail.domain.MailPreference;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Per-user mail switches.
 */
public interface MailPreferenceRepository extends JpaRepository<MailPreference, MailPreference.Pk> {

    /**
     * @param ccgid owner
     * @return saved switches
     */
    List<MailPreference> findByIdCcgidIgnoreCase(String ccgid);
}
