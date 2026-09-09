package com.lisovskyi.core_service.holiday.dto.request;

import static com.lisovskyi.core_service.holiday.HolidayConstants.NAME_LENGTH;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lisovskyi.core_service.holiday.enums.HolidayType;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

// date - лише ключ, за яким HolidayService.updateHoliday знаходить рядок (date = PK, а не
// поле, яке можна "оновити" - зміна дати означала б інший рядок, не патч наявного). year тут
// теж відсутній: він похідний від date (див. HolidayCreateRequest), а date незмінний - тож
// перераховувати year при оновленні нема підстав. isWorking теж відсутній - похідне від
// holidayType (Holiday.isWorking()), не окреме поле для патчу.
public record HolidayUpdateRequest(
        @NotNull LocalDate date,

        JsonNullable<@Size(max = NAME_LENGTH) String> name,

        JsonNullable<HolidayType> holidayType,

        JsonNullable<LocalDate> effectiveFrom
) {
    public HolidayUpdateRequest {
        name = StringNormalization.blankToNull(name);
    }

    // name/holidayType/effectiveFrom - усі NOT NULL на Holiday. Без цих перевірок тіло виду
    // {"name": null} проходило б валідацію (JsonNullable сам по собі не забороняє "присутнє
    // й null") і впало б пізніше на Hibernate-флаші - той самий підхід, що й
    // DeadlineRuleUpdateRequest.isTitleValidIfPresent.
    @JsonIgnore
    @AssertTrue(message = "'name' must not be explicitly set to null")
    public boolean isNameValidIfPresent() {
        return notExplicitlyNull(name);
    }

    @JsonIgnore
    @AssertTrue(message = "'holidayType' must not be explicitly set to null")
    public boolean isHolidayTypeValidIfPresent() {
        return notExplicitlyNull(holidayType);
    }

    @JsonIgnore
    @AssertTrue(message = "'effectiveFrom' must not be explicitly set to null")
    public boolean isEffectiveFromValidIfPresent() {
        return notExplicitlyNull(effectiveFrom);
    }

    private static boolean notExplicitlyNull(JsonNullable<?> value) {
        return value == null || !value.isPresent() || value.get() != null;
    }
}
