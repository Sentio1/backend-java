package com.lisovskyi.core_service.client.dto.request;

import com.lisovskyi.core_service.client.ClientType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

import static com.lisovskyi.core_service.client.ClientConstants.*;

public record ClientCreateRequest(
        ClientType type,

        @Size(max = NAME_LENGTH) String lastName,
        @Size(max = NAME_LENGTH) String firstName,
        @Size(max = NAME_LENGTH) String middleName,

        LocalDate birthDate,

        @Size(max = RNOKPP_LENGTH) String rnokpp,
        @Size(max = PASSPORT_LENGTH) String passport,
        @Size(max = COMPANY_NAME_LENGTH) String companyName,
        @Size(max = EDRPOU_LENGTH) String edrpou,
        @Size(max = DIRECTOR_NAME_LENGTH) String directorName,

        @Email String email,
        @Size(max = PHONE_NUMBER_LENGTH) String phoneNumber,

        String address,
        String notes
) {
    @AssertTrue(message = "For COMPANY, 'companyName' is required. For individuals, 'lastName' and 'firstName' are required.")
    public boolean isValidNaming() {
        if (type == ClientType.COMPANY) {
            return StringUtils.hasText(companyName);
        } else {
            return StringUtils.hasText(lastName) && StringUtils.hasText(firstName);
        }
    }
}
