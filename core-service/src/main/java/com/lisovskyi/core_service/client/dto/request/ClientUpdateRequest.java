package com.lisovskyi.core_service.client.dto.request;

import static com.lisovskyi.core_service.client.ClientConstants.COMPANY_NAME_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.DIRECTOR_NAME_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.NAME_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.PASSPORT_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.PHONE_NUMBER_LENGTH;

import com.lisovskyi.core_service.client.validation.Edrpou;
import com.lisovskyi.core_service.client.validation.Rnokpp;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

public record ClientUpdateRequest(

        // фізична особа / ФОП
        JsonNullable<@Size(max = NAME_LENGTH) String> lastName,
        JsonNullable<@Size(max = NAME_LENGTH) String> firstName,
        JsonNullable<@Size(max = NAME_LENGTH) String> middleName,
        JsonNullable<LocalDate> birthDate,
        JsonNullable<@Rnokpp String> rnokpp,
        JsonNullable<@Size(max = PASSPORT_LENGTH) String> passport,

        // юридична особа
        JsonNullable<@Size(max = COMPANY_NAME_LENGTH) String> companyName,
        JsonNullable<@Edrpou String> edrpou,
        JsonNullable<@Size(max = DIRECTOR_NAME_LENGTH) String> directorName,

        // спільні
        JsonNullable<@Email String> email,
        JsonNullable<@Size(max = PHONE_NUMBER_LENGTH) String> phoneNumber,
        JsonNullable<String> address,
        JsonNullable<String> notes) {

    public ClientUpdateRequest {
        rnokpp = StringNormalization.blankToNull(rnokpp);
        edrpou = StringNormalization.blankToNull(edrpou);
    }
}
