package com.lisovskyi.core_service.case_party.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.case_.enums.CaseInstance;
import com.lisovskyi.core_service.case_.enums.ProcedureType;
import com.lisovskyi.core_service.case_party.CaseParty;
import com.lisovskyi.core_service.case_party.CasePartyRole;
import com.lisovskyi.core_service.case_party.dto.request.CasePartyCreateRequest;
import com.lisovskyi.core_service.case_party.dto.request.CasePartyUpdateRequest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;

// toResponse навмисно не тестується тут - CasePartyMapperImpl.clientMapper - @Autowired-поле,
// не піднімається без Spring-контексту.
class CasePartyMapperTest {

    private final CasePartyMapper casePartyMapper = new CasePartyMapperImpl();

    private Case caseWithOwnAuditFields() {
        // id/createdAt/deletedAt/deletedBy виставлені явно, щоб довести регресію нижче: до
        // фіксу MapStruct бачив другий параметр toEntity (Case case_) як ще одне ДЖЕРЕЛО для
        // мапінгу (не просто значення для поля case_) і за збігом імен властивостей (обидві
        // сутності успадковують CoreEntity) тихцем копіював їх зі СПРАВИ в нову CaseParty.
        return Case.builder()
                .id(999L)
                .organizationId(100L)
                .responsibleUserId(1L)
                .title("Позов")
                .procedure(ProcedureType.CIVIL)
                .instance(CaseInstance.FIRST)
                .deletedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .deletedBy(42L)
                .build();
    }

    // ─── toEntity (create) ────────────────────────────────────────────

    @Test
    void toEntity_doesNotLeakCaseAuditFieldsIntoNewCaseParty_regressionForPropertyNameCollision() {
        Case case_ = caseWithOwnAuditFields();
        CasePartyCreateRequest request = new CasePartyCreateRequest(
                CasePartyRole.PLAINTIFF,
                JsonNullable.undefined(),
                true,
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        CaseParty caseParty = casePartyMapper.toEntity(request, case_);

        assertThat(caseParty.getId()).isNull();
        assertThat(caseParty.getCreatedAt()).isNull();
        assertThat(caseParty.getUpdatedAt()).isNull();
        assertThat(caseParty.getDeletedAt()).isNull();
        assertThat(caseParty.getDeletedBy()).isNull();
        assertThat(caseParty.getRestoredAt()).isNull();
        assertThat(caseParty.getRestoredBy()).isNull();
    }

    @Test
    void toEntity_stillCopiesOrganizationIdFromCase_andLinksTheCaseAssociation() {
        // organizationId - єдине поле, яке МАЄ прийти з case_ (сторона завжди в організації
        // своєї справи) - явний @Mapping, а не побічний ефект бага з попереднього тесту.
        Case case_ = caseWithOwnAuditFields();
        CasePartyCreateRequest request = new CasePartyCreateRequest(
                CasePartyRole.DEFENDANT,
                JsonNullable.undefined(),
                false,
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        CaseParty caseParty = casePartyMapper.toEntity(request, case_);

        assertThat(caseParty.getOrganizationId()).isEqualTo(100L);
        assertThat(caseParty.getCase_()).isSameAs(case_);
        assertThat(caseParty.getRole()).isEqualTo(CasePartyRole.DEFENDANT);
        assertThat(caseParty.isPrimary()).isFalse();
    }

    // ─── updateEntityFromRequest (PATCH semantics) ──────────────────────

    private CaseParty existingPlaintiffParty() {
        return CaseParty.builder()
                .organizationId(100L)
                .role(CasePartyRole.PLAINTIFF)
                .isPrimary(false)
                .build();
    }

    private CasePartyUpdateRequest allUndefinedRequest() {
        return new CasePartyUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());
    }

    @Test
    void updateEntityFromRequest_withAllFieldsAbsent_doesNotTouchExistingValues() {
        CaseParty caseParty = existingPlaintiffParty();

        casePartyMapper.updateEntityFromRequest(allUndefinedRequest(), caseParty);

        assertThat(caseParty.getRole()).isEqualTo(CasePartyRole.PLAINTIFF);
        assertThat(caseParty.isPrimary()).isFalse();
    }

    @Test
    void updateEntityFromRequest_withIsPrimaryPresent_updatesFlag_regressionForIsXxxRecordMismatch() {
        // Регресія: request.isPrimary() тут JsonNullable<Boolean> (не примітивний boolean, як
        // на CasePartyCreateRequest) - MapStruct не зрізає "is"-префікс для не-boolean типу
        // повернення, тож без явного мапінгу властивість "primary" лишалась немапленою і
        // isPrimary ніколи не змінювався через PATCH.
        CaseParty caseParty = existingPlaintiffParty();
        CasePartyUpdateRequest request = new CasePartyUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(true),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        casePartyMapper.updateEntityFromRequest(request, caseParty);

        assertThat(caseParty.isPrimary()).isTrue();
    }

    @Test
    void updateEntityFromRequest_withRolePresent_replacesExistingValue() {
        CaseParty caseParty = existingPlaintiffParty();
        CasePartyUpdateRequest request = new CasePartyUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.of(CasePartyRole.DEFENDANT),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        casePartyMapper.updateEntityFromRequest(request, caseParty);

        assertThat(caseParty.getRole()).isEqualTo(CasePartyRole.DEFENDANT);
    }

    @Test
    void updateEntityFromRequest_withOpponentNamePresentAndNull_clearsExistingValue() {
        CaseParty caseParty = existingPlaintiffParty();
        caseParty.setOpponentName("Старий опонент");
        CasePartyUpdateRequest request = new CasePartyUpdateRequest(
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.undefined(),
                JsonNullable.of(null),
                JsonNullable.undefined(),
                JsonNullable.undefined());

        casePartyMapper.updateEntityFromRequest(request, caseParty);

        assertThat(caseParty.getOpponentName()).isNull();
    }
}
