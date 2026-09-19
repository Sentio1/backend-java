package com.sentio.core_service.litigation.internal.mapper;

import static com.sentio.shared.util.JsonNullableSupport.setIfPresent;

import com.sentio.core_service.litigation.internal.model.Case;
import com.sentio.core_service.litigation.internal.model.CaseParty;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyCreateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyUpdateRequest;
import com.sentio.core_service.litigation.internal.controller.dto.CasePartyResponse;
import com.sentio.core_service.client.api.dto.ClientResponse;
import com.sentio.shared.util.JsonNullableSupport;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = JsonNullableSupport.class)
public interface CasePartyMapper {

    // isPrimary: той самий isXxx()-геттер / record-accessor mismatch, що вже виправлений у
    // CourtMapper.isActive - Lombok генерує isPrimary() (не getIsPrimary()), MapStruct з нього
    // виводить властивість "primary", а record-акцесор CasePartyResponse.isPrimary() лишається
    // "isPrimary" без стрипінгу.
    //
    // client - resolved by the caller through client's ClientService (CaseParty only stores
    // clientId); id/organizationId/createdAt/updatedAt exist on both sources, hence explicit.
    @Mapping(target = "id", source = "caseParty.id")
    @Mapping(target = "organizationId", source = "caseParty.organizationId")
    @Mapping(target = "createdAt", source = "caseParty.createdAt")
    @Mapping(target = "updatedAt", source = "caseParty.updatedAt")
    @Mapping(target = "caseId", source = "caseParty.case_.id")
    @Mapping(target = "client", source = "client")
    @Mapping(target = "isClient", expression = "java(caseParty.getClientId() != null)")
    @Mapping(target = "isPrimary", source = "caseParty.primary")
    @Mapping(target = "role", source = "caseParty.role")
    @Mapping(target = "opponentName", source = "caseParty.opponentName")
    @Mapping(target = "opponentContact", source = "caseParty.opponentContact")
    @Mapping(target = "opponentDetails", source = "caseParty.opponentDetails")
    CasePartyResponse toResponse(CaseParty caseParty, ClientResponse client);

    // clientId не мапиться звідси: спершу треба перевірити через ClientService (модуль client),
    // що клієнт існує й належить тій самій організації, а мапер такого доступу навмисно не має.
    // CasePartyService робить цю перевірку й виставляє caseParty.setClientId(...) після toEntity(...).
    //
    // id/createdAt/updatedAt/deletedAt/deletedBy/deleteReason/restoredAt/restoredBy - явний
    // ignore=true. Без цього MapStruct бачить другий параметр (Case case_) як ще одне
    // ДЖЕРЕЛО для мапінгу (не просто значення для поля case_) і за збігом імен властивостей
    // (обидві сутності успадковують CoreEntity) тихцем копіює їх зі СПРАВИ: нова CaseParty
    // отримувала case_.getId() як власний id (колізія/дублікат PK з іншою стороною), а також
    // createdAt/deletedAt/... справи замість власних. organizationId - той самий механізм, але
    // тут навмисно залишений і зроблений явним (@Mapping нижче), бо це насправді правильне
    // значення (сторона завжди в організації своєї справи), просто без явного @Mapping воно було
    // б випадковим побічним ефектом того самого бага, а не свідомим рішенням.
    @Mapping(target = "clientId", ignore = true)
    @Mapping(target = "case_", source = "case_")
    @Mapping(target = "organizationId", source = "case_.organizationId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    CaseParty toEntity(CasePartyCreateRequest request, Case case_);

    // Усі JsonNullable-поля ignore=true й проставляються вручну в applyPresentFields(...) - той
    // самий PATCH-патерн, що й CaseMapper/ClientMapper: без цього unwrap() зводить "відсутнє" й
    // "присутнє-й-null" до одного й того ж null та завжди перезаписує поле, навіть коли воно не
    // передане в тілі запиту.
    //
    // isPrimary: request.isPrimary() тут JsonNullable<Boolean>, а не примітивний boolean (як на
    // CasePartyCreateRequest) - для не-boolean типу повернення MapStruct НЕ зрізає "is"-префікс
    // за JavaBean-конвенцією, тож властивість джерела лишається буквально "isPrimary", а не
    // "primary", тому обробляється так само вручну через applyPresentFields, а не автомапінгом.
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "opponentName", ignore = true)
    @Mapping(target = "opponentContact", ignore = true)
    @Mapping(target = "opponentDetails", ignore = true)
    @Mapping(target = "primary", ignore = true)
    // clientId - set by CasePartyService via setIfPresent (validated against the client module);
    // auto-mapping it here would null it on every PATCH that simply doesn't mention it.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "deletedBy", ignore = true)
    @Mapping(target = "deleteReason", ignore = true)
    @Mapping(target = "restoredAt", ignore = true)
    @Mapping(target = "restoredBy", ignore = true)
    @Mapping(target = "organizationId", ignore = true)
    @Mapping(target = "case_", ignore = true)
    @Mapping(target = "clientId", ignore = true)
    void updateEntityFromRequest(CasePartyUpdateRequest request, @MappingTarget CaseParty caseParty);

    @AfterMapping
    default void applyPresentFields(CasePartyUpdateRequest request, @MappingTarget CaseParty caseParty) {
        setIfPresent(request.role(), caseParty::setRole);
        setIfPresent(request.opponentName(), caseParty::setOpponentName);
        setIfPresent(request.opponentContact(), caseParty::setOpponentContact);
        setIfPresent(request.opponentDetails(), caseParty::setOpponentDetails);
        setIfPresent(request.isPrimary(), caseParty::setPrimary);
    }
}
