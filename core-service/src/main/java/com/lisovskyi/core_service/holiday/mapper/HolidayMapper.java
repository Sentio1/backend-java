package com.lisovskyi.core_service.holiday.mapper;

import static com.sentio.shared.util.JsonNullableSupport.setIfPresent;

import com.lisovskyi.core_service.holiday.Holiday;
import com.lisovskyi.core_service.holiday.dto.request.HolidayCreateRequest;
import com.lisovskyi.core_service.holiday.dto.request.HolidayUpdateRequest;
import com.lisovskyi.core_service.holiday.dto.response.HolidayResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HolidayMapper {

    // isWorking, а не source="isWorking": Holiday.isWorking() - гетер-only метод (похідний
    // від holidayType, без поля й без сетера), і MapStruct все одно виводить JavaBean-ім'я
    // властивості з нього як "working" (стандартне зняття префікса "is" для boolean-гетера),
    // а не з імені методу - тож джерело саме "working". holidayType мапиться автоматично за
    // збігом імені.
    @Mapping(target = "isWorking", source = "working")
    HolidayResponse toResponse(Holiday holiday);

    // year - НЕ з request (HolidayCreateRequest його взагалі не приймає, див. коментар там) -
    // HolidayService.createHoliday виставляє його окремим setYear(...) після мапінгу, так
    // само як DeadlineRuleMapper лишає version за DeadlineRuleService.
    @Mapping(target = "year", ignore = true)
    Holiday toEntity(HolidayCreateRequest request);

    // Усі JsonNullable-поля ignore=true й проставляються вручну в applyPresentFields(...):
    // це PATCH, JsonNullable розрізняє "поле відсутнє в тілі" (не чіпати) від "поле є і
    // дорівнює null" (очистити) - той самий підхід, що й DeadlineRuleMapper.
    @Mapping(target = "date", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "holidayType", ignore = true)
    @Mapping(target = "effectiveFrom", ignore = true)
    // year - похідне від date (HolidayUpdateRequest його не приймає), а date незмінний - тож
    // при оновленні year ніколи не перераховується.
    @Mapping(target = "year", ignore = true)
    void updateEntityFromRequest(HolidayUpdateRequest request, @MappingTarget Holiday holiday);

    @AfterMapping
    default void applyPresentFields(HolidayUpdateRequest request, @MappingTarget Holiday holiday) {
        setIfPresent(request.name(), holiday::setName);
        setIfPresent(request.holidayType(), holiday::setHolidayType);
        setIfPresent(request.effectiveFrom(), holiday::setEffectiveFrom);
    }
}
