package com.sip.portal.admin;

import jakarta.validation.constraints.*;

final class AdminRequests {
    private AdminRequests() {}

    record Remarks(@Size(max = 500, message = "Remarks cannot exceed 500 characters") String remarks) {}

    record Rejection(
            @NotBlank(message = "A reason is required to reject.")
            @Size(max = 500, message = "The reason cannot exceed 500 characters") String reason) {}

    record Assign(
            @NotNull(message = "Please choose a university.") Long organizationId,
            @Size(max = 500, message = "Remarks cannot exceed 500 characters") String remarks) {}
}
