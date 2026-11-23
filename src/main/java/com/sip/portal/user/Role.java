package com.sip.portal.user;

import java.util.Locale;

public enum Role {
    CITIZEN, ADMIN, UNIVERSITY, INDUSTRY;

    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }
}
