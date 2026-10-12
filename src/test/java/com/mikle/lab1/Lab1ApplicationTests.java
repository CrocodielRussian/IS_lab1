package com.mikle.lab1;

import com.mikle.lab1.model.UserData;
import com.mikle.lab1.model.User;
import com.mikle.lab1.repository.AuthRepository;
import com.mikle.lab1.repository.DataRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:lab1-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class Lab1ApplicationTests {
    private static final SecretKey KEY = Jwts.SIG.HS256.key().build();
    private static final String PASSWORD = UUID.randomUUID().toString();
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthRepository users;
    @Autowired DataRepository data;
    @Autowired PasswordEncoder encoder;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> Encoders.BASE64.encode(KEY.getEncoded()));
    }

    @BeforeEach
    void prepareData() {
        data.deleteAll();
        users.deleteAll();
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("tester");
        user.setPassword(encoder.encode(PASSWORD));
        users.saveAndFlush(user);
        var entry = new UserData();
        entry.setId(UUID.randomUUID());
        entry.setUserId(user.getId());
        entry.setDescription("Test record");
        data.saveAndFlush(entry);
    }

    @Test
    void registrationCreatesUserWithBcryptAndUsableToken() throws Exception {
        String password = "я".repeat(36);
        String body = mapper.writeValueAsString(java.util.Map.of("username", "new-user", "password", password));
        String response = mvc.perform(post("/auth/create").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("username").value("new-user"))
                .andExpect(jsonPath("password").doesNotExist()).andReturn().getResponse().getContentAsString();
        var user = users.findByUsername("new-user").orElseThrow();
        assertThat(encoder.matches(password, user.getPassword())).isTrue();
        String token = mapper.readTree(response).get("token").asText();
        mvc.perform(get("/api/data").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void registrationRejectsDuplicateUsernameWithoutChangingPassword() throws Exception {
        mvc.perform(post("/auth/create").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", "tester", "password", "different"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("error").value("Username already exists"));
        assertThat(users.count()).isEqualTo(1);
        assertThat(encoder.matches(PASSWORD, users.findByUsername("tester").orElseThrow().getPassword())).isTrue();
    }

    @Test
    void registrationRejectsInvalidInputAndPasswordsOverBcryptByteLimit() throws Exception {
        for (String body : new String[]{"{}", "{", mapper.writeValueAsString(
                java.util.Map.of("username", "new-user", "password", "я".repeat(37)))}) {
            mvc.perform(post("/auth/create").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("error").value("Invalid request"));
        }
        assertThat(users.count()).isEqualTo(1);
    }

    private String login() throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of("username", "tester", "password", PASSWORD));
        String response = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("username").value("tester"))
                .andExpect(jsonPath("password").doesNotExist()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }

    @Test
    void loginUsesBcryptAndGrantsAccessToBothEndpoints() throws Exception {
        var user = users.findByUsername("tester").orElseThrow();
        assertThat(user.getPassword()).startsWith("$2").isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD, user.getPassword())).isTrue();
        String token = login();
        mvc.perform(get("/api/data").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists());
        var entry = data.findAllByUserIdOrderById(user.getId()).get(0);
        mvc.perform(get("/api/description/" + entry.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("id").value(entry.getId().toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/data", "/api/description/00000000-0000-0000-0000-000000000001"})
    void protectedEndpointsRejectMissingAndMalformedTokens(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String token : new String[]{"", "garbage", "a.b.c"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rejectsWrongPasswordUnknownUserAndSqlInjection() throws Exception {
        for (String username : new String[]{"tester", "missing", "' OR '1'='1' --"}) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(java.util.Map.of("username", username, "password", "wrong"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("error").value("Invalid credentials"));
        }
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void rejectsPasswordsOverBcryptByteLimit() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("username", "tester", "password", "я".repeat(40)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredForgedAndIncompleteTokens() throws Exception {
        Instant now = Instant.now();
        String expired = Jwts.builder().issuer("lab1").subject("tester")
                .expiration(Date.from(now.minusSeconds(10))).signWith(KEY).compact();
        String forged = Jwts.builder().issuer("lab1").subject("tester")
                .expiration(Date.from(now.plusSeconds(60))).signWith(Jwts.SIG.HS256.key().build()).compact();
        String noExpiration = Jwts.builder().issuer("lab1").subject("tester").signWith(KEY).compact();
        String missingUser = Jwts.builder().issuer("lab1").subject("missing")
                .expiration(Date.from(now.plusSeconds(60))).signWith(KEY).compact();
        String wrongIssuer = Jwts.builder().issuer("other").subject("tester")
                .expiration(Date.from(now.plusSeconds(60))).signWith(KEY).compact();
        for (String token : new String[]{expired, forged, noExpiration, missingUser, wrongIssuer}) {
            mvc.perform(get("/api/data").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void escapesStoredXssOnBothEndpointsAndHidesOtherUsersData() throws Exception {
        var entry = new UserData();
        entry.setId(UUID.randomUUID());
        entry.setUserId(users.findByUsername("tester").orElseThrow().getId());
        entry.setDescription("<script>alert('x')</script> & \"text\"");
        data.save(entry);
        var other = new UserData();
        other.setId(UUID.randomUUID());
        other.setUserId(UUID.randomUUID());
        other.setDescription("Private other user record");
        data.save(other);
        try {
            String token = login();
            String escaped = "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;text&quot;";
            mvc.perform(get("/api/description/" + entry.getId()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("description").value(escaped));
            mvc.perform(get("/api/data").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].description", org.hamcrest.Matchers.hasItem(escaped)))
                    .andExpect(jsonPath("$[*].description", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(other.getDescription()))));
            for (UUID id : new UUID[]{other.getId(), UUID.randomUUID()}) {
                mvc.perform(get("/api/description/" + id).header("Authorization", "Bearer " + token))
                        .andExpect(status().isNotFound());
            }
        } finally {
            data.deleteById(entry.getId());
            data.deleteById(other.getId());
        }
    }

    @Test
    void createdDataBelongsToAuthenticatedUserAndIsHiddenFromAnotherUser() throws Exception {
        String ownerToken = login();
        String registration = mvc.perform(post("/auth/create").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(java.util.Map.of("username", "other", "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String otherToken = mapper.readTree(registration).get("token").asText();
        String body = mapper.writeValueAsString(java.util.Map.of(
                "name", "<b>Название</b>", "description", "<script>text</script>",
                "userId", users.findByUsername("other").orElseThrow().getId().toString()));
        String created = mvc.perform(post("/api/data/create").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("name").value("&lt;b&gt;Название&lt;/b&gt;"))
                .andExpect(jsonPath("description").value("&lt;script&gt;text&lt;/script&gt;"))
                .andReturn().getResponse().getContentAsString();
        String id = mapper.readTree(created).get("id").asText();
        assertThat(data.findById(UUID.fromString(id)).orElseThrow().getUserId())
                .isEqualTo(users.findByUsername("tester").orElseThrow().getId());
        mvc.perform(get("/api/description/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(content().json(created));
        mvc.perform(get("/api/data").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.hasItem(id)));
        mvc.perform(get("/api/data").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/description/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void createDataRequiresAuthenticationAndValidFields() throws Exception {
        long count = data.count();
        String valid = "{\"name\":\"Name\",\"description\":\"Description\"}";
        mvc.perform(post("/api/data/create").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/data/create").header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        String token = login();
        for (String body : new String[]{"{}", "{", "{\"name\":\"Name\"}",
                "{\"name\":\" \",\"description\":\"Text\"}",
                "{\"name\":\"Name\",\"description\":\" \"}",
                mapper.writeValueAsString(java.util.Map.of("name", "x".repeat(256), "description", "Text")),
                mapper.writeValueAsString(java.util.Map.of("name", "Name", "description", "x".repeat(256)))}) {
            mvc.perform(post("/api/data/create").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(data.count()).isEqualTo(count);
    }


    @Test
    void invalidInputReturnsBadRequestWithoutEchoingInput() throws Exception {
        for (String body : new String[]{"{}", "{\"username\":\"tester\"}", "{", "{\"username\":\" \",\"password\":\"x\"}"}) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("error").value("Invalid request"));
        }
        mvc.perform(get("/api/description/not-a-uuid").header("Authorization", "Bearer " + login()))
                .andExpect(status().isBadRequest());
        assertThat(new com.mikle.lab1.dto.AuthResponse("jwt", UUID.randomUUID(), "<b>name</b>").username())
                .isEqualTo("&lt;b&gt;name&lt;/b&gt;");
    }
}
