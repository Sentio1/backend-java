package com.sentio.shared.entity.id.case_event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record CaseEventId(@JsonValue long id) implements EntityId {

    public CaseEventId {
        EntityId.requirePositive(id, "Case event id");
    }

    @JsonCreator
    public static CaseEventId of(long id) {
        return new CaseEventId(id);
    }
}
