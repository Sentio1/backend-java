package com.lisovskyi.core_service.deadline_rule.mapper;

import static com.sentio.shared.util.JsonNullableSupport.setIfPresent;

import com.lisovskyi.core_service.deadline_rule.DeadlineRule;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleCreateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.request.DeadlineRuleUpdateRequest;
import com.lisovskyi.core_service.deadline_rule.dto.response.DeadlineRuleResponse;
import com.sentio.shared.util.JsonNullableSupport;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        uses = JsonNullableSupport.class)
public interface DeadlineRuleMapper {

    @Mapping(target = "instance", source = "courtInstance")
    DeadlineRuleResponse toResponse(DeadlineRule deadlineRule);

    // version - НЕ з request: це саме те поле, яке DeadlineRuleCreateRequest навмисно не
    // приймає від клієнта (див. коментар там) - сервіс рахує його сам (1 для нового
    // code, попередня версія + 1 для нової версії наявного) і виставляє окремим
    // setVersion(...) після мапінгу, так само як CaseMapper.toEntity лишає
    // status/court/... за CaseService.
    // isExtendable - явний @Mapping під іменем БІЛДЕРА, не властивості: toEntity генерується
    // через DeadlineRule.builder() (SuperBuilder), а метод білдера для `private boolean
    // isExtendable` називається буквально isExtendable(boolean) - як само поле, без
    // get/set-нормалізації, на відміну від сетера сутності (setIsExtendable), який MapStruct
    // бачить під іншим іменем в updateEntityFromRequest нижче. Тому тут ціль саме
    // "isExtendable", а не "extendable" (target="extendable" тут не компілюється: "Unknown
    // property "extendable" in result type DeadlineRule.DeadlineRuleBuilder"). Без цього
    // @Mapping request.extendable() автозіставленням не резолвиться на builder.isExtendable(...)
    // і поле мовчки лишається false.
    @Mapping(target = "courtInstance", source = "instance")
    @Mapping(target = "isExtendable", source = "extendable")
    @Mapping(target = "version", ignore = true)
    DeadlineRule toEntity(DeadlineRuleCreateRequest request);

    // Усі JsonNullable-поля ignore=true й проставляються вручну в applyPresentFields(...):
    // це PATCH, JsonNullable розрізняє "поле відсутнє в тілі" (не чіпати) від "поле є і
    // дорівнює null" (очистити) - той самий підхід, що й CaseMapper.updateEntityFromRequest.
    // id тут не мапиться на entity взагалі: він лише ключ, за яким сервіс знаходить
    // рядок для оновлення, не поле, яке можна "оновити".
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", ignore = true)
    @Mapping(target = "legalBasis", ignore = true)
    @Mapping(target = "extendable", ignore = true)
    @Mapping(target = "validTo", ignore = true)
    void updateEntityFromRequest(DeadlineRuleUpdateRequest request, @MappingTarget DeadlineRule deadlineRule);

    @AfterMapping
    default void applyPresentFields(DeadlineRuleUpdateRequest request, @MappingTarget DeadlineRule deadlineRule) {
        setIfPresent(request.title(), deadlineRule::setTitle);
        setIfPresent(request.legalBasis(), deadlineRule::setLegalBasis);
        setIfPresent(request.extendable(), deadlineRule::setExtendable);
        setIfPresent(request.validTo(), deadlineRule::setValidTo);
    }
}
