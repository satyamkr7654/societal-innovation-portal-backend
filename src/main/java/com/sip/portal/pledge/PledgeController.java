package com.sip.portal.pledge;

import com.sip.portal.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** /industry and /university are limited to those roles by SecurityConfig. */
@RestController
@RequestMapping("/api")
class PledgeController {
    private final PledgeService service;

    PledgeController(PledgeService service) {
        this.service = service;
    }

    @GetMapping("/industry/proposals")
    List<PledgeService.OpenProposal> proposals(@AuthenticationPrincipal User user) {
        return service.openProposals(user);
    }

    @GetMapping("/industry/pledges")
    List<PledgeService.MyPledge> mine(@AuthenticationPrincipal User user) {
        return service.mine(user);
    }

    @PostMapping("/industry/problems/{caseId}/pledges")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void offer(@PathVariable String caseId, @Valid @RequestBody PledgeRequests.Offer body,
            @AuthenticationPrincipal User user) {
        service.offer(caseId, body, user);
    }

    @PostMapping("/industry/problems/{caseId}/pledges/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable String caseId, @PathVariable long id, @AuthenticationPrincipal User user) {
        service.withdraw(caseId, id, user);
    }

    @PostMapping("/university/problems/{caseId}/pledges/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@PathVariable String caseId, @PathVariable long id, @AuthenticationPrincipal User user) {
        service.accept(caseId, id, user);
    }

    @PostMapping("/university/problems/{caseId}/pledges/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@PathVariable String caseId, @PathVariable long id, @Valid @RequestBody PledgeRequests.Reason body,
            @AuthenticationPrincipal User user) {
        service.decline(caseId, id, body.reason(), user);
    }
}
