package com.example.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.taskmanager.dto.LoginRequest;
import com.example.taskmanager.dto.RegisterRequest;
import com.example.taskmanager.entity.User;
import com.example.taskmanager.repository.TaskRepository;
import com.example.taskmanager.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskManagerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndRejectsDuplicate() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("First User", "FIRST@example.com", "StrongPassword123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("first@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        User saved = userRepository.findByEmail("first@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("StrongPassword123", saved.getPassword())).isTrue();
        assertThat(saved.getPassword()).isNotEqualTo("StrongPassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Another User", "first@example.com", "OtherPassword456")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginReturnsBearerTokenAndMeRequiresValidToken() throws Exception {
        register("Login User", "login@example.com", "StrongPassword123");
        String token = login("login@example.com", "StrongPassword123");

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Login User"));
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login@example.com", "WrongPassword123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void protectedRoutesRejectMissingAndInvalidTokens() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/tasks").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        String expiredToken = Jwts.builder()
                .subject("expired@example.com")
                .claim("uid", 1L)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor("test-only-signing-secret-with-more-than-32-bytes".getBytes()))
                .compact();
        mockMvc.perform(get("/api/tasks").header("Authorization", bearer(expiredToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void taskCrudIsOwnedPaginatedFilteredAndStatisticsAreScoped() throws Exception {
        register("Owner", "owner@example.com", "StrongPassword123");
        register("Other", "other@example.com", "DifferentPassword456");
        String ownerToken = login("owner@example.com", "StrongPassword123");
        String otherToken = login("other@example.com", "DifferentPassword456");

        MvcResult createdResult = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Owned urgent task", "Search me", "HIGH", "PENDING", LocalDate.now().minusDays(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();
        long taskId = objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Other person's task", "Private", "LOW", "COMPLETED", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks?search=urgent&priority=HIGH&page=0&size=1&sortBy=createdAt&direction=DESC")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Owned urgent task"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));

        mockMvc.perform(get("/api/tasks/{id}", taskId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/{id}", taskId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Owned urgent task"));

        mockMvc.perform(put("/api/tasks/{id}", taskId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(taskJson("Updated task", "Updated details", "MEDIUM", "IN_PROGRESS", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(patch("/api/tasks/{id}/status", taskId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/tasks/statistics").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andExpect(jsonPath("$.completedTasks").value(1))
                .andExpect(jsonPath("$.overdueTasks").value(0))
                .andExpect(jsonPath("$.completionPercentage").value(100.0));

        mockMvc.perform(delete("/api/tasks/{id}", taskId).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/tasks/{id}", taskId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/tasks/{id}", taskId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidTaskAndRegistrationPayloads() throws Exception {
        register("Validation User", "validation@example.com", "StrongPassword123");
        String token = login("validation@example.com", "StrongPassword123");

        mockMvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \",\"description\":\"\",\"priority\":\"URGENT\",\"status\":\"PENDING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Bad", "invalid-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").exists());

        mockMvc.perform(get("/api/tasks?page=-1").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks?sortBy=user.password").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks?priority=URGENT").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroTaskStatisticsAreZero() throws Exception {
        register("Empty User", "empty@example.com", "StrongPassword123");
        String token = login("empty@example.com", "StrongPassword123");
        mockMvc.perform(get("/api/tasks/statistics").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.completionPercentage").value(0.0));
    }

    @Test
    void healthIsPublicAndFrontendCorsPreflightIsAllowed() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(options("/api/tasks")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    private void register(String name, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson(name, email, password))).andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String registerJson(String name, String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new RegisterRequest(name, email, password));
    }

    private String loginJson(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginRequest(email, password));
    }

    private String taskJson(String title, String description, String priority, String status, LocalDate dueDate)
            throws Exception {
        return "{\"title\":" + objectMapper.writeValueAsString(title)
                + ",\"description\":" + objectMapper.writeValueAsString(description)
                + ",\"priority\":" + objectMapper.writeValueAsString(priority)
                + ",\"status\":" + objectMapper.writeValueAsString(status)
                + ",\"dueDate\":" + (dueDate == null ? "null" : objectMapper.writeValueAsString(dueDate.toString()))
                + "}";
    }
}
