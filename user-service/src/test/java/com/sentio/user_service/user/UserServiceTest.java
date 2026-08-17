package com.sentio.user_service.user;

import com.lisovskyi.web.error.autoconfigure.standard.ResourceNotFoundException;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.user.dto.UserContextResponse;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.mapper.UserMapper;
import com.sentio.user_service.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private User user(long id) {
        User u = User.builder().email("user@sentio.dev").firstName("Jane").lastName("Doe").build();
        u.setId(id);
        return u;
    }

    private OrganizationMember membership(User user) {
        Organization org = Organization.builder().name("Acme Legal").slug("acme").build();
        org.setId(10L);
        return OrganizationMember.builder().user(user).organization(org).role(OrgRole.OWNER).isDefault(true).build();
    }

    @Test
    void existingUserWithDefaultMembership_returnsMappedContext() {
        User user = user(1L);
        OrganizationMember membership = membership(user);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(organizationMemberRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(membership));

        UserContextResponse expected = UserContextResponse.builder()
                .id(1L).email("user@sentio.dev").orgName("Acme Legal").orgRole("OWNER").build();
        when(userMapper.toResponse(user, membership)).thenReturn(expected);

        UserContextResponse result = userService.findUserById(1L);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void unknownUser_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findUserById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void userWithoutDefaultMembership_returnsContextWithNullOrgFields() {
        // An org-less user (registered pending invite acceptance - see
        // AuthService.register's org-less path) is a valid state, not a 404:
        // findUserById must tolerate a missing default membership.
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(organizationMemberRepository.findByUserIdAndIsDefaultTrue(1L)).thenReturn(Optional.empty());

        UserContextResponse expected = UserContextResponse.builder()
                .id(1L).email("user@sentio.dev").orgName(null).orgRole(null).build();
        when(userMapper.toResponse(user, null)).thenReturn(expected);

        UserContextResponse result = userService.findUserById(1L);

        assertThat(result).isEqualTo(expected);
    }
}
