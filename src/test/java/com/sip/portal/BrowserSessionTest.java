package com.sip.portal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

/** Real HTTP with a cookie jar, because the MockMvc csrf() helper replaces the real CSRF store. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:browser;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DB_USERNAME:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "app.jwt-secret=test-secret-test-secret-test-secret-1234",
        "app.cookie-secure=false",
        "app.upload-dir=target/test-uploads",
        "app.frontend-dir=src/test/resources/site",
        "app.demo-data=true"})
class BrowserSessionTest {
    @Value("${local.server.port}") int port;
    final CookieManager jar = new CookieManager();
    final HttpClient client = HttpClient.newBuilder().cookieHandler(jar).build();

    int send(String method, String path, String body, boolean withCsrf) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (withCsrf) request.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
        return client.send(request.build(), BodyHandlers.ofString()).statusCode();
    }

    String cookie(String name) {
        return jar.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue).findFirst().orElse(null);
    }

    @Test
    void loginBrowseAndLogout() throws Exception {
        String credentials = "{\"email\":\"citizen@demo.example\",\"password\":\"Password123\"}";
        assertThat(send("GET", "/api/auth/me", null, false)).isEqualTo(204);
        assertThat(cookie("XSRF-TOKEN")).isNotBlank();
        assertThat(send("POST", "/api/auth/login", credentials, false)).isEqualTo(403);
        assertThat(send("POST", "/api/auth/login", credentials, true)).isEqualTo(200);
        assertThat(send("GET", "/api/problems", null, false)).isEqualTo(200);
        String copied = cookie("sip_session");
        assertThat(send("POST", "/api/auth/logout", "", true)).isEqualTo(204);
        assertThat(send("GET", "/api/problems", null, false)).isEqualTo(401);

        // A token copied before signing out no longer works
        assertThat(get("/api/problems", "sip_session=" + copied).statusCode()).isEqualTo(401);
        // ...and signing in again does
        assertThat(send("POST", "/api/auth/login", credentials, true)).isEqualTo(200);
        assertThat(get("/api/problems", "sip_session=" + cookie("sip_session")).statusCode()).isEqualTo(200);
    }

    HttpResponse<String> get(String path, String cookies) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookies != null) request.header("Cookie", cookies);
        return HttpClient.newHttpClient().send(request.build(), BodyHandlers.ofString());
    }

    @Test
    void servesTheBuiltFrontend() throws Exception {
        for (String page : new String[] {"/", "/admin/review", "/citizen/problems/JH-2026-00001"}) {
            var response = get(page, null);
            assertThat(response.statusCode()).as(page).isEqualTo(200);
            assertThat(response.body()).contains("test site");
            assertThat(response.headers().firstValue("Content-Security-Policy").orElse("")).contains("default-src 'self'");
        }
        assertThat(get("/assets/app.js", null).headers().firstValue("Cache-Control").orElse("")).contains("immutable");
        assertThat(get("/assets/missing.js", null).statusCode()).isEqualTo(404);
        assertThat(get("/missing.png", null).statusCode()).isEqualTo(404);
        // Nothing outside the folder, and the API keeps its own answers
        assertThat(get("/..%2f..%2fapplication.yml", null).statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(get("/%2e%2e/secret.txt", null).statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(get("/api/unknown", null).statusCode()).isEqualTo(401);
        assertThat(get("/api", null).statusCode()).isEqualTo(401);
    }
}
