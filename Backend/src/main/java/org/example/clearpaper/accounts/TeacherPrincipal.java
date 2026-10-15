package org.example.clearpaper.accounts;

import java.security.Principal;

public record TeacherPrincipal(String id, String displayName, String email) implements Principal {
    public String getName() {
        return id;
    }

    public TeacherView view() {
        return new TeacherView(id, displayName, email, "TEACHER");
    }

    public record TeacherView(String id, String displayName, String email, String role) {}
}
