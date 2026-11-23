package com.sip.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sip.portal.problem.*;
import com.sip.portal.user.*;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs on in-memory H2, or on the MySQL database named by TEST_DB_URL. */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:portal;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DB_USERNAME:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "app.jwt-secret=test-secret-test-secret-test-secret-1234",
        "app.cookie-secure=true",
        "app.upload-dir=target/test-uploads",
        "app.demo-data=true"})
@AutoConfigureMockMvc
abstract class ApiTestSupport {
    static final String RAMESH = "citizen.ramesh@gmail.com";
    static final String GOOD = """
            {"title":"Broken water pipe near school","description":"Water has been leaking here for many days.",
             "category":"Water","district":"Ranchi","location":{"address":"Harmu","lat":23.35,"lng":85.31}}""";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProblemRepository problems;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    String ip;

    @BeforeEach
    void freshAddress() { // keeps the login throttle of one test from affecting another
        ip = UUID.randomUUID().toString();
    }

    // ---- helpers ----
    ResultActions postJson(String url, String body, Cookie... cookies) throws Exception {
        var request = post(url).with(csrf()).with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(cookies.length == 0 ? request : request.cookie(cookies));
    }

    ResultActions tryLogin(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    Cookie login(String email, String password) throws Exception {
        return tryLogin(email, password).andExpect(status().isOk()).andReturn().getResponse().getCookie("sip_session");
    }

    Cookie ramesh() throws Exception {
        return login(RAMESH, "Password123");
    }

    ResultActions register(String email, String role, String extra) throws Exception {
        return postJson("/api/auth/register",
                "{\"name\":\"Asha Devi\",\"email\":\"%s\",\"password\":\"Secret123\",\"role\":\"%s\"%s}"
                        .formatted(email, role, extra));
    }

    Cookie newCitizen() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email, "citizen", ",\"phone\":\"9876543210\"").andExpect(status().isCreated());
        return login(email, "Secret123");
    }

    ResultActions submit(Cookie session, String json, MockMultipartFile photo) throws Exception {
        var request = multipart("/api/problems")
                .file(new MockMultipartFile("problem", "", "application/json", json.getBytes()));
        if (photo != null) request.file(photo);
        return mvc.perform(request.cookie(session).with(csrf()));
    }

    String newSolvedProblem(String email) {
        return tx.execute(s -> {
            Problem p = problems.save(new Problem(users.findByEmail(email).orElseThrow(), "Title long enough",
                    "Description", Category.WATER, "Ranchi", "Address", 23.35, 85.31, null, Instant.now()));
            p.assignCaseId(2026);
            p.moveTo(ProblemStatus.SOLVED, "Test", "Solved", Instant.now());
            return p.getCaseId();
        });
    }

    static byte[] png(int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    Cookie admin() throws Exception {
        return login("admin.dept@jharkhand.gov.in", "Password123");
    }

    ResultActions decide(String caseId, String action, String body, Cookie session) throws Exception {
        return postJson("/api/admin/problems/" + caseId + "/" + action, body, session);
    }

    String newProblem() throws Exception {
        String body = submit(ramesh(), GOOD, null).andReturn().getResponse().getContentAsString();
        return body.replaceAll("(?s)^\\{\"id\":\"([^\"]+)\".*", "$1");
    }

    long approvedUniversity() {
        return users.findByEmail("faculty@bitmesra.ac.in").orElseThrow().getOrganization().getId();
    }

    String pendingUniversity(String organization) throws Exception {
        String email = UUID.randomUUID() + "@uni.example.com";
        register(email, "university", ",\"organizationName\":\"" + organization + "\"")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.approved").value(false));
        return users.findByEmail(email).orElseThrow().getId();
    }
}
