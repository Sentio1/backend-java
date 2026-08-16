package com.sentio.user_service.auth;

import com.sentio.user_service.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The JWKS endpoint (com.lisovskyi.security.autoconfigure.security.jwt.JwksController, from the
 * security starter) - this is what SEN-33 / other services rely on to validate our RS256 tokens
 * without a shared secret. No cookies/auth here: it must be reachable anonymously.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JwksControllerIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void jwks_isPubliclyReachableAndContainsOneRsaKey() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists())
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].e").exists())
                // A public JWK must never carry private-key material.
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }

    // app.jwt.previous-private-key is set in src/test/resources/application.yaml
    // specifically so this rotation contract - both the current and previous key's
    // public half staying published at once, so in-flight tokens signed with the
    // old key keep validating during a rotation - has real test coverage instead of
    // being documented but silently unverified.
    @Test
    void jwks_duringKeyRotation_publishesBothCurrentAndPreviousKeyAsDistinctValidRsaJwks() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");

        assertThat(keys).hasSize(2);

        for (Map<String, Object> key : keys) {
            assertThat(key.get("kty")).isEqualTo("RSA");
            assertThat(key.get("kid")).isNotNull();
            assertThat(key.get("n")).isNotNull();
            assertThat(key.get("e")).isNotNull();
            // A public JWK must never carry private-key material.
            assertThat(key).doesNotContainKey("d");
        }

        assertThat(keys.get(0).get("kid")).isNotEqualTo(keys.get(1).get("kid"));
    }
}
