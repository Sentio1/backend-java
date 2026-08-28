package com.lisovskyi.core_service.client;

import com.lisovskyi.core_service.entity.CoreEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

import static com.lisovskyi.core_service.client.ClientConstants.*;

@Entity
@Table(name = "clients")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class Client extends CoreEntity {

    // soft-ref auth.organizations.id
    @Column(name = "organization_id", nullable = false)
    private long organizationId;

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

    // soft-ref auth.users.id
    @Column(name = "created_by", nullable = false)
    private long createdBy;
}
