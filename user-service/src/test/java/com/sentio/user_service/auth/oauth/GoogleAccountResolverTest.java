package com.sentio.user_service.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lisovskyi.web.error.autoconfigure.standard.UnauthorizedException;
import com.sentio.user_service.auth.AuthGuards;
import com.sentio.user_service.auth.oauth.dto.GoogleIdentity;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.entity.UserIdentity;
import com.sentio.user_service.user.enums.AuthProvider;
import com.sentio.user_service.user.repository.UserIdentityRepository;
import com.sentio.user_service.user.repository.UserRepository;
import com.sentio.user_service.user.service.finder.UserIdentityFinder;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GoogleAccountResolverTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @Mock
    private UserIdentityFinder userIdentityFinder;

    @Mock
    private AuthGuards authGuards;

    // The actual insert + flush now lives in GoogleNewUserCreator's own
    // REQUIRES_NEW transaction (see its javadoc) - this test only needs to verify
    // GoogleAccountResolver delegates to it and handles the two outcomes.
    @Mock
    private GoogleNewUserCreator newUserCreator;

    @InjectMocks
    private GoogleAccountResolver googleAccountResolver;

    private GoogleIdentity identity(boolean emailVerified) {
        return new GoogleIdentity("google-sub-1", "user@sentio.dev", "Jane", "Doe", emailVerified);
    }

    @Test
    void knownGoogleIdentity_returnsItsUserWithoutTouchingUserRepository() {
        User user = User.builder().email("user@sentio.dev").build();
        user.setId(1L);
        UserIdentity existing = UserIdentity.builder()
                .user(user)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub-1")
                .build();
        when(userIdentityFinder.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(existing));

        User resolved = googleAccountResolver.resolveOrCreate(identity(true));

        assertThat(resolved).isEqualTo(user);
        verify(userRepository, never()).findByEmail(any());
        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void verifiedEmailMatchingLocalAccount_linksGoogleIdentityToIt() {
        User existingLocalUser =
                User.builder().email("user@sentio.dev").password("bcrypt-hash").build();
        existingLocalUser.setId(1L);
        when(userIdentityFinder.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
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

    // The check is hoisted above both the "link to existing account" and "create new
    // account" branches, so an unverified Google email is rejected before we ever look
    // up (or create) a User by email - whether or not user@sentio.dev already exists is
    // irrelevant, which is exactly the point: without this, an attacker with an
    // unverified Google identity for someone else's email could register/claim that
    // email first via the new-user path, since only the "existing account" branch used
    // to check verification.
    @Test
    void unverifiedEmail_isRejectedBeforeAnyAccountLookupOrCreation() {
        when(userIdentityFinder.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleAccountResolver.resolveOrCreate(identity(false)))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
        verify(userIdentityRepository, never()).save(any());
    }

    @Test
    void noMatchAtAll_delegatesToNewUserCreator() {
        User created = User.builder()
                .email("user@sentio.dev")
                .firstName("Jane")
                .lastName("Doe")
                .build();
        created.setId(5L);

        when(userIdentityFinder.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.empty());
        when(newUserCreator.createAndLink(identity(true))).thenReturn(created);

        User resolved = googleAccountResolver.resolveOrCreate(identity(true));

        assertThat(resolved).isEqualTo(created);
    }

    // GoogleNewUserCreator runs the insert in its own REQUIRES_NEW transaction
    // specifically so this fallback is reachable at all - see its javadoc for why a
    // plain try/catch around a save() in the caller's own transaction wouldn't work
    // on Postgres (the whole transaction aborts, poisoning the lookup below too).
    @Test
    void conflictOnCreate_fallsBackToTheWinnerResolvedByGoogleIdentity() {
        User winner = User.builder().email("user@sentio.dev").build();
        winner.setId(7L);
        UserIdentity winnerIdentity = UserIdentity.builder()
                .user(winner)
                .provider(AuthProvider.GOOGLE)
                .providerUserId("google-sub-1")
                .build();

        when(userIdentityFinder.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty(), Optional.of(winnerIdentity));
        when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.empty());
        when(newUserCreator.createAndLink(identity(true))).thenThrow(new DataIntegrityViolationException("conflict"));

        User resolved = googleAccountResolver.resolveOrCreate(identity(true));

        assertThat(resolved).isEqualTo(winner);
    }

    @Test
    void conflictOnCreate_fallsBackToTheWinnerResolvedByEmail_whenIdentityLinkAlsoLostTheRace() {
        User winner = User.builder().email("user@sentio.dev").build();
        winner.setId(7L);

        when(userIdentityFinder.findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@sentio.dev")).thenReturn(Optional.empty(), Optional.of(winner));
        when(newUserCreator.createAndLink(identity(true))).thenThrow(new DataIntegrityViolationException("conflict"));

        User resolved = googleAccountResolver.resolveOrCreate(identity(true));

        assertThat(resolved).isEqualTo(winner);
    }
}
