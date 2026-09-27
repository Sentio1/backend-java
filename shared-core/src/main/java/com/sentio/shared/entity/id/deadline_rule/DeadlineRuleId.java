package com.sentio.shared.entity.id.deadline_rule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.sentio.shared.entity.id.EntityId;

public record DeadlineRuleId(@JsonValue long id) implements EntityId {

    public DeadlineRuleId {
        EntityId.requirePositive(id, "DeadlineRule id");
    }

    @JsonCreator
    public static DeadlineRuleId of(long id) {
        return new DeadlineRuleId(id);
    }
}
