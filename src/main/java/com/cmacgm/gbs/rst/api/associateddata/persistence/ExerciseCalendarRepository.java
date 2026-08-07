package com.cmacgm.gbs.rst.api.associateddata.persistence;

import java.util.UUID;

import com.cmacgm.gbs.rst.api.associateddata.domain.ExerciseCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for Exercise calendar headers. */
public interface ExerciseCalendarRepository extends JpaRepository<ExerciseCalendar, UUID> {
}
