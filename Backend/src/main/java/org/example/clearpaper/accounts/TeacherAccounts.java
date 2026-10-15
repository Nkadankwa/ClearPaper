package org.example.clearpaper.accounts;

import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class TeacherAccounts {
    private final JdbcTemplate jdbc;

    public TeacherAccounts(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TeacherPrincipal resolve(Jwt jwt) {
        String id = jwt.getSubject(), email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || email.length() > 254)
            throw new BadCredentialsException("A verified email account is required.");
        email = email.strip().toLowerCase(Locale.ROOT);
        Map<String, Object> metadata = jwt.getClaimAsMap("user_metadata");
        Object value = metadata == null ? null : metadata.get("display_name");
        String name =
                value instanceof String text && !text.isBlank()
                        ? text.strip()
                        : email.split("@", 2)[0];
        name = name.substring(0, Math.min(80, name.length()));
        var existing =
                jdbc
                        .query(
                                "SELECT id,display_name,email,role FROM app_users WHERE id=?",
                                (rs, n) -> {
                                    if (!"TEACHER".equals(rs.getString("role")))
                                        throw new BadCredentialsException(
                                                "Teacher access is required.");
                                    return new TeacherPrincipal(
                                            rs.getString("id"),
                                            rs.getString("display_name"),
                                            rs.getString("email"));
                                },
                                id)
                        .stream()
                        .findFirst();
        if (existing.isPresent()) return existing.get();
        try {
            jdbc.update(
                    "INSERT INTO app_users(id,display_name,email,role) VALUES(?,?,?,'TEACHER')",
                    id,
                    name,
                    email);
        } catch (DuplicateKeyException race) {
            return jdbc
                    .query(
                            "SELECT id,display_name,email FROM app_users WHERE id=? AND role='TEACHER'",
                            (rs, n) ->
                                    new TeacherPrincipal(
                                            rs.getString("id"),
                                            rs.getString("display_name"),
                                            rs.getString("email")),
                            id)
                    .stream()
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new BadCredentialsException(
                                            "Account profile could not be provisioned."));
        }
        return new TeacherPrincipal(id, name, email);
    }
}
