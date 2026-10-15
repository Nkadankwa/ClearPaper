package org.example.clearpaper.accounts;

import java.util.List;
import java.util.UUID;
import org.example.clearpaper.api.ApiErrors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    public static OAuth2TokenValidator<Jwt> validator(String issuer) {
        OAuth2TokenValidator<Jwt> claims =
                jwt -> {
                    boolean valid =
                            jwt.getAudience() != null
                                    && jwt.getAudience().contains("authenticated")
                                    && "authenticated".equals(jwt.getClaim("role"))
                                    && !Boolean.TRUE.equals(jwt.getClaim("is_anonymous"))
                                    && jwt.getExpiresAt() != null;
                    try {
                        valid =
                                valid
                                        && UUID.fromString(jwt.getSubject())
                                                .toString()
                                                .equalsIgnoreCase(jwt.getSubject());
                    } catch (RuntimeException invalid) {
                        valid = false;
                    }
                    return valid
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error(
                                            "invalid_token",
                                            "A Supabase user access token is required.",
                                            null));
                };
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), claims);
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${clearpaper.supabase.url}") String url) {
        String issuer = url.replaceAll("/+$", "") + "/auth/v1";
        var decoder =
                NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json")
                        .jwsAlgorithm(SignatureAlgorithm.ES256)
                        .jwsAlgorithm(SignatureAlgorithm.RS256)
                        .build();
        decoder.setJwtValidator(validator(issuer));
        return decoder;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, TeacherAccounts accounts, ApiErrors errors)
            throws Exception {
        var entry =
                (org.springframework.security.web.AuthenticationEntryPoint)
                        (req, res, ex) ->
                                errors.write(
                                        res,
                                        401,
                                        "UNAUTHENTICATED",
                                        "Sign in with Supabase to continue.");
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/health")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/shared/*")
                                        .permitAll()
                                        .requestMatchers("/api/**")
                                        .hasRole("TEACHER")
                                        .anyRequest()
                                        .permitAll())
                .oauth2ResourceServer(
                        oauth ->
                                oauth.authenticationEntryPoint(entry)
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                token ->
                                                                        UsernamePasswordAuthenticationToken
                                                                                .authenticated(
                                                                                        accounts
                                                                                                .resolve(
                                                                                                        token),
                                                                                        token,
                                                                                        List.of(
                                                                                                new SimpleGrantedAuthority(
                                                                                                        "ROLE_TEACHER"))))))
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(entry)
                                        .accessDeniedHandler(
                                                (req, res, ex) ->
                                                        errors.write(
                                                                res,
                                                                403,
                                                                "FORBIDDEN",
                                                                "Access denied.")))
                .requestCache(c -> c.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .build();
    }
}
