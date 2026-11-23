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
class PortalApiTest {
    private static final String RAMESH = "citizen.ramesh@gmail.com";
    private static final String GOOD = """
            {"title":"Broken water pipe near school","description":"Water has been leaking here for many days.",
             "category":"Water","district":"Ranchi","location":{"address":"Harmu","lat":23.35,"lng":85.31}}""";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ProblemRepository problems;
    @Autowired PasswordEncoder encoder;
    @Autowired TransactionTemplate tx;
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

    // ---- accounts ----
    @Test
    void loginSetsAProtectedCookieAndHidesTheHash() throws Exception {
        tryLogin(RAMESH, "Password123").andExpect(status().isOk())
                .andExpect(cookie().httpOnly("sip_session", true)).andExpect(cookie().secure("sip_session", true))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.role").value("citizen"))
                .andExpect(content().string(not(containsString("assword"))));
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        for (String email : new String[] {RAMESH, "nobody@example.com"}) {
            tryLogin(email, "wrong").andExpect(status().isUnauthorized())
                    .andExpect(cookie().doesNotExist("sip_session"))
                    .andExpect(jsonPath("$.message").value("Incorrect email or password."));
        }
    }

    @Test
    void repeatedFailuresAreThrottled() throws Exception {
        for (int i = 0; i < 5; i++) tryLogin(RAMESH, "wrong").andExpect(status().isUnauthorized());
        tryLogin(RAMESH, "Password123").andExpect(status().isTooManyRequests());
    }

    @Test
    void registrationKeepsTheChosenRoleAndHashesThePassword() throws Exception {
        String email = "Badminton.Admin" + UUID.randomUUID() + "@Gmail.com";
        register(email, "citizen", ",\"phone\":\"9876543210\"").andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("citizen"));
        User saved = users.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").doesNotContain("Secret123");
        register(email.toUpperCase(), "citizen", ",\"phone\":\"9876543210\"").andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationsAreRefused() throws Exception {
        register("x1@example.com", "admin", "").andExpect(status().isBadRequest());
        register("x2@example.com", "ADMIN", "").andExpect(status().isBadRequest());
        register("x3@example.com", "citizen", "").andExpect(status().isBadRequest());
        register("x4@example.com", "university", "").andExpect(status().isBadRequest());
        register("bad", "citizen", ",\"phone\":\"9876543210\"").andExpect(status().isBadRequest());
        postJson("/api/auth/register", "{nonsense").andExpect(status().isBadRequest());
        assertThat(users.existsByEmail("x1@example.com")).isFalse();
    }

