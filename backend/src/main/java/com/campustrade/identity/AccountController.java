package com.campustrade.identity;

import com.campustrade.common.Api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@org.springframework.context.annotation.Profile("!commerce")
@RestController
@RequestMapping("/api/auth")
class AccountController {
    private static final String USERNAME = "[A-Za-z0-9_]{3,32}";
    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    record Registration(@NotBlank @Pattern(regexp = USERNAME) String username,
            @NotBlank @Size(max = 40) String displayName, @NotBlank @Size(min = 8, max = 72) String password) {}

    record Credentials(@NotBlank @Pattern(regexp = USERNAME) String username,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    @PostMapping("/register")
    Api<Account.View> register(@Valid @RequestBody Registration form) {
        return Api.ok(accounts.register(form.username(), form.displayName(), form.password()));
    }

    @PostMapping("/login")
    Api<Account.Session> login(@Valid @RequestBody Credentials form) {
        return Api.ok(accounts.login(form.username(), form.password()));
    }

    @GetMapping("/me")
    Api<Account.View> me(@AuthenticationPrincipal Jwt token) {
        return Api.ok(accounts.me(Long.parseLong(token.getSubject())));
    }
}
