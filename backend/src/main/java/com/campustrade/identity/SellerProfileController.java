package com.campustrade.identity;

import com.campustrade.common.Api;
import com.campustrade.common.BusinessException;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("identity")
@RequestMapping("/internal/sellers")
class SellerProfileController {
    private final AccountService accounts;
    private final byte[] callerSecret;

    SellerProfileController(AccountService accounts, @Value("${campus.internal-key}") String key) {
        this.accounts = accounts;
        this.callerSecret = key.getBytes(StandardCharsets.UTF_8);
        if (callerSecret.length < 32) {
            throw new IllegalArgumentException("INTERNAL_SERVICE_KEY must contain at least 32 UTF-8 bytes");
        }
    }

    @GetMapping("/{id}")
    Api<SellerProfile> profile(@PathVariable @Positive long id,
            @RequestHeader(value = "X-Internal-Key", defaultValue = "") String caller) {
        if (!MessageDigest.isEqual(callerSecret, caller.getBytes(StandardCharsets.UTF_8))) {
            throw BusinessException.forbidden("服务凭证无效");
        }
        return Api.ok(accounts.profile(id));
    }
}
