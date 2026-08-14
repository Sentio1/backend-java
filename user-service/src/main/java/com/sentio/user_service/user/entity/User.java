package com.sentio.user_service.user.entity;

import com.lisovskyi.jpa.autoconfigure.entity.TimestampedEntity;
import com.lisovskyi.jpa.autoconfigure.generator.SequenceSize;
import com.sentio.user_service.user.enums.PlatformRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.sentio.user_service.user.UserConstants.*;

@Entity
@Table(name = "users")
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor
@SuperBuilder
@SequenceSize(size = 50)
public class User extends TimestampedEntity {

    @Column(name = "email", columnDefinition = "citext", unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", length = PASSWORD_HASH_LENGTH)
    private String password;

    @Column(name = "phone_number", unique = true, length = PHONE_NUMBER_LENGTH)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
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
    private final List<UserIdentity> identities = new ArrayList<>();
}
