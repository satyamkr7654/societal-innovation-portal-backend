package com.sip.portal.problem;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ProblemRequest(
        @NotBlank(message = "Title is required") @Size(max = 120, message = "Title cannot exceed 120 characters") String title,
        @NotBlank(message = "Description is required") @Size(max = 2000, message = "Description cannot exceed 2000 characters") String description,
        @NotBlank(message = "Category is required") String category,
        @NotBlank(message = "District is required") String district,
        @NotNull(message = "Location is required") @Valid Location location) {

    public record Location(
            @NotBlank(message = "Address is required") @Size(max = 200, message = "Address cannot exceed 200 characters") String address,
            @NotNull(message = "Latitude is required") Double lat,
            @NotNull(message = "Longitude is required") Double lng) {}
}
