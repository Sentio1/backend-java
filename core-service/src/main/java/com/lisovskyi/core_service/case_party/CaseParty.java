package com.lisovskyi.core_service.case_party;

import com.lisovskyi.core_service.case_.Case;
import com.lisovskyi.core_service.client.Client;
import com.lisovskyi.core_service.entity.CoreEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
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
@SequenceSize(size = 50)
public class CaseParty extends CoreEntity {

    @Column(name = "organization_id", nullable = false)
    private long organizationId;

    // фізичний FK у межах core-service: core.cases(id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, referencedColumnName = "id")
    private Case case_;

    // фізичний FK у межах core-service: core.clients(id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, referencedColumnName = "id")
    private Client client;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private CasePartyRole role;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;
}
