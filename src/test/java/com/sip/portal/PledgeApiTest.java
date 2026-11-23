package com.sip.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class PledgeApiTest extends ApiTestSupport {
    private static final String LONG = "A clear explanation that is comfortably longer than fifty characters.";

    Cookie faculty() throws Exception {
        return login("university@demo.example", "Password123");
    }

    Cookie company() throws Exception {
        String email = UUID.randomUUID() + "@corp.example.com";
        register(email, "industry", ",\"organizationName\":\"Corp " + UUID.randomUUID() + "\"");
        postJson("/api/admin/accounts/" + users.findByEmail(email).orElseThrow().getId() + "/approve", "", admin());
        return login(email, "Secret123");
    }

    ResultActions uni(String caseId, String action, String body) throws Exception {
        return postJson("/api/university/problems/" + caseId + "/" + action, body, faculty());
    }

    /** In progress with a submitted ₹1,00,000 proposal. */
    String proposed() throws Exception {
        String caseId = newProblem();
        decide(caseId, "validate", "{}", admin());
        decide(caseId, "assign", "{\"organizationId\":" + approvedUniversity() + "}", admin());
        uni(caseId, "accept", "{\"department\":\"Civil\",\"facultyLead\":\"Dr Sen\",\"studentsCount\":4}");
        uni(caseId, "proposal", """
                {"summary":"%s","approach":"%s","durationWeeks":8,"items":[{"label":"Pump","amount":100000}]}"""
                .formatted(LONG, LONG)).andExpect(status().isNoContent());
        return caseId;
    }

    String open() throws Exception {
        String caseId = proposed();
        postJson("/api/admin/problems/" + caseId + "/proposal/approve", "{}", admin()).andExpect(status().isNoContent());
        return caseId;
    }

    ResultActions offer(String caseId, String type, String amount, Cookie session) throws Exception {
        return postJson("/api/industry/problems/" + caseId + "/pledges",
                "{\"type\":\"%s\",\"amount\":%s,\"description\":\"Help\"}".formatted(type, amount), session);
    }

    <T> T project(String caseId, Cookie session, String path) throws Exception {
        return JsonPath.read(mvc.perform(get("/api/problems/" + caseId + "/project").cookie(session)).andReturn()
                .getResponse().getContentAsString(), path);
    }

    int lastPledge(String caseId) throws Exception {
        List<Integer> ids = project(caseId, faculty(), "$.pledges[*].id");
        return ids.getLast();
    }

    @Test
    void onlyApprovedProposalsAreOpen() throws Exception {
        String caseId = proposed();
        Cookie corp = company();
        assertThat((Object) project(caseId, corp, "$.proposal")).isNull();
        mvc.perform(get("/api/industry/proposals").cookie(corp)).andExpect(jsonPath("$[*].problem.id", not(hasItem(caseId))));
        offer(caseId, "FUNDING", "5000", corp).andExpect(status().isConflict());
        offer("JH-2026-EDU-0105", "MENTORSHIP", "null", corp).andExpect(status().isConflict());

        postJson("/api/admin/problems/" + caseId + "/proposal/approve", "{}", admin());
        mvc.perform(get("/api/industry/proposals").cookie(corp)).andExpect(jsonPath("$[*].problem.id", hasItem(caseId)));
        assertThat((Integer) project(caseId, corp, "$.proposal.total")).isEqualTo(100000);
        assertThat((Object) project(caseId, ramesh(), "$.proposal")).isNull();
    }

    @Test
    void acceptedFundingReducesWhatIsNeeded() throws Exception {
        String caseId = open();
        offer(caseId, "FUNDING", "60000", company()).andExpect(status().isNoContent());
        assertThat((Integer) project(caseId, faculty(), "$.funding.remaining")).isEqualTo(100000);
        uni(caseId, "pledges/" + lastPledge(caseId) + "/accept", "").andExpect(status().isNoContent());
        assertThat((Integer) project(caseId, faculty(), "$.funding.remaining")).isEqualTo(40000);
        mvc.perform(get("/api/problems/" + caseId).cookie(ramesh()))
                .andExpect(jsonPath("$.supporters.length()").value(1))
                .andExpect(jsonPath("$.supporters[0].amount").value(60000))
                .andExpect(jsonPath("$.statusHistory[-1:].remarks", contains(startsWith("Support accepted from Corp"))));
        offer(caseId, "FUNDING", "40001", company()).andExpect(status().isConflict());
        offer(caseId, "FUNDING", "40000", company()).andExpect(status().isNoContent());
    }

    @Test
    void anAcceptedOfferCannotBeChanged() throws Exception {
        String caseId = open();
        Cookie corp = company();
        offer(caseId, "EQUIPMENT", "null", corp);
        int id = lastPledge(caseId);
        uni(caseId, "pledges/" + id + "/accept", "").andExpect(status().isNoContent());
        uni(caseId, "pledges/" + id + "/accept", "").andExpect(status().isConflict());
        uni(caseId, "pledges/" + id + "/decline", "{\"reason\":\"x\"}").andExpect(status().isConflict());
        withdraw(caseId, id, corp).andExpect(status().isConflict());
    }

    ResultActions withdraw(String caseId, int id, Cookie session) throws Exception {
        return postJson("/api/industry/problems/" + caseId + "/pledges/" + id + "/withdraw", "", session);
    }

    @Test
    void offersOfEquipmentAndMentorshipHaveNoAmount() throws Exception {
        String caseId = open();
        offer(caseId, "MENTORSHIP", "5000", company()).andExpect(status().isNoContent());
        List<Object> amounts = project(caseId, faculty(), "$.pledges[*].amount");
        assertThat(amounts).containsOnlyNulls();
    }

    @Test
    void invalidOffersAreRefused() throws Exception {
        String caseId = open();
        Cookie corp = company();
        for (String amount : new String[] {"null", "0", "-5", "99999999999"}) {
            offer(caseId, "FUNDING", amount, corp).andExpect(status().isBadRequest());
        }
        offer(caseId, "BRIBE", "5", corp).andExpect(status().isBadRequest());
        postJson("/api/industry/problems/" + caseId + "/pledges", "{\"type\":\"EQUIPMENT\",\"description\":\" \"}", corp)
                .andExpect(status().isBadRequest());
        offer("NOPE", "FUNDING", "5", corp).andExpect(status().isNotFound());
    }

    @Test
    void oneOpenOfferPerCompany() throws Exception {
        String caseId = open();
        Cookie corp = company();
        offer(caseId, "FUNDING", "100", corp).andExpect(status().isNoContent());
        offer(caseId, "EQUIPMENT", "null", corp).andExpect(status().isConflict());
        withdraw(caseId, lastPledge(caseId), company()).andExpect(status().isForbidden());
        withdraw(caseId, lastPledge(caseId), corp).andExpect(status().isNoContent());
        uni(caseId, "pledges/" + lastPledge(caseId) + "/accept", "").andExpect(status().isConflict());
        offer(caseId, "EQUIPMENT", "null", corp).andExpect(status().isNoContent());
        uni(caseId, "pledges/" + lastPledge(caseId) + "/decline", "{\"reason\":\"\"}").andExpect(status().isBadRequest());
        uni(caseId, "pledges/" + lastPledge(caseId) + "/decline", "{\"reason\":\"Not suitable\"}").andExpect(status().isNoContent());
        mvc.perform(get("/api/industry/pledges").cookie(corp)).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pledge.responseNote").value("Not suitable"))
                .andExpect(jsonPath("$[1].pledge.status").value("WITHDRAWN"));
    }

    @Test
    void offersArePrivate() throws Exception {
        String caseId = open();
        Cookie corp = company();
        offer(caseId, "FUNDING", "100", corp);
        for (Cookie other : new Cookie[] {company(), ramesh()}) {
            assertThat((List<?>) project(caseId, other, "$.pledges")).isEmpty();
        }
        assertThat((List<?>) project(caseId, corp, "$.pledges")).hasSize(1);
        assertThat((List<?>) project(caseId, admin(), "$.pledges")).hasSize(1);
        assertThat((Object) project(caseId, ramesh(), "$.funding")).isNull();
        mvc.perform(get("/api/problems/" + caseId).cookie(ramesh())).andExpect(jsonPath("$.supporters.length()").value(0));
    }

    @Test
    void rolesAreEnforced() throws Exception {
        String caseId = open();
        offer(caseId, "FUNDING", "100", company());
        int id = lastPledge(caseId);
        for (Cookie other : new Cookie[] {ramesh(), faculty(), admin()}) {
            offer(caseId, "FUNDING", "100", other).andExpect(status().isForbidden());
            withdraw(caseId, id, other).andExpect(status().isForbidden());
            mvc.perform(get("/api/industry/proposals").cookie(other)).andExpect(status().isForbidden());
        }
        String uniId = pendingUniversity("Rival " + UUID.randomUUID());
        postJson("/api/admin/accounts/" + uniId + "/approve", "", admin());
        Cookie rival = login(users.findById(uniId).orElseThrow().getEmail(), "Secret123");
        for (Cookie other : new Cookie[] {rival, company(), admin(), ramesh()}) {
            postJson("/api/university/problems/" + caseId + "/pledges/" + id + "/accept", "", other).andExpect(status().isForbidden());
            postJson("/api/university/problems/" + caseId + "/pledges/" + id + "/decline", "{\"reason\":\"x\"}", other)
                    .andExpect(status().isForbidden());
        }
        // An id from another problem is not found here
        uni(open(), "pledges/" + id + "/accept", "").andExpect(status().isNotFound());
    }

    @Test
    void simultaneousAcceptsCannotExceedTheBudget() throws Exception {
        String caseId = open();
        offer(caseId, "FUNDING", "70000", company()).andExpect(status().isNoContent());
        offer(caseId, "FUNDING", "70000", company()).andExpect(status().isNoContent());
        List<Integer> ids = project(caseId, faculty(), "$.pledges[*].id");
        Cookie session = faculty();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> attempts = ids.stream().map(id -> (Callable<Integer>) () -> {
                barrier.await();
                return postJson("/api/university/problems/" + caseId + "/pledges/" + id + "/accept", "", session)
                        .andReturn().getResponse().getStatus();
            }).toList();
            var a = pool.submit(attempts.get(0));
            var b = pool.submit(attempts.get(1));
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(204, 409);
        }
        assertThat((Integer) project(caseId, faculty(), "$.funding.accepted")).isEqualTo(70000);
    }

    @Test
    void finishingTheProjectClosesOpenOffers() throws Exception {
        String caseId = open();
        Cookie corp = company();
        offer(caseId, "EQUIPMENT", "null", corp);
        List<Integer> steps = project(caseId, faculty(), "$.milestones[*].id");
        for (int step : steps) uni(caseId, "milestones/" + step + "/complete", "{\"note\":\"Done\"}");
        uni(caseId, "request-completion", "").andExpect(status().isNoContent());
        uni(caseId, "pledges/" + lastPledge(caseId) + "/accept", "").andExpect(status().isConflict());
        offer(caseId, "FUNDING", "5", company()).andExpect(status().isConflict());
        postJson("/api/admin/problems/" + caseId + "/complete", "{}", admin()).andExpect(status().isNoContent());
        mvc.perform(get("/api/industry/pledges").cookie(corp))
                .andExpect(jsonPath("$[0].pledge.status").value("DECLINED"))
                .andExpect(jsonPath("$[0].pledge.responseNote", containsString("finished")));
    }
}
