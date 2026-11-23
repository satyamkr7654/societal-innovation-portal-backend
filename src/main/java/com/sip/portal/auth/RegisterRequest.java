package com.sip.portal.auth;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Full name is required") @Size(min = 2, max = 100, message = "Full name must be 2 to 100 characters") String name,
        @NotBlank(message = "Email is required") @Email(message = "Please provide a valid email address") @Size(max = 190, message = "Email is too long") String email,
        @NotNull(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one number") String password,
        @NotBlank(message = "Role is required") String role,
        @Pattern(regexp = "[6-9]\\d{9}", message = "Enter a valid 10-digit Indian phone number") String phone,
        @Size(max = 150, message = "Organisation name is too long") String organizationName) {

    @Override
    public String toString() {
        return "RegisterRequest[hidden]";
    }
}
