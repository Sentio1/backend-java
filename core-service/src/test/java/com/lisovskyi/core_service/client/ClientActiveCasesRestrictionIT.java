package com.lisovskyi.core_service.client;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.CaseRepository;
import com.lisovskyi.core_service.case_.enums.CaseStatus;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_party.CaseParty;
import com.lisovskyi.core_service.case_party.CasePartyRepository;
import com.lisovskyi.core_service.case_party.CasePartyRole;
import com.lisovskyi.core_service.client.exception.ClientHasActiveCasesException;
import com.lisovskyi.core_service.court.Court;
import com.sentio.shared.entity.id.client.ClientId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// SEN-20 AC: "клієнт з активними справами не видаляється - тільки архівується". Перевіряє
// саме склейку ClientService.deleteClient -> CasePartyRepository.existsActiveCaseForClient,
// не кожен шматок окремо.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ClientActiveCasesRestrictionIT {

    @Autowired
    private ClientService clientService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private CaseRepository caseRepository;

    @Autowired
    private CasePartyRepository casePartyRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    private Court persistCourt() {
        Court court = Court.builder()
                .name("Печерський районний суд м. Києва")
                .code("757-active-cases-it")
                .instance((short) 1)
                .timeZone("Europe/Kyiv")
                .isActive(true)
                .build();
        entityManager.persist(court);
        return court;
    }

    private Client persistClient(Long organizationId) {
        Client client = Client.builder()
                .organizationId(organizationId)
                .type(ClientType.INDIVIDUAL)
                .firstName("Іван")
                .lastName("Тестовий")
                .createdBy(1L)
                .build();
        return clientRepository.save(client);
    }

    private Case persistCase(Long organizationId, Court court, CaseStatus status) {
        Case case_ = Case.builder()
                .organizationId(organizationId)
                .responsibleUserId(1L)
                .title("Позов про стягнення заборгованості")
                .procedure(ProcedureType.CIVIL)
                .court(court)
                .status(status)
                .build();
        return caseRepository.save(case_);
    }

    private void linkAsParty(Long organizationId, Case case_, Client client) {
        CaseParty party = CaseParty.builder()
                .organizationId(organizationId)
                .case_(case_)
                .client(client)
                .role(CasePartyRole.PLAINTIFF)
                .build();
        casePartyRepository.save(party);
    }

    @Test
    void deleteClient_withActiveCase_throwsAndLeavesClientUndeleted() {
        Long organizationId = 3_001L;
        Court court = persistCourt();
        Client client = persistClient(organizationId);
        Case activeCase = persistCase(organizationId, court, CaseStatus.FIRST_INSTANCE);
        linkAsParty(organizationId, activeCase, client);
        flushAndDetach();

        assertThatThrownBy(() -> clientService.deleteClient(
                        ClientId.of(client.getId()), OrganizationId.of(organizationId), UserId.of(1L), "test"))
                .isInstanceOf(ClientHasActiveCasesException.class);
        flushAndDetach();

        assertThat(clientRepository.findByIdAndOrganizationId(client.getId(), organizationId))
                .isPresent();
    }

    @Test
    void deleteClient_withOnlyClosedOrArchivedCases_deletesSuccessfully() {
        Long organizationId = 3_002L;
        Court court = persistCourt();
        Client client = persistClient(organizationId);
        Case closedCase = persistCase(organizationId, court, CaseStatus.CLOSED);
        Case archivedCase = persistCase(organizationId, court, CaseStatus.ARCHIVED);
        linkAsParty(organizationId, closedCase, client);
        linkAsParty(organizationId, archivedCase, client);
        flushAndDetach();

        clientService.deleteClient(ClientId.of(client.getId()), OrganizationId.of(organizationId), UserId.of(1L), "test");
        flushAndDetach();

        assertThat(clientRepository.findByIdAndOrganizationId(client.getId(), organizationId))
                .isEmpty();
    }

    @Test
    void deleteClient_withNoCases_deletesSuccessfully() {
        Long organizationId = 3_003L;
        Client client = persistClient(organizationId);
        flushAndDetach();

        clientService.deleteClient(ClientId.of(client.getId()), OrganizationId.of(organizationId), UserId.of(1L), "test");
        flushAndDetach();

        assertThat(clientRepository.findByIdAndOrganizationId(client.getId(), organizationId))
                .isEmpty();
    }
}
