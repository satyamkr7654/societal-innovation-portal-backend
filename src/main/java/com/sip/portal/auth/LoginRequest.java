package com.sip.portal.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required") @Size(max = 190, message = "Email is too long") String email,
        @NotBlank(message = "Password is required") @Size(max = 200, message = "Password is too long") String password) {

    @Override
    public String toString() {
        return "LoginRequest[hidden]";
    }
}
