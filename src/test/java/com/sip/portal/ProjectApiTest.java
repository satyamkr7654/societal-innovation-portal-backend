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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.ResultActions;

class ProjectApiTest extends ApiTestSupport {
    private static final String TEAM = "{\"department\":\"Civil\",\"facultyLead\":\"Dr Sen\",\"studentsCount\":4}";
    private static final String LONG = "A clear explanation that is comfortably longer than fifty characters.";
    private static final String PROPOSAL = """
            {"summary":"%s","approach":"%s","durationWeeks":12,
             "items":[{"label":"Sensors","amount":150000},{"label":"Travel","amount":50000}]}""".formatted(LONG, LONG);

    Cookie faculty() throws Exception {
        return login("university@demo.example", "Password123");
    }

    /** A different, approved university. */
    Cookie rival() throws Exception {
        String id = pendingUniversity("Rival " + UUID.randomUUID());
        postJson("/api/admin/accounts/" + id + "/approve", "", admin());
        return login(users.findById(id).orElseThrow().getEmail(), "Secret123");
    }

    String assigned() throws Exception {
        String caseId = newProblem();
        decide(caseId, "validate", "{}", admin());
        decide(caseId, "assign", "{\"organizationId\":" + approvedUniversity() + "}", admin()).andExpect(status().isOk());
        return caseId;
    }

    String running() throws Exception {
        String caseId = assigned();
        act(caseId, "accept", TEAM, faculty()).andExpect(status().isNoContent());
        return caseId;
    }

    ResultActions act(String caseId, String action, String body, Cookie session) throws Exception {
        return postJson("/api/university/problems/" + caseId + "/" + action, body, session);
    }

    ResultActions project(String caseId, Cookie session) throws Exception {
        return mvc.perform(get("/api/problems/" + caseId + "/project").cookie(session));
    }

    List<Integer> milestoneIds(String caseId) throws Exception {
        return JsonPath.read(project(caseId, faculty()).andReturn().getResponse().getContentAsString(), "$.milestones[*].id");
    }

    void completeAll(String caseId) throws Exception {
        for (int id : milestoneIds(caseId)) {
            act(caseId, "milestones/" + id + "/complete", "{\"note\":\"Done\"}", faculty()).andExpect(status().isNoContent());
        }
    }

    @Test
    void acceptStartsWorkWithATeamAndMilestones() throws Exception {
        String caseId = assigned();
        act(caseId, "accept", "{}", faculty()).andExpect(status().isBadRequest());
        act(caseId, "accept", TEAM.replace("4", "0"), faculty()).andExpect(status().isBadRequest());
        act(caseId, "accept", TEAM, faculty()).andExpect(status().isNoContent());
        act(caseId, "accept", TEAM, faculty()).andExpect(status().isConflict());
        act(caseId, "decline", "{\"reason\":\"late\"}", faculty()).andExpect(status().isConflict());
        mvc.perform(get("/api/problems/" + caseId).cookie(ramesh()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedUniversity.facultyLead").value("Dr Sen"));
        project(caseId, faculty()).andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.milestones[*].title",
                        contains("Design", "Prototype", "Field Deployment", "Citizen Handover")));
    }

    @Test
    void declineReturnsToTheAdminWhoCanReassign() throws Exception {
        String caseId = assigned();
        act(caseId, "decline", "{\"reason\":\" \"}", faculty()).andExpect(status().isBadRequest());
        act(caseId, "decline", "{\"reason\":\"No capacity\"}", faculty()).andExpect(status().isNoContent());
        mvc.perform(get("/api/problems/" + caseId).cookie(ramesh()))
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.assignedUniversity").value(nullValue()))
                .andExpect(jsonPath("$.statusHistory[-1:].remarks", contains("Assignment declined: No capacity")));
        project(caseId, ramesh()).andExpect(status().isNoContent());
        decide(caseId, "assign", "{\"organizationId\":" + approvedUniversity() + "}", admin()).andExpect(status().isOk());
    }

    @Test
    void longDeclineReasonsFitTheHistory() throws Exception {
        act(assigned(), "decline", "{\"reason\":\"" + "x".repeat(500) + "\"}", faculty()).andExpect(status().isNoContent());
    }

