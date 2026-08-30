package com.lisovskyi.core_service.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ClientSoftDeleteRestrictionIT {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EntityManager entityManager;

    private Client persistClient(Long organizationId, String rnokpp, String edrpou) {
        Client client = Client.builder()
                .organizationId(organizationId)
                .type(ClientType.INDIVIDUAL)
                .firstName("Іван")
                .lastName("Тестовий")
                .rnokpp(rnokpp)
                .edrpou(edrpou)
                .createdBy(1L)
                .build();
        return clientRepository.save(client);
    }

    private void softDelete(Client client) {
        client.setDeletedAt(Instant.now());
        client.setDeletedBy(1L);
        client.setDeleteReason("test");
        clientRepository.save(client);
    }

    // The persistence-context identity map can otherwise hand back the very instance we
    // just saved without re-running the query, which would make these assertions pass
    // even if @SQLRestriction (or the repository method) were broken.
    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void findAllByOrganizationId_excludesSoftDeletedClients() {
        Long orgId = 1_001L;
        Client active = persistClient(orgId, "1111111111", null);
        Client deleted = persistClient(orgId, "2222222222", null);
        softDelete(deleted);
        flushAndDetach();

        var page = clientRepository.findAllByOrganizationId(orgId, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Client::getId).containsExactly(active.getId());
    }

    @Test
    void findByIdAndOrganizationId_returnsEmptyForSoftDeletedClient() {
        Long orgId = 1_002L;
        Client deleted = persistClient(orgId, "3333333333", null);
        softDelete(deleted);
        flushAndDetach();

        assertThat(clientRepository.findByIdAndOrganizationId(deleted.getId(), orgId))
                .isEmpty();
    }

    @Test
    void plainFindById_alsoRespectsRestriction_notJustCustomDerivedQueries() {
        Long orgId = 1_003L;
        Client deleted = persistClient(orgId, "4444444444", null);
        softDelete(deleted);
        flushAndDetach();

        assertThat(clientRepository.findById(deleted.getId())).isEmpty();
    }

    @Test
    void existsByRnokppAndIdNot_ignoresSoftDeletedClient_soTheRnokppFreesUpForReuse() {
        Long orgId = 1_004L;
        Client deleted = persistClient(orgId, "5555555555", null);
        softDelete(deleted);
        flushAndDetach();

        // excludeId = null mirrors the real create-path call in
        // ClientService.assertUniqueTaxIds(...), which has no id to exclude yet.
        assertThat(clientRepository.existsByOrganizationIdAndRnokppAndIdNot(orgId, "5555555555", null))
                .isFalse();
    }

    @Test
    void existsByEdrpouAndIdNot_ignoresSoftDeletedClient_soTheEdrpouFreesUpForReuse() {
        Long orgId = 1_005L;
        Client deleted = persistClient(orgId, null, "12345670");
        softDelete(deleted);
        flushAndDetach();

        assertThat(clientRepository.existsByOrganizationIdAndEdrpouAndIdNot(orgId, "12345670", null))
                .isFalse();
    }

    @Test
    void existsByRnokppAndIdNot_stillDetectsAnActiveDuplicate_withNullExcludeId() {
        Long orgId = 1_006L;
        persistClient(orgId, "6666666666", null);
        flushAndDetach();

        // This is the exact call shape used on client creation: no id to exclude yet,
        // and Spring Data JPA needs to turn "id <> :id" with a null :id into
        // "id IS NOT NULL" (always true for a not-null PK) rather than silently
        // matching nothing and disabling the duplicate check.
        assertThat(clientRepository.existsByOrganizationIdAndRnokppAndIdNot(orgId, "6666666666", null))
                .isTrue();
    }

    @Test
    void existsByRnokppAndIdNot_excludesItsOwnRow_onUpdate() {
        Long orgId = 1_007L;
        Client self = persistClient(orgId, "7777777777", null);
        flushAndDetach();

        // This is the update-path call shape: excluding the client's own id must not
        // make it "collide with itself".
        assertThat(clientRepository.existsByOrganizationIdAndRnokppAndIdNot(orgId, "7777777777", self.getId()))
                .isFalse();
    }

    @Test
    void existsByRnokppAndIdNot_stillDetectsAnActiveDuplicate_otherThanTheExcludedId() {
        Long orgId = 1_008L;
        Client other = persistClient(orgId, "8888888888", null);
        Client self = persistClient(orgId, "9999999999", null);
        flushAndDetach();

        assertThat(clientRepository.existsByOrganizationIdAndRnokppAndIdNot(orgId, "8888888888", self.getId()))
                .isTrue();
        assertThat(clientRepository.existsByOrganizationIdAndRnokppAndIdNot(orgId, "8888888888", other.getId()))
                .isFalse();
    }

    @Test
    void nativeSearchQuery_doesNotRelyOnSqlRestriction_stillExcludesSoftDeletedClients() {
        Long orgId = 1_009L;
        Client active = persistClient(orgId, "1010101010", null);
        Client deleted = persistClient(orgId, "2020202020", null);
        active.setLastName("Ковальчук");
        deleted.setLastName("Ковальчук");
        clientRepository.save(active);
        clientRepository.save(deleted);
        softDelete(deleted);
        flushAndDetach();

        var page = clientRepository.searchClient(orgId, "Ковальчук", PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Client::getId).containsExactly(active.getId());
    }

    @Test
    void nativeFindAllDeletedByOrganizationId_returnsOnlySoftDeletedClients() {
        Long orgId = 1_010L;
        Client active = persistClient(orgId, "3030303030", null);
        Client deleted = persistClient(orgId, "4040404040", null);
        softDelete(deleted);
        flushAndDetach();

        var page = clientRepository.findAllDeletedByOrganizationId(orgId, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Client::getId).containsExactly(deleted.getId());
        assertThat(page.getContent()).extracting(Client::getId).doesNotContain(active.getId());
    }

    @Test
    void nativeFindDeletedByIdAndOrganizationId_returnsEmptyForAnActiveClient() {
        Long orgId = 1_011L;
        Client active = persistClient(orgId, "5050505050", null);
        flushAndDetach();

        assertThat(clientRepository.findDeletedByIdAndOrganizationId(active.getId(), orgId))
                .isEmpty();
    }
}
