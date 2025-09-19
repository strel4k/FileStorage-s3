package com.crudapp.filestorage.controller;

import com.crudapp.filestorage.dto.EventDto;
import com.crudapp.filestorage.dto.PageResponse;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Events", description = "История событий по файлам")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/events")
public class EventController {

    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @Operation(summary = "Список событий (все/свои)")
    @GetMapping
    public Flux<EventDto> list(Authentication auth) {
        User current = extractUserOrThrow(auth);
        boolean modOrAdmin = hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_MODERATOR");
        return service.list(current, modOrAdmin);
    }

    @Operation(summary = "Постраничный список событий")
    @GetMapping("/paged")
    public Mono<PageResponse<EventDto>> listPaged(
            Authentication auth,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        User current = extractUserOrThrow(auth);
        boolean modOrAdmin = hasRole(auth, "ROLE_ADMIN") || hasRole(auth, "ROLE_MODERATOR");
        return service.listPaged(current, modOrAdmin, userId, status, page, size, sort);
    }

    private static User extractUserOrThrow(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User u)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return u;
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }
}
