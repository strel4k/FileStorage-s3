package com.crudapp.filestorage.controller;

import com.crudapp.filestorage.dto.FileDto;
import com.crudapp.filestorage.dto.FileUpdateRequest;
import com.crudapp.filestorage.dto.PageResponse;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Tag(name = "Files", description = "Загрузка, получение, переименование и удаление файлов")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/files")
public class FilesController {

    private final FileStorageService storage;

    public FilesController(FileStorageService storage) {
        this.storage = storage;
    }

    private static User currentUserOr401(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User u)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return u;
    }

    private static boolean isModOrAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
    }

    @Operation(summary = "Загрузить файл", description = "Создаёт файл в S3 и Event(CREATED)")
    @PostMapping(path = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FileDto> upload(@RequestPart("file") FilePart file, Authentication auth) {
        User u = currentUserOr401(auth);
        return storage.upload(file, u);
    }

    @Operation(summary = "Удалить (архивировать) файл", description = "Помечает файл ARCHIVED и создаёт Event(DELETED)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Integer id, Authentication auth) {
        User u = currentUserOr401(auth);
        boolean modOrAdmin = isModOrAdmin(auth);
        return storage.delete(id, u, modOrAdmin);
    }

    @Operation(summary = "Получить файл по ID")
    @GetMapping("/{id}")
    public Mono<FileDto> getById(@PathVariable Integer id, Authentication auth) {
        currentUserOr401(auth);
        return storage.getById(id);
    }

    @Operation(summary = "Список файлов (свои для USER, все для MODERATOR/ADMIN)")
    @GetMapping(path = "")
    public Flux<FileDto> list(Authentication auth) {
        User u = currentUserOr401(auth);
        boolean modOrAdmin = isModOrAdmin(auth);
        return storage.list(u, modOrAdmin);
    }

    @Operation(summary = "Переименовать файл", description = "Создаёт Event(UPDATED)")
    @PutMapping("/{id}")
    public Mono<FileDto> rename(@PathVariable Integer id,
                                @RequestBody @Valid FileUpdateRequest req,
                                Authentication auth) {
        User u = currentUserOr401(auth);
        boolean modOrAdmin = isModOrAdmin(auth);
        return storage.rename(id, req.name(), u, modOrAdmin);
    }

    @Operation(summary = "Постраничный список файлов")
    @GetMapping("/paged")
    public Mono<PageResponse<FileDto>> listPaged(Authentication auth,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String sort) {
        User u = currentUserOr401(auth);
        boolean modOrAdmin = isModOrAdmin(auth);
        return storage.listPaged(u, modOrAdmin, page, size, sort);
    }

    @Operation(summary = "Скачать файл по ID (302 на presigned URL)")
    @GetMapping("/{id}/download")
    public Mono<org.springframework.http.ResponseEntity<Void>> download(@PathVariable Integer id, Authentication auth) {
        User u = currentUserOr401(auth);
        boolean modOrAdmin = isModOrAdmin(auth);
        return storage.presignDownload(id, u, modOrAdmin, Duration.ofMinutes(15))
                .map(url -> org.springframework.http.ResponseEntity.status(302)
                        .header(HttpHeaders.LOCATION, url)
                        .build());
    }
}