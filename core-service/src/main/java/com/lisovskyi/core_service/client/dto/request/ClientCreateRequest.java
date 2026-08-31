package com.lisovskyi.core_service.client.dto.request;

import static com.lisovskyi.core_service.client.ClientConstants.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lisovskyi.core_service.client.ClientType;
import com.lisovskyi.core_service.client.validation.Edrpou;
import com.lisovskyi.core_service.client.validation.Rnokpp;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

// Усі опційні поля - JsonNullable, як у ClientUpdateRequest: уніфікований DTO-стиль
// create/update, а не голі nullable-типи лише для створення. type лишається plain -
// він завжди обов'язковий, "відсутнє значення" тут не має сенсу.
public record ClientCreateRequest(
        @NotNull ClientType type,

        JsonNullable<@Size(max = NAME_LENGTH) String> lastName,
        JsonNullable<@Size(max = NAME_LENGTH) String> firstName,
        JsonNullable<@Size(max = NAME_LENGTH) String> middleName,

        JsonNullable<LocalDate> birthDate,

        JsonNullable<@Rnokpp String> rnokpp,
        JsonNullable<@Size(max = PASSPORT_LENGTH) String> passport,
        JsonNullable<@Size(max = COMPANY_NAME_LENGTH) String> companyName,
        JsonNullable<@Edrpou String> edrpou,
        JsonNullable<@Size(max = DIRECTOR_NAME_LENGTH) String> directorName,
        JsonNullable<@Size(max = CONTACT_PERSON_NAME_LENGTH) String> contactPersonName,

        JsonNullable<@Email String> email,
        JsonNullable<@Size(max = PHONE_NUMBER_LENGTH) String> phoneNumber,

        JsonNullable<String> address,
        JsonNullable<String> notes,
        JsonNullable<List<@Size(max = ACTIVITY_LENGTH) String>> activities) {

    // 1. ПІБ або Назва компанії
    @JsonIgnore
    @AssertTrue(message = "For COMPANY, 'companyName' is required. For individuals/sole traders, 'lastName' and 'firstName' are required.")
    public boolean isValidNaming() {
        if (type == null) return true;
        if (type == ClientType.COMPANY) {
            return StringUtils.hasText(companyName.orElse(null));
        } else {
            return StringUtils.hasText(lastName.orElse(null)) && StringUtils.hasText(firstName.orElse(null));
        }
    }

    // 2. Ідентифікаційні коди (РНОКПП / ЄДРПОУ / Паспорт)
    @JsonIgnore
    @AssertTrue(message = "For INDIVIDUAL, 'rnokpp' (or 'passport') is required. For SOLE_TRADER, 'rnokpp' or 'edrpou' (or 'passport') is required. For COMPANY, 'edrpou' is required.")
    public boolean isValidTaxIdentifier() {
        if (type == null) return true;
        boolean hasRnokpp = StringUtils.hasText(rnokpp.orElse(null));
        boolean hasPassport = StringUtils.hasText(passport.orElse(null));
        boolean hasEdrpou = StringUtils.hasText(edrpou.orElse(null));

        return switch (type) {
            case INDIVIDUAL -> hasRnokpp || hasPassport;
            case SOLE_TRADER -> hasRnokpp || hasEdrpou || hasPassport;
            case COMPANY -> hasEdrpou;
        };
    }

    // 3. Дата народження (обов'язкова для фізосіб та ФОП)
    @JsonIgnore
    @AssertTrue(message = "Birth date is required for INDIVIDUAL and SOLE_TRADER.")
    public boolean isValidBirthDate() {
        if (type == null) return true;
        if (type == ClientType.INDIVIDUAL || type == ClientType.SOLE_TRADER) {
            return birthDate.orElse(null) != null;
        }
        return true;
    }

    // 4. Адреса (обов'язкова для всіх типів клієнтів за AC)
    @JsonIgnore
    @AssertTrue(message = "Address is required for all client types.")
    public boolean isValidAddress() {
        if (type == null) return true;
        return StringUtils.hasText(address.orElse(null));
    }

    // 5. Контакти (принаймні телефон або email)
    @JsonIgnore
    @AssertTrue(message = "At least one contact method (email or phone number) is required.")
    public boolean isValidContacts() {
        if (type == null) return true;
        return StringUtils.hasText(email.orElse(null)) || StringUtils.hasText(phoneNumber.orElse(null));
    }

    // 6. Специфіка Юрособи (керівник та контактна особа)
    @JsonIgnore
    @AssertTrue(message = "Director name and contact person name are required for COMPANY.")
    public boolean isValidCompanyDetails() {
        if (type == null) return true;
        if (type == ClientType.COMPANY) {
            return StringUtils.hasText(directorName.orElse(null))
                    && StringUtils.hasText(contactPersonName.orElse(null));
        }
        return true;
    }

    // 7. Специфіка ФОП (види діяльності обов'язкові тільки для ФОП і заборонені для інших)
    @JsonIgnore
    @AssertTrue(message = "Activities list is required for SOLE_TRADER and must be empty for INDIVIDUAL or COMPANY.")
    public boolean isValidActivities() {
        if (type == null) return true;
        List<String> acts = activities.orElse(null);
        boolean hasActivities = !CollectionUtils.isEmpty(acts);

        if (type == ClientType.SOLE_TRADER) {
            return hasActivities;
        }
        return !hasActivities;
    }

    public ClientCreateRequest {
        rnokpp = StringNormalization.blankToNull(rnokpp);
        edrpou = StringNormalization.blankToNull(edrpou);
    }
}
