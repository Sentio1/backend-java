package com.sentio.user_service.organization;

import com.sentio.user_service.organization.entity.Organization;
import com.sentio.user_service.organization.entity.OrganizationMember;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.security.SecurityUser;
import com.sentio.user_service.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationSecurityTest {

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks
    private OrganizationSecurity organizationSecurity;

    private User user(long id) {
        User u = User.builder().email("user@sentio.dev").build();
        u.setId(id);
        return u;
    }

    private OrganizationMember membership(User user, OrgRole role) {
        Organization org = Organization.builder().name("Acme Legal").slug("acme").build();
        org.setId(10L);
        return OrganizationMember.builder().user(user).organization(org).role(role).isDefault(true).build();
    }

    @Test
    void ownerOfTheOrg_canManage() {
        User user = user(1L);
        Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
        when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L))
                .thenReturn(Optional.of(membership(user, OrgRole.OWNER)));

        assertThat(organizationSecurity.canManage(10L, auth)).isTrue();
    }

    @Test
    void nonOwnerMemberOfTheOrg_cannotManage() {
        User user = user(1L);
        Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
        when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L))
                .thenReturn(Optional.of(membership(user, OrgRole.LAWYER)));

        assertThat(organizationSecurity.canManage(10L, auth)).isFalse();
    }

    @Test
    void notAMemberOfTheOrgAtAll_cannotManage() {
        User user = user(1L);
        Authentication auth = new TestingAuthenticationToken(new SecurityUser(user), null);
        when(organizationMemberRepository.findByUserIdAndOrganizationId(1L, 10L)).thenReturn(Optional.empty());

        assertThat(organizationSecurity.canManage(10L, auth)).isFalse();
    }

    @Test
    void nonSecurityUserPrincipal_cannotManage() {
        Authentication auth = new TestingAuthenticationToken("not-a-security-user", null);

        assertThat(organizationSecurity.canManage(10L, auth)).isFalse();
    }
}
