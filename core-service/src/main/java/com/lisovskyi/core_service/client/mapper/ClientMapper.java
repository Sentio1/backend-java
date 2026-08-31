package com.lisovskyi.core_service.client.mapper;

import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.client.dto.request.ClientCreateRequest;
import com.lisovskyi.core_service.client.dto.request.ClientUpdateRequest;
import com.lisovskyi.core_service.client.dto.response.ClientMaskedResponse;
import com.lisovskyi.core_service.client.dto.response.ClientResponse;
import com.lisovskyi.core_service.client_activity.ClientActivity;
import com.sentio.shared.util.DataMaskingUtils;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.mapstruct.*;
import org.openapitools.jackson.nullable.JsonNullable;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
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
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "middleName", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    @Mapping(target = "rnokpp", ignore = true)
    @Mapping(target = "passport", ignore = true)
    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "edrpou", ignore = true)
    @Mapping(target = "directorName", ignore = true)
    @Mapping(target = "contactPersonName", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "address", ignore = true)
    @Mapping(target = "notes", ignore = true)
    void updateEntityFromRequest(ClientUpdateRequest request, @MappingTarget Client client);

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
    default void applyPresentFields(ClientUpdateRequest request, @MappingTarget Client client) {
        setIfPresent(request.lastName(), client::setLastName);
        setIfPresent(request.firstName(), client::setFirstName);
        setIfPresent(request.middleName(), client::setMiddleName);
        setIfPresent(request.birthDate(), client::setBirthDate);
        setIfPresent(request.rnokpp(), client::setRnokpp);
        setIfPresent(request.passport(), client::setPassport);
        setIfPresent(request.companyName(), client::setCompanyName);
        setIfPresent(request.edrpou(), client::setEdrpou);
        setIfPresent(request.directorName(), client::setDirectorName);
        setIfPresent(request.contactPersonName(), client::setContactPersonName);
        setIfPresent(request.email(), client::setEmail);
        setIfPresent(request.phoneNumber(), client::setPhoneNumber);
        setIfPresent(request.address(), client::setAddress);
        setIfPresent(request.notes(), client::setNotes);
    }

    default <T> void setIfPresent(JsonNullable<T> value, Consumer<T> setter) {
        if (value != null && value.isPresent()) {
            setter.accept(value.get());
        }
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

    default <T> T unwrap(JsonNullable<T> nullable) {
        return nullable == null || !nullable.isPresent() ? null : nullable.get();
    }
}
