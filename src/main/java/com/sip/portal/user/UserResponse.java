package com.sip.portal.user;

/** The signed-in user's own profile. Never contains the password hash. */
public record UserResponse(String id, String name, String email, String phone, String role, String organizationName,
        boolean approved) {
    public static UserResponse of(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole().json(),
                u.getOrganization() == null ? null : u.getOrganization().getName(),
                u.getAccountStatus() == AccountStatus.ACTIVE);
    }
}
