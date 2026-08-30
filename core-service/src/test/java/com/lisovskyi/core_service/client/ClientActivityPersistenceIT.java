package com.lisovskyi.core_service.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.TestcontainersConfiguration;
import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client_activity.ClientActivityRepository;
import com.sentio.shared.entity.id.client.ClientId;
import com.sentio.shared.entity.id.organization.OrganizationId;
import com.sentio.shared.entity.id.user.UserId;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

// Юніт-тест ClientMapperTest перевіряє поведінку @AfterMapping в пам'яті; цей IT перевіряє,
// що те саме дійсно доїжджає до БД - персистяться, замінюються (orphanRemoval) і не
// зникають, коли activities відсутнє в запиті на апдейт.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ClientActivityPersistenceIT {

    @Autowired
    private ClientService clientService;

    @Autowired
    private ClientActivityRepository clientActivityRepository;

    @Autowired
    private EntityManager entityManager;

    private void flushAndDetach() {
        entityManager.flush();
        entityManager.clear();
    }

    private ClientCreateRequest soleTraderRequest(String rnokpp, JsonNullable<List<String>> activities) {
        return new ClientCreateRequest(
                ClientType.SOLE_TRADER,
                JsonNullable.of("Тестовий"),
                JsonNullable.of("Іван"),
                JsonNullable.undefined(),
                JsonNullable.<LocalDate>undefined(),
                JsonNullable.of(rnokpp),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                activities);
    }

    private ClientUpdateRequest updateRequest(JsonNullable<List<String>> activities) {
        return new ClientUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                activities);
    }

    @Test
    void createClient_withActivities_persistsThemWithTheClientsOwnOrganizationId() {
        OrganizationId organizationId = OrganizationId.of(2_001L);
        ClientCreateRequest request =
                soleTraderRequest("1111111111", JsonNullable.of(List.of("62.01 Комп'ютерне програмування")));

        ClientResponse response = clientService.createClient(organizationId, UserId.of(1L), request);
        flushAndDetach();

        assertThat(response.activities()).containsExactly("62.01 Комп'ютерне програмування");
        assertThat(clientActivityRepository.findAll())
                .filteredOn(a -> a.getClient().getId().equals(response.id()))
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.getActivity()).isEqualTo("62.01 Комп'ютерне програмування");
                    assertThat(activity.getOrganizationId()).isEqualTo(organizationId.id());
                });
    }

    @Test
    void updateClient_withNewActivities_replacesOldRowsInsteadOfAccumulatingThem() {
        OrganizationId organizationId = OrganizationId.of(2_002L);
        ClientResponse created = clientService.createClient(
                organizationId,
                UserId.of(1L),
                soleTraderRequest("2222222222", JsonNullable.of(List.of("62.01 Стара"))));
        flushAndDetach();
        ClientId clientId = ClientId.of(created.id());

        ClientResponse updated = clientService.updateClient(
                clientId, organizationId, updateRequest(JsonNullable.of(List.of("69.10 Нова"))));
        flushAndDetach();

        assertThat(updated.activities()).containsExactly("69.10 Нова");
        assertThat(clientActivityRepository.findAll())
                .filteredOn(a -> a.getClient().getId().equals(created.id()))
                .extracting(a -> a.getActivity())
                .containsExactly("69.10 Нова");
    }

    @Test
    void updateClient_withActivitiesAbsentFromRequest_leavesExistingActivitiesUntouched() {
        OrganizationId organizationId = OrganizationId.of(2_003L);
        ClientResponse created = clientService.createClient(
                organizationId,
                UserId.of(1L),
                soleTraderRequest("3333333333", JsonNullable.of(List.of("62.01 Стара"))));
        flushAndDetach();
        ClientId clientId = ClientId.of(created.id());

        // activities відсутнє в тілі запиту -> PATCH не повинен його чіпати
        ClientResponse updated =
                clientService.updateClient(clientId, organizationId, updateRequest(JsonNullable.undefined()));
        flushAndDetach();

        assertThat(updated.activities()).containsExactly("62.01 Стара");
    }

    @Test
    void updateClient_withActivitiesExplicitlyNull_clearsThemInTheDatabase() {
        OrganizationId organizationId = OrganizationId.of(2_004L);
        ClientResponse created = clientService.createClient(
                organizationId,
                UserId.of(1L),
                soleTraderRequest("4444444444", JsonNullable.of(List.of("62.01 Стара"))));
        flushAndDetach();
        ClientId clientId = ClientId.of(created.id());

        ClientResponse updated =
                clientService.updateClient(clientId, organizationId, updateRequest(JsonNullable.of(null)));
        flushAndDetach();

        assertThat(updated.activities()).isEmpty();
        assertThat(clientActivityRepository.findAll())
                .filteredOn(a -> a.getClient().getId().equals(created.id()))
                .isEmpty();
    }
}
