package com.crudapp.filestorage.controller;

import com.crudapp.filestorage.dto.*;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.security.JwtTokenService;
import com.crudapp.filestorage.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder encoder;
    private final JwtTokenService tokens;
    private final long expiresMinutes;

    public AuthController(UserService userService,
                          PasswordEncoder encoder,
                          JwtTokenService tokens,
                          @Value("${jwt.expiresMinutes:120}") long expiresMinutes) {
        this.userService = userService;
        this.encoder = encoder;
        this.tokens = tokens;
        this.expiresMinutes = expiresMinutes;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<AuthResponse> register(@RequestBody RegisterRequest req) {
        return userService.registerUser(req.username(), req.password())
                .onErrorMap(IllegalArgumentException.class, e ->
                        new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()))
                .flatMap(this::issueToken);
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@RequestBody LoginRequest req) {
        return userService.getByUsername(req.username())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials")))
                .flatMap(u -> encoder.matches(req.password(), u.getPasswordHash())
                        ? issueToken(u)
                        : Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bad credentials")));
    }

    private Mono<AuthResponse> issueToken(User u) {
        List<String> roles = u.getRoles().stream().map(r -> r.getName()).toList();
        String token = tokens.generateToken(u.getId(), u.getUsername(), roles,
                Map.of("status", u.getStatus().name()));

        return Mono.just(new AuthResponse(
                token,
                "Bearer",
                expiresMinutes * 60,
                u.getId(),
                u.getUsername(),
                roles
        ));
    }
}