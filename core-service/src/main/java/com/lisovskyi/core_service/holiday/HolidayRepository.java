package com.lisovskyi.core_service.holiday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    @Query("SELECT h FROM Holiday h " +
            "WHERE h.date >= :from " +
            "AND h.date <= :to")
    List<Holiday> findAllByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
