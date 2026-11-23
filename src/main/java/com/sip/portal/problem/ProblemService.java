package com.sip.portal.problem;

import com.sip.portal.common.ApiException;
import com.sip.portal.user.User;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
class ProblemService {
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final ProblemRepository problems;
    private final PhotoStorage photos;
    private final TransactionTemplate transaction;

    ProblemService(ProblemRepository problems, PhotoStorage photos, TransactionTemplate transaction) {
        this.problems = problems;
        this.photos = photos;
        this.transaction = transaction;
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> list() {
        return problems.findAllByOrderBySubmittedAtDesc().stream().map(ProblemResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(String caseId) {
        return ProblemResponse.of(find(caseId));
    }

    ProblemResponse create(ProblemRequest request, MultipartFile photo, User citizen) {
        String title = trimmed(request.title(), 10, "Title must be at least 10 characters.");
        String description = trimmed(request.description(), 25, "Description must be at least 25 characters.");
        String address = trimmed(request.location().address(), 5, "Address must be at least 5 characters.");
        Category category = Category.fromLabel(request.category())
                .orElseThrow(() -> bad("Please select a civic category."));
        double lat = request.location().lat();
        double lng = request.location().lng();
        if (!Jharkhand.isDistrict(request.district())) throw bad("Please select a Jharkhand district.");
        if (!Jharkhand.isInArea(lat, lng)) throw bad("The pin is outside the Jharkhand area.");
        if (!Jharkhand.isNearDistrict(lat, lng, request.district())) {
            throw bad("The pin is too far from the selected district.");
        }

        String photoUrl = photo == null || photo.isEmpty() ? null : photos.save(photo);
        try {
            return transaction.execute(status -> {
                Instant now = Instant.now();
                Problem problem = problems.save(new Problem(citizen, title, description, category,
                        request.district(), address, lat, lng, photoUrl, now));
                problem.assignCaseId(Year.now(INDIA).getValue());
                problem.moveTo(ProblemStatus.SUBMITTED, "Citizen " + citizen.getName(),
                        "Problem registered through the portal.", now);
                return ProblemResponse.of(problem);
            });
        } catch (RuntimeException e) {
            if (photoUrl != null) photos.delete(photoUrl); // do not leave a file nobody refers to
            throw e;
        }
    }

    /** Only the citizen who reported a solved problem may review it, and only once. */
    @Transactional
    public ProblemResponse addFeedback(String caseId, FeedbackRequest request, User user) {
        Problem problem = problems.findForUpdate(caseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This problem does not exist."));
        if (!problem.getCitizen().getId().equals(user.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the citizen who reported this problem can review it.");
        }
        if (problem.getStatus() != ProblemStatus.SOLVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Feedback opens once the problem is solved.");
        }
        if (problem.getFeedback() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Feedback has already been submitted.");
        }
        String remarks = request.remarks() == null || request.remarks().isBlank() ? null : request.remarks().strip();
        problem.setFeedback(new Feedback(problem, request.rating(), remarks, request.verifiedOnGround(), Instant.now()));
        return ProblemResponse.of(problem);
    }

    private Problem find(String caseId) {
        return problems.findByCaseId(caseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "This problem does not exist."));
    }

    private static String trimmed(String value, int min, String message) {
        String clean = value.strip();
        if (clean.length() < min) throw bad(message);
        return clean;
    }

    private static ApiException bad(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
