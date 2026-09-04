package com.mysend.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mysendtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/test-uploads",
        "mysend.development-code-enabled=true"
})
@AutoConfigureMockMvc
class GuestRoomFlowTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void guestCreatesARoomWithoutSigningIn() throws Exception {
        mvc.perform(post("/api/rooms")
                        .with(browserRequest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visibility": "PUBLIC",
                                  "lifetimeMinutes": 15,
                                  "accessLimit": 20
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan").value("GUEST"))
                .andExpect(jsonPath("$.accessCode").value(matchesPattern("\\d{4}[A-HJ-NP-Z]")))
                .andExpect(jsonPath("$.clipboardLimit").value(2000))
                .andExpect(jsonPath("$.owner").value(true));
    }

    @Test
    void guestCannotOpenTheMyShareRoomsIndex() throws Exception {
        mvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCOUNT_REQUIRED"));
    }

    @Test
    void enteringAnotherRoomKeepsExistingRoomAccess() throws Exception {
        String firstCode = createRoom("first-owner");
        String secondCode = createRoom("second-owner");

        Cookie firstAccess = enterRoom(firstCode.toLowerCase(Locale.ROOT));
        Cookie secondAccess = enterRoom(secondCode);

        mvc.perform(get("/api/rooms/{code}", firstCode)
                        .cookie(firstAccess, secondAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCode").value(firstCode));
        mvc.perform(get("/api/rooms/{code}", secondCode)
                        .cookie(firstAccess, secondAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCode").value(secondCode));

        mvc.perform(get("/api/rooms/{code}/files", firstCode)
                        .cookie(firstAccess, secondAccess))
                .andExpect(status().isOk());
        mvc.perform(get("/api/rooms/{code}/files", secondCode)
                        .cookie(firstAccess, secondAccess))
                .andExpect(status().isOk());
    }

    @Test
    void authorizedVisitorsCanCheckWhetherRoomStateChanged() throws Exception {
        String deviceToken = "revision-owner";
        String accessCode = createRoom(deviceToken);
        Cookie ownerCookie = new Cookie("mysend_device", deviceToken);

        mvc.perform(get("/api/rooms/{code}/revision", accessCode)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.clipboardText").doesNotExist());

        enterRoom(accessCode);

        mvc.perform(get("/api/rooms/{code}/revision", accessCode)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.available").value(true));

        mvc.perform(delete("/api/rooms/{code}", accessCode)
                        .with(browserRequest())
                        .cookie(ownerCookie))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/rooms/{code}/revision", accessCode)
                        .cookie(ownerCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void roomRevisionRequiresExistingRoomAccess() throws Exception {
        String accessCode = createRoom("revision-private-owner");

        mvc.perform(get("/api/rooms/{code}/revision", accessCode))
                .andExpect(status().isNotFound());
    }

    @Test
    void visitorCanSeeThatAnEnteredRoomExpiredNaturally() throws Exception {
        String accessCode = createRoom("natural-expiry-owner");
        Cookie access = enterRoom(accessCode);
        long roomExpiry = jdbc.sql("select expires_at_ms from rooms where access_code = :accessCode")
                .param("accessCode", accessCode)
                .query(Long.class)
                .single();
        long accessExpiry = jdbc.sql("""
                        select expires_at_ms from room_access_tokens
                        where room_id = (select id from rooms where access_code = :accessCode)
                        """)
                .param("accessCode", accessCode)
                .query(Long.class)
                .single();
        assertThat(accessExpiry - roomExpiry).isEqualTo(RoomAccessService.REVISION_GRACE.toMillis());
        assertThat(access.getMaxAge()).isGreaterThan(24 * 60 * 60);

        jdbc.sql("update rooms set expires_at_ms = 0 where access_code = :accessCode")
                .param("accessCode", accessCode)
                .update();

        mvc.perform(get("/api/rooms/{code}", accessCode)
                        .cookie(access))
                .andExpect(status().isGone());
        mvc.perform(get("/api/rooms/{code}/revision", accessCode)
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }

    private String createRoom(String deviceToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/rooms")
                        .with(browserRequest())
                        .cookie(new Cookie("mysend_device", deviceToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "visibility": "PUBLIC",
                                  "lifetimeMinutes": 15,
                                  "accessLimit": 20
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.path("accessCode").asText();
    }

    private Cookie enterRoom(String accessCode) throws Exception {
        MvcResult result = mvc.perform(post("/api/rooms/enter")
                        .with(browserRequest())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accessCode": "%s"
                                }
                                """.formatted(accessCode)))
                .andExpect(status().isOk())
                .andReturn();
        String cookieName = RoomAccessCookie.name(accessCode);
        Cookie cookie = result.getResponse().getCookie(cookieName);
        if (cookie == null) {
            throw new AssertionError("Missing room access cookie " + cookieName);
        }
        return cookie;
    }

    private static RequestPostProcessor browserRequest() {
        return request -> {
            request.addHeader("Origin", "http://localhost:3000");
            request.addHeader("X-Requested-With", "MySendWeb");
            return request;
        };
    }
}
