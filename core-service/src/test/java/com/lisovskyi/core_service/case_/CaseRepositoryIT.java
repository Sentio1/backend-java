package com.lisovskyi.core_service.case_;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_party.CaseParty;
import com.lisovskyi.core_service.case_party.CasePartyRepository;
import com.lisovskyi.core_service.case_party.CasePartyRole;
import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.ClientRepository;
import com.lisovskyi.core_service.client.ClientType;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

// findAllByClientIdAndOrganizationId - гілка "клієнт -> його справи" у SearchService (SEN-22).
// case_parties_case_id_client_id_role_idx (V5__case_parties.sql) - унікальний по (case_id,
// client_id, role), НЕ (case_id, client_id): той самий клієнт легально буває стороною однієї
// справи під двома ролями одночасно (напр. VICTIM + APPLICANT, SEN-21) - на SQL-рівні такий
// JOIN дає два рядки на одну справу. Hibernate тут наразі сам дедуплікує root-entity результати
// за identity навіть без DISTINCT (перевірено), тому явного дубля в Case-списку зараз немає й
// без DISTINCT у запиті - але це деталь реалізації ORM, покладатись на неї мовчки не варто.
// Цей тест фіксує очікуваний результат (одна справа, не дві) як контракт незалежно від того,
// чи саме зараз тримає його DISTINCT у запиті, чи дедуп Hibernate.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class CaseRepositoryIT {

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CasePartyRepository casePartyRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findAllByClientIdAndOrganizationId_clientWithTwoRolesOnSameCase_returnsCaseOnce() {
        Long organizationId = 5_001L;

        Client client = clientRepository.save(Client.builder()
                .organizationId(organizationId)
                .type(ClientType.INDIVIDUAL)
                .firstName("Іван")
                .lastName("Потерпілий")
                .birthDate(LocalDate.of(1990, 1, 1))
                .rnokpp("3123456789")
                .address("м. Київ")
                .email("victim@example.test")
                .createdBy(1L)
                .build());

        Case case_ = caseRepository.save(Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .createdBy(1L)
                .title("Кримінальне провадження")
                .procedure(ProcedureType.CRIMINAL)
                .instance(CaseInstance.FIRST)
                .status(CaseStatus.ACTIVE)
                .build());

        // Один і той самий клієнт - водночас потерпілий і заявник у тій самій справі: дозволено
        // на рівні схеми (унікальність по role, не по (case, client) самому по собі).
        casePartyRepository.save(CaseParty.builder()
                .organizationId(organizationId)
                .case_(case_)
                .client(client)
                .role(CasePartyRole.VICTIM)
                .build());
        casePartyRepository.save(CaseParty.builder()
                .organizationId(organizationId)
                .case_(case_)
                .client(client)
                .role(CasePartyRole.APPLICANT)
                .build());
        flushAndDetach();

        // Підтверджує саму передумову тесту: 2 окремих CaseParty-рядки дійсно існують (не один
        // через якийсь неочікуваний upsert/merge).
        long partyRowCount = casePartyRepository
                .findAllByCaseIdAndOrganizationId(case_.getId(), organizationId, Pageable.unpaged())
                .getTotalElements();
        assertThat(partyRowCount).isEqualTo(2);

        Page<Case> result =
                caseRepository.findAllByClientIdAndOrganizationId(client.getId(), organizationId, Pageable.ofSize(100));

        assertThat(result.getContent()).extracting(Case::getId).containsExactly(case_.getId());
    }
}
