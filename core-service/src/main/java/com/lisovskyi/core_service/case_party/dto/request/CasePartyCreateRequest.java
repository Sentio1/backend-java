package com.lisovskyi.core_service.case_party.dto.request;

import static com.lisovskyi.core_service.case_party.CasePartyConstants.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lisovskyi.core_service.case_party.CasePartyRole;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.util.StringUtils;

public record CasePartyCreateRequest(
        @NotNull CasePartyRole role,

        JsonNullable<Long> clientId,

        // основний клієнт справи (біллінг/головний контакт) - лише для клієнтської сторони,
        // не для опонента, див. isValidPrimaryFlag(). Не JsonNullable: на create "відсутнє" й
        // "false" - те саме, primitive-дефолт false вже покриває обидва випадки.
        boolean isPrimary,

        JsonNullable<@Size(max = OPPONENT_NAME_LENGTH) String> opponentName,

        JsonNullable<@Size(max = OPPONENT_CONTACT_LENGTH) String> opponentContact,

        JsonNullable<String> opponentDetails) {
    public CasePartyCreateRequest {
        opponentName = StringNormalization.blankToNull(opponentName);
        opponentContact = StringNormalization.blankToNull(opponentContact);
        opponentDetails = StringNormalization.blankToNull(opponentDetails);
    }

    @JsonIgnore
    @AssertTrue(
            message =
                    "Either 'clientId' must be specified for existing client OR 'opponentName' for an external opponent, but not both.")
    public boolean isValidPartySource() {
        boolean hasClient = clientId != null && clientId.isPresent() && clientId.get() != null;
        boolean hasOpponentName = opponentName != null && StringUtils.hasText(opponentName.orElse(null));

        // XOR: рівно одне з двох джерел
        return hasClient ^ hasOpponentName;
    }

    @JsonIgnore
    @AssertTrue(
            message =
                    "Opponent contact and details are only allowed when 'opponentName' is specified without 'clientId'.")
    public boolean isValidOpponentMetadata() {
        boolean hasClient = clientId != null && clientId.isPresent() && clientId.get() != null;
        boolean hasExtraOpponentData = (opponentContact != null && StringUtils.hasText(opponentContact.orElse(null)))
                || (opponentDetails != null && StringUtils.hasText(opponentDetails.orElse(null)));

        return !hasClient || !hasExtraOpponentData;
    }

    @JsonIgnore
    @AssertTrue(
            message = "'isPrimary' can only be true for a client-side party (requires 'clientId'), not for an opponent.")
    public boolean isValidPrimaryFlag() {
        boolean hasClient = clientId != null && clientId.isPresent() && clientId.get() != null;
        return !isPrimary || hasClient;
    }
}
