package com.lisovskyi.core_service.client.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.client.ClientType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * SEN-20: pins down the per-type required-field invariants on {@link ClientCreateRequest}
 * (isValidNaming/isValidTaxIdentifier/isValidBirthDate/isValidAddress/isValidContacts/
 * isValidCompanyDetails/isValidActivities). Runs through real Bean Validation rather than calling
 * the boolean methods directly, since the thing that actually reaches an API caller is the
 * violation *message*, not just a true/false.
 */
class ClientCreateRequestValidationTest {

    // Довільні цифри тут провалили б @Rnokpp/@Edrpou (контрольна сума) і підмішали б у тест
    // сторонні порушення, тоді як мета цього класу - перевірити лише обов'язковість полів за
    // типом клієнта. Тому беремо реальні валідні за контрольною сумою значення.
    private static final String VALID_RNOKPP = "3123456789";
    private static final String VALID_EDRPOU = "32855961";

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

    private Req validIndividual() {
        return new Req()
                .type(ClientType.INDIVIDUAL)
                .lastName("Тестовий")
                .firstName("Іван")
                .birthDate(LocalDate.of(1990, 1, 1))
                .rnokpp(VALID_RNOKPP)
                .address("м. Київ, вул. Хрещатик, 1")
                .email("test@example.com");
    }

    private Req validSoleTrader() {
        return new Req()
                .type(ClientType.SOLE_TRADER)
                .lastName("Тестовий")
                .firstName("Іван")
                .birthDate(LocalDate.of(1990, 1, 1))
                .rnokpp(VALID_RNOKPP)
                .address("м. Київ, вул. Хрещатик, 1")
                .phoneNumber("+380501234567")
                .activities(List.of("62.01 Комп'ютерне програмування"));
    }

    private Req validCompany() {
        return new Req()
                .type(ClientType.COMPANY)
                .companyName("ТОВ Тест")
                .edrpou(VALID_EDRPOU)
                .directorName("Директор Директорович")
                .contactPersonName("Контактна особа")
                .address("м. Київ, вул. Хрещатик, 1")
                .email("company@example.com");
    }

    private Set<ConstraintViolation<ClientCreateRequest>> violationsOf(Req req) {
        return validator.validate(req.build());
    }

    private void assertSingleViolationContains(Req req, String expectedMessageFragment) {
        Set<ConstraintViolation<ClientCreateRequest>> violations = violationsOf(req);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains(expectedMessageFragment);
    }

    // ─── Позитивні кейси: повністю заповнений запит по кожному типу проходить без порушень ──

    @Test
    void individual_withAllRequiredFields_hasNoViolations() {
        assertThat(violationsOf(validIndividual())).isEmpty();
    }

    @Test
    void individual_withPassportInsteadOfRnokpp_hasNoViolations() {
        assertThat(violationsOf(validIndividual().clearRnokpp().passport("АА123456")))
                .isEmpty();
    }

    @Test
    void soleTrader_withAllRequiredFields_hasNoViolations() {
        assertThat(violationsOf(validSoleTrader())).isEmpty();
    }

    @Test
    void soleTrader_withEdrpouInsteadOfRnokpp_hasNoViolations() {
        assertThat(violationsOf(validSoleTrader().clearRnokpp().edrpou(VALID_EDRPOU)))
                .isEmpty();
    }

    @Test
    void soleTrader_withPassportInsteadOfRnokppOrEdrpou_hasNoViolations() {
        assertThat(violationsOf(validSoleTrader().clearRnokpp().passport("АА123456")))
                .isEmpty();
    }

    @Test
    void company_withAllRequiredFields_hasNoViolations() {
        assertThat(violationsOf(validCompany())).isEmpty();
    }

    // ─── Фізособа: кожне обов'язкове поле окремо ─────────────────────────────────────────

    @Test
    void individual_withoutName_failsIsValidNaming() {
        assertSingleViolationContains(
                validIndividual().clearLastName().clearFirstName(), "'lastName' and 'firstName' are required");
    }

    @Test
    void individual_withoutRnokppAndPassport_failsIsValidTaxIdentifier() {
        assertSingleViolationContains(validIndividual().clearRnokpp(), "'rnokpp' (or 'passport') is required");
    }

