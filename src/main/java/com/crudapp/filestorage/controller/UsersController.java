package com.crudapp.filestorage.controller;

import com.crudapp.filestorage.dto.UserDto;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@RestController
public class UsersController {

    private final UserRepository users;

    public UsersController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/users/me")
    public UserDto me(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        Object principal = auth.getPrincipal();

        if (principal instanceof User uEntity) {
            return new UserDto(uEntity.getId(), safeUsername(uEntity.getUsername()), roles);
        }

        if (principal instanceof UserDetails ud) {
            String username = ud.getUsername();
            User u = users.findByUsername(username)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "User not found by username: " + username));
            return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
        }

        if (principal instanceof Number num) {
            int id = num.intValue();
            User u = users.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "User not found by id: " + id));
            return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
        }

        if (principal instanceof String s && !s.isBlank()) {
            Optional<User> byId = parseIntSafe(s).flatMap(users::findById);
            if (byId.isPresent()) {
                User u = byId.get();
                return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
            }
            Optional<User> byUsername = users.findByUsername(s);
            if (byUsername.isPresent()) {
                User u = byUsername.get();
                return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
            }
            String name = auth.getName();
            if (name != null && !name.isBlank()) {
                Optional<User> byIdFromName = parseIntSafe(name).flatMap(users::findById);
                if (byIdFromName.isPresent()) {
                    User u = byIdFromName.get();
                    return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
                }
                User u = users.findByUsername(name)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED, "User not found by auth name: " + name));
                return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
            }
        }

        String name = auth.getName();
        if (name != null && !name.isBlank()) {
            Optional<User> byId = parseIntSafe(name).flatMap(users::findById);
            if (byId.isPresent()) {
                User u = byId.get();
                return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
            }
            User u = users.findByUsername(name)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "User not found by name: " + name));
            return new UserDto(u.getId(), safeUsername(u.getUsername()), roles);
        }

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unable to resolve current user");
    }

    private static Optional<Integer> parseIntSafe(String s) {
        try {
            return Optional.of(Integer.parseInt(s.trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static String safeUsername(String username) {
        if (username == null) return null;
        if (username.startsWith("com.") && username.contains("@")) {
            return null;
        }
        return username;
    }
}