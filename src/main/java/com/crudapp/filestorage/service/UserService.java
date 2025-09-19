package com.crudapp.filestorage.service;

import com.crudapp.filestorage.model.Role;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.model.UserStatus;
import com.crudapp.filestorage.repository.RoleRepository;
import com.crudapp.filestorage.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Set;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users; this.roles = roles; this.encoder = encoder;
    }

    public Mono<User> registerUser(String username, String rawPassword) {
        return Mono.fromCallable(() -> {
            if (users.existsByUsername(username)) {
                throw new IllegalArgumentException("Username already exists");
            }
            Role userRole = roles.findByName("USER").orElseThrow();
            User u = User.builder()
                    .username(username)
                    .passwordHash(encoder.encode(rawPassword))
                    .status(UserStatus.ACTIVE)
                    .roles(Set.of(userRole))
                    .build();
            users.save(u);
            return users.findWithRolesByUsername(username).orElseThrow();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<User> getByUsername(String username) {
        return Mono.fromCallable(() -> users.findWithRolesByUsername(username).orElse(null))
                .subscribeOn(Schedulers.boundedElastic());
    }
}