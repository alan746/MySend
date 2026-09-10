package com.mysend.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:multipart-http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/multipart-http-uploads",
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=2KB"
})
class MultipartUploadHttpTest {
    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    @Test
    void acceptsAFileAtTheLimitIncludingMultipartOverhead() throws Exception {
        String actor = UUID.randomUUID().toString();
        var response = upload(createRoom(actor), actor, part("file", 1024), false);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(response.body()).path("sizeBytes").asLong()).isEqualTo(1024);
    }

    @Test
    void rejectsOversizedFilesForKnownAndChunkedBodyLengths() throws Exception {
        String actor = UUID.randomUUID().toString();
        String code = createRoom(actor);
        for (boolean chunked : new boolean[] {false, true}) {
            var response = upload(code, actor, part("file", 1025), chunked);
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo("UPLOAD_TOO_LARGE");
        }
    }

    @Test
    void rejectsAggregateRequestSizeEvenWhenIndividualPartsAreSmall() throws Exception {
        String actor = UUID.randomUUID().toString();
        String code = createRoom(actor);
        for (boolean chunked : new boolean[] {false, true}) {
            var response = upload(code, actor, part("file", 900) + part("extra", 900), chunked);
            assertThat(response.statusCode()).isEqualTo(413);
            assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo("UPLOAD_TOO_LARGE");
        }
    }

    @Test
    void deniesUnenteredVisitorsBeforeOversizedMultipartParsing() throws Exception {
        String code = createRoom(UUID.randomUUID().toString());
        var response = upload(code, UUID.randomUUID().toString(), part("file", 4096), false);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(mapper.readTree(response.body()).path("code").asText()).isEqualTo("ROOM_UNAVAILABLE");
    }

    private String createRoom(String actor) throws Exception {
        var request = request("/api/rooms", actor)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"visibility":"PUBLIC","lifetimeMinutes":15,"accessLimit":20}
                        """)).build();
        var response = send(request);
        assertThat(response.statusCode()).isEqualTo(201);
        return mapper.readTree(response.body()).path("accessCode").asText();
    }

    private HttpResponse<String> upload(String code, String actor, String parts, boolean chunked)
            throws Exception {
        byte[] body = (parts + "--upload-boundary--\r\n").getBytes(StandardCharsets.UTF_8);
        var publisher = chunked
                ? HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))
                : HttpRequest.BodyPublishers.ofByteArray(body);
        return send(request("/api/rooms/" + code + "/files", actor)
                .header("Content-Type", "multipart/form-data; boundary=upload-boundary")
                .POST(publisher).build());
    }

    private static String part(String name, int size) {
        return "--upload-boundary\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"sample.txt\"\r\nContent-Type: text/plain\r\n\r\n"
                + "x".repeat(size) + "\r\n";
    }

    private HttpRequest.Builder request(String path, String actor) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .header("Cookie", "mysend_device=" + actor)
                .header("Origin", "http://localhost:3000")
                .header("X-Requested-With", "MySendWeb");
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
