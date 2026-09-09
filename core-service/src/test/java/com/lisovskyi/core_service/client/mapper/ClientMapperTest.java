package com.lisovskyi.core_service.client.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.ClientType;
import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client_activity.ClientActivity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

class ClientMapperTest {

    private final ClientMapper clientMapper = new ClientMapperImpl();

    private ClientCreateRequest createRequest(JsonNullable<List<String>> activities) {
        return new ClientCreateRequest(
                ClientType.SOLE_TRADER,
                JsonNullable.of("Тестовий"),
                JsonNullable.of("Іван"),
                JsonNullable.undefined(),
                JsonNullable.<LocalDate>undefined(),
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

    // ─── toEntity (create) ───────────────────────────────────────────

    @Test
    void toEntity_withActivitiesPresent_addsActivitiesLinkedBackToTheSameClient() {
        ClientCreateRequest request =
                createRequest(JsonNullable.of(List.of("62.01 Комп'ютерне програмування", "69.10 Юридична діяльність")));

        Client client = clientMapper.toEntity(request, 100L, 5L);

        assertThat(client.getActivities())
                .extracting(ClientActivity::getActivity)
                .containsExactly("62.01 Комп'ютерне програмування", "69.10 Юридична діяльність");
        assertThat(client.getActivities()).allSatisfy(activity -> {
            assertThat(activity.getClient()).isSameAs(client);
            assertThat(activity.getOrganizationId()).isEqualTo(100L);
        });
    }

    @Test
    void toEntity_withActivitiesAbsent_leavesActivitiesEmpty() {
        ClientCreateRequest request = createRequest(JsonNullable.undefined());

        Client client = clientMapper.toEntity(request, 100L, 5L);

        assertThat(client.getActivities()).isEmpty();
    }

    @Test
    void toEntity_withActivitiesPresentButEmptyList_leavesActivitiesEmpty() {
        ClientCreateRequest request = createRequest(JsonNullable.of(List.of()));

        Client client = clientMapper.toEntity(request, 100L, 5L);

        assertThat(client.getActivities()).isEmpty();
    }

    private ClientUpdateRequest updateRequestWithContactPersonName(JsonNullable<String> contactPersonName) {
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
                contactPersonName,
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());
    }

    // ─── updateEntityFromRequest ─────────────────────────────────────

    private Client existingCompanyClientWithContactPerson() {
        return Client.builder()
                .organizationId(100L)
                .type(ClientType.COMPANY)
                .companyName("ТОВ Тест")
                .contactPersonName("Стара контактна особа")
                .createdBy(5L)
                .build();
    }

    @Test
    void updateEntityFromRequest_withContactPersonNameAbsent_doesNotTouchExistingValue() {
        Client client = existingCompanyClientWithContactPerson();
        ClientUpdateRequest request = updateRequestWithContactPersonName(JsonNullable.undefined());

        clientMapper.updateEntityFromRequest(request, client);

        assertThat(client.getContactPersonName()).isEqualTo("Стара контактна особа");
    }

    @Test
    void updateEntityFromRequest_withContactPersonNamePresentAndNull_clearsExistingValue() {
        Client client = existingCompanyClientWithContactPerson();
        ClientUpdateRequest request = updateRequestWithContactPersonName(JsonNullable.of(null));

        clientMapper.updateEntityFromRequest(request, client);

        assertThat(client.getContactPersonName()).isNull();
    }

    @Test
    void updateEntityFromRequest_withContactPersonNamePresent_replacesExistingValue() {
        Client client = existingCompanyClientWithContactPerson();
        ClientUpdateRequest request = updateRequestWithContactPersonName(JsonNullable.of("Нова контактна особа"));

        clientMapper.updateEntityFromRequest(request, client);

        assertThat(client.getContactPersonName()).isEqualTo("Нова контактна особа");
    }

    private Client existingClientWithActivities() {
        Client client = Client.builder()
                .organizationId(100L)
                .type(ClientType.SOLE_TRADER)
                .lastName("Тестовий")
                .firstName("Іван")
                .createdBy(5L)
                .build();
        client.addActivity(ClientActivity.builder()
                .organizationId(100L)
                .activity("62.01 Стара діяльність")
                .build());
        return client;
    }

    @Test
    void updateEntityFromRequest_withActivitiesAbsent_doesNotTouchExistingActivities() {
        Client client = existingClientWithActivities();
        ClientUpdateRequest request = updateRequest(JsonNullable.undefined());

        clientMapper.updateEntityFromRequest(request, client);

        assertThat(client.getActivities())
                .extracting(ClientActivity::getActivity)
                .containsExactly("62.01 Стара діяльність");
    }

    @Test
    void updateEntityFromRequest_withActivitiesPresentAndNull_clearsExistingActivities() {
        Client client = existingClientWithActivities();
        ClientUpdateRequest request = updateRequest(JsonNullable.of(null));

        clientMapper.updateEntityFromRequest(request, client);

        assertThat(client.getActivities()).isEmpty();
    }

    @Test
    void updateEntityFromRequest_withActivitiesPresent_replacesExistingActivitiesWholesale() {
        Client client = existingClientWithActivities();
        ClientUpdateRequest request = updateRequest(JsonNullable.of(List.of("69.10 Нова діяльність")));

        clientMapper.updateEntityFromRequest(request, client);

        assertThat(client.getActivities())
                .extracting(ClientActivity::getActivity)
                .containsExactly("69.10 Нова діяльність");
        assertThat(client.getActivities()).allSatisfy(activity -> {
            assertThat(activity.getClient()).isSameAs(client);
            assertThat(activity.getOrganizationId()).isEqualTo(100L);
        });
    }

    // ─── toResponse ───────────────────────────────────────────────────

    @Test
    void toResponse_mapsClientActivitiesToPlainStringList() {
        Client client = existingClientWithActivities();
        client.addActivity(ClientActivity.builder()
                .organizationId(100L)
                .activity("69.10 Друга діяльність")
                .build());

        ClientResponse response = clientMapper.toResponse(client);

        assertThat(response.activities()).containsExactly("62.01 Стара діяльність", "69.10 Друга діяльність");
    }
}
