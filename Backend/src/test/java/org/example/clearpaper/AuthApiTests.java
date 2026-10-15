package org.example.clearpaper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestTokens.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthApiTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM app_users");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM app_users");
    }

    @Test
    void bearerIdentityIsStableAndNeverCreatesLocalPasswordsOrSessions() throws Exception {
        String token = TestTokens.token("teacher@example.com"),
                id = TestTokens.id("teacher@example.com");
        var result =
                mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.id").value(id))
                        .andExpect(jsonPath("$.role").value("TEACHER"))
                        .andExpect(jsonPath("$.password").doesNotExist())
                        .andExpect(header().doesNotExist("Set-Cookie"))
                        .andReturn();
        assertNull(result.getRequest().getSession(false));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME)='APP_USERS' AND UPPER(COLUMN_NAME)='PASSWORD_HASH'",
                        Integer.class));
        mvc.perform(get("/api/teachers/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/api/teachers/" + TestTokens.id("other@example.com"))
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidIssuerAudienceRoleExpirySubjectAndSignatureAreRejected() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/library/resources").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        for (var bad :
                java.util.List.of(
                        Map.<String, Object>of("iss", "https://wrong.example/auth/v1"),
                        Map.<String, Object>of("aud", java.util.List.of("other")),
                        Map.<String, Object>of("role", "service_role"),
                        Map.<String, Object>of(
                                "exp", Instant.now().minusSeconds(120).getEpochSecond()),
                        Map.<String, Object>of("sub", "not-a-user"),
                        Map.<String, Object>of("is_anonymous", true))) {
            mvc.perform(
                            get("/api/auth/me")
                                    .header(
                                            "Authorization",
                                            "Bearer "
                                                    + TestTokens.token("invalid@example.com", bad)))
                    .andExpect(status().isUnauthorized());
        }
        var missing = new java.util.HashMap<String, Object>();
        missing.put("exp", null);
        mvc.perform(
                        get("/api/auth/me")
                                .header(
                                        "Authorization",
                                        "Bearer "
                                                + TestTokens.token("invalid@example.com", missing)))
                .andExpect(status().isUnauthorized());
        String token = TestTokens.token("invalid@example.com");
        int index = token.lastIndexOf('.') + 1;
        token =
                token.substring(0, index)
                        + (token.charAt(index) == 'a' ? 'b' : 'a')
                        + token.substring(index + 1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class));
    }

    @Test
    void legacyPasswordEndpointsAreGoneAndAuthenticatedValidationStillWorks() throws Exception {
        String token = TestTokens.token("teacher@example.com");
        mvc.perform(
                        post("/api/auth/login")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/library/resources")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
        mvc.perform(
                        post("/api/library/resources")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
