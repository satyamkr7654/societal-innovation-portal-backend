package com.sip.portal.problem;

import jakarta.validation.constraints.*;

public record FeedbackRequest(
        @NotNull(message = "Please choose a star rating.") @Min(value = 1, message = "Rating must be 1 to 5") @Max(value = 5, message = "Rating must be 1 to 5") Integer rating,
        @Size(max = 1000, message = "Remarks cannot exceed 1000 characters") String remarks,
        boolean verifiedOnGround) {}
