package com.sentio.shared.entity.id.case_;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record CaseId(@JsonValue long id) implements EntityId {

    public CaseId {
        EntityId.requirePositive(id, "Case id");
    }

    @JsonCreator
    public static CaseId of(long id) {
        return new CaseId(id);
    }
}
