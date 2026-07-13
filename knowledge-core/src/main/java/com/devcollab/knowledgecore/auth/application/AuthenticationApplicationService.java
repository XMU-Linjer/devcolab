package com.devcollab.knowledgecore.auth.application;

import com.devcollab.knowledgecore.auth.application.exception.InvalidCredentialsException;
import com.devcollab.knowledgecore.auth.application.exception.InvalidRefreshTokenException;
import com.devcollab.knowledgecore.auth.application.exception.UsernameAlreadyExistsException;
import com.devcollab.knowledgecore.auth.domain.UserAccount;
import com.devcollab.knowledgecore.auth.domain.UserRepository;
import com.devcollab.knowledgecore.auth.domain.UserStatus;
import com.devcollab.knowledgecore.auth.infrastructure.JwtTokenService;
import com.devcollab.knowledgecore.auth.infrastructure.RefreshTokenService;
import com.devcollab.knowledgecore.auth.infrastructure.RefreshTokenService.RefreshCredentials;
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
    private final RefreshTokenService refreshTokenService;

    public AuthenticationApplicationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
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

        return createAuthenticatedUser(userRepository.save(userAccount));
    }

    public AuthenticatedUser login(LoginCommand command) {
        String normalizedUsername = normalizeUsername(command.username());

        UserAccount userAccount = userRepository
                .findByNormalizedUsername(normalizedUsername)
                .orElseThrow(this::invalidCredentials);

        if (!userAccount.canLogin()
                || !passwordEncoder.matches(
                        command.password(),
                        userAccount.passwordHash()
                )) {
            throw invalidCredentials();
        }

        return createAuthenticatedUser(userAccount);
    }

    public AuthenticatedUser refresh(
            String refreshToken,
            String csrfToken
    ) {
        RefreshCredentials credentials = refreshTokenService.rotate(
                refreshToken,
                csrfToken
        );

        UserAccount userAccount = userRepository
                .findById(credentials.userId())
                .filter(UserAccount::canLogin)
                .orElseThrow(() -> {
                    refreshTokenService.revoke(credentials.refreshToken());
                    return new InvalidRefreshTokenException();
                });

        return createAuthenticatedUser(userAccount, credentials);
    }

    public void logout(String refreshToken, String csrfToken) {
        refreshTokenService.revoke(refreshToken, csrfToken);
    }

    private AuthenticatedUser createAuthenticatedUser(
            UserAccount userAccount
    ) {
        return createAuthenticatedUser(
                userAccount,
                refreshTokenService.issue(userAccount.id())
        );
    }

    private AuthenticatedUser createAuthenticatedUser(
            UserAccount userAccount,
            RefreshCredentials credentials
    ) {
        String accessToken = jwtTokenService.issueAccessToken(
                userAccount.id(),
                userAccount.username(),
                credentials.sessionId()
        );

        return new AuthenticatedUser(
                userAccount.id(),
                userAccount.username(),
                userAccount.displayName(),
                accessToken,
                "Bearer",
                jwtTokenService.expiresInSeconds(),
                credentials.sessionId(),
                credentials.refreshToken(),
                credentials.csrfToken()
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
