package com.cmacgm.gbs.rst.api.geography.persistence;

import java.util.List;
import java.util.Optional;

import com.cmacgm.gbs.rst.api.geography.domain.CustomerCountry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCountryRepository extends JpaRepository<CustomerCountry, String> {

    List<CustomerCountry> findAllByOrderByNameAsc();

    Optional<CustomerCountry> findByNameIgnoreCase(String name);
}
