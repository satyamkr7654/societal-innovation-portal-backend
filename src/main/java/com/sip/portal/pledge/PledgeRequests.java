package com.sip.portal.pledge;

import jakarta.validation.constraints.*;

final class PledgeRequests {
    private PledgeRequests() {}

    record Offer(
            @NotNull(message = "Please choose the kind of support.") PledgeType type,
            @Min(value = 1, message = "Amount must be at least ₹1.") @Max(value = 1_000_000_000, message = "Amount is too large.") Long amount,
            @NotBlank(message = "Please describe the support.") @Size(max = 500, message = "Description cannot exceed 500 characters.") String description) {}

    record Reason(
            @NotBlank(message = "A reason is required.") @Size(max = 500, message = "The reason cannot exceed 500 characters.") String reason) {}
}
