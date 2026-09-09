package com.lisovskyi.core_service.holiday.dto.request;

import static com.lisovskyi.core_service.holiday.HolidayConstants.NAME_LENGTH;

import com.lisovskyi.core_service.holiday.enums.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

// year НЕ приймається від клієнта: він повністю похідний від date (EXTRACT(YEAR FROM date) -
// так само й у сідингу V34), і дозволити клієнту задати його окремо означало б новий клас
// помилок "рік не збігається з датою" без жодної користі - HolidayService.createHoliday
// виставляє його сам одразу після мапінгу.
//
// holidayType - навмисно NOT NULL (а не мовчазний дефолт): "яка природа цього дня" - це і є
// сам факт, який записує цей рядок, тож автор запису вказує його явно, той самий підхід, що й
// DeadlineRuleCreateRequest.durationUnit/dayKind/countFrom (AC SEN-24 - "не мовчазний дефолт").
// isWorking НЕ приймається взагалі - похідне від holidayType (Holiday.isWorking()), не окреме
// поле, яке можна встановити незалежно (інакше саме той ризик розсинхрону, якого SEN-26
// навмисно уникає).
public record HolidayCreateRequest(
        @NotNull LocalDate date,

        @NotBlank @Size(max = NAME_LENGTH) String name,

        @NotNull HolidayType holidayType,

        // відколи цей рядок календаря чинний (SEN-26) - може бути в майбутньому відносно
        // "сьогодні", якщо перенесення на конкретну дату оголошене заздалегідь.
        @NotNull LocalDate effectiveFrom) {}
