package org.example.clearpaper.library;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Component
public class ResourceValidator {
    private void require(boolean ok, String message) {
        if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private boolean text(JsonNode n, int max) {
        return n != null && n.isString() && n.asString().length() <= max;
    }

    public void validate(String kind, JsonNode data) {
        require(
                data != null && data.isObject() && data.path("schemaVersion").asInt() == 1,
                "Use schemaVersion 1.");
        require(data.toString().length() <= 1_000_000, "Resource is too large.");
        if ("DIAGRAM".equals(kind)) {
            geometry(data);
            return;
        }
        require("WORKSHEET".equals(kind), "Unknown resource type.");
        if (data.has("paper")) {
            var p = data.path("paper");
            require(
                    p.isObject()
                            && Set.of("A4", "A5", "A3", "Letter", "Legal")
                                    .contains(p.path("size").asString())
                            && Set.of("portrait", "landscape")
                                    .contains(p.path("orientation").asString()),
                    "Choose a supported paper size and orientation.");
        }
        JsonNode blocks = data.path("blocks");
        require(
                blocks.isArray() && blocks.size() <= 30,
                "Worksheet requires at most 30 question blocks.");
        Set<String> questionIds = new HashSet<>();
        for (JsonNode b : blocks) {
            if (b.has("figureHeight"))
                require(
                        b.path("figureHeight").isNumber()
                                && b.path("figureHeight").asDouble() >= 30
                                && b.path("figureHeight").asDouble() <= 150,
                        "Figure height must be 30–150 mm.");
            if (b.has("pageBreakBefore"))
                require(b.path("pageBreakBefore").isBoolean(), "Invalid question page break.");
            if (b.has("id"))
                require(
                        text(b.get("id"), 64)
                                && !b.path("id").asString().isBlank()
                                && questionIds.add(b.path("id").asString()),
                        "Invalid or duplicate question identity.");
            if (b.has("type"))
                require(
                        Set.of("WRITTEN", "MC").contains(b.path("type").asString()),
                        "Invalid question type.");
            if ("MC".equals(b.path("type").asString())) {
                JsonNode choices = b.path("choices");
                require(
                        choices.isArray() && choices.size() >= 2 && choices.size() <= 6,
                        "Multiple choice requires 2–6 options.");
                for (JsonNode c : choices)
                    require(
                            text(c, 300) && !c.asString().isBlank(),
                            "Enter each multiple choice option (up to 300 characters).");
            }
            if (b.has("answerLines"))
                require(
                        b.path("answerLines").isNumber()
                                && b.path("answerLines").asDouble()
                                        == Math.rint(b.path("answerLines").asDouble())
                                && b.path("answerLines").asDouble() >= 0
                                && b.path("answerLines").asDouble() <= 10,
                        "Answer lines must be a whole number from 0 to 10.");
            require(b.isObject() && text(b.get("text"), 1500), "Invalid question text.");
            if (b.has("diagram")) geometry(b.get("diagram"));
        }
    }

    private void geometry(JsonNode g) {
        require(
                g != null && g.isObject() && g.path("schemaVersion").asInt() == 1,
                "Invalid geometry version.");
        JsonNode points = g.path("points"), edges = g.path("segments"), angles = g.path("angles");
        require(
                points.isArray()
                        && points.size() <= 300
                        && edges.isArray()
                        && edges.size() <= 400
                        && angles.isArray()
                        && angles.size() <= 500,
                "Invalid geometry lists or drawing limit exceeded.");
        Set<String> ids = new HashSet<>(),
                edgeIds = new HashSet<>(),
                angleIds = new HashSet<>(),
                connections = new HashSet<>();
        Map<String, JsonNode> nodes = new HashMap<>();
        for (JsonNode p : points) {
            require(
                    text(p.get("id"), 64)
                            && !p.path("id").asString().isBlank()
                            && ids.add(p.path("id").asString())
                            && text(p.get("name"), 20),
                    "Invalid or duplicate point.");
            for (String axis : List.of("x", "y"))
                require(
                        p.path(axis).isNumber()
                                && Double.isFinite(p.path(axis).asDouble())
                                && Math.abs(p.path(axis).asDouble()) <= 10000,
                        "Invalid point coordinate.");
            nodes.put(p.path("id").asString(), p);
        }
        for (JsonNode e : edges) {
            String a = e.path("a").asString(), b = e.path("b").asString();
            require(
                    text(e.get("id"), 64)
                            && edgeIds.add(e.path("id").asString())
                            && ids.contains(a)
                            && ids.contains(b)
                            && !a.equals(b)
                            && text(e.get("label"), 40)
                            && Set.of("solid", "dashed").contains(e.path("style").asString()),
                    "Invalid segment.");
            require(
                    Math.hypot(
                                    nodes.get(a).path("x").asDouble()
                                            - nodes.get(b).path("x").asDouble(),
                                    nodes.get(a).path("y").asDouble()
                                            - nodes.get(b).path("y").asDouble())
                            > .01,
                    "A segment must have length.");
            require(!connections.contains(a + "|" + b), "These points are already connected.");
            connections.add(a + "|" + b);
            connections.add(b + "|" + a);
        }
        for (JsonNode a : angles) {
            String v = a.path("v").asString(),
                    p = a.path("a").asString(),
                    q = a.path("b").asString();
            require(
                    text(a.get("id"), 64)
                            && angleIds.add(a.path("id").asString())
                            && ids.contains(v)
                            && ids.contains(p)
                            && ids.contains(q)
                            && !v.equals(p)
                            && !v.equals(q)
                            && !p.equals(q)
                            && connections.contains(v + "|" + p)
                            && connections.contains(v + "|" + q)
                            && text(a.get("label"), 40)
                            && a.path("reflex").isBoolean(),
                    "Invalid angle rays.");
            for (String key : List.of("hidden", "sweep", "manual"))
                if (a.has(key))
                    require(a.get(key).isBoolean(), "Invalid angle visibility/direction.");
        }
        if (g.has("model")) model(g.get("model"));
    }

    private double number(JsonNode p, String key, double min, double max, boolean integer) {
        JsonNode n = p.path(key);
        require(
                n.isNumber()
                        && Double.isFinite(n.asDouble())
                        && n.asDouble() >= min
                        && n.asDouble() <= max
                        && (!integer || n.asDouble() == Math.rint(n.asDouble())),
                "Invalid model value: "
                        + key
                        + ". Use "
                        + (integer ? "a whole number" : "a number")
                        + " from "
                        + min
                        + " to "
                        + max
                        + ".");
        return n.asDouble();
    }

    private void model(JsonNode m) {
        require(m.isObject() && m.path("parameters").isObject(), "Invalid model parameters.");
        require(
                m.path("showAnswers").isBoolean() && m.path("showAngles").isBoolean(),
                "Invalid model visibility settings.");
        if (m.has("hiddenAngles")) {
            require(m.get("hiddenAngles").isObject(), "Invalid individual angle visibility.");
            for (JsonNode flag : m.get("hiddenAngles"))
                require(flag.isBoolean(), "Invalid individual angle visibility.");
        }
        JsonNode p = m.path("parameters");
        switch (m.path("type").asString()) {
            case "TRIANGLE" -> {
                double a = number(p, "a", 5, 170, true), b = number(p, "b", 5, 170, true);
                require(a + b <= 175, "Angles A and B must total no more than 175 degrees.");
                number(p, "base", 1, 100, false);
            }
            case "BEARING" -> {
                number(p, "angle", 0, 359, true);
                number(p, "distance", 1, 100, false);
            }
            case "INCLINE" -> {
                number(p, "angle", 5, 75, true);
                number(p, "mass", .1, 100, false);
            }
            case "SCALE" -> {
                double min = number(p, "min", -10000, 10000, false),
                        max = number(p, "max", -10000, 10000, false),
                        reading = number(p, "reading", -10000, 10000, false);
                number(p, "divisions", 2, 20, true);
                require(
                        max > min && reading >= min && reading <= max,
                        "Maximum must exceed minimum and the reading must be in range.");
                require(
                        text(p.get("unit"), 12) && !p.path("unit").asString().isBlank(),
                        "Enter a unit of up to 12 characters.");
            }
            default ->
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Unknown diagram model.");
        }
    }
}
