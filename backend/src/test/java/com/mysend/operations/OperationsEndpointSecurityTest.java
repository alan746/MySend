package com.mysend.operations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:operations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "mysend.upload-directory=target/operations-test-uploads",
        "mysend.operations.metrics-token=operations-test-token"
})
@AutoConfigureMockMvc
@AutoConfigureObservability
class OperationsEndpointSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rejectsOperationsWithoutTheBearerToken() throws Exception {
        mvc.perform(get("/actuator/operations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void exposesOperationalStatusWithTheBearerToken() throws Exception {
        mvc.perform(get("/actuator/operations")
                        .header("Authorization", "Bearer operations-test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.liveStorageBytes").isNumber())
                .andExpect(jsonPath("$.pendingDeletions").isNumber())
                .andExpect(jsonPath("$.cleanupLastSuccessAgeSeconds").isNumber());
    }

    @Test
    void exposesPrometheusOnlyWithTheBearerToken() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/actuator/prometheus")
                        .header("Authorization", "Bearer operations-test-token"))
                .andExpect(status().isOk());
    }
}
