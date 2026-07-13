package com.devcollab.knowledgecore.auth.application;

import com.devcollab.knowledgecore.auth.application.exception.InvalidCredentialsException;
import com.devcollab.knowledgecore.auth.application.exception.UsernameAlreadyExistsException;
import com.devcollab.knowledgecore.auth.domain.UserAccount;
import com.devcollab.knowledgecore.auth.domain.UserRepository;
import com.devcollab.knowledgecore.auth.domain.UserStatus;
import com.devcollab.knowledgecore.auth.infrastructure.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthenticationApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthenticationApplicationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public AuthenticatedUser register(RegisterCommand command) {
        String normalizedUsername = normalizeUsername(command.username());

        if (userRepository.existsByNormalizedUsername(normalizedUsername)) {
            throw new UsernameAlreadyExistsException();
        }

        Instant now = Instant.now();

        UserAccount userAccount = new UserAccount(
                UUID.randomUUID(),
                command.username().trim(),
                normalizedUsername,
                command.displayName().trim(),
                passwordEncoder.encode(command.password()),
                UserStatus.ACTIVE,
                now,
                now
        );

        UserAccount savedUser = userRepository.save(userAccount);

        return createAuthenticatedUser(savedUser);
    }

    public AuthenticatedUser login(LoginCommand command) {
        String normalizedUsername = normalizeUsername(command.username());

        UserAccount userAccount = userRepository
                .findByNormalizedUsername(normalizedUsername)
                .orElseThrow(this::invalidCredentials);

        if (!userAccount.canLogin()) {
            throw invalidCredentials();
        }

        boolean passwordMatches = passwordEncoder.matches(
                command.password(),
                userAccount.passwordHash()
        );

        if (!passwordMatches) {
            throw invalidCredentials();
        }

        return createAuthenticatedUser(userAccount);
    }

    private AuthenticatedUser createAuthenticatedUser(UserAccount userAccount) {
        String accessToken = jwtTokenService.issueAccessToken(
                userAccount.id(),
                userAccount.username()
        );

        return new AuthenticatedUser(
                userAccount.id(),
                userAccount.username(),
                userAccount.displayName(),
                accessToken,
                "Bearer",
                jwtTokenService.expiresInSeconds()
        );
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }

        return username.trim().toLowerCase(Locale.ROOT);
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException();
    }
}
