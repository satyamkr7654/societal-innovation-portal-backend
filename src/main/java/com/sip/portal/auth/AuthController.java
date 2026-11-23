package com.sip.portal.auth;

import com.sip.portal.user.User;
import com.sip.portal.user.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
class AuthController {
    private final AuthService service;
    private final SessionCookies cookies;

    AuthController(AuthService service, SessionCookies cookies) {
        this.service = service;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.of(service.register(request));
    }

    @PostMapping("/login")
    ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        User user = service.login(request, http.getRemoteAddr());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.issue(user).toString())
                .body(UserResponse.of(user));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }

    /** 204 when nobody is signed in. The frontend calls this first, so it also issues the CSRF cookie. */
    @GetMapping("/me")
    ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user, CsrfToken csrf) {
        csrf.getToken(); // reading the token is what writes the cookie
        return user == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(UserResponse.of(user));
    }
}
