package com.sentio.user_service.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

@ExtendWith(MockitoExtension.class)
class GoogleNewUserCreatorTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityRepository userIdentityRepository;

    @InjectMocks
    private GoogleNewUserCreator newUserCreator;

    @Test
    void createAndLink_savesUserThenLinksGoogleIdentityToTheSavedInstance() {
        GoogleIdentity identity = new GoogleIdentity("google-sub-1", "user@sentio.dev", "Jane", "Doe", true);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(5L);
            return u;
        });

        User created = newUserCreator.createAndLink(identity);

        assertThat(created.getId()).isEqualTo(5L);
        assertThat(created.getEmail()).isEqualTo("user@sentio.dev");
        assertThat(created.getFirstName()).isEqualTo("Jane");
        assertThat(created.getLastName()).isEqualTo("Doe");
        assertThat(created.getPassword()).isNull();

        ArgumentCaptor<UserIdentity> captor = ArgumentCaptor.forClass(UserIdentity.class);
        verify(userIdentityRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(created);
        assertThat(captor.getValue().getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(captor.getValue().getProviderUserId()).isEqualTo("google-sub-1");
    }
}
