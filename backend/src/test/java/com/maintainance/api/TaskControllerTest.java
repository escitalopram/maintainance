package com.maintainance.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listTasks_reconcilesPersistedStateInTransaction() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Water plants",
                                  "rules": {
                                    "intervalType": "EVERY_N_DAYS",
                                    "intervalN": 1,
                                    "anchorMode": "EPOCH",
                                    "catchUp": true,
                                    "useBacklogMultiplier": true,
                                    "allowedWeekdays": [],
                                    "durationMinutes": 15,
                                    "importanceWeight": 1,
                                    "graceEarlyDays": 0,
                                    "graceLateDays": 0,
                                    "sigmaEarly": 3,
                                    "sigmaLate": 3,
                                    "backlogP": 0.6
                                  }
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Water plants"));
    }
}