    @Test
    void individual_withoutBirthDate_failsIsValidBirthDate() {
        assertSingleViolationContains(
                validIndividual().clearBirthDate(), "Birth date is required for INDIVIDUAL and SOLE_TRADER");
    }

    @Test
    void individual_withoutAddress_failsIsValidAddress() {
        assertSingleViolationContains(validIndividual().clearAddress(), "Address is required for all client types");
    }

    @Test
    void individual_withoutEmailOrPhone_failsIsValidContacts() {
        assertSingleViolationContains(
                validIndividual().clearEmail().clearPhoneNumber(),
                "At least one contact method (email or phone number) is required");
    }

    @Test
    void individual_withActivities_failsIsValidActivities() {
        assertSingleViolationContains(
                validIndividual().activities(List.of("62.01 Комп'ютерне програмування")),
                "must be empty for INDIVIDUAL or COMPANY");
    }

    // ─── ФОП: специфічні для типу правила ────────────────────────────────────────────────

    @Test
    void soleTrader_withoutAnyTaxIdentifier_failsIsValidTaxIdentifier() {
        assertSingleViolationContains(
                validSoleTrader().clearRnokpp(), "'rnokpp' or 'edrpou' (or 'passport') is required");
    }

    @Test
    void soleTrader_withoutBirthDate_failsIsValidBirthDate() {
        assertSingleViolationContains(
                validSoleTrader().clearBirthDate(), "Birth date is required for INDIVIDUAL and SOLE_TRADER");
    }

    @Test
    void soleTrader_withoutActivities_failsIsValidActivities() {
        assertSingleViolationContains(
                validSoleTrader().clearActivities(), "Activities list is required for SOLE_TRADER");
    }

    @Test
    void soleTrader_withEmptyActivitiesList_failsIsValidActivities() {
        assertSingleViolationContains(
                validSoleTrader().activities(List.of()), "Activities list is required for SOLE_TRADER");
    }

    // ─── Юрособа: специфічні для типу правила ────────────────────────────────────────────

    @Test
    void company_withoutCompanyName_failsIsValidNaming() {
        assertSingleViolationContains(validCompany().clearCompanyName(), "'companyName' is required");
    }

    @Test
    void company_withoutEdrpou_failsIsValidTaxIdentifier() {
        assertSingleViolationContains(validCompany().clearEdrpou(), "For COMPANY, 'edrpou' is required");
    }

    @Test
    void company_withoutDirectorName_failsIsValidCompanyDetails() {
        assertSingleViolationContains(
                validCompany().clearDirectorName(), "Director name and contact person name are required");
    }

    @Test
    void company_withoutContactPersonName_failsIsValidCompanyDetails() {
        assertSingleViolationContains(
                validCompany().clearContactPersonName(), "Director name and contact person name are required");
    }

    @Test
    void company_withoutAddress_failsIsValidAddress() {
        assertSingleViolationContains(validCompany().clearAddress(), "Address is required for all client types");
    }

    @Test
    void company_withoutEmailOrPhone_failsIsValidContacts() {
        assertSingleViolationContains(
                validCompany().clearEmail(), "At least one contact method (email or phone number) is required");
    }

    @Test
    void company_withActivities_failsIsValidActivities() {
        assertSingleViolationContains(
                validCompany().activities(List.of("62.01 Комп'ютерне програмування")),
                "must be empty for INDIVIDUAL or COMPANY");
    }

    // ─── Кілька одночасних порушень складаються, а не гасять одне одного ────────────────

    @Test
    void company_withoutMultipleRequiredFields_reportsAllOfThem() {
        Set<ConstraintViolation<ClientCreateRequest>> violations =
                violationsOf(validCompany().clearEdrpou().clearAddress().clearDirectorName());

        assertThat(violations).hasSize(3);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .anyMatch(m -> m.contains("'edrpou' is required"))
                .anyMatch(m -> m.contains("Address is required"))
                .anyMatch(m -> m.contains("Director name and contact person name are required"));
    }

