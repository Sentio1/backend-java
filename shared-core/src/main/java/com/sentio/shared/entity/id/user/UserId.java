package com.sentio.shared.entity.id.user;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record UserId(@JsonValue long id) implements EntityId {

    public UserId {
        EntityId.requirePositive(id, "User id");
    }

    @JsonCreator
    public static UserId of(long id) {
        return new UserId(id);
    }
}
