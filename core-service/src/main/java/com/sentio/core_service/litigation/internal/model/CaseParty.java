package com.sentio.core_service.litigation.internal.model;

import com.sentio.core_service.litigation.internal.enums.CasePartyRole;

import com.sentio.core_service.common.model.CoreEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
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
public class CaseParty extends CoreEntity {

    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, referencedColumnName = "id")
    private Case case_;

    // фізичний FK у межах core-service: core.clients(id). Client belongs to the client module -
    // referenced by id only; resolve it through client's ClientService.
    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "opponent_name", columnDefinition = "TEXT")
    private String opponentName;

    @Column(name = "opponent_contact", columnDefinition = "TEXT")
    private String opponentContact;

    @Column(name = "opponent_details", columnDefinition = "TEXT")
    private String opponentDetails;

    @Column(name = "role", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CasePartyRole role;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;
}
