package com.mysend.room;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Locale;

import static org.hamcrest.Matchers.matchesPattern;
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

    @Test
    void guestCreatesARoomWithoutSigningIn() throws Exception {
        mvc.perform(post("/api/rooms")
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

    private String createRoom(String deviceToken) throws Exception {
        MvcResult result = mvc.perform(post("/api/rooms")
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
}
