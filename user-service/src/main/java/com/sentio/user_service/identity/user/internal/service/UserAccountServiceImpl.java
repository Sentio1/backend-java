package com.sentio.user_service.identity.user.internal.service;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.user.api.dto.NewExternalUser;
import com.sentio.user_service.identity.user.api.dto.NewLocalUser;
import com.sentio.user_service.identity.user.api.dto.UserContextResponse;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import com.sentio.user_service.identity.user.api.service.UserService;
import com.sentio.user_service.identity.user.internal.entity.User;
import com.sentio.user_service.identity.user.internal.entity.UserIdentity;
import com.sentio.user_service.identity.user.internal.exception.UserNotFoundException;
import com.sentio.user_service.identity.user.internal.mapper.UserMapper;
import com.sentio.user_service.identity.user.internal.repository.UserIdentityRepository;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

import static com.sentio.shared.persistence.ConstraintViolations.isUniqueConstraintViolation;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService, UserService {

    // Real index/constraint names (V6__make_users_email_unique.sql and the column-level
    // UNIQUE in V3__auth_schema.sql) - anything else never matches and the raw 500 leaks.
    private static final String EMAIL_UNIQUE_INDEX = "users_email_active_idx";
    private static final String PHONE_UNIQUE_CONSTRAINT = "users_phone_number_key";

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserDto findUserById(long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toDto)
                .orElseThrow(() -> new UserNotFoundException("id", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public UserContextResponse buildUserContext(long userId, @Nullable OrganizationMemberDto membership) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));
        return userMapper.toUserContextResponse(user, membership);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findActiveById(long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .map(userMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findActiveByEmail(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> !user.isDeleted())
                .map(userMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findByExternalIdentity(AuthProvider provider, String providerUserId) {
        return userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserIdentity::getUser)
                .map(userMapper::toDto);
    }

    @Override
    @Transactional
    public UserDto registerLocal(NewLocalUser newUser) {
        if (userRepository.existsByEmail(newUser.email())) {
            log.warn("Registration failed: user with email {} already exists", newUser.email());
            throw emailTaken(newUser.email());
        }
        // existsByPhoneNumber(null) would compare IS NULL and report a conflict as soon as
        // any other user has no phone - only check when a phone was actually given.
        if (StringUtils.hasText(newUser.phoneNumber()) && userRepository.existsByPhoneNumber(newUser.phoneNumber())) {
            log.warn("Registration failed: user with phone number {} already exists", newUser.phoneNumber());
            throw phoneTaken(newUser.phoneNumber());
        }

        User user = User.builder()
                .email(newUser.email())
                .password(newUser.passwordHash())
                .firstName(newUser.firstName())
                .lastName(newUser.lastName())
                .phoneNumber(StringUtils.hasText(newUser.phoneNumber()) ? newUser.phoneNumber() : null)
                .middleName(StringUtils.hasText(newUser.middleName()) ? newUser.middleName() : null)
                .build();

        try {
            User saved = userRepository.saveAndFlush(user);
            // Second step: the LOCAL identity is keyed by the id the insert just assigned.
            saved.getIdentities().add(userMapper.toLocalIdentity(saved));
            saved = userRepository.saveAndFlush(saved);
            log.info("Successfully registered user with email: {}", newUser.email());
            return userMapper.toDto(saved);
        } catch (DataIntegrityViolationException e) {
            // Race-safety net for the pre-checks above: a concurrent registration can
            // still win between the exists* query and this insert.
            if (isUniqueConstraintViolation(e, EMAIL_UNIQUE_INDEX)) {
                throw emailTaken(newUser.email());
            }
            if (isUniqueConstraintViolation(e, PHONE_UNIQUE_CONSTRAINT)) {
                throw phoneTaken(newUser.phoneNumber());
            }
            log.error("DataIntegrityViolationException during registration for email: {}", newUser.email(), e);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserDto createFromExternalIdentity(NewExternalUser newUser) {
        User user = User.builder()
                .email(newUser.email())
                .firstName(newUser.firstName())
                .lastName(newUser.lastName())
                .emailVerifiedAt(Instant.now())
                .build();

        user.getIdentities().add(identity(user, newUser.provider(), newUser.providerUserId()));

        User saved = userRepository.saveAndFlush(user);
        log.info("Created user {} from {} identity", saved.getId(), newUser.provider());
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void linkExternalIdentity(long userId, AuthProvider provider, String providerUserId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

        user.getIdentities().add(identity(user, provider, providerUserId));
        userRepository.saveAndFlush(user);
        log.info("Linked {} identity to user id: {}", provider, userId);
    }

    @Override
    @Transactional
    public void updatePasswordHash(long userId, String passwordHash) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("id", userId));

        user.setPassword(passwordHash);
    }

    private static UserIdentity identity(User user, AuthProvider provider, String providerUserId) {
        return UserIdentity.builder()
                .user(user)
                .provider(provider)
                .providerUserId(providerUserId)
                .build();
    }

    private static ResourceAlreadyExistsException emailTaken(String email) {
        return new ResourceAlreadyExistsException("User with email: " + email + " already exists");
    }

    private static ResourceAlreadyExistsException phoneTaken(String phoneNumber) {
        return new ResourceAlreadyExistsException("User with phone number: " + phoneNumber + " already exists");
    }
}
