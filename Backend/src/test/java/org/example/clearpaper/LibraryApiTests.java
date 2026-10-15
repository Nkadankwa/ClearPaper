package org.example.clearpaper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:library_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@Import(TestTokens.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LibraryApiTests {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    MockMvc mvc;

    record Browser(String token) {}

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.update("DELETE FROM app_users");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM app_users");
    }

    JsonNode send(String method, String path, String body, Browser b, int code) throws Exception {
        var req =
                request(org.springframework.http.HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + b.token());
        if (body != null) req.contentType("application/json").content(body);
        var r = mvc.perform(req).andExpect(status().is(code)).andReturn();
        return r.getResponse().getContentAsString().isBlank()
                ? null
                : json.readTree(r.getResponse().getContentAsString());
    }

    Browser login(String email) throws Exception {
        var b = new Browser(TestTokens.token(email));
        send("GET", "/api/auth/me", null, b, 200);
        return b;
    }

    String diagram =
            """
 {"schemaVersion":1,"points":[{"id":"a","name":"A","x":100,"y":100},{"id":"b","name":"B","x":100,"y":200},{"id":"c","name":"C","x":200,"y":200}],"segments":[{"id":"ab","a":"a","b":"b","label":"x","style":"solid"},{"id":"bc","a":"b","b":"c","label":"","style":"solid"}],"angles":[{"id":"abc","v":"b","a":"a","b":"c","label":"","reflex":false,"hidden":false}]}
 """;

    String input(String name, String collection, String data, String kind) {
        return "{\"name\":\""
                + name
                + "\",\"kind\":\""
                + kind
                + "\",\"collectionId\":"
                + (collection == null ? "null" : "\"" + collection + "\"")
                + ",\"data\":"
                + data
                + "}";
    }

    @Test
    void worksheetChoicesPersistAndInvalidCountsReject() throws Exception {
        var a = login("worksheet@example.com");
        String data =
                "{\"schemaVersion\":1,\"blocks\":[{\"id\":\"q1\",\"text\":\"Find x\",\"type\":\"MC\",\"choices\":[\"40\",\"60\"],\"answerLines\":3,\"diagram\":"
                        + diagram
                        + "}]}";
        var saved =
                send(
                        "POST",
                        "/api/library/resources",
                        input("MC quiz", null, data, "WORKSHEET"),
                        a,
                        201);
        String id = saved.path("id").asString();
        assertEquals(
                saved.path("data"),
                send("GET", "/api/library/resources/" + id, null, a, 200).path("data"));
        send(
                "PUT",
                "/api/library/resources/" + id,
                input("MC quiz", null, data.replace("[\"40\",\"60\"]", "[\"40\"]"), "WORKSHEET"),
                a,
                400);
        send(
                "PUT",
                "/api/library/resources/" + id,
                input(
                        "MC quiz",
                        null,
                        data.replace("[\"40\",\"60\"]", "[\"40\",\"\"]"),
                        "WORKSHEET"),
                a,
                400);
        send(
                "PUT",
                "/api/library/resources/" + id,
                input(
                        "MC quiz",
                        null,
                        data.replace("\"answerLines\":3", "\"answerLines\":1.5"),
                        "WORKSHEET"),
                a,
                400);
        var b = login("worksheet-other@example.com");
        send(
                "PUT",
                "/api/library/resources/" + id,
                input("Other", null, data, "WORKSHEET"),
                b,
                404);
    }

    @Test
    void sharedSnapshotsAreReadOnlyRevocableAndCopiesAreIndependent() throws Exception {
        var a = login("sharing-a@example.com");
        var b = login("sharing-b@example.com");
        String source =
                send(
                                "POST",
                                "/api/library/resources",
                                input("Shared triangle", null, diagram, "DIAGRAM"),
                                a,
                                201)
                        .path("id")
                        .asString();
        String base = "/api/library/resources/" + source + "/shares";
        send("POST", base, "{\"permission\":\"EDIT\"}", a, 400);
        var view = send("POST", base, "{\"permission\":\"VIEW\"}", a, 201);
        String viewToken = view.path("token").asString();
        mvc.perform(get("/api/shared/" + viewToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        send("POST", "/api/shared/" + viewToken + "/copy", null, b, 403);
        send("POST", base, "{\"permission\":\"COPY\"}", b, 404);
        var link = send("POST", base, "{\"permission\":\"COPY\"}", a, 201);
        String token = link.path("token").asString(), shareId = link.path("id").asString();
        assertEquals(43, token.length());
        assertNotEquals(
                token,
                jdbc.queryForObject(
                        "SELECT token_hash FROM resource_shares WHERE id=?",
                        String.class,
                        shareId));
        mvc.perform(post("/api/shared/" + token + "/copy")).andExpect(status().isUnauthorized());
        send(
                "PUT",
                "/api/library/resources/" + source,
                input(
                        "Private edit",
                        null,
                        diagram.replace("\"label\":\"x\"", "\"label\":\"private\""),
                        "DIAGRAM"),
                a,
                200);
        var visible = send("GET", "/api/shared/" + token, null, b, 200);
        assertEquals("Shared triangle", visible.path("name").asString());
        assertFalse(visible.has("id"));
        assertFalse(visible.has("ownerId"));
        assertEquals("x", visible.path("data").path("segments").get(0).path("label").asString());
        var copy = send("POST", "/api/shared/" + token + "/copy", null, b, 201);
        String copyId = copy.path("id").asString();
        assertNotEquals(source, copyId);
        send(
                "PUT",
                "/api/library/resources/" + copyId,
                input(
                        "My copy",
                        null,
                        diagram.replace("\"label\":\"x\"", "\"label\":\"mine\""),
                        "DIAGRAM"),
                b,
                200);
        send("GET", "/api/library/resources/" + source, null, b, 404);
        send(
                "PUT",
                "/api/library/resources/" + source,
                input("Attack", null, diagram, "DIAGRAM"),
                b,
                404);
        send("DELETE", base + "/" + shareId, null, b, 404);
        send("DELETE", base + "/" + shareId, null, a, 204);
        mvc.perform(get("/api/shared/" + token)).andExpect(status().isNotFound());
        send("POST", "/api/shared/" + token + "/copy", null, b, 404);
        assertEquals(
                "mine",
                send("GET", "/api/library/resources/" + copyId, null, b, 200)
                        .path("data")
                        .path("segments")
                        .get(0)
                        .path("label")
                        .asString());
        send("DELETE", "/api/library/resources/" + source, null, a, 204);
        mvc.perform(get("/api/shared/" + viewToken)).andExpect(status().isNotFound());
    }

    @Test
    void resourceBackupsValidateScopeAndPaperAndAlwaysCreateNewRecords() throws Exception {
        var a = login("backup-a@example.com");
        var b = login("backup-b@example.com");
        String worksheet =
                "{\"schemaVersion\":1,\"paper\":{\"size\":\"Legal\",\"orientation\":\"landscape\"},\"blocks\":[{\"id\":\"q1\",\"text\":\"Choose x\",\"type\":\"MC\",\"choices\":[\"1\",\"2\"],\"answerLines\":2,\"figureHeight\":60,\"pageBreakBefore\":true,\"diagram\":"
                        + diagram
                        + "}]}";
        String source =
                send(
                                "POST",
                                "/api/library/resources",
                                input("Portable quiz", null, worksheet, "WORKSHEET"),
                                a,
                                201)
                        .path("id")
                        .asString();
        var backup = send("GET", "/api/library/resources/" + source + "/export", null, a, 200);
        send("GET", "/api/library/resources/" + source + "/export", null, b, 404);
        assertFalse(backup.path("resource").has("id"));
        assertFalse(backup.path("resource").has("collectionId"));
        var restored = send("POST", "/api/library/import", backup.toString(), b, 201);
        assertNotEquals(source, restored.path("id").asString());
        assertEquals(backup.path("resource").path("data"), restored.path("data"));
        send(
                "POST",
                "/api/library/import",
                backup.toString().replace("\"backupVersion\":1", "\"backupVersion\":2"),
                b,
                400);
        send(
                "POST",
                "/api/library/import",
                backup.toString()
                        .replace("\"resource\":{", "\"resource\":{\"ownerId\":\"forged\","),
                b,
                400);
        send(
                "POST",
                "/api/library/import",
                backup.toString().replace("\"size\":\"Legal\"", "\"size\":\"Unknown\""),
                b,
                400);
        send(
                "POST",
                "/api/library/import",
                backup.toString().replace("\"figureHeight\":60", "\"figureHeight\":200"),
                b,
                400);
        for (String size : java.util.List.of("A4", "A5", "A3", "Letter", "Legal"))
            for (String orientation : java.util.List.of("portrait", "landscape"))
                send(
                        "PUT",
                        "/api/library/resources/" + source,
                        input(
                                "Paper",
                                null,
                                worksheet.replace("Legal", size).replace("landscape", orientation),
                                "WORKSHEET"),
                        a,
                        200);
    }

    @Test
    void saveReopenSearchCollectionDuplicateAndDelete() throws Exception {
        var a = login("a@example.com");
        String col =
                send("POST", "/api/library/collections", "{\"name\":\"Class 4\"}", a, 201)
                        .path("id")
                        .asString();
        var saved =
                send(
                        "POST",
                        "/api/library/resources",
                        input("Right angle", col, diagram, "DIAGRAM"),
                        a,
                        201);
        String id = saved.path("id").asString();
        assertEquals(
                saved.path("data"),
                send("GET", "/api/library/resources/" + id, null, a, 200).path("data"));
        assertEquals(
                1,
                send(
                                "GET",
                                "/api/library/resources?search=RIGHT&kind=DIAGRAM&collectionId="
                                        + col,
                                null,
                                a,
                                200)
                        .size());
        assertEquals(0, send("GET", "/api/library/resources?search=missing", null, a, 200).size());
        var copy = send("POST", "/api/library/resources/" + id + "/duplicate", null, a, 201);
        String copyId = copy.path("id").asString();
        assertNotEquals(id, copyId);
        send(
                "PUT",
                "/api/library/resources/" + copyId,
                input(
                        "Changed copy",
                        null,
                        diagram.replace("\"label\":\"x\"", "\"label\":\"y\""),
                        "DIAGRAM"),
                a,
                200);
        assertEquals(
                "x",
                send("GET", "/api/library/resources/" + id, null, a, 200)
                        .path("data")
                        .path("segments")
                        .get(0)
                        .path("label")
                        .asString());
        String worksheet =
                "{\"schemaVersion\":1,\"blocks\":[{\"text\":\"Find x.\",\"diagram\":"
                        + diagram
                        + "}]}";
        String wid =
                send(
                                "POST",
                                "/api/library/resources",
                                input("Quiz", col, worksheet, "WORKSHEET"),
                                a,
                                201)
                        .path("id")
                        .asString();
        assertEquals(
                "Find x.",
                send("GET", "/api/library/resources/" + wid, null, a, 200)
                        .path("data")
                        .path("blocks")
                        .get(0)
                        .path("text")
                        .asString());
        send("DELETE", "/api/library/resources/" + copyId, null, a, 204);
        send("GET", "/api/library/resources/" + copyId, null, a, 404);
    }

    @Test
    void anotherTeacherCannotReadWriteCopyDeleteOrUsePrivateCollection() throws Exception {
        var a = login("a@example.com");
        var b = login("b@example.com");
        String col =
                send("POST", "/api/library/collections", "{\"name\":\"Private class\"}", a, 201)
                        .path("id")
                        .asString();
        String body = input("Private", col, diagram, "DIAGRAM");
        String id = send("POST", "/api/library/resources", body, a, 201).path("id").asString();
        assertEquals(0, send("GET", "/api/library/resources", null, b, 200).size());
        assertEquals(0, send("GET", "/api/library/collections", null, b, 200).size());
        send("GET", "/api/library/resources/" + id, null, b, 404);
        send("PUT", "/api/library/resources/" + id, body, b, 404);
        send("DELETE", "/api/library/resources/" + id, null, b, 404);
        send("POST", "/api/library/resources/" + id + "/duplicate", null, b, 404);
        send("POST", "/api/library/resources", body, b, 404);
        send("GET", "/api/library/resources?collectionId=" + col, null, b, 404);
        send("GET", "/api/library/resources/" + id, null, a, 200);
    }

    @Test
    void malformedGeometryIsRejectedClearly() throws Exception {
        var a = login("a@example.com");
        assertTrue(
                send(
                                "POST",
                                "/api/library/resources",
                                input(
                                        "Bad",
                                        null,
                                        diagram.replace(
                                                "\"schemaVersion\":1", "\"schemaVersion\":2"),
                                        "DIAGRAM"),
                                a,
                                400)
                        .path("message")
                        .asString()
                        .contains("schemaVersion"));
        send(
                "POST",
                "/api/library/resources",
                input("Bad", null, diagram.replace("\"a\":\"a\"", "\"a\":\"missing\""), "DIAGRAM"),
                a,
                400);
        send("POST", "/api/library/resources", "{}", a, 400);
        send("GET", "/api/library/resources?kind=UNKNOWN", null, a, 400);
    }

    @Test
    void hiddenManualAnglesAndExpressionsPersistAndInvalidConnectionsReject() throws Exception {
        var a = login("angles@example.com");
        String marked =
                diagram.replace("\"label\":\"x\"", "\"label\":\"2x + 5\"")
                        .replace("\"hidden\":false", "\"hidden\":true,\"manual\":true");
        var saved =
                send(
                        "POST",
                        "/api/library/resources",
                        input("Hidden angle", null, marked, "DIAGRAM"),
                        a,
                        201);
        String id = saved.path("id").asString();
        var data = send("GET", "/api/library/resources/" + id, null, a, 200).path("data");
        assertTrue(data.path("angles").get(0).path("hidden").asBoolean());
        assertTrue(data.path("angles").get(0).path("manual").asBoolean());
        assertEquals("2x + 5", data.path("segments").get(0).path("label").asString());
        String duplicate =
                diagram.replace(
                        "\"segments\":[",
                        "\"segments\":[{\"id\":\"reverse\",\"a\":\"b\",\"b\":\"a\",\"label\":\"\",\"style\":\"solid\"},");
        send("POST", "/api/library/resources", input("Bad", null, duplicate, "DIAGRAM"), a, 400);
        send(
                "POST",
                "/api/library/resources",
                input("Bad", null, diagram.replace("\"x\":200", "\"x\":100"), "DIAGRAM"),
                a,
                400);
        send(
                "POST",
                "/api/library/resources",
                input(
                        "Bad",
                        null,
                        marked.replace("\"manual\":true", "\"manual\":\"yes\""),
                        "DIAGRAM"),
                a,
                400);
    }

    @Test
    void modelParametersVisibilityAndCopiesRoundTripAndBadValuesReject() throws Exception {
        var a = login("models@example.com");
        java.util.Map<String, String> values =
                java.util.Map.of(
                        "TRIANGLE",
                        "{\"a\":50,\"b\":60,\"base\":8}",
                        "BEARING",
                        "{\"angle\":65,\"distance\":12}",
                        "INCLINE",
                        "{\"angle\":30,\"mass\":2}",
                        "SCALE",
                        "{\"min\":0,\"max\":100,\"reading\":37.5,\"divisions\":10,\"unit\":\"cm\"}");
        for (var entry : values.entrySet()) {
            String data = modelData(entry.getKey(), entry.getValue()),
                    rid =
                            send(
                                            "POST",
                                            "/api/library/resources",
                                            input(entry.getKey(), null, data, "DIAGRAM"),
                                            a,
                                            201)
                                    .path("id")
                                    .asString();
            assertEquals(
                    json.readTree(data),
                    send("GET", "/api/library/resources/" + rid, null, a, 200).path("data"));
            String updated =
                    data.replace("\"showAnswers\":true", "\"showAnswers\":false")
                            .replace("\"showAngles\":true", "\"showAngles\":false");
            send(
                    "PUT",
                    "/api/library/resources/" + rid,
                    input("Updated " + entry.getKey(), null, updated, "DIAGRAM"),
                    a,
                    200);
            var copy = send("POST", "/api/library/resources/" + rid + "/duplicate", null, a, 201);
            assertNotEquals(rid, copy.path("id").asString());
            assertEquals(json.readTree(updated), copy.path("data"));
        }
        send(
                "POST",
                "/api/library/resources",
                input(
                        "Bad",
                        null,
                        modelData("TRIANGLE", "{\"a\":120,\"b\":100,\"base\":8}"),
                        "DIAGRAM"),
                a,
                400);
        send(
                "POST",
                "/api/library/resources",
                input(
                        "Bad",
                        null,
                        modelData("TRIANGLE", "{\"a\":50.5,\"b\":60,\"base\":8}"),
                        "DIAGRAM"),
                a,
                400);
        send(
                "POST",
                "/api/library/resources",
                input(
                        "Bad",
                        null,
                        modelData("BEARING", "{\"angle\":360,\"distance\":12}"),
                        "DIAGRAM"),
                a,
                400);
        send(
                "POST",
                "/api/library/resources",
                input("Bad", null, modelData("INCLINE", "{\"angle\":30,\"mass\":0}"), "DIAGRAM"),
                a,
                400);
        send(
                "POST",
                "/api/library/resources",
                input(
                        "Bad",
                        null,
                        modelData(
                                "SCALE",
                                "{\"min\":0,\"max\":100,\"reading\":101,\"divisions\":10,\"unit\":\"cm\"}"),
                        "DIAGRAM"),
                a,
                400);
        send(
                "POST",
                "/api/library/resources",
                input("Bad", null, modelData("UNKNOWN", "{}"), "DIAGRAM"),
                a,
                400);
    }

    String modelData(String type, String parameters) {
        return "{\"schemaVersion\":1,\"points\":[],\"segments\":[],\"angles\":[],\"model\":{\"type\":\""
                + type
                + "\",\"parameters\":"
                + parameters
                + ",\"showAnswers\":true,\"showAngles\":true,\"hiddenAngles\":{\"a\":true,\"b\":false,\"c\":false}}}";
    }
}
