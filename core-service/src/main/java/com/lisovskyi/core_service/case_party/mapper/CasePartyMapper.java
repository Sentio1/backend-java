package com.lisovskyi.core_service.case_party.mapper;

import static com.sentio.shared.util.JsonNullableSupport.setIfPresent;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_party.CaseParty;
import com.lisovskyi.core_service.case_party.dto.request.CasePartyCreateRequest;
import com.lisovskyi.core_service.case_party.dto.request.CasePartyUpdateRequest;
import com.lisovskyi.core_service.case_party.dto.response.CasePartyResponse;
import com.lisovskyi.core_service.client.mapper.ClientMapper;
import com.sentio.shared.util.JsonNullableSupport;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {ClientMapper.class, JsonNullableSupport.class})
public interface CasePartyMapper {

    // isPrimary: той самий isXxx()-геттер / record-accessor mismatch, що вже виправлений у
    // CourtMapper.isActive - Lombok генерує isPrimary() (не getIsPrimary()), MapStruct з нього
    // виводить властивість "primary", а record-акцесор CasePartyResponse.isPrimary() лишається
    // "isPrimary" без стрипінгу.
    @Mapping(target = "caseId", source = "case_.id")
    @Mapping(target = "client", source = "client")
    @Mapping(target = "isClient", expression = "java(caseParty.getClient() != null)")
    @Mapping(target = "isPrimary", source = "primary")
    CasePartyResponse toResponse(CaseParty caseParty);

    // client не мапиться звідси: request.clientId() - це просто Long-ідентифікатор, а не сам
    // Client - щоб виставити реальний зв'язок, треба піти в ClientRepository і перевірити, що
    // клієнт належить тій самій організації (як і скрізь у ClientService/CaseFinderImpl), а
    // мапер такого доступу до БД навмисно не має. CasePartyService мусить сам зробити
    // find-by-id-and-organizationId і виставити caseParty.setClient(...) після toEntity(...).
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
    @Mapping(target = "client", ignore = true)
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
