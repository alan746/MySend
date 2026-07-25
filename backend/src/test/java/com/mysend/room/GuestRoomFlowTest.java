package com.mysend.room;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
}
