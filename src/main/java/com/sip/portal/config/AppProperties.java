package com.sip.portal.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET needs at least 32 characters") String jwtSecret,
        @Min(1) int sessionHours,
        boolean cookieSecure,
        @NotBlank String uploadDir,
        String frontendDir,
        boolean demoData) {

    boolean servesFrontend() {
        return frontendDir != null && !frontendDir.isBlank();
    }

    @Override
    public String toString() {
        return "AppProperties[secret hidden]";
    }
}
