package com.lisovskyi.core_service.client.dto.request;

import com.lisovskyi.core_service.client.ClientType;
import com.lisovskyi.core_service.client.validation.Edrpou;
import com.lisovskyi.core_service.client.validation.Rnokpp;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.lisovskyi.core_service.client.ClientConstants.*;

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
    @AssertTrue(
            message =
                    "For COMPANY, 'companyName' is required. For individuals, 'lastName' and 'firstName' are required.")
    public boolean isValidNaming() {
        if (type == ClientType.COMPANY) {
            return StringUtils.hasText(companyName.orElse(null));
        } else {
            return StringUtils.hasText(lastName.orElse(null)) && StringUtils.hasText(firstName.orElse(null));
        }
    }

    public ClientCreateRequest {
        rnokpp = StringNormalization.blankToNull(rnokpp);
        edrpou = StringNormalization.blankToNull(edrpou);
    }
}
