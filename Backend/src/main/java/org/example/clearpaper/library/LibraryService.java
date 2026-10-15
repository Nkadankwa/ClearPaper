package org.example.clearpaper.library;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LibraryService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ResourceValidator validator;

    public LibraryService(JdbcTemplate jdbc, JsonMapper json, ResourceValidator validator) {
        this.jdbc = jdbc;
        this.json = json;
        this.validator = validator;
    }

    public record Resource(
            String id,
            String name,
            String kind,
            String collectionId,
            JsonNode data,
            String updatedAt) {}

    public record Collection(String id, String name) {}

    private Resource row(ResultSet rs, int index) throws SQLException {
        return new Resource(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getString("collection_id"),
                json.readTree(rs.getString("data_json")),
                rs.getTimestamp("updated_at").toInstant().toString());
    }

    public Resource get(String owner, String id) {
        return jdbc
                .query(
                        "SELECT * FROM library_resources WHERE owner_id=? AND id=?",
                        this::row,
                        owner,
                        id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<Resource> list(String owner, String search, String kind, String collection) {
        if (search.length() > 100 || kind != null && !Set.of("DIAGRAM", "WORKSHEET").contains(kind))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String sql =
                "SELECT * FROM library_resources WHERE owner_id=? AND LOWER(name) LIKE ? ESCAPE '!'";
        List<Object> args =
                new ArrayList<>(
                        List.of(
                                owner,
                                "%"
                                        + search.toLowerCase(Locale.ROOT)
                                                .replace("!", "!!")
                                                .replace("%", "!%")
                                                .replace("_", "!_")
                                        + "%"));
        if (kind != null && !kind.isBlank()) {
            sql += " AND kind=?";
            args.add(kind);
        }
        if (collection != null && !collection.isBlank()) {
            ownedCollection(owner, collection);
            sql += " AND collection_id=?";
            args.add(collection);
        }
        return jdbc.query(sql + " ORDER BY updated_at DESC,id", this::row, args.toArray());
    }

    private void ownedCollection(String owner, String id) {
        if (id != null
                && !id.isBlank()
                && jdbc.queryForObject(
                                "SELECT COUNT(*) FROM resource_collections WHERE id=? AND owner_id=?",
                                Integer.class,
                                id,
                                owner)
                        == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private String data(String owner, LibraryController.ResourceInput input) {
        ownedCollection(owner, input.collectionId());
        validator.validate(input.kind(), input.data());
        return json.writeValueAsString(input.data());
    }

    @Transactional
    public Resource create(String owner, LibraryController.ResourceInput input) {
        String body = data(owner, input), id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO library_resources(id,owner_id,name,kind,collection_id,data_json) VALUES(?,?,?,?,?,?)",
                id,
                owner,
                input.name().strip(),
                input.kind(),
                emptyToNull(input.collectionId()),
                body);
        return get(owner, id);
    }

    @Transactional
    public Resource update(String owner, String id, LibraryController.ResourceInput input) {
        get(owner, id);
        String body = data(owner, input);
        jdbc.update(
                "UPDATE library_resources SET name=?,kind=?,collection_id=?,data_json=?,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND id=?",
                input.name().strip(),
                input.kind(),
                emptyToNull(input.collectionId()),
                body,
                owner,
                id);
        return get(owner, id);
    }

    public void delete(String owner, String id) {
        if (jdbc.update("DELETE FROM library_resources WHERE owner_id=? AND id=?", owner, id) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public List<Collection> collections(String owner) {
        return jdbc.query(
                "SELECT id,name FROM resource_collections WHERE owner_id=? ORDER BY name",
                (rs, n) -> new Collection(rs.getString("id"), rs.getString("name")),
                owner);
    }

    public Collection collection(String owner, String name) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO resource_collections(id,owner_id,name) VALUES(?,?,?)",
                id,
                owner,
                name.strip());
        return new Collection(id, name.strip());
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