    @Test
    void organisationsAreSharedNotDuplicated() throws Exception {
        String name = "Uni " + UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            register(UUID.randomUUID() + "@example.com", "university", ",\"organizationName\":\"" + name + "\"")
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.organizationName").value(name));
        }
    }

    @Test
    void sessionLifecycle() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(ramesh())).andExpect(jsonPath("$.email").value(RAMESH));
        postJson("/api/auth/logout", "").andExpect(cookie().maxAge("sip_session", 0));
    }

    @Test
    void forgedAndExpiredTokensAreIgnored() throws Exception {
        String id = users.findByEmail("admin.dept@jharkhand.gov.in").orElseThrow().getId();
        Instant now = Instant.now();
        String wrongKey = token("another-secret-another-secret-another-12", id, now, now.plusSeconds(600));
        String expired = token("test-secret-test-secret-test-secret-1234", id, now.minusSeconds(7200),
                now.minusSeconds(3600));
        for (String value : new String[] {"x", "", wrongKey, expired, "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0."}) {
            mvc.perform(get("/api/problems").cookie(new Cookie("sip_session", value)))
                    .andExpect(status().isUnauthorized());
        }
    }

    static String token(String secret, String subject, Instant from, Instant to) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(), "HmacSHA256")));
        var claims = JwtClaimsSet.builder().subject(subject).issuedAt(from).expiresAt(to).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void csrfTokenIsRequired() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + RAMESH + "\",\"password\":\"Password123\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/problems/JH-2026-INF-0110/feedback").cookie(ramesh()).contentType("application/json")
                .content("{\"rating\":1}")).andExpect(status().isForbidden());
    }

    // ---- problems ----
    @Test
    void visitorsSeeNothing() throws Exception {
        for (String url : new String[] {"/api/problems", "/api/problems/JH-2026-WTR-0101", "/api/photos/x.jpg"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void listNeverExposesContactDetails() throws Exception {
        mvc.perform(get("/api/problems").cookie(newCitizen())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(10)))
                .andExpect(content().string(not(containsString("9835123450"))))
                .andExpect(content().string(not(containsString("@"))));
    }

    @Test
    void sampleDataMatchesTheOriginalCaseIds() throws Exception {
        mvc.perform(get("/api/problems/JH-2026-WTR-0101").cookie(ramesh()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.submittedAt").value("2026-09-14T09:30:00Z"))
                .andExpect(jsonPath("$.statusHistory[*].stage", contains("SUBMITTED", "VALIDATED", "ASSIGNED")))
                .andExpect(jsonPath("$.industrySupport.amount").value(250000));
        mvc.perform(get("/api/problems/NOPE").cookie(ramesh())).andExpect(status().isNotFound());
    }

    @Test
    void ownerComesFromTheSession() throws Exception {
        String myId = users.findByEmail(RAMESH).orElseThrow().getId();
        String forged = GOOD.replaceFirst("\\{", "{\"citizen\":{\"id\":\"someone\"},\"status\":\"SOLVED\",");
        submit(ramesh(), forged, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.citizen.id").value(myId)).andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.id", matchesPattern("JH-\\d{4}-WTR-\\d{4,}")))
                .andExpect(jsonPath("$.photoUrl").value(nullValue()));
    }

    @Test
    void onlyCitizensCanSubmit() throws Exception {
        for (String email : new String[] {"admin.dept@jharkhand.gov.in", "faculty@bitmesra.ac.in", "csr@tatasteel.com"}) {
            submit(login(email, "Password123"), GOOD, null).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @CsvSource({"Broken water pipe near school,'             '", "Water has been leaking here for many days.,'                                '",
            "\"Harmu\",\"       \"", "\"Water\",\"Nonsense\"", "\"Ranchi\",\"Mumbai\"", "\"Ranchi\",\"Garhwa\"",
            "23.35,28.61", "23.35,null"})
    void invalidReportsAreRefused(String from, String to) throws Exception {
        String json = GOOD.replace(from, to);
        assertThat(json).isNotEqualTo(GOOD);
        submit(ramesh(), json, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    // ---- photos ----
    @Test
    void photosAreReencodedAndOnlyServedToSignedInUsers() throws Exception {
        Cookie session = ramesh();
        String body = submit(session, GOOD, new MockMultipartFile("photo", "../../evil.png", "image/png", png(40, 30)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String url = body.replaceAll("(?s).*\"photoUrl\":\"([^\"]+)\".*", "$1");
        assertThat(url).matches("/api/photos/[0-9a-f-]{36}\\.jpg");
        byte[] stored = mvc.perform(get(url).cookie(session)).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(stored[0]).isEqualTo((byte) 0xFF);
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @Test
    void fakeAndHugePhotosAreRefused() throws Exception {
        byte[] script = "<script>alert(1)</script>".getBytes();
        submit(ramesh(), GOOD, new MockMultipartFile("photo", "a.jpg", "image/jpeg", script))
                .andExpect(status().isBadRequest());
        submit(ramesh(), GOOD, new MockMultipartFile("photo", "a.png", "image/png", png(6000, 5000)))
                .andExpect(status().isBadRequest());
        var gif = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB), "gif", gif);
        submit(ramesh(), GOOD, new MockMultipartFile("photo", "a.jpg", "image/jpeg", gif.toByteArray()))
                .andExpect(status().isBadRequest());
    }

    // ---- feedback ----
    @Test
    void ownerReviewsASolvedProblemOnce() throws Exception {
        String url = "/api/problems/" + newSolvedProblem(RAMESH) + "/feedback";
        postJson(url, "{\"rating\":6}", ramesh()).andExpect(status().isBadRequest());
        postJson(url, "{\"verifiedOnGround\":true}", ramesh()).andExpect(status().isBadRequest());
        postJson(url, "{\"rating\":4,\"remarks\":\"  \",\"verifiedOnGround\":false}", ramesh()).andExpect(status().isOk())
                .andExpect(jsonPath("$.feedback.rating").value(4))
                .andExpect(jsonPath("$.feedback.remarks").value(nullValue()))
                .andExpect(jsonPath("$.feedback.verifiedOnGround").value(false));
        postJson(url, "{\"rating\":5,\"verifiedOnGround\":false}", ramesh()).andExpect(status().isConflict());
    }

    @Test
    void othersCannotReview() throws Exception {
        String url = "/api/problems/" + newSolvedProblem(RAMESH) + "/feedback";
        String review = "{\"rating\":1,\"verifiedOnGround\":false}";
        postJson(url, review, newCitizen()).andExpect(status().isForbidden());
        postJson(url, review, login("admin.dept@jharkhand.gov.in", "Password123")).andExpect(status().isForbidden());
        postJson(url, review).andExpect(status().isUnauthorized());
        postJson("/api/problems/JH-2026-WTR-0101/feedback", review, ramesh()).andExpect(status().isConflict());
    }

    @Test
    void simultaneousReviewsSaveOnlyOne() throws Exception {
        String url = "/api/problems/" + newSolvedProblem(RAMESH) + "/feedback";
        Cookie session = ramesh();
        var barrier = new CyclicBarrier(4);
        Callable<Integer> attempt = () -> {
            barrier.await();
            return postJson(url, "{\"rating\":3,\"verifiedOnGround\":false}", session).andReturn().getResponse().getStatus();
        };
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = IntStream.range(0, 4).mapToObj(i -> pool.submit(attempt)).toList();
            int saved = 0;
            for (var future : futures) {
                int code = future.get();
                assertThat(code).isIn(200, 409);
                if (code == 200) saved++;
            }
            assertThat(saved).isEqualTo(1);
        }
    }
}
