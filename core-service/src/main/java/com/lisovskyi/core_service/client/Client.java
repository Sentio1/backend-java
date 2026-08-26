package com.lisovskyi.core_service.client;

import static com.lisovskyi.core_service.client.ClientConstants.COMPANY_NAME_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.DELETE_REASON_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.DIRECTOR_NAME_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.EDRPOU_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.NAME_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.PASSPORT_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.PHONE_NUMBER_LENGTH;
import static com.lisovskyi.core_service.client.ClientConstants.RNOKPP_LENGTH;

import com.lisovskyi.jpa.autoconfigure.entity.TimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "clients")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
@SQLRestriction("deleted_at IS NULL")
public class Client extends TimestampedEntity {

    // soft-ref auth.organizations.id — user-service й core-service мають
    // окремі БД, тому це plain bigint, не @ManyToOne на чужий entity.
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Builder.Default
    private ClientType type = ClientType.INDIVIDUAL;

    // фізична особа / ФОП
    @Column(name = "last_name", length = NAME_LENGTH)
    private String lastName;

    @Column(name = "first_name", length = NAME_LENGTH)
    private String firstName;

    @Column(name = "middle_name", length = NAME_LENGTH)
    private String middleName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "rnokpp", length = RNOKPP_LENGTH)
    private String rnokpp;

    @Column(name = "passport", length = PASSPORT_LENGTH)
    private String passport;

    // юридична особа
    @Column(name = "company_name", length = COMPANY_NAME_LENGTH)
    private String companyName;

    @Column(name = "edrpou", length = EDRPOU_LENGTH)
    private String edrpou;

    @Column(name = "director_name", length = DIRECTOR_NAME_LENGTH)
    private String directorName;

    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "phone_number", length = PHONE_NUMBER_LENGTH)
    private String phoneNumber;

    @Column(name = "address", columnDefinition = "text")
    private String address;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    // soft-ref auth.users.id — так само, без @ManyToOne
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // soft-ref auth.users.id
    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "delete_reason", length = DELETE_REASON_LENGTH)
    private String deleteReason;
}
