package com.sip.portal.project;

import jakarta.persistence.Embeddable;

@Embeddable
public record BudgetItem(String label, long amountInr) {}
