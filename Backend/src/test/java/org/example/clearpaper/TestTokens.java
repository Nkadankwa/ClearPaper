package org.example.clearpaper;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.example.clearpaper.accounts.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.*;

@TestConfiguration
class TestTokens {
    static final String ISSUER = "https://supabase.test/auth/v1";
    static final RSAKey KEY;

    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-key").generate();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String id(String email) {
        return UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String token(String email) {
        return token(email, Map.of());
    }

    static String token(String email, Map<String, Object> overrides) {
        try {
            Map<String, Object> claims = new HashMap<>();
            long now = Instant.now().getEpochSecond();
            claims.put("iss", ISSUER);
            claims.put("sub", id(email));
            claims.put("aud", List.of("authenticated"));
            claims.put("iat", now);
            claims.put("exp", now + 3600);
            claims.put("role", "authenticated");
            claims.put("email", email);
            claims.put("user_metadata", Map.of("display_name", "Teacher", "role", "STUDENT"));
            claims.putAll(overrides);
            var jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),
                            JWTClaimsSet.parse(claims));
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Bean
    @Primary
    JwtDecoder testJwtDecoder() throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        decoder.setJwtValidator(SecurityConfig.validator(ISSUER));
        return decoder;
    }
}
