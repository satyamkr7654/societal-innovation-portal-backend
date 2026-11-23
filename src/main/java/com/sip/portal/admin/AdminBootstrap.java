package com.sip.portal.admin;

import com.sip.portal.user.*;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the first admin from ADMIN_EMAIL and ADMIN_PASSWORD, only while no admin exists. */
@Component
@Order(2) // after the demo data, which needs an empty database
@ConditionalOnExpression("'${ADMIN_EMAIL:}' != ''")
class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    AdminBootstrap(UserRepository users, PasswordEncoder encoder, @Value("${ADMIN_EMAIL}") String email,
            @Value("${ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) return;
        if (password.length() < 12) throw new IllegalStateException("ADMIN_PASSWORD needs at least 12 characters");
        users.save(new User("Portal Admin", email, encoder.encode(password), Role.ADMIN, null, null));
        log.info("First admin account created. ADMIN_PASSWORD can now be removed from the settings.");
    }
}
