package com.lisovskyi.core_service.case_event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseEventRepository extends JpaRepository<CaseEvent, Long> {


}
