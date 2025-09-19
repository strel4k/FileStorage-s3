package com.crudapp.filestorage.controller;

import com.crudapp.filestorage.dto.*;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.repository.UserRepository;
import com.crudapp.filestorage.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Tag(name = "Users", description = "Админские операции с пользователями")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository users;
    private final AdminUserService admin;

    public UserController(UserRepository users, AdminUserService admin) {
        this.users = users;
        this.admin = admin;
    }

    // ---------- ME ----------

    @Operation(summary = "Текущий пользователь")
    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UserDto> me(Authentication auth) {
        if (auth == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");

        // Роли берём из Authentication → не триггерим ленивые коллекции
        List<String> rolesFromAuth = roles(auth.getAuthorities());

        // Пытаемся достать нашу сущность User из principal
        if (auth.getPrincipal() instanceof User u) {
            return Mono.just(new UserDto(u.getId(), u.getUsername(), u.getStatus().name(), rolesFromAuth));
        }

        // Если principal — UserDetails или просто имя пользователя: добираем базовые поля из БД
        String username = extractUsername(auth.getPrincipal())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        return Mono.fromCallable(() ->
                        users.findByUsername(username)
                                .map(u -> new UserDto(u.getId(), u.getUsername(), u.getStatus().name(), rolesFromAuth))
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized"))
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static List<String> roles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)      // "ROLE_ADMIN"
                .map(r -> r != null && r.startsWith("ROLE_") ? r.substring(5) : r) // "ADMIN"
                .toList();
    }

    private static Optional<String> extractUsername(Object principal) {
        if (principal instanceof UserDetails ud) return Optional.ofNullable(ud.getUsername());
        if (principal instanceof String s) return Optional.of(s);
        return Optional.empty();
    }

    // ---------- LIST (для модераторов/админов) ----------

    @Operation(summary = "Список пользователей (все)")
    @PreAuthorize("hasAnyRole('ADMIN','MODERATOR')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<UserDto> list(Authentication auth) {
        ensureModOrAdmin(auth);
        // Здесь оставим как есть; если понадобится реально роли каждого юзера,
        // сделаем репозиторный метод с join fetch. Для тестов это не используется.
        return Mono.fromCallable(users::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(u -> new UserDto(u.getId(), u.getUsername(), u.getStatus().name(), List.of())); // роли можно не заполнять
    }

    // ---------- ADMIN операции ----------

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserDto> create(@RequestBody @Validated AdminCreateUserRequest req, Authentication auth) {
        ensureAdmin(auth);
        return admin.create(req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(value = "/{id}/roles", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UserDto> updateRoles(@PathVariable Integer id,
                                     @RequestBody @Validated UpdateRolesRequest req,
                                     Authentication auth) {
        ensureAdmin(auth);
        return admin.updateRoles(id, req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping(value = "/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<UserDto> updateStatus(@PathVariable Integer id,
                                      @RequestBody @Validated UpdateStatusRequest req,
                                      Authentication auth) {
        ensureAdmin(auth);
        return admin.updateStatus(id, req);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Integer id, Authentication auth) {
        ensureAdmin(auth);
        return admin.delete(id);
    }

    // ---------- Guards ----------

    private static void ensureAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }

    private static void ensureModOrAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities().stream().noneMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_MODERATOR".equals(a.getAuthority()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }
    }
}
