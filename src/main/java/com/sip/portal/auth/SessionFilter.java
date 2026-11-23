package com.sip.portal.auth;

import com.sip.portal.user.AccountStatus;
import com.sip.portal.user.User;
import com.sip.portal.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Loads the user on every request, so the role always comes from the database. */
public class SessionFilter extends OncePerRequestFilter {
    private final SessionCookies cookies;
    private final UserRepository users;

    public SessionFilter(SessionCookies cookies, UserRepository users) {
        this.cookies = cookies;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        cookies.userId(req).flatMap(users::findWithOrganizationById)
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE).ifPresent(SessionFilter::authenticate);
        chain.doFilter(req, res);
    }

    private static void authenticate(User user) {
        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of(authority)));
        SecurityContextHolder.setContext(context);
    }
}
