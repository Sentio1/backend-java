package com.lisovskyi.core_service.holiday;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, LocalDate> {

    @Query("SELECT h FROM Holiday h " + "WHERE h.date >= :from " + "AND h.date <= :to AND h.effectiveFrom <= :effectiveFrom")
    List<Holiday> findAllByDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("effectiveFrom") LocalDate effectiveFrom);

    Optional<Holiday> findByDate(LocalDate date);

    void deleteByDate(LocalDate date);

    Page<Holiday> findAllByYear(short year, Pageable pageable);
}
