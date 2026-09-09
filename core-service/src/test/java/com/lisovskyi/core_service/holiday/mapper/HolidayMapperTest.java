package com.lisovskyi.core_service.holiday.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.holiday.Holiday;
import com.lisovskyi.core_service.holiday.dto.request.HolidayCreateRequest;
import com.lisovskyi.core_service.holiday.dto.request.HolidayUpdateRequest;
import com.lisovskyi.core_service.holiday.dto.response.HolidayResponse;
import com.lisovskyi.core_service.holiday.enums.HolidayType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

// Регресія: Holiday.isWorking() - гетер-only метод, похідний від holidayType, без поля й без
// сетера. MapStruct все одно виводить JavaBean-ім'я властивості з isWorking() як "working"
// (стандартне зняття префікса "is" для boolean-гетера), а не з імені методу - тож
// автозіставлення за іменем бачить ціль HolidayResponse.isWorking як "isWorking", а джерело
// Holiday як "working", і мовчки лишає поле false незалежно від holidayType (компілятор
// попереджає лише "Unmapped target property"). Явний @Mapping(target = "isWorking", source =
// "working") на HolidayMapper.toResponse - фікс; тести нижче ловлять регресію, якщо його
// прибрати.
class HolidayMapperTest {

    private final HolidayMapper holidayMapper = new HolidayMapperImpl();

    private Holiday existingHoliday() {
        return Holiday.builder()
                .date(LocalDate.of(2026, 1, 1))
                .name("Новий рік")
                .holidayType(HolidayType.PUBLIC_HOLIDAY)
                .year((short) 2026)
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .build();
    }

    // ─── toResponse ───────────────────────────────────────────────────────

    @Test
    void toResponse_publicHoliday_reportsIsWorkingFalse() {
        HolidayResponse response = holidayMapper.toResponse(existingHoliday());

        assertThat(response.holidayType()).isEqualTo(HolidayType.PUBLIC_HOLIDAY);
        assertThat(response.isWorking()).isFalse();
    }

    @Test
    void toResponse_transferredWorkingDay_reportsIsWorkingTrue() {
        // Не просто "isWorking лишився false за замовчуванням" - без фіксу @Mapping обидва
        // кейси (PUBLIC_HOLIDAY і TRANSFERRED_WORKING_DAY) схлопнулись би в false. Пара з
        // попереднім тестом - саме те, що ловить регресію.
        Holiday holiday = Holiday.builder()
                .date(LocalDate.of(2026, 11, 14))
                .name("Відпрацювання за перенесенням")
                .holidayType(HolidayType.TRANSFERRED_WORKING_DAY)
                .year((short) 2026)
                .effectiveFrom(LocalDate.of(2026, 10, 1))
                .build();

        HolidayResponse response = holidayMapper.toResponse(holiday);

        assertThat(response.isWorking()).isTrue();
    }

    // ─── toEntity (create) ────────────────────────────────────────────────

    @Test
    void toEntity_mapsHolidayTypeByName() {
        HolidayCreateRequest request = new HolidayCreateRequest(
                LocalDate.of(2026, 11, 14),
                "Відпрацювання за перенесенням",
                HolidayType.TRANSFERRED_WORKING_DAY,
                LocalDate.of(2026, 10, 1));

        Holiday holiday = holidayMapper.toEntity(request);

        assertThat(holiday.getHolidayType()).isEqualTo(HolidayType.TRANSFERRED_WORKING_DAY);
        assertThat(holiday.isWorking()).isTrue();
    }

    @Test
    void toEntity_ignoresYear_leavesItAtJavaDefault() {
        // @Mapping(target = "year", ignore = true) - мапер узагалі не чіпає year, тож toEntity()
        // лишає його на дефолтному 0 (short без @Builder.Default в Holiday). HolidayService
        // виставляє реальне значення (з date.getYear()) одразу після мапінгу - цей тест лише
        // фіксує, що мапер сам нічого не вираховує, а не яке саме число тут з'являється.
        HolidayCreateRequest request = new HolidayCreateRequest(
                LocalDate.of(2026, 1, 1), "Новий рік", HolidayType.PUBLIC_HOLIDAY, LocalDate.of(2026, 1, 1));

        Holiday holiday = holidayMapper.toEntity(request);

        assertThat(holiday.getYear()).isEqualTo((short) 0);
    }

    // ─── updateEntityFromRequest (PATCH semantics) ─────────────────────────

    private HolidayUpdateRequest allUndefinedUpdateRequest(LocalDate date) {
        return new HolidayUpdateRequest(date, JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.undefined());
    }

    @Test
    void updateEntityFromRequest_withAllFieldsAbsent_doesNotTouchExistingValues() {
        Holiday holiday = existingHoliday();

        holidayMapper.updateEntityFromRequest(allUndefinedUpdateRequest(holiday.getDate()), holiday);

        assertThat(holiday.getName()).isEqualTo("Новий рік");
        assertThat(holiday.getHolidayType()).isEqualTo(HolidayType.PUBLIC_HOLIDAY);
        assertThat(holiday.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void updateEntityFromRequest_doesNotTouchDateOrYear() {
        // date = ключ пошуку (PK), year - похідне від date (див. HolidayUpdateRequest) - жодне з
        // них не має поля для патчу в HolidayUpdateRequest узагалі.
        Holiday holiday = existingHoliday();

        holidayMapper.updateEntityFromRequest(allUndefinedUpdateRequest(holiday.getDate()), holiday);

        assertThat(holiday.getDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(holiday.getYear()).isEqualTo((short) 2026);
    }

    @Test
    void updateEntityFromRequest_withNamePresent_replacesExistingValue() {
        Holiday holiday = existingHoliday();
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                holiday.getDate(), JsonNullable.of("Уточнена назва"), JsonNullable.undefined(), JsonNullable.undefined());

        holidayMapper.updateEntityFromRequest(request, holiday);

        assertThat(holiday.getName()).isEqualTo("Уточнена назва");
    }

    @Test
    void updateEntityFromRequest_withHolidayTypePresent_replacesExistingValue_andIsWorkingFollows() {
        Holiday holiday = existingHoliday();
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                holiday.getDate(),
                JsonNullable.undefined(),
                JsonNullable.of(HolidayType.TRANSFERRED_WORKING_DAY),
                JsonNullable.undefined());

        holidayMapper.updateEntityFromRequest(request, holiday);

        assertThat(holiday.getHolidayType()).isEqualTo(HolidayType.TRANSFERRED_WORKING_DAY);
        assertThat(holiday.isWorking()).isTrue();
    }

    @Test
    void updateEntityFromRequest_withEffectiveFromPresent_replacesExistingValue() {
        Holiday holiday = existingHoliday();
        HolidayUpdateRequest request = new HolidayUpdateRequest(
                holiday.getDate(), JsonNullable.undefined(), JsonNullable.undefined(), JsonNullable.of(LocalDate.of(2026, 6, 1)));

        holidayMapper.updateEntityFromRequest(request, holiday);

        assertThat(holiday.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 6, 1));
    }
}