    @Test
    void onlyTheAssignedUniversityCanAct() throws Exception {
        String caseId = running();
        String milestone = "milestones/" + milestoneIds(caseId).getFirst() + "/complete";
        for (Cookie other : new Cookie[] {rival(), ramesh(), admin(), login("industry@demo.example", "Password123")}) {
            act(caseId, "accept", TEAM, other).andExpect(status().isForbidden());
            act(caseId, "decline", "{\"reason\":\"x\"}", other).andExpect(status().isForbidden());
            act(caseId, "proposal", PROPOSAL, other).andExpect(status().isForbidden());
            act(caseId, milestone, "{\"note\":\"x\"}", other).andExpect(status().isForbidden());
            act(caseId, "request-completion", "", other).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/university/assignments").cookie(rival())).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/university/assignments").cookie(faculty()))
                .andExpect(jsonPath("$[*].problem.id", hasItem(caseId)));
        mvc.perform(get("/api/university/assignments").cookie(ramesh())).andExpect(status().isForbidden());
    }

    @Test
    void proposalReviewCycle() throws Exception {
        String caseId = running();
        String admin = "/api/admin/problems/" + caseId + "/proposal/";
        postJson(admin + "approve", "{}", admin()).andExpect(status().isConflict());
        act(caseId, "proposal", PROPOSAL, faculty()).andExpect(status().isNoContent());
        act(caseId, "proposal", PROPOSAL, faculty()).andExpect(status().isConflict());
        mvc.perform(get("/api/problems/" + caseId).cookie(admin())).andExpect(jsonPath("$.pendingDecision").value("PROPOSAL"));
        postJson(admin + "request-changes", "{\"reason\":\"\"}", admin()).andExpect(status().isBadRequest());
        postJson(admin + "request-changes", "{\"reason\":\"Reduce travel\"}", admin()).andExpect(status().isNoContent());
        project(caseId, faculty()).andExpect(jsonPath("$.proposal.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.proposal.reviewNote").value("Reduce travel"));
        act(caseId, "proposal", PROPOSAL.replace("50000}]", "20000}]"), faculty()).andExpect(status().isNoContent());
        postJson(admin + "approve", "{}", admin()).andExpect(status().isNoContent());
        postJson(admin + "request-changes", "{\"reason\":\"late\"}", admin()).andExpect(status().isConflict());
        project(caseId, admin()).andExpect(jsonPath("$.proposal.total").value(170000))
                .andExpect(jsonPath("$.proposal.status").value("APPROVED"))
                .andExpect(jsonPath("$.proposal.reviewNote").value(nullValue()))
                .andExpect(jsonPath("$.canManage").value(false));
        postJson(admin + "approve", "{}", faculty()).andExpect(status().isForbidden());
    }

    @Test
    void proposalsAreHiddenFromOthers() throws Exception {
        String caseId = running();
        act(caseId, "proposal", PROPOSAL, faculty());
        for (Cookie other : new Cookie[] {rival(), ramesh(), login("industry@demo.example", "Password123")}) {
            project(caseId, other).andExpect(status().isOk()).andExpect(jsonPath("$.proposal").value(nullValue()))
                    .andExpect(jsonPath("$.milestones.length()").value(4));
        }
        mvc.perform(get("/api/problems/" + caseId + "/project")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"amount\":150000|\"amount\":0", "\"amount\":150000|\"amount\":-5", "\"amount\":150000|\"amount\":999999999999",
            "\"label\":\"Sensors\"|\"label\":\" \"", "\"durationWeeks\":12|\"durationWeeks\":0", "\"durationWeeks\":12|\"durationWeeks\":105"})
    void invalidProposalsAreRefused(String change) throws Exception {
        String[] parts = change.split("\\|");
        assertThat(PROPOSAL).contains(parts[0]);
        act(running(), "proposal", PROPOSAL.replace(parts[0], parts[1]), faculty()).andExpect(status().isBadRequest());
    }

    @Test
    void proposalNeedsItemsAndRealText() throws Exception {
        String caseId = running();
        act(caseId, "proposal", PROPOSAL.replaceAll("(?s)\\[.*\\]", "[]"), faculty()).andExpect(status().isBadRequest());
        act(caseId, "proposal", PROPOSAL.replaceAll("(?s)\\[.*\\]", "[null]"), faculty()).andExpect(status().isBadRequest());
        act(caseId, "proposal", PROPOSAL.replace(LONG, "x" + " ".repeat(60)), faculty()).andExpect(status().isBadRequest());
    }

    @Test
    void milestonesGoInOrderAndOnlyOnce() throws Exception {
        String caseId = running();
        List<Integer> ids = milestoneIds(caseId);
        act(caseId, "milestones/" + ids.get(1) + "/complete", "{\"note\":\"x\"}", faculty()).andExpect(status().isConflict());
        act(caseId, "milestones/" + ids.get(0) + "/complete", "{\"note\":\" \"}", faculty()).andExpect(status().isBadRequest());
        act(caseId, "milestones/" + ids.get(0) + "/complete", "{\"note\":\"" + "n".repeat(500) + "\"}", faculty())
                .andExpect(status().isNoContent());
        act(caseId, "milestones/" + ids.get(0) + "/complete", "{\"note\":\"x\"}", faculty()).andExpect(status().isConflict());
        int foreign = milestoneIds(running()).getFirst();
        act(caseId, "milestones/" + foreign + "/complete", "{\"note\":\"x\"}", faculty()).andExpect(status().isConflict());
        project(caseId, ramesh()).andExpect(jsonPath("$.milestones[*].done", contains(true, false, false, false)));
    }

    @Test
    void completionNeedsTheAdmin() throws Exception {
        String caseId = running();
        String admin = "/api/admin/problems/" + caseId;
        act(caseId, "request-completion", "", faculty()).andExpect(status().isConflict());
        postJson(admin + "/complete", "{}", admin()).andExpect(status().isConflict());
        completeAll(caseId);
        act(caseId, "request-completion", "", faculty()).andExpect(status().isConflict());
        act(caseId, "proposal", PROPOSAL, faculty()).andExpect(status().isNoContent());
        act(caseId, "request-completion", "", faculty()).andExpect(status().isConflict());
        postJson(admin + "/proposal/approve", "{}", admin()).andExpect(status().isNoContent());
        act(caseId, "request-completion", "", faculty()).andExpect(status().isNoContent());
        act(caseId, "request-completion", "", faculty()).andExpect(status().isConflict());
        mvc.perform(get("/api/problems/" + caseId).cookie(admin())).andExpect(jsonPath("$.pendingDecision").value("COMPLETION"));
        mvc.perform(get("/api/admin/stats").cookie(admin())).andExpect(jsonPath("$.completionsToConfirm", greaterThan(0)));
        postJson(admin + "/complete", "{}", faculty()).andExpect(status().isForbidden());

        postJson(admin + "/return", "{\"reason\":\"Leak remains\"}", admin()).andExpect(status().isNoContent());
        project(caseId, faculty()).andExpect(jsonPath("$.milestones[*].done", contains(true, true, true, false)))
                .andExpect(jsonPath("$.completionRequestedAt").value(nullValue()));
        int last = milestoneIds(caseId).getLast();
        act(caseId, "milestones/" + last + "/complete", "{\"note\":\"Fixed\"}", faculty()).andExpect(status().isNoContent());
        act(caseId, "request-completion", "", faculty()).andExpect(status().isNoContent());
        postJson(admin + "/complete", "{}", admin()).andExpect(status().isNoContent());
        postJson(admin + "/return", "{\"reason\":\"late\"}", admin()).andExpect(status().isConflict());
        mvc.perform(get("/api/problems/" + caseId).cookie(ramesh())).andExpect(jsonPath("$.status").value("SOLVED"));
        postJson("/api/problems/" + caseId + "/feedback", "{\"rating\":5,\"verifiedOnGround\":true}", ramesh())
                .andExpect(status().isOk());
    }

    @Test
    void simultaneousAcceptAndDeclineSaveOne() throws Exception {
        String caseId = assigned();
        Cookie session = faculty();
        var barrier = new CyclicBarrier(2);
        Callable<Integer> accept = () -> { barrier.await(); return act(caseId, "accept", TEAM, session).andReturn().getResponse().getStatus(); };
        Callable<Integer> decline = () -> { barrier.await(); return act(caseId, "decline", "{\"reason\":\"No\"}", session).andReturn().getResponse().getStatus(); };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(accept);
            var d = pool.submit(decline);
            // The loser sees either a changed status (409) or an assignment that is gone (403)
            assertThat(List.of(a.get(), d.get())).containsOnlyOnce(204).allMatch(code -> code < 500);
        }
    }
}
