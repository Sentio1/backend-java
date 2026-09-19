package com.sentio.shared.entity.id.deadline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record DeadlineId(@JsonValue long id) implements EntityId {

    public DeadlineId {
        EntityId.requirePositive(id, "Deadline id");
    }

    @JsonCreator
    public static DeadlineId of(long id) {
        return new DeadlineId(id);
    }
}
