package com.sip.portal.admin;

import com.sip.portal.common.ApiException;
import com.sip.portal.user.*;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    record PendingAccount(String id, String name, String email, String role, String organizationName,
            Instant registeredAt) {}

    private final UserRepository users;

    AccountService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<PendingAccount> pending() {
        return users.findByAccountStatusOrderByCreatedAt(AccountStatus.PENDING).stream()
                .map(u -> new PendingAccount(u.getId(), u.getName(), u.getEmail(), u.getRole().json(),
                        u.getOrganization() == null ? null : u.getOrganization().getName(), u.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void decide(String userId, boolean approve, User admin) {
        AccountStatus next = approve ? AccountStatus.ACTIVE : AccountStatus.REJECTED;
        // Conditional update, so an account can be decided only once
        if (users.decidePending(userId, next, admin.getId(), Instant.now()) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "This account is no longer waiting for approval.");
        }
    }
}
