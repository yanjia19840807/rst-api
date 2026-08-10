package com.cmacgm.gbs.rst.api.cycletime.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaselineSample;
import com.cmacgm.gbs.rst.api.cycletime.domain.CycleTimeBaselineSample.Pk;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for frozen SYSTEM baseline samples. */
public interface CycleTimeBaselineSampleRepository extends JpaRepository<CycleTimeBaselineSample, Pk> {
}
