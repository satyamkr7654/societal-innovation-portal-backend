package com.sip.portal.project;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

final class ProjectRequests {
    private ProjectRequests() {}

    record Accept(
            @NotBlank(message = "Department is required.") @Size(max = 150, message = "Department is too long.") String department,
            @NotBlank(message = "Faculty lead is required.") @Size(max = 100, message = "Faculty lead is too long.") String facultyLead,
            @NotNull(message = "Number of students is required.")
            @Min(value = 1, message = "At least one student is required.") @Max(value = 100, message = "At most 100 students.") Integer studentsCount) {}

    record Reason(
            @NotBlank(message = "A reason is required.") @Size(max = 500, message = "The reason cannot exceed 500 characters.") String reason) {}

    record Note(
            @NotBlank(message = "Please describe what was done.") @Size(max = 500, message = "The note cannot exceed 500 characters.") String note) {}

    record Remarks(@Size(max = 500, message = "Remarks cannot exceed 500 characters.") String remarks) {}

    record ProposalForm(
            @NotBlank(message = "Summary is required.") @Size(min = 50, max = 2000, message = "Summary must be 50 to 2000 characters.") String summary,
            @NotBlank(message = "Approach is required.") @Size(min = 50, max = 3000, message = "Approach must be 50 to 3000 characters.") String approach,
            @NotNull(message = "Duration is required.") @Min(value = 1, message = "Duration must be 1 to 104 weeks.") @Max(value = 104, message = "Duration must be 1 to 104 weeks.") Integer durationWeeks,
            @NotEmpty(message = "Add at least one budget item.") @Size(max = 20, message = "At most 20 budget items.") List<@NotNull(message = "Budget item is empty.") @Valid Item> items) {}

    record Item(
            @NotBlank(message = "Every budget item needs a name.") @Size(max = 100, message = "Budget item name is too long.") String label,
            @NotNull(message = "Every budget item needs an amount.") @Min(value = 1, message = "Amounts must be at least ₹1.") @Max(value = 100_000_000, message = "An amount cannot exceed ₹10 crore.") Long amount) {}
}
