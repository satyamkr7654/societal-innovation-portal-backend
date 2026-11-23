package com.sip.portal.auth;

import com.sip.portal.common.ApiException;
import com.sip.portal.notification.Notifier;
import com.sip.portal.user.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {
    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final String dummyHash;
    private final Notifier notifier;

    AuthService(UserRepository users, OrganizationRepository organizations, PasswordEncoder encoder,
            LoginThrottle throttle, Notifier notifier) {
        this.notifier = notifier;
        this.users = users;
        this.organizations = organizations;
        this.encoder = encoder;
        this.throttle = throttle;
        this.dummyHash = encoder.encode("not a real account");
    }

    @Transactional
    User register(RegisterRequest request) {
        Role role = selfRegisterableRole(request.role());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password is too long.");
        }
        if (role == Role.CITIZEN && request.phone() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Phone number is required.");
        }
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }
        Organization organization = role == Role.CITIZEN ? null : organization(role, request.organizationName());
        User user = users.saveAndFlush(new User(request.name().strip(), email, encoder.encode(request.password()), role,
                role == Role.CITIZEN ? request.phone() : null, organization));
        if (organization != null) {
            notifier.admins(null, "Account waiting for approval: " + organization.getName());
        }
        return user;
    }

    User login(LoginRequest request, String clientAddress) {
        String email = normalize(request.email());
        String throttleKey = email + "|" + clientAddress;
        throttle.check(throttleKey);
        User user = users.findByEmail(email).orElse(null);
        // Always verify a hash, so response time does not reveal which emails exist
        boolean matches = encoder.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            throttle.failed(throttleKey);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }
        throttle.succeeded(throttleKey);
        // Only after the password is proven, so this does not reveal account details
        switch (user.getAccountStatus()) {
            case PENDING -> throw new ApiException(HttpStatus.FORBIDDEN, "Your account is waiting for admin approval.");
            case REJECTED -> throw new ApiException(HttpStatus.FORBIDDEN, "Your registration was not approved.");
            case ACTIVE -> { }
        }
        return user;
    }

    void logout(User user) {
        users.endSessions(user.getId());
    }

    private Organization organization(Role type, String name) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Organisation name is required.");
        }
        String clean = name.strip();
        return organizations.findByTypeAndName(type, clean)
                .orElseGet(() -> organizations.save(new Organization(clean, type)));
    }

    private static Role selfRegisterableRole(String value) {
        return switch (value) {
            case "citizen" -> Role.CITIZEN;
            case "university" -> Role.UNIVERSITY;
            case "industry" -> Role.INDUSTRY;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "This role cannot be self-registered.");
        };
    }

    private static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
