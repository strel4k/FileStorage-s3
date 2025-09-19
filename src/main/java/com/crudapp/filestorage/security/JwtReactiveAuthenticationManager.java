package com.crudapp.filestorage.security;

import com.crudapp.filestorage.model.Role;
import com.crudapp.filestorage.repository.UserRepository;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtTokenService tokenService;
    private final UserRepository userRepository;

    public JwtReactiveAuthenticationManager(JwtTokenService tokenService, UserRepository userRepository) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        final String token = String.valueOf(authentication.getCredentials());

        return Mono.fromCallable(() -> tokenService.parseAndVerify(token))
                .flatMap((SignedJWT jwt) -> {
                    try {
                        String username = jwt.getJWTClaimsSet().getSubject();
                        return Mono.fromCallable(() -> userRepository.findByUsername(username).orElse(null))
                                .subscribeOn(Schedulers.boundedElastic());
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })
                .flatMap(user -> {
                    if (user == null) return Mono.empty();
                    List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                            .map(Role::getName)
                            .map(r -> "ROLE_" + r)
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                    return Mono.just(new UsernamePasswordAuthenticationToken(user, token, authorities));
                });
    }
}