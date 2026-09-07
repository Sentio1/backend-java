package com.lisovskyi.core_service.deadline_rule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_event.enums.EventCode;
import com.lisovskyi.core_service.court.CourtInstance;
import com.lisovskyi.core_service.deadline_rule.enums.CountFrom;
import com.lisovskyi.core_service.deadline_rule.enums.DayKind;
import com.lisovskyi.core_service.deadline_rule.enums.DurationUnit;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * SEN-24: durationUnit/dayKind/countFrom/extendable are deliberately required (@NotNull, not
 * JsonNullable falling back to the entity's @Builder.Default) - for a legal deadline rule, a
 * silently-applied default (CALENDAR/NEXT_DAY/DAY/false) on an omitted field is exactly the
 * hidden decision the issue is about ("юрист має бачити, за яким правилом порахована дата").
 * This test runs through real Bean Validation, not just the annotations, since that's what
 * actually reaches an API caller as a 400.
 */
class DeadlineRuleCreateRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    /**
     * Mutable "wither" around {@link DeadlineRuleCreateRequest} - the record itself is immutable
     * and its 13-argument constructor is unwieldy to repeat per test, so this is one point to
     * build a valid request and then point-mutate a single field per test (same approach as
     * ClientCreateRequestValidationTest.Req).
     */
    private static final class Req {
        private String code = "CPC_STATEMENT_OF_DEFENCE";
        private ProcedureType procedure = ProcedureType.CIVIL;
        private CourtInstance instance = CourtInstance.FIRST;
        private EventCode triggerEventCode = EventCode.RULING_RECEIVED;
        private String title = "Подання відзиву на позовну заяву";
        private String legalBasis = "ст. 178 ЦПК України";
        private Short durationValue = (short) 15;
        private DurationUnit durationUnit = DurationUnit.DAY;
        private DayKind dayKind = DayKind.CALENDAR;
        private CountFrom countFrom = CountFrom.NEXT_DAY;
        private Boolean extendable = true;
        private LocalDate validFrom = LocalDate.of(2017, 12, 15);
        private JsonNullable<LocalDate> validTo = JsonNullable.undefined();

        DeadlineRuleCreateRequest build() {
            return new DeadlineRuleCreateRequest(
                    code, procedure, instance, triggerEventCode, title, legalBasis, durationValue, durationUnit,
                    dayKind, countFrom, extendable, validFrom, validTo);
        }
    }

    private Set<ConstraintViolation<DeadlineRuleCreateRequest>> violationsOf(Req req) {
        return validator.validate(req.build());
    }

    @Test
    void fullyPopulatedRequest_isValid() {
        assertThat(violationsOf(new Req())).isEmpty();
    }

    @Test
    void withValidToExplicitlyPresent_isValid_becauseValidToIsGenuinelyOptional() {
        Req req = new Req();
        req.validTo = JsonNullable.of(LocalDate.of(2020, 1, 1));

        assertThat(violationsOf(req)).isEmpty();
    }

    @Test
    void blankCode_isRejected() {
        Req req = new Req();
        req.code = "   ";

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullProcedure_isRejected() {
        Req req = new Req();
        req.procedure = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullInstance_isRejected() {
        Req req = new Req();
        req.instance = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullTriggerEventCode_isRejected() {
        Req req = new Req();
        req.triggerEventCode = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void blankTitle_isRejected() {
        Req req = new Req();
        req.title = " ";

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void blankLegalBasis_isRejected() {
        Req req = new Req();
        req.legalBasis = "";

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullDurationValue_isRejected() {
        Req req = new Req();
        req.durationValue = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void zeroDurationValue_isRejected_becauseMustBePositive() {
        Req req = new Req();
        req.durationValue = (short) 0;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void negativeDurationValue_isRejected() {
        Req req = new Req();
        req.durationValue = (short) -5;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullDurationUnit_isRejected_noSilentDefault() {
        Req req = new Req();
        req.durationUnit = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullDayKind_isRejected_noSilentDefault() {
        Req req = new Req();
        req.dayKind = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullCountFrom_isRejected_noSilentDefault() {
        Req req = new Req();
        req.countFrom = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullExtendable_isRejected_noSilentDefault() {
        Req req = new Req();
        req.extendable = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void nullValidFrom_isRejected() {
        Req req = new Req();
        req.validFrom = null;

        assertThat(violationsOf(req)).isNotEmpty();
    }

    @Test
    void codeTooLong_isRejected() {
        Req req = new Req();
        req.code = "X".repeat(51);

        assertThat(violationsOf(req)).isNotEmpty();
    }
}
