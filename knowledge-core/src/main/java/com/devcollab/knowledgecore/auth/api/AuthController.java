package com.devcollab.knowledgecore.auth.api;

import com.devcollab.knowledgecore.auth.application.AuthenticatedUser;
import com.devcollab.knowledgecore.auth.application.AuthenticationApplicationService;
import com.devcollab.knowledgecore.auth.application.LoginCommand;
import com.devcollab.knowledgecore.auth.application.RegisterCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.devcollab.knowledgecore.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationApplicationService authenticationService;

    public AuthController(
            AuthenticationApplicationService authenticationService
    ) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        RegisterCommand command = new RegisterCommand(
                request.username(),
                request.displayName(),
                request.password()
        );

        AuthenticatedUser authenticatedUser =
                authenticationService.register(command);

        return AuthResponse.from(authenticatedUser);
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginCommand command = new LoginCommand(
                request.username(),
                request.password()
        );

        AuthenticatedUser authenticatedUser =
                authenticationService.login(command);

        return AuthResponse.from(authenticatedUser);
    }
    @GetMapping("/me")
    public CurrentUserResponse currentUser(
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return CurrentUserResponse.from(currentUser);
    }
}