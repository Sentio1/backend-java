package com.lisovskyi.core_service.deadline_rule.dto.request;

import static com.lisovskyi.core_service.deadline_rule.DeadlineRuleConstants.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sentio.shared.util.StringNormalization;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.openapitools.jackson.nullable.JsonNullable;

// На відміну від DeadlineRuleCreateRequest, це справді частковий PATCH наявного рядка -
// але лише для того, що не міняє юридичну суть уже застосованого правила: code,
// procedure, instance, triggerEventCode, durationValue/durationUnit/dayKind/countFrom і
// validFrom тут навмисно відсутні. Їх зміна заднім числом означала б переписування
// правила, за яким уже могли порахувати дедлайн (AC SEN-24: "правило ніколи не
// редагується на місці") - для нового строку/тригера/дії потрібна нова версія через
// DeadlineRuleCreateRequest, а не PATCH цього рядка.
//
// title/legalBasis - косметичні правки (одруківка, уточнення формулювання) не
// зачіпають уже розраховані Deadline.dueOn (DeadlineEngine рахує лише
// durationValue/durationUnit/dayKind/countFrom, а title/legalBasis лише копіює на
// момент розрахунку - див. DeadlineEngine.generateDeadline).
// extendable - інформаційне поле, DeadlineEngine його не читає при розрахунку.
// validTo - єдиний спосіб "закрити" версію (наприклад, коли нова версія випускається на
// заміну) без створення нового рядка для цього самого effective-вікна.
public record DeadlineRuleUpdateRequest(
        @NotNull Long id,

        JsonNullable<@Size(max = TITLE_LENGTH) String> title,

        JsonNullable<@Size(max = LEGAL_BASIS_LENGTH) String> legalBasis,

        JsonNullable<Boolean> extendable,

        // null = зробити знову чинним безстроково.
        JsonNullable<LocalDate> validTo) {
    public DeadlineRuleUpdateRequest {
        title = StringNormalization.blankToNull(title);
        legalBasis = StringNormalization.blankToNull(legalBasis);
    }

    // title/legalBasis/extendable - усі NOT NULL на DeadlineRule. Без цих перевірок тіло
    // виду {"title": null} проходило б валідацію (JsonNullable сам по собі не забороняє
    // "присутнє й null") і впало б пізніше - або на Hibernate-флаші для String-полів,
    // або NPE на автоанбоксингу в setExtendable(boolean). Той самий підхід, що й
    // CaseUpdateRequest.isTitleValidIfPresent.
    @JsonIgnore
    @AssertTrue(message = "'title' must not be explicitly set to null")
    public boolean isTitleValidIfPresent() {
        return notExplicitlyNull(title);
    }

    @JsonIgnore
    @AssertTrue(message = "'legalBasis' must not be explicitly set to null")
    public boolean isLegalBasisValidIfPresent() {
        return notExplicitlyNull(legalBasis);
    }

    @JsonIgnore
    @AssertTrue(message = "'extendable' must not be explicitly set to null")
    public boolean isExtendableValidIfPresent() {
        return notExplicitlyNull(extendable);
    }

    private static boolean notExplicitlyNull(JsonNullable<?> value) {
        return value == null || !value.isPresent() || value.get() != null;
    }
}
