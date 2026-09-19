package com.sentio.core_service.court.internal.repository;

import com.sentio.core_service.court.internal.model.Court;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtRepository extends JpaRepository<Court, Long> {
}
