package org.example.clearpaper.library;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.example.clearpaper.accounts.TeacherPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/library")
public class LibraryController {
    private final LibraryService library;

    public LibraryController(LibraryService library) {
        this.library = library;
    }

    public record ResourceInput(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Pattern(regexp = "DIAGRAM|WORKSHEET") String kind,
            @Size(max = 36) String collectionId,
            JsonNode data) {}

    public record CollectionInput(@NotBlank @Size(max = 80) String name) {}

    @GetMapping("/resources")
    public List<LibraryService.Resource> list(
            @AuthenticationPrincipal TeacherPrincipal teacher,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String collectionId) {
        return library.list(teacher.id(), search, kind, collectionId);
    }

    @PostMapping("/resources")
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryService.Resource create(
            @AuthenticationPrincipal TeacherPrincipal teacher,
            @Valid @RequestBody ResourceInput input) {
        return library.create(teacher.id(), input);
    }

    @GetMapping("/resources/{id}")
    public LibraryService.Resource get(
            @AuthenticationPrincipal TeacherPrincipal teacher, @PathVariable String id) {
        return library.get(teacher.id(), id);
    }

    @PutMapping("/resources/{id}")
    public LibraryService.Resource update(
            @AuthenticationPrincipal TeacherPrincipal teacher,
            @PathVariable String id,
            @Valid @RequestBody ResourceInput input) {
        return library.update(teacher.id(), id, input);
    }

    @DeleteMapping("/resources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal TeacherPrincipal teacher, @PathVariable String id) {
        library.delete(teacher.id(), id);
    }

    @PostMapping("/resources/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryService.Resource duplicate(
            @AuthenticationPrincipal TeacherPrincipal teacher, @PathVariable String id) {
        var r = library.get(teacher.id(), id);
        return library.create(
                teacher.id(),
                new ResourceInput(
                        r.name().substring(0, Math.min(93, r.name().length())) + " (copy)",
                        r.kind(),
                        r.collectionId(),
                        r.data()));
    }

    @GetMapping("/collections")
    public List<LibraryService.Collection> collections(
            @AuthenticationPrincipal TeacherPrincipal teacher) {
        return library.collections(teacher.id());
    }

    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public LibraryService.Collection collection(
            @AuthenticationPrincipal TeacherPrincipal teacher,
            @Valid @RequestBody CollectionInput input) {
        return library.collection(teacher.id(), input.name());
    }
}
