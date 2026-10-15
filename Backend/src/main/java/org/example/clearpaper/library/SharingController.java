package org.example.clearpaper.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.example.clearpaper.accounts.TeacherPrincipal;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@RestController
public class SharingController {
    private final LibraryService library;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final SecureRandom random = new SecureRandom();

    public SharingController(LibraryService library, JdbcTemplate jdbc, JsonMapper json) {
        this.library = library;
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Permission(@NotBlank @Pattern(regexp = "VIEW|COPY") String permission) {}

    public record Publication(String id, String permission, String createdAt) {}

    public record Published(String id, String permission, String token) {}

    public record Shared(String name, String kind, JsonNode data, String permission) {}

    @PostMapping("/api/library/resources/{id}/shares")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Published publish(
            @AuthenticationPrincipal TeacherPrincipal teacher,
            @PathVariable String id,
            @Valid @RequestBody Permission input) {
        var r = library.get(teacher.id(), id);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                key = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO resource_shares(id,owner_id,resource_id,token_hash,permission,name,kind,data_json) VALUES(?,?,?,?,?,?,?,?)",
                key,
                teacher.id(),
                id,
                hash(token),
                input.permission(),
                r.name(),
                r.kind(),
                json.writeValueAsString(r.data()));
        return new Published(key, input.permission(), token);
    }

    @GetMapping("/api/library/resources/{id}/shares")
    public List<Publication> publications(
            @AuthenticationPrincipal TeacherPrincipal teacher, @PathVariable String id) {
        library.get(teacher.id(), id);
        return jdbc.query(
                "SELECT id,permission,created_at FROM resource_shares WHERE owner_id=? AND resource_id=? ORDER BY created_at,id",
                (rs, n) ->
                        new Publication(
                                rs.getString("id"),
                                rs.getString("permission"),
                                rs.getTimestamp("created_at").toInstant().toString()),
                teacher.id(),
                id);
    }

    @DeleteMapping("/api/library/resources/{id}/shares/{shareId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal TeacherPrincipal teacher,
            @PathVariable String id,
            @PathVariable String shareId) {
        library.get(teacher.id(), id);
        if (jdbc.update(
                        "DELETE FROM resource_shares WHERE owner_id=? AND resource_id=? AND id=?",
                        teacher.id(),
                        id,
                        shareId)
                == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private Shared lookup(String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return jdbc
                .query(
                        "SELECT name,kind,data_json,permission FROM resource_shares WHERE token_hash=?",
                        (rs, n) ->
                                new Shared(
                                        rs.getString("name"),
                                        rs.getString("kind"),
                                        json.readTree(rs.getString("data_json")),
                                        rs.getString("permission")),
                        hash(token))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/api/shared/{token}")
    public ResponseEntity<Shared> view(@PathVariable String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(lookup(token));
    }

    @PostMapping("/api/shared/{token}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LibraryService.Resource copy(
            @AuthenticationPrincipal TeacherPrincipal teacher, @PathVariable String token) {
        var r = lookup(token);
        if (!"COPY".equals(r.permission()))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This link permits viewing only.");
        return library.create(
                teacher.id(),
                new LibraryController.ResourceInput(
                        r.name().substring(0, Math.min(93, r.name().length())) + " (copy)",
                        r.kind(),
                        null,
                        r.data()));
    }

    private String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
