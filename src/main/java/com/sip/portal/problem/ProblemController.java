package com.sip.portal.problem;

import com.sip.portal.user.User;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/problems")
class ProblemController {
    private final ProblemService service;

    ProblemController(ProblemService service) {
        this.service = service;
    }

    @GetMapping
    List<ProblemResponse> list() {
        return service.list();
    }

    @GetMapping("/{caseId}")
    ProblemResponse get(@PathVariable String caseId) {
        return service.get(caseId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ProblemResponse create(@Valid @RequestPart("problem") ProblemRequest request,
            @RequestPart(name = "photo", required = false) MultipartFile photo, @AuthenticationPrincipal User user) {
        return service.create(request, photo, user);
    }

    @PostMapping("/{caseId}/feedback")
    ProblemResponse feedback(@PathVariable String caseId, @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal User user) {
        return service.addFeedback(caseId, request, user);
    }
}
