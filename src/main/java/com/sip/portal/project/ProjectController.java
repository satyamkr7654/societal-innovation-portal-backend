package com.sip.portal.project;

import com.sip.portal.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** /university is limited to universities and /admin to admins by SecurityConfig. */
@RestController
@RequestMapping("/api")
class ProjectController {
    private final ProjectService service;

    ProjectController(ProjectService service) {
        this.service = service;
    }

    /** 204 when no university is assigned. */
    @GetMapping("/problems/{caseId}/project")
    ResponseEntity<ProjectResponse> project(@PathVariable String caseId, @AuthenticationPrincipal User user) {
        return service.get(caseId, user).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/university/assignments")
    List<ProjectService.Summary> assignments(@AuthenticationPrincipal User user) {
        return service.mine(user);
    }

    @PostMapping("/university/problems/{caseId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@PathVariable String caseId, @Valid @RequestBody ProjectRequests.Accept body,
            @AuthenticationPrincipal User user) {
        service.accept(caseId, body, user);
    }

    @PostMapping("/university/problems/{caseId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable String caseId, @Valid @RequestBody ProjectRequests.Reason body,
            @AuthenticationPrincipal User user) {
        service.decline(caseId, body.reason(), user);
    }

    @PostMapping("/university/problems/{caseId}/proposal")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void proposal(@PathVariable String caseId, @Valid @RequestBody ProjectRequests.ProposalForm body,
            @AuthenticationPrincipal User user) {
        service.submitProposal(caseId, body, user);
    }

    @PostMapping("/university/problems/{caseId}/milestones/{milestoneId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void milestone(@PathVariable String caseId, @PathVariable long milestoneId,
            @Valid @RequestBody ProjectRequests.Note body, @AuthenticationPrincipal User user) {
        service.completeMilestone(caseId, milestoneId, body.note(), user);
    }

    @PostMapping("/university/problems/{caseId}/request-completion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void requestCompletion(@PathVariable String caseId, @AuthenticationPrincipal User user) {
        service.requestCompletion(caseId, user);
    }

    @PostMapping("/admin/problems/{caseId}/proposal/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approveProposal(@PathVariable String caseId, @Valid @RequestBody ProjectRequests.Remarks body,
            @AuthenticationPrincipal User admin) {
        service.reviewProposal(caseId, true, body.remarks(), admin);
    }

    @PostMapping("/admin/problems/{caseId}/proposal/request-changes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void requestChanges(@PathVariable String caseId, @Valid @RequestBody ProjectRequests.Reason body,
            @AuthenticationPrincipal User admin) {
        service.reviewProposal(caseId, false, body.reason(), admin);
    }

    @PostMapping("/admin/problems/{caseId}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void complete(@PathVariable String caseId, @Valid @RequestBody ProjectRequests.Remarks body,
            @AuthenticationPrincipal User admin) {
        service.confirmCompletion(caseId, body.remarks(), admin);
    }

    @PostMapping("/admin/problems/{caseId}/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void returnForWork(@PathVariable String caseId, @Valid @RequestBody ProjectRequests.Reason body,
            @AuthenticationPrincipal User admin) {
        service.returnForWork(caseId, body.reason(), admin);
    }
}
