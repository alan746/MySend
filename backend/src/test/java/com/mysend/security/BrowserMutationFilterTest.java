package com.mysend.security;

import com.mysend.billing.StripeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:securitytest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/security-test-uploads",
        "mysend.web-origins=http://localhost:3000"
})
@AutoConfigureMockMvc
class BrowserMutationFilterTest {

    private static final String CREATE_ROOM = """
            {
              "visibility": "PUBLIC",
              "lifetimeMinutes": 15,
              "accessLimit": 20
            }
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private StripeService stripe;

    @Test
    void allowsMarkedMutationFromConfiguredOrigin() throws Exception {
        mvc.perform(post("/api/rooms")
                        .header("Origin", "http://localhost:3000")
                        .header("X-Requested-With", BrowserMutationFilter.REQUEST_MARKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ROOM))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsMutationWithoutRequestMarker() throws Exception {
        mvc.perform(post("/api/rooms")
                        .header("Origin", "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ROOM))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUEST_MARKER_REQUIRED"));
    }

    @Test
    void rejectsMutationWithoutOrigin() throws Exception {
        mvc.perform(post("/api/rooms")
                        .header("X-Requested-With", BrowserMutationFilter.REQUEST_MARKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ROOM))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WEB_ORIGIN_REQUIRED"));
    }

    @Test
    void rejectsMutationFromAnotherOrigin() throws Exception {
        mvc.perform(post("/api/rooms")
                        .header("Origin", "https://example.net")
                        .header("X-Requested-With", BrowserMutationFilter.REQUEST_MARKER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_ROOM))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsConfiguredCorsPreflight() throws Exception {
        mvc.perform(options("/api/rooms")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-requested-with"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
    }

    @Test
    void leavesStripeWebhookToSignatureVerification() throws Exception {
        String payload = "{\"id\":\"evt_123\",\"type\":\"customer.subscription.deleted\"}";

        mvc.perform(post("/api/billing/webhook")
                        .header("Stripe-Signature", "signed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        verify(stripe).handleWebhook(payload, "signed");
    }
}
