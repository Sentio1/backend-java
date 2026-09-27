package com.sentio.shared.entity.id.case_party;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record CasePartyId(@JsonValue long id) implements EntityId {

    public CasePartyId {
        EntityId.requirePositive(id, "Case party id");
    }

    @JsonCreator
    public static CasePartyId of(long id) {
        return new CasePartyId(id);
    }
}
