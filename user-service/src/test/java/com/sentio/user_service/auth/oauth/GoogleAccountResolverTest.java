package com.sentio.user_service.auth.oauth;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
import com.sentio.user_service.user.repository.UserIdentityRepository;
import com.sentio.user_service.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleAccountResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserIdentityRepository userIdentityRepository;

    @InjectMocks
    private GoogleAccountResolver googleAccountResolver;

    private GoogleIdentity identity(boolean emailVerified) {
        return new GoogleIdentity("google-sub-1", "user@sentio.dev", "Jane", "Doe", emailVerified);
    }

    @Test
    void knownGoogleIdentity_returnsItsUserWithoutTouchingUserRepository() {
        User user = User.builder().email("user@sentio.dev").build();
        user.setId(1L);
        UserIdentity existing = UserIdentity.builder().user(user).provider(AuthProvider.GOOGLE).providerUserId("google-sub-1").build();
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(existing));

        User resolved = googleAccountResolver.resolveOrCreate(identity(true));

        assertThat(resolved).isEqualTo(user);
        verify(userRepository, never()).findByEmail(any());
        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void verifiedEmailMatchingLocalAccount_linksGoogleIdentityToIt() {
        User existingLocalUser = User.builder().email("user@sentio.dev").password("bcrypt-hash").build();
        existingLocalUser.setId(1L);
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.of(existingLocalUser));

        User resolved = googleAccountResolver.resolveOrCreate(identity(true));

        assertThat(resolved).isEqualTo(existingLocalUser);
        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(existingLocalUser);
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("google-sub-1");
        verify(userRepository, never()).save(any());
    }

    @Test
    void unverifiedEmailMatchingLocalAccount_refusesToLinkAndThrowsUnauthorized() {
        User existingLocalUser = User.builder().email("user@sentio.dev").build();
        existingLocalUser.setId(1L);
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.of(existingLocalUser));

        assertThatThrownBy(() -> googleAccountResolver.resolveOrCreate(identity(false)))
                .isInstanceOf(UnauthorizedException.class);

        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void noMatchAtAll_createsNewUserAndLinksIdentity() {
        when(userIdentityRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });

        User resolved = googleAccountResolver.resolveOrCreate(identity(true));

        assertThat(resolved.getId()).isEqualTo(5L);
        assertThat(resolved.getEmail()).isEqualTo("user@sentio.dev");
        assertThat(resolved.getFirstName()).isEqualTo("Jane");
        assertThat(resolved.getLastName()).isEqualTo("Doe");
        assertThat(resolved.getPassword()).isNull();

        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(resolved);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("google-sub-1");
    }
}
