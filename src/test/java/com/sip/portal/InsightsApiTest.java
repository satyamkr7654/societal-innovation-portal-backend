package com.sip.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InsightsApiTest extends ApiTestSupport {
    Cookie faculty() throws Exception {
        return login("university@demo.example", "Password123");
    }

    List<String> messages(Cookie session, String caseId) throws Exception {
        String body = mvc.perform(get("/api/notifications").cookie(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return JsonPath.read(body, "$[?(@.problemId == '" + caseId + "')].message");
    }

    int unread(Cookie session) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/notifications/unread-count").cookie(session)).andReturn()
                .getResponse().getContentAsString(), "$.count");
    }

    // ---- notifications ----
    @Test
    void theRightPeopleAreTold() throws Exception {
        Cookie citizen = newCitizen();
        String caseId = JsonPath.read(submit(citizen, GOOD, null).andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(messages(admin(), caseId)).hasSize(1);
        assertThat(messages(citizen, caseId)).isEmpty();

        decide(caseId, "validate", "{}", admin());
        decide(caseId, "assign", "{\"organizationId\":" + approvedUniversity() + "}", admin());
        assertThat(messages(citizen, caseId)).hasSize(2);
        assertThat(messages(faculty(), caseId)).hasSize(1);
        assertThat(messages(ramesh(), caseId)).isEmpty();

        postJson("/api/university/problems/" + caseId + "/decline", "{\"reason\":\"Full\"}", faculty());
        assertThat(messages(admin(), caseId)).hasSize(2);
    }

    @Test
    void aRefusedActionSendsNothing() throws Exception {
        Cookie citizen = newCitizen();
        String caseId = JsonPath.read(submit(citizen, GOOD, null).andReturn().getResponse().getContentAsString(), "$.id");
        decide(caseId, "assign", "{\"organizationId\":" + approvedUniversity() + "}", admin()).andExpect(status().isConflict());
        submit(citizen, GOOD.replace("Ranchi", "Mumbai"), null).andExpect(status().isBadRequest());
        assertThat(messages(citizen, caseId)).isEmpty();
        assertThat(messages(admin(), caseId)).hasSize(1);
    }

    @Test
    void notificationsBelongToOneUser() throws Exception {
        Cookie citizen = newCitizen();
        String caseId = JsonPath.read(submit(citizen, GOOD, null).andReturn().getResponse().getContentAsString(), "$.id");
        decide(caseId, "reject", "{\"reason\":\"Duplicate\"}", admin());
        assertThat(unread(citizen)).isEqualTo(1);
        int id = JsonPath.read(mvc.perform(get("/api/notifications").cookie(citizen)).andReturn().getResponse()
                .getContentAsString(), "$[0].id");
        postJson("/api/notifications/" + id + "/read", "", newCitizen()).andExpect(status().isNoContent());
        postJson("/api/notifications/read-all", "", newCitizen());
        assertThat(unread(citizen)).isEqualTo(1);
        postJson("/api/notifications/" + id + "/read", "", citizen).andExpect(status().isNoContent());
        assertThat(unread(citizen)).isZero();
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void readAllClearsTheCount() throws Exception {
        newProblem();
        assertThat(unread(admin())).isPositive();
        postJson("/api/notifications/read-all", "", admin()).andExpect(status().isNoContent());
        assertThat(unread(admin())).isZero();
    }

    @Test
    void adminsHearAboutNewOrganisations() throws Exception {
        String name = "Uni " + UUID.randomUUID();
        pendingUniversity(name);
        mvc.perform(get("/api/notifications").cookie(admin()))
                .andExpect(jsonPath("$[0].message", containsString(name)))
                .andExpect(jsonPath("$[0].problemId").value(nullValue()));
    }

    // ---- similar ----
    String at(double lat, double lng, String category) {
        return GOOD.replace("23.35", "" + lat).replace("85.31", "" + lng).replace("\"Water\"", "\"" + category + "\"");
    }

    List<String> similar(Cookie session, String category, double lat, double lng) throws Exception {
        String body = mvc.perform(get("/api/problems/similar").param("category", category).param("lat", "" + lat)
                .param("lng", "" + lng).cookie(session)).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        return JsonPath.read(body, "$[*].id");
    }

    @Test
    void findsOpenProblemsOfTheSameKindNearby() throws Exception {
        Cookie c = newCitizen();
        String id = JsonPath.read(submit(c, at(23.5, 85.5, "Health"), null).andReturn().getResponse().getContentAsString(), "$.id");
        assertThat(similar(c, "Health", 23.5, 85.5)).contains(id);
        assertThat(similar(c, "Health", 23.5035, 85.5)).contains(id);       // about 390 m
        assertThat(similar(c, "Health", 23.5055, 85.5)).doesNotContain(id); // about 610 m
        assertThat(similar(c, "Health", 23.504, 85.5045)).doesNotContain(id); // inside the box, outside the circle
        // Someone else is warned, without seeing what was written or which case it is
        mvc.perform(get("/api/problems/similar?category=Health&lat=23.5&lng=85.5").cookie(newCitizen()))
                .andExpect(jsonPath("$[0].title").value("A report waiting for verification"))
                .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.nullValue()));
        assertThat(similar(c, "Energy", 23.5, 85.5)).doesNotContain(id);
        decide(id, "reject", "{\"reason\":\"x\"}", admin());
        assertThat(similar(c, "Health", 23.5, 85.5)).doesNotContain(id);
    }

    @Test
    void similarRefusesNonsense() throws Exception {
        assertThat(similar(ramesh(), "Health", 51.5, 0.1)).isEmpty();
        assertThat(similar(ramesh(), "Health", Double.NaN, 85.5)).isEmpty();
        for (String query : new String[] {"category=Nope&lat=23.5&lng=85.5", "category=Health&lat=abc&lng=85.5", "category=Health"}) {
            mvc.perform(get("/api/problems/similar?" + query).cookie(ramesh())).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/problems/similar?category=Health&lat=23.5&lng=85.5")).andExpect(status().isUnauthorized());
    }

    // ---- public map ----
    @Test
    void publicMapShowsOnlyVerifiedReportsWithoutPersonalDetails() throws Exception {
        String submitted = newProblem();
        String verified = newProblem();
        String rejected = newProblem();
        decide(verified, "validate", "{}", admin());
        decide(rejected, "reject", "{\"reason\":\"x\"}", admin());
        String body = mvc.perform(get("/api/public/problems")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(verified)))
                .andExpect(jsonPath("$[*].id", not(hasItem(submitted))))
                .andExpect(jsonPath("$[*].id", not(hasItem(rejected))))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Ramesh").doesNotContain("citizen").doesNotContain("address")
                .doesNotContain("photo").doesNotContain("description").doesNotContain("23.3512");
        mvc.perform(get("/api/public/other")).andExpect(status().is4xxClientError());
    }

    @Test
    void unverifiedReportsAreOnlySeenByTheirAuthorAndAdmins() throws Exception {
        String caseId = newProblem();
        Cookie[] others = {newCitizen(), login("university@demo.example", "Password123"),
                login("industry@demo.example", "Password123")};
        for (String decision : new String[] {null, "reject"}) {
            if (decision != null) decide(caseId, decision, "{\"reason\":\"x\"}", admin());
            for (Cookie other : others) {
                mvc.perform(get("/api/problems/" + caseId).cookie(other)).andExpect(status().isNotFound());
                mvc.perform(get("/api/problems").cookie(other)).andExpect(jsonPath("$[*].id", not(hasItem(caseId))));
            }
            for (Cookie allowed : new Cookie[] {ramesh(), admin()}) {
                mvc.perform(get("/api/problems/" + caseId).cookie(allowed)).andExpect(status().isOk());
                mvc.perform(get("/api/problems").cookie(allowed)).andExpect(jsonPath("$[*].id", hasItem(caseId)));
            }
        }
        String verified = newProblem();
        decide(verified, "validate", "{}", admin());
        mvc.perform(get("/api/problems/" + verified).cookie(newCitizen())).andExpect(status().isOk());
    }

    // ---- overdue and trend ----
    @Test
    void longWaitsAreFlagged() throws Exception {
        String caseId = newProblem();
        mvc.perform(get("/api/problems/" + caseId).cookie(admin())).andExpect(jsonPath("$.overdue").value(false));
        int before = JsonPath.read(mvc.perform(get("/api/admin/stats").cookie(admin())).andReturn().getResponse()
                .getContentAsString(), "$.overdue");
        tx.executeWithoutResult(s -> jdbc.update("update problems set status_since = ? where case_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(4 * 86_400)), caseId));
        mvc.perform(get("/api/problems/" + caseId).cookie(admin())).andExpect(jsonPath("$.overdue").value(true))
                .andExpect(jsonPath("$.daysInStage").value(anyOf(is(3), is(4))));
        mvc.perform(get("/api/admin/stats").cookie(admin())).andExpect(jsonPath("$.overdue").value(before + 1));
        decide(caseId, "validate", "{}", admin());
        mvc.perform(get("/api/problems/" + caseId).cookie(admin())).andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void solvedProblemsAreNeverOverdue() throws Exception {
        mvc.perform(get("/api/problems/JH-2026-INF-0106").cookie(admin())).andExpect(jsonPath("$.overdue").value(false));
        mvc.perform(get("/api/problems/JH-2026-SAN-0109").cookie(admin())).andExpect(jsonPath("$.overdue").value(false));
    }

    @Test
    void monthlyTrendCoversTwelveMonths() throws Exception {
        newProblem();
        String now = YearMonth.now(ZoneId.of("Asia/Kolkata")).toString();
        mvc.perform(get("/api/admin/stats").cookie(admin())).andExpect(jsonPath("$.monthly.length()").value(12))
                .andExpect(jsonPath("$.monthly[11].month").value(now))
                .andExpect(jsonPath("$.monthly[11].reported", greaterThan(0)));
    }
}
