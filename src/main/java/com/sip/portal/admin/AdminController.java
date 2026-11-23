package com.sip.portal.admin;

import com.sip.portal.problem.ProblemResponse;
import com.sip.portal.user.OrganizationRepository;
import com.sip.portal.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Everything here is limited to admins by SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
class AdminController {
    record University(Long id, String name) {}

    private final ReviewService reviews;
    private final AccountService accounts;
    private final StatsService stats;
    private final OrganizationRepository organizations;

    AdminController(ReviewService reviews, AccountService accounts, StatsService stats,
            OrganizationRepository organizations) {
        this.reviews = reviews;
        this.accounts = accounts;
        this.stats = stats;
        this.organizations = organizations;
    }

    @GetMapping("/stats")
    StatsService.Stats stats() {
        return stats.stats();
    }

    @GetMapping("/universities")
    List<University> universities() {
        return organizations.findAssignableUniversities().stream()
                .map(o -> new University(o.getId(), o.getName())).toList();
    }

    @GetMapping("/accounts/pending")
    List<AccountService.PendingAccount> pendingAccounts() {
        return accounts.pending();
    }

    @PostMapping("/accounts/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable String id, @AuthenticationPrincipal User admin) {
        accounts.decide(id, true, admin);
    }

    @PostMapping("/accounts/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rejectAccount(@PathVariable String id, @AuthenticationPrincipal User admin) {
        accounts.decide(id, false, admin);
    }

    @PostMapping("/problems/{caseId}/validate")
    ProblemResponse validate(@PathVariable String caseId, @Valid @RequestBody AdminRequests.Remarks body,
            @AuthenticationPrincipal User admin) {
        return reviews.validate(caseId, body.remarks(), admin);
    }

    @PostMapping("/problems/{caseId}/reject")
    ProblemResponse reject(@PathVariable String caseId, @Valid @RequestBody AdminRequests.Rejection body,
            @AuthenticationPrincipal User admin) {
        return reviews.reject(caseId, body.reason(), admin);
    }

    @PostMapping("/problems/{caseId}/assign")
    ProblemResponse assign(@PathVariable String caseId, @Valid @RequestBody AdminRequests.Assign body,
            @AuthenticationPrincipal User admin) {
        return reviews.assign(caseId, body.organizationId(), body.remarks(), admin);
    }
}
