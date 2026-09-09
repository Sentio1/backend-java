package com.lisovskyi.core_service.client.mapper;

import static com.sentio.shared.util.JsonNullableSupport.setIfPresent;

import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientMaskedResponse;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client_activity.ClientActivity;
import com.sentio.shared.util.DataMaskingUtils;
import com.sentio.shared.util.JsonNullableSupport;
import java.time.LocalDate;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = JsonNullableSupport.class)
public interface ClientMapper {

    // organizationId/createdById надходять не з тіла запиту (JWT/шлях), а не з ClientCreateRequest,
    // тому мапляться як окремі параметри - щоб ClientService не виставляв їх вручну сеттерами
    // після toEntity(request).
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", source = "organizationId")
    @Mapping(target = "createdBy", source = "createdById")
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    @Mapping(target = "activities", ignore = true)
    Client toEntity(ClientCreateRequest request, Long organizationId, Long createdById);

    // Усі поля DTO нижче ignore=true й проставляються вручну в applyPresentFields(...): це PATCH,
    // JsonNullable розрізняє "поле відсутнє в тілі" (не чіпати) від "поле є і дорівнює null"
    // (очистити) - а звичайний згенерований MapStruct-мапінг через unwrap() обидва випадки
    // зводить до null і тому завжди перезаписував поле, навіть коли воно просто не передане.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "activities", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromRequest(ClientUpdateRequest request, @MappingTarget Client client);

    @Condition
    default <T> boolean isPresent(org.openapitools.jackson.nullable.JsonNullable<T> value) {
        return value != null && value.isPresent();
    }

    ClientResponse toResponse(Client client);

    @Named("toMaskedResponse")
    @Mapping(target = "rnokpp", source = "rnokpp", qualifiedByName = "maskRnokpp")
    @Mapping(target = "passport", source = "passport", qualifiedByName = "maskPassport")
    @Mapping(target = "birthDate", source = "birthDate", qualifiedByName = "maskBirthDate")
    ClientMaskedResponse toMaskedResponse(Client client);

    @Named("maskRnokpp")
    default String maskRnokpp(String rnokpp) {
        return DataMaskingUtils.maskRnokpp(rnokpp);
    }

    @Named("maskPassport")
    default String maskPassport(String passport) {
        return DataMaskingUtils.maskPassport(passport);
    }

    @Named("maskBirthDate")
    default String maskBirthDate(LocalDate birthDate) {
        return DataMaskingUtils.maskBirthDate(birthDate);
    }


    @AfterMapping
    default void mapActivities(ClientCreateRequest request, @MappingTarget Client client) {
        if (request.activities() != null && request.activities().isPresent()) {
            var activities = request.activities().get();
            if (activities != null && !activities.isEmpty()) {
                activities.forEach(activity -> client.addActivity(ClientActivity.builder()
                        .organizationId(client.getOrganizationId())
                        .activity(activity)
                        .build()));
            }
        }
    }

    @AfterMapping
    default void mapActivities(ClientUpdateRequest request, @MappingTarget Client client) {
        if (request.activities() != null && request.activities().isPresent()) {
            client.getActivities().clear();
            var activities = request.activities().get();
            if (activities != null && !activities.isEmpty()) {
                activities.forEach(activity -> client.addActivity(ClientActivity.builder()
                        .organizationId(client.getOrganizationId())
                        .activity(activity)
                        .build()));
            }
        }
    }

    default String toActivityValue(ClientActivity activity) {
        return activity.getActivity();
    }
}
