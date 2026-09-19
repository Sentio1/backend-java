package com.sentio.user_service.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceAlreadyExistsException;
import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.identity.user.api.dto.NewExternalUser;
import com.sentio.user_service.identity.user.api.dto.NewLocalUser;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.AuthProvider;
import com.sentio.user_service.identity.user.internal.entity.User;
import com.sentio.user_service.identity.user.internal.entity.UserIdentity;
import com.sentio.user_service.identity.user.internal.mapper.UserMapper;
import com.sentio.user_service.identity.user.internal.mapper.UserMapperImpl;
import com.sentio.user_service.identity.user.internal.repository.UserIdentityRepository;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import com.sentio.user_service.identity.user.internal.service.UserAccountServiceImpl;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** UserMapper stays real (pure MapStruct) so the returned DTOs reflect what was actually built. */
@ExtendWith(MockitoExtension.class)
class UserAccountServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    private final UserMapper userMapper = new UserMapperImpl();

    private UserAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountServiceImpl(userRepository, userIdentityRepository, userMapper);
    }

    private static User withId(User user, long id) {
        user.setId(id);
        return user;
    }

    @Test
    void registerLocal_savesUserWithLocalIdentityAndHash() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 5L));

        UserDto result = service.registerLocal(
                new NewLocalUser("user@sentio.dev", "bcrypt-hash", " ", "Jane", "Doe", null));

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.emailVerified()).isFalse();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("bcrypt-hash");
        // Blank optional fields are stored as NULL, not "" (phone_number is UNIQUE).
        assertThat(saved.getPhoneNumber()).isNull();
        // Keyed by the (never reused) user id, not the email - see UserMapper.toLocalIdentity.
        UserIdentity local = saved.getIdentities().getFirst();
        assertThat(local.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(local.getProviderUserId()).isEqualTo("5");
    }

    @Test
    void registerLocal_existingEmail_throwsAlreadyExists() {
        when(userRepository.existsByEmail("user@sentio.dev")).thenReturn(true);

        assertThatThrownBy(() -> service.registerLocal(
                new NewLocalUser("user@sentio.dev", "hash", null, "Jane", "Doe", null)))
                .isInstanceOf(ResourceAlreadyExistsException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void createFromExternalIdentity_createsVerifiedPasswordlessUserLinkedToProvider() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> withId(inv.getArgument(0), 5L));

        UserDto created = service.createFromExternalIdentity(
                new NewExternalUser(AuthProvider.GOOGLE, "google-sub-1", "user@sentio.dev", "Jane", "Doe"));

        assertThat(created.id()).isEqualTo(5L);
        assertThat(created.password()).isNull();
        // The provider already verified the email - that's what later allows
        // linking and makes the account a trustworthy owner of this address.
        assertThat(created.emailVerified()).isTrue();

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserIdentity identity = captor.getValue().getIdentities().getFirst();
        assertThat(identity.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(identity.getProviderUserId()).isEqualTo("google-sub-1");
        assertThat(identity.getUser()).isSameAs(captor.getValue());
    }

    @Test
    void findByExternalIdentity_returnsLinkedUserEvenIfSoftDeleted() {
        User deleted = withId(User.builder().email("user@sentio.dev").deletedAt(Instant.now()).build(), 3L);
        UserIdentity identity = UserIdentity.builder()
                .user(deleted).provider(AuthProvider.GOOGLE).providerUserId("sub").build();
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "sub"))
                .thenReturn(Optional.of(identity));

        Optional<UserDto> result = service.findByExternalIdentity(AuthProvider.GOOGLE, "sub");

        assertThat(result).get().extracting(UserDto::deleted).isEqualTo(true);
    }

    @Test
    void findActiveById_filtersOutSoftDeletedUser() {
        User deleted = withId(User.builder().email("user@sentio.dev").deletedAt(Instant.now()).build(), 3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(deleted));

        assertThat(service.findActiveById(3L)).isEmpty();
    }

    // UserService part - organization-free on purpose (see UserServiceImpl's javadoc):
    // an org-less user is a valid state, and the membership is never looked up here.
    @Test
    void findUserById_returnsMappedDto() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(withId(User.builder().email("user@sentio.dev").build(), 1L)));

        assertThat(service.findUserById(1L).email()).isEqualTo("user@sentio.dev");
    }

    @Test
    void findUserById_unknownUser_throwsResourceNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findUserById(999L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
