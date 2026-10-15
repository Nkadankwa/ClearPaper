package org.example.clearpaper.accounts;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "application", "ClearPaper");
    }

    @GetMapping("/auth/me")
    public TeacherPrincipal.TeacherView me(@AuthenticationPrincipal TeacherPrincipal teacher) {
        return teacher.view();
    }

    @GetMapping("/teachers/{id}")
    public TeacherPrincipal.TeacherView teacher(
            @PathVariable String id, @AuthenticationPrincipal TeacherPrincipal teacher) {
        if (!teacher.id().equals(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return teacher.view();
    }
}
