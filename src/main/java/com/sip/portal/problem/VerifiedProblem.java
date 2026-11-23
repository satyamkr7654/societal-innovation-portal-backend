package com.sip.portal.problem;

/** Only the columns the public map needs, so private ones are never even loaded. */
public record VerifiedProblem(String caseId, String title, Category category, ProblemStatus status, String district,
        double latitude, double longitude) {}
