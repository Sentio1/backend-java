package com.lisovskyi.core_service.case_party;

import com.lisovskyi.core_service.entity.CoreEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

// Сторони справи — заміна колишнього cases.client_id/client_role на множинні сторони
// з роллю. Основний клієнт справи = is_primary = true (біллінг/головний контакт).
@Entity
@Table(name = "case_parties")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
@SQLRestriction("deleted_at IS NULL")
public class CaseParty extends CoreEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @Column(name = "case_id", nullable = false)
    private Long caseId;

    // фізичний FK у межах core-service: core.clients(id)
    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CasePartyRole role;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;
}
