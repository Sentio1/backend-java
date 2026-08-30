package com.lisovskyi.core_service.case_party;

import com.lisovskyi.core_service.case_.enums.CaseStatus;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CasePartyRepository extends JpaRepository<CaseParty, Long> {

    @Query("SELECT COUNT(cp) > 0 FROM CaseParty cp " + "WHERE cp.client.id = :clientId "
            + "AND cp.organizationId = :organizationId "
            + "AND cp.case_.status NOT IN :terminalStatuses")
    boolean existsActiveCaseForClient(
            @Param("clientId") Long clientId,
            @Param("organizationId") Long organizationId,
            @Param("terminalStatuses") Set<CaseStatus> terminalStatuses);
}
