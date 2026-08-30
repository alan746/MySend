package com.mysend.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:roomabusefilter;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/room-abuse-filter-uploads",
        "mysend.room-abuse.create.ip-limit=2",
        "mysend.room-abuse.create.actor-limit=1",
        "mysend.room-abuse.enter.ip-limit=100",
        "mysend.room-abuse.enter.actor-limit=2",
        "mysend.room-abuse.upload.ip-limit=100",
        "mysend.room-abuse.upload.actor-limit=1"
})
@AutoConfigureMockMvc
class RoomAbuseFilterTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void clearAttempts() {
        jdbc.sql("delete from room_abuse_attempts").update();
    }

    @Test
    void limitsRoomCreationByActorBeforeCreatingAnotherRoom() throws Exception {
        Cookie actor = new Cookie("mysend_device", "create-rate-actor");

        mvc.perform(createRoom(actor, "PUBLIC", null))
                .andExpect(status().isCreated());
        mvc.perform(createRoom(actor, "PUBLIC", null))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("ROOM_CREATE_RATE_LIMITED"))
                .andExpect(jsonPath("$.fields.retryAfterSeconds").isNotEmpty());
    }

    @Test
    void firstRequestWithoutADeviceCookieIssuesOneDeviceIdentity() throws Exception {
        MvcResult result = mvc.perform(createRoomWithoutCookie())
                .andExpect(status().isCreated())
                .andReturn();

        List<String> deviceCookies = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("mysend_device="))
                .toList();
        assertThat(deviceCookies).hasSize(1);
    }

    @Test
    void limitsRoomCreationAcrossDifferentActorsAtOneAddress() throws Exception {
        mvc.perform(createRoom(new Cookie("mysend_device", "shared-ip-1"), "PUBLIC", null))
                .andExpect(status().isCreated());
        mvc.perform(createRoom(new Cookie("mysend_device", "shared-ip-2"), "PUBLIC", null))
                .andExpect(status().isCreated());
        mvc.perform(createRoom(new Cookie("mysend_device", "shared-ip-3"), "PUBLIC", null))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ROOM_CREATE_RATE_LIMITED"));
    }

    @Test
    void ignoresForwardedAddressesSuppliedByTheBrowser() throws Exception {
        mvc.perform(createRoom(new Cookie("mysend_device", "forwarded-ip-1"), "PUBLIC", null)
                        .header("X-Forwarded-For", "198.51.100.1"))
                .andExpect(status().isCreated());
        mvc.perform(createRoom(new Cookie("mysend_device", "forwarded-ip-2"), "PUBLIC", null)
                        .header("X-Forwarded-For", "198.51.100.2"))
                .andExpect(status().isCreated());
        mvc.perform(createRoom(new Cookie("mysend_device", "forwarded-ip-3"), "PUBLIC", null)
                        .header("X-Forwarded-For", "198.51.100.3"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ROOM_CREATE_RATE_LIMITED"));
    }

    @Test
    void limitsUnknownRoomEntryAttemptsWithoutRevealingAValidRoom() throws Exception {
        Cookie actor = new Cookie("mysend_device", "entry-rate-actor");

        mvc.perform(enterRoom("0000A", null, actor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
        mvc.perform(enterRoom("0001A", null, actor))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
        mvc.perform(enterRoom("0002A", null, actor))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("ROOM_ENTRY_RATE_LIMITED"));
    }

    @Test
    void givesTheSameResponseForUnknownUnenteredAndWrongPasswordRooms() throws Exception {
        Cookie owner = new Cookie("mysend_device", "private-room-owner");
        String code = createdCode(createRoom(owner, "PRIVATE", "correct-password"));

        mvc.perform(get("/api/rooms/{code}", code)
                        .cookie(new Cookie("mysend_device", "unentered-reader")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
        mvc.perform(enterRoom(
                        code,
                        "wrong-password",
                        new Cookie("mysend_device", "wrong-password-reader")
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
        mvc.perform(enterRoom(
                        "9999Z",
                        "wrong-password",
                        new Cookie("mysend_device", "unknown-room-reader")
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_UNAVAILABLE"));
    }

    @Test
    void limitsUploadsBeforeASecondMultipartRequestIsHandled() throws Exception {
        Cookie owner = new Cookie("mysend_device", "upload-rate-owner");
        String code = createdCode(createRoom(owner, "PUBLIC", null));

        mvc.perform(upload(code, owner, "first.txt"))
                .andExpect(status().isCreated());
        mvc.perform(upload(code, owner, "second.txt"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("ROOM_UPLOAD_RATE_LIMITED"));
    }

    private MockHttpServletRequestBuilder createRoom(
            Cookie actor,
            String visibility,
            String password
    ) {
        String passwordJson = password == null ? "null" : "\"" + password + "\"";
        return post("/api/rooms")
                .with(browserRequest())
                .cookie(actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "visibility": "%s",
                          "password": %s,
                          "lifetimeMinutes": 15,
                          "accessLimit": 20
                        }
                        """.formatted(visibility, passwordJson));
    }

    private MockHttpServletRequestBuilder enterRoom(
            String code,
            String password,
            Cookie actor
    ) {
        String passwordJson = password == null ? "null" : "\"" + password + "\"";
        return post("/api/rooms/enter")
                .with(browserRequest())
                .cookie(actor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "accessCode": "%s",
                          "password": %s
                        }
                        """.formatted(code, passwordJson));
    }

    private MockHttpServletRequestBuilder createRoomWithoutCookie() {
        return post("/api/rooms")
                .with(browserRequest())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "visibility": "PUBLIC",
                          "lifetimeMinutes": 15,
                          "accessLimit": 20
                        }
                        """);
    }

    private MockHttpServletRequestBuilder upload(String code, Cookie actor, String name) {
        return multipart("/api/rooms/{code}/files", code)
                .file(new MockMultipartFile(
                        "file",
                        name,
                        MediaType.TEXT_PLAIN_VALUE,
                        "payload".getBytes(java.nio.charset.StandardCharsets.UTF_8)
                ))
                .with(browserRequest())
                .cookie(actor);
    }

    private String createdCode(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("accessCode").asText();
    }

    private static RequestPostProcessor browserRequest() {
        return request -> {
            request.addHeader("Origin", "http://localhost:3000");
            request.addHeader("X-Requested-With", "MySendWeb");
            return request;
        };
    }
}
