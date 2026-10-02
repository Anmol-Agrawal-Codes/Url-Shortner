package com.example.urlshortener.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder,
            Clock clock, @Value("${app.jwt-ttl}") Duration ttl) {
        this.users = users;
        this.passwords = passwords;
        this.encoder = encoder;
        this.clock = clock;
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("JWT TTL must be positive");
        }
        this.ttl = ttl;
        this.dummyHash = passwords.encode("not-a-user-password");
    }

    @Transactional
    public TokenResponse register(Credentials credentials) {
        checkPasswordLength(credentials.password());
        String username = normalize(credentials.username());
        if (users.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        User user = users.saveAndFlush(new User(username, passwords.encode(credentials.password())));
        return token(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(Credentials credentials) {
        checkPasswordLength(credentials.password());
        User user = users.findByUsername(normalize(credentials.username())).orElse(null);
        boolean matches = passwords.matches(credentials.password(),
                user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return token(user);
    }

    private TokenResponse token(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("url-shortener")
                .subject(user.getId().toString()).issuedAt(now).expiresAt(now.plus(ttl)).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
    }

    private String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }

    private void checkPasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
    }
}
