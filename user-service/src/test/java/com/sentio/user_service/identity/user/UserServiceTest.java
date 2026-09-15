package com.sentio.user_service.identity.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.identity.organization.api.dto.OrganizationMemberDto;
import com.sentio.user_service.identity.organization.api.enums.OrgRole;
import com.sentio.user_service.identity.organization.api.service.OrganizationMemberService;
import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.enums.PlatformRole;
import com.sentio.user_service.identity.user.internal.entity.User;
import com.sentio.user_service.identity.user.internal.mapper.UserMapper;
import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import com.sentio.user_service.identity.user.internal.service.UserServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
/** UserServiceTest class. */
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationMemberService organizationMemberService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User user(long id) {
        User u = User.builder()
                .email("user@sentio.dev")
                .firstName("Jane")
                .lastName("Doe")
                .build();
        u.setId(id);
        return u;
    }

    private OrganizationMemberDto membership() {
        return new OrganizationMemberDto(1L, 10L, "Acme Legal", OrgRole.OWNER);
    }

    @Test
    void existingUserWithDefaultMembership_returnsMappedDto() {
        User user = user(1L);
        OrganizationMemberDto membership = membership();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(organizationMemberService.findDefaultMembership(1L)).thenReturn(Optional.of(membership));

        UserDto expected = new UserDto(1L, "user@sentio.dev", null, PlatformRole.USER);
        when(userMapper.toDto(user, membership)).thenReturn(expected);

        UserDto result = userService.findUserById(1L);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void unknownUser_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findUserById(999L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userWithoutDefaultMembership_returnsDtoWithNullMembershipFields() {
        // An org-less user (registered pending invite acceptance - see
        // AuthService.register's org-less path) is a valid state, not a 404:
        // findUserById must tolerate a missing default membership.
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(organizationMemberService.findDefaultMembership(1L)).thenReturn(Optional.empty());

        UserDto expected = new UserDto(1L, "user@sentio.dev", null, PlatformRole.USER);
        when(userMapper.toDto(user, null)).thenReturn(expected);

        UserDto result = userService.findUserById(1L);

        assertThat(result).isEqualTo(expected);
    }
}