    /**
     * Мутабельний "wither" навколо {@link ClientCreateRequest} - сам record незмінний і його
     * 16-аргументний конструктор незручно повторювати в кожному тесті, тому тут одна точка
     * побудови валідного запиту з подальшими точковими "очищеннями" окремих полів.
     */
    private static final class Req {
        private ClientType type;
        private JsonNullable<String> lastName = JsonNullable.undefined();
        private JsonNullable<String> firstName = JsonNullable.undefined();
        private JsonNullable<String> middleName = JsonNullable.undefined();
        private JsonNullable<LocalDate> birthDate = JsonNullable.undefined();
        private JsonNullable<String> rnokpp = JsonNullable.undefined();
        private JsonNullable<String> passport = JsonNullable.undefined();
        private JsonNullable<String> companyName = JsonNullable.undefined();
        private JsonNullable<String> edrpou = JsonNullable.undefined();
        private JsonNullable<String> directorName = JsonNullable.undefined();
        private JsonNullable<String> contactPersonName = JsonNullable.undefined();
        private JsonNullable<String> email = JsonNullable.undefined();
        private JsonNullable<String> phoneNumber = JsonNullable.undefined();
        private JsonNullable<String> address = JsonNullable.undefined();
        private JsonNullable<String> notes = JsonNullable.undefined();
        private JsonNullable<List<String>> activities = JsonNullable.undefined();

        Req type(ClientType v) {
            this.type = v;
            return this;
        }

        Req lastName(String v) {
            this.lastName = JsonNullable.of(v);
            return this;
        }

        Req firstName(String v) {
            this.firstName = JsonNullable.of(v);
            return this;
        }

        Req birthDate(LocalDate v) {
            this.birthDate = JsonNullable.of(v);
            return this;
        }

        Req rnokpp(String v) {
            this.rnokpp = JsonNullable.of(v);
            return this;
        }

        Req passport(String v) {
            this.passport = JsonNullable.of(v);
            return this;
        }

        Req companyName(String v) {
            this.companyName = JsonNullable.of(v);
            return this;
        }

        Req edrpou(String v) {
            this.edrpou = JsonNullable.of(v);
            return this;
        }

        Req directorName(String v) {
            this.directorName = JsonNullable.of(v);
            return this;
        }

        Req contactPersonName(String v) {
            this.contactPersonName = JsonNullable.of(v);
            return this;
        }

        Req email(String v) {
            this.email = JsonNullable.of(v);
            return this;
        }

        Req phoneNumber(String v) {
            this.phoneNumber = JsonNullable.of(v);
            return this;
        }

        Req address(String v) {
            this.address = JsonNullable.of(v);
            return this;
        }

        Req activities(List<String> v) {
            this.activities = JsonNullable.of(v);
            return this;
        }

        Req clearLastName() {
            this.lastName = JsonNullable.undefined();
            return this;
        }

        Req clearFirstName() {
            this.firstName = JsonNullable.undefined();
            return this;
        }

        Req clearBirthDate() {
            this.birthDate = JsonNullable.undefined();
            return this;
        }

        Req clearRnokpp() {
            this.rnokpp = JsonNullable.undefined();
            return this;
        }

        Req clearEdrpou() {
            this.edrpou = JsonNullable.undefined();
            return this;
        }

        Req clearCompanyName() {
            this.companyName = JsonNullable.undefined();
            return this;
        }

        Req clearDirectorName() {
            this.directorName = JsonNullable.undefined();
            return this;
        }

        Req clearContactPersonName() {
            this.contactPersonName = JsonNullable.undefined();
            return this;
        }

        Req clearAddress() {
            this.address = JsonNullable.undefined();
            return this;
        }

        Req clearEmail() {
            this.email = JsonNullable.undefined();
            return this;
        }

        Req clearPhoneNumber() {
            this.phoneNumber = JsonNullable.undefined();
            return this;
        }

        Req clearActivities() {
            this.activities = JsonNullable.undefined();
            return this;
        }

        ClientCreateRequest build() {
            return new ClientCreateRequest(
                    type,
                    lastName,
                    firstName,
                    middleName,
                    birthDate,
                    rnokpp,
                    passport,
                    companyName,
                    edrpou,
                    directorName,
                    contactPersonName,
                    email,
                    phoneNumber,
                    address,
                    notes,
                    activities);
        }
    }
}
