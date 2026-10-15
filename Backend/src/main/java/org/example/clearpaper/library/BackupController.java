package org.example.clearpaper.library;

import java.util.*;
import org.example.clearpaper.accounts.TeacherPrincipal;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
public class BackupController {
    private final LibraryService library;

    public BackupController(LibraryService library) {
        this.library = library;
    }

    public record Content(String name, String kind, JsonNode data) {}

    public record Backup(String format, int backupVersion, Content resource) {}

    @GetMapping("/api/library/resources/{id}/export")
    public ResponseEntity<Backup> export(
            @AuthenticationPrincipal TeacherPrincipal teacher, @PathVariable String id) {
        var r = library.get(teacher.id(), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new Backup("ClearPaper", 1, new Content(r.name(), r.kind(), r.data())));
    }

    private void require(boolean ok, String message) {
        if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private boolean keys(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) return false;
        for (String key : node.propertyNames()) if (!allowed.contains(key)) return false;
        return true;
    }

    @PostMapping("/api/library/import")
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryService.Resource restore(
            @AuthenticationPrincipal TeacherPrincipal teacher, @RequestBody JsonNode backup) {
        require(backup != null && backup.toString().length() <= 1_010_000, "Backup is too large.");
        require(
                keys(backup, Set.of("format", "backupVersion", "resource"))
                        && "ClearPaper".equals(backup.path("format").asString())
                        && backup.path("backupVersion").isNumber()
                        && backup.path("backupVersion").asDouble() == 1,
                "Use a ClearPaper version 1 resource backup.");
        var r = backup.path("resource");
        require(
                keys(r, Set.of("name", "kind", "data")),
                "Import only resource name, kind and data; private IDs and account fields are not accepted.");
        require(
                r.path("name").isString()
                        && !r.path("name").asString().isBlank()
                        && r.path("name").asString().length() <= 100,
                "Enter a resource name of up to 100 characters.");
        require(
                Set.of("DIAGRAM", "WORKSHEET").contains(r.path("kind").asString()),
                "Only diagrams and worksheets can be imported.");
        return library.create(
                teacher.id(),
                new LibraryController.ResourceInput(
                        r.path("name").asString(), r.path("kind").asString(), null, r.get("data")));
    }
}
