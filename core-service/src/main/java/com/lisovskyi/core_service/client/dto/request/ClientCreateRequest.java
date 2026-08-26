package com.lisovskyi.core_service.client.dto.request;

import static com.lisovskyi.core_service.client.ClientConstants.*;

import com.lisovskyi.core_service.client.ClientType;
import com.lisovskyi.core_service.client.validation.Edrpou;
import com.lisovskyi.core_service.client.validation.Rnokpp;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.NonNull;
import org.springframework.util.StringUtils;

public record ClientCreateRequest(
        @NonNull ClientType type,

        @Size(max = NAME_LENGTH) String lastName,
        @Size(max = NAME_LENGTH) String firstName,
        @Size(max = NAME_LENGTH) String middleName,

        LocalDate birthDate,

        @Rnokpp String rnokpp,
        @Size(max = PASSPORT_LENGTH) String passport,
        @Size(max = COMPANY_NAME_LENGTH) String companyName,
        @Edrpou String edrpou,
        @Size(max = DIRECTOR_NAME_LENGTH) String directorName,

        @Email String email,
        @Size(max = PHONE_NUMBER_LENGTH) String phoneNumber,

        String address,
        String notes) {
    @AssertTrue(
            message =
                    "For COMPANY, 'companyName' is required. For individuals, 'lastName' and 'firstName' are required.")
    public boolean isValidNaming() {
        if (type == ClientType.COMPANY) {
            return StringUtils.hasText(companyName);
        } else {
            return StringUtils.hasText(lastName) && StringUtils.hasText(firstName);
        }
    }
}
