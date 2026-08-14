package com.sentio.user_service.auth;

import com.sentio.user_service.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

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
}
