package com.sip.portal.pledge;

import java.time.Instant;

public record PledgeResponse(Long id, String organization, String type, Long amount, String description, String status,
        String responseNote, Instant createdAt) {
    public static PledgeResponse of(Pledge p) {
        return new PledgeResponse(p.getId(), p.getOrganization().getName(), p.getType().name(), p.getAmountInr(),
                p.getDescription(), p.getStatus().name(), p.getResponseNote(), p.getCreatedAt());
    }
}
