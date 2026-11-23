package com.campustrade.identity;

import com.campustrade.common.BusinessException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import javax.crypto.SecretKey;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.context.annotation.Profile("!commerce")
@Service
public class AccountService {
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final AccountMapper mapper;
    private final JwtEncoder encoder;

    AccountService(AccountMapper mapper, SecretKey key) {
        this.mapper = mapper;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Transactional
    public Account.View register(String username, String displayName, String password) {
        checkPasswordLength(password);
        String normalized = username.toLowerCase(Locale.ROOT);
        try {
            mapper.insert(normalized, displayName.strip(), passwords.encode(password));
        } catch (DuplicateKeyException exception) {
            throw BusinessException.conflict("USERNAME_TAKEN", "用户名已注册");
        }
        return new Account.View(mapper.findByUsername(normalized));
    }

    public Account.Session login(String username, String password) {
        checkPasswordLength(password);
        Account account = mapper.findByUsername(username.toLowerCase(Locale.ROOT));
        if (account == null || !passwords.matches(password, account.passwordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("campus-trade").subject(Long.toString(account.id()))
                .issuedAt(now).expiresAt(now.plus(2, ChronoUnit.HOURS)).claim("role", account.role().name()).build();
        var request = JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims);
        return new Account.Session(encoder.encode(request).getTokenValue(), new Account.View(account));
    }

    public SellerProfile profile(long id) {
        var account = me(id);
        return new SellerProfile(account.id(), account.displayName());
    }

    public Account.View me(long id) {
        Account account = mapper.findById(id);
        if (account == null) {
            throw BusinessException.notFound("用户不存在");
        }
        return new Account.View(account);
    }

    private void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "密码的 UTF-8 长度不能超过 72 字节");
        }
    }
}
