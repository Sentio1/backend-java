package com.sentio.user_service.organization;

import tools.jackson.databind.ObjectMapper;
import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.auth.dto.RegistrationRequest;
import com.sentio.user_service.auth.rate_limiting.RateLimitingService;
import com.sentio.user_service.organization.dto.organization_invite.OrganizationInviteRequest;
import com.sentio.user_service.organization.enums.OrgRole;
import com.sentio.user_service.organization.repository.OrganizationMemberRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of SEN-16's invite mechanism: an OWNER creates an invite,
 * the invitee (already registered, but with no org of their own - see
 * AuthService.register's org-less path) redeems it and becomes a member.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrganizationInviteFlowIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @MockitoBean
    private RateLimitingService rateLimitingService;

    // See OrganizationIsolationIT - CookieCsrfTokenRepository double-submit pattern,
    // every response carries a fresh XSRF-TOKEN cookie that state-changing requests
    // must echo back as the X-XSRF-TOKEN header.
    private record Session(String accessToken, String xsrfToken) {}

    private Session register(String email, String orgName) throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                email, "Password123!", "Password123!", null,
                "Doe", "John", null,
                orgName, null, null
        );
        MockHttpServletResponse response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse();
        return new Session(
                response.getCookie("access_token").getValue(),
                response.getCookie("XSRF-TOKEN").getValue()
        );
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder, Session session) {
        return builder
                .cookie(new Cookie("access_token", session.accessToken()), new Cookie("XSRF-TOKEN", session.xsrfToken()))
                .header("X-XSRF-TOKEN", session.xsrfToken());
    }

    private long orgIdOwnedBy(String email) {
        return organizationMemberRepository.findAll().stream()
                .filter(m -> m.getUser().getEmail().equals(email))
                .findFirst().orElseThrow()
                .getOrganization().getId();
    }

    @Test
    void ownerInvites_inviteeAccepts_becomesDefaultMemberWithInvitedRole() throws Exception {
        Session ownerSession = register("owner@sentio.dev", "Acme Legal");
        long orgId = orgIdOwnedBy("owner@sentio.dev");

        // Invitee registers with no org name yet - the register()-without-a-name path -
        // so they have an authenticated session but no membership until they accept.
        Session inviteeSession = register("invitee@sentio.dev", null);

        MvcResult inviteResult = mockMvc.perform(authenticated(post("/organizations/" + orgId + "/invites"), ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrganizationInviteRequest("invitee@sentio.dev", OrgRole.LAWYER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(orgId))
                .andExpect(jsonPath("$.email").value("invitee@sentio.dev"))
                .andExpect(jsonPath("$.role").value("LAWYER"))
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        Map<String, Object> inviteBody = objectMapper.readValue(inviteResult.getResponse().getContentAsString(), Map.class);
        String rawToken = (String) inviteBody.get("token");

        mockMvc.perform(authenticated(post("/users/me/invites/" + rawToken + "/accept"), inviteeSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orgId").value(orgId))
                .andExpect(jsonPath("$.role").value("LAWYER"))
                .andExpect(jsonPath("$.isDefault").value(true));

        // Prove it's a real membership, not just a response body: the invitee can
        // now reach an org-scoped endpoint that requireMembership gates.
        mockMvc.perform(authenticated(get("/organizations/" + orgId + "/members"), inviteeSession))
                .andExpect(status().isOk());
    }

    @Test
    void nonMemberCannotCreateInvites() throws Exception {
        register("owner2@sentio.dev", "Acme Legal 2");
        long orgId = orgIdOwnedBy("owner2@sentio.dev");

        // Registered, but never invited/accepted into orgId - not a member of
        // anything. inviteUserToOrganization requires an existing membership
        // scoped to orgId, same as every other org-mutating endpoint.
        Session outsiderSession = register("outsider@sentio.dev", null);

        mockMvc.perform(authenticated(post("/organizations/" + orgId + "/invites"), outsiderSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrganizationInviteRequest("nobody@sentio.dev", OrgRole.ASSISTANT))))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateActiveInviteForSameEmail_isRejected() throws Exception {
        Session ownerSession = register("owner3@sentio.dev", "Acme Legal 3");
        long orgId = orgIdOwnedBy("owner3@sentio.dev");

        mockMvc.perform(authenticated(post("/organizations/" + orgId + "/invites"), ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrganizationInviteRequest("dup@sentio.dev", OrgRole.LAWYER))))
                .andExpect(status().isOk());

        mockMvc.perform(authenticated(post("/organizations/" + orgId + "/invites"), ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrganizationInviteRequest("dup@sentio.dev", OrgRole.ASSISTANT))))
                .andExpect(status().isConflict());
    }
}
