package com.lisovskyi.core_service.case_party.dto.request;

import static com.lisovskyi.core_service.case_party.CasePartyConstants.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lisovskyi.core_service.case_party.CasePartyRole;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;

public record CasePartyUpdateRequest(
        JsonNullable<Long> clientId,

        JsonNullable<CasePartyRole> role,

        JsonNullable<Boolean> isPrimary,

        JsonNullable<@Size(max = OPPONENT_NAME_LENGTH) String> opponentName,

        JsonNullable<@Size(max = OPPONENT_CONTACT_LENGTH) String> opponentContact,

        JsonNullable<String> opponentDetails
) {
    // XOR client/opponentName інваріант тут НЕ перевіряється (на відміну від
    // CasePartyCreateRequest.isValidPartySource): PATCH-тіло бачить лише поля, що змінюються, а
    // не вже наявний стан сутності - валідація на самому DTO дала б хибні спрацювання на
    // частковому оновленні (напр. лише opponentContact без дотику до clientId/opponentName).
    // Інваріант перевіряється в CasePartyService.updateCaseParty на змерженій сутності.
    public CasePartyUpdateRequest {
        opponentName = StringNormalization.blankToNull(opponentName);
        opponentContact = StringNormalization.blankToNull(opponentContact);
        opponentDetails = StringNormalization.blankToNull(opponentDetails);
    }

    // role/isPrimary - NOT NULL на CaseParty (isPrimary - примітивний boolean). "role": null у
    // тілі PATCH інакше проходив би валідацію (JsonNullable сам по собі не забороняє
    // "присутнє й null"), а CasePartyMapper.applyPresentFields чесно виконав би
    // setIfPresent(..., caseParty::setRole) - для role це впало б пізніше на
    // Hibernate-флаші (500), а для isPrimary - одразу NPE на автоанбоксингу в сеттер. Той самий
    // підхід, що й CaseUpdateRequest - явний 400 на межі контролера.
    @JsonIgnore
    @AssertTrue(message = "'role' must not be explicitly set to null")
    public boolean isRoleValidIfPresent() {
        return notExplicitlyNull(role);
    }

    @JsonIgnore
    @AssertTrue(message = "'isPrimary' must not be explicitly set to null")
    public boolean isPrimaryValidIfPresent() {
        return notExplicitlyNull(isPrimary);
    }

    private static boolean notExplicitlyNull(JsonNullable<?> value) {
        return value == null || !value.isPresent() || value.get() != null;
    }
}
