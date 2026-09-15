package com.sentio.user_service.identity.user.internal.entity;

import static com.sentio.user_service.identity.user.internal.UserConstants.*;

import com.lisovskyi.jpa.autoconfigure.entity.TimestampedEntity;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@SQLRestriction("deleted_at IS NULL")
public class User extends TimestampedEntity {

    @Column(name = "email", columnDefinition = "citext", nullable = false)
    private String email;

    @Column(name = "password_hash", length = PASSWORD_HASH_LENGTH)
    private String password;

    @Column(name = "phone_number", unique = true, length = PHONE_NUMBER_LENGTH)
    private String phoneNumber;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "platform_role", nullable = false)
    @Builder.Default
    private PlatformRole platformRole = PlatformRole.USER;

    @Column(name = "last_name", length = NAME_LENGTH)
    private String lastName;

    @Column(name = "first_name", length = NAME_LENGTH)
    private String firstName;

    @Column(name = "middle_name", length = NAME_LENGTH)
    private String middleName;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<UserIdentity> identities = new ArrayList<>();

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
