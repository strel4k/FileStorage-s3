package com.crudapp.filestorage.service;

import com.crudapp.filestorage.dto.AdminCreateUserRequest;
import com.crudapp.filestorage.dto.UpdateRolesRequest;
import com.crudapp.filestorage.dto.UpdateStatusRequest;
import com.crudapp.filestorage.dto.UserDto;
import com.crudapp.filestorage.model.Role;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.model.UserStatus;
import com.crudapp.filestorage.repository.EventRepository;
import com.crudapp.filestorage.repository.RoleRepository;
import com.crudapp.filestorage.repository.StorageFileRepository;
import com.crudapp.filestorage.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final StorageFileRepository files;
    private final EventRepository events;
    private final PasswordEncoder encoder;


    public AdminUserService(UserRepository users, RoleRepository roles, StorageFileRepository files, EventRepository events, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.files = files;
        this.events = events;
        this.encoder = encoder;
    }

    public Mono<UserDto> create(AdminCreateUserRequest req) {
        return Mono.fromCallable(() -> {
            if(users.existsByUsername(req.username())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
            }
            Set<Role> roleSet = resolveRoles(req.roles());
            UserStatus status = parseStatusOrDefault(req.status(), UserStatus.ACTIVE);

            User u = new User();
            u.setUsername(req.username());
            u.setPasswordHash(encoder.encode(req.password()));
            u.setStatus(status);
            u.setRoles(roleSet);

            users.save(u);
            User saved = users.findWithRolesByUsername(req.username()).orElseThrow();
            return toDto(saved);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<UserDto> updateRoles(Integer userId, UpdateRolesRequest req) {
        return Mono.fromCallable(() -> users.findById(userId).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(u -> Mono.fromCallable(()-> {
                    u.setRoles(resolveRoles(req.roles()));
                    users.save(u);
                    User reloaded = users.findWithRolesByUsername(u.getUsername()).orElseThrow();
                    return toDto(reloaded);
                        }).subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<UserDto> updateStatus(Integer userId, UpdateStatusRequest req) {
        return Mono.fromCallable(() -> users.findById(userId).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(u -> Mono.fromCallable(() -> {
                    u.setStatus(parseStatusOrDefault(req.status(), u.getStatus()));
                    users.save(u);
                    User reloaded = users.findWithRolesByUsername(u.getUsername()).orElseThrow();
                    return toDto(reloaded);
                }).subscribeOn(Schedulers.boundedElastic()));
    }

    public Mono<Void> delete(Integer userId) {
        return Mono.fromCallable(() -> users.findById(userId).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")))
                .flatMap(u -> Mono.fromCallable(() -> {
                    if (files.existsByOwner_Id(userId) || events.existsByUser_Id(userId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "User has files or events");
                    }
                    users.deleteById(userId);
                    return null;
                }).subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    private Set<Role> resolveRoles(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of(roles.findByName("USER").orElseThrow());
        }
        Set<Role> set = new HashSet<>();
        for (String n : names) {
            Role r = roles.findByName(n).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + n));
            set.add(r);
        }
        return set;
    }
    private static UserStatus parseStatusOrDefault(String s, UserStatus def) {
        if (s == null || s.isBlank()) return def;
        try { return UserStatus.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + s); }
    }

    private static UserDto toDto(User u) {
        List<String> roles = u.getRoles().stream().map(r -> r.getName()).toList();
        return new UserDto(u.getId(), u.getUsername(), roles);
    }
}
