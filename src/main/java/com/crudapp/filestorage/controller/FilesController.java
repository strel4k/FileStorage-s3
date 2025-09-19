package com.crudapp.filestorage.controller;

import com.crudapp.filestorage.dto.FileDto;
import com.crudapp.filestorage.dto.FileUpdateRequest;
import com.crudapp.filestorage.dto.PageResponse;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Tag(name = "Files", description = "Загрузка, получение, переименование и удаление файлов")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/files")
public class FileController {

    private final FileStorageService storage;

    public FileController(FileStorageService storage) {
        this.storage = storage;
    }



    @Operation(summary = "Загрузить файл", description = "Создаёт файл в S3 и Event(CREATED)")
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<FileDto> upload(@RequestPart("file") FilePart file,
                                @AuthenticationPrincipal Object principal,
                                Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User u))
            return Mono.error(new RuntimeException("Unauthorized"));
        return storage.upload(file, u);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Integer id, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User u))
            return Mono.error(new RuntimeException("Unauthorized"));

        boolean modOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));

        return storage.delete(id, u, modOrAdmin);
    }

    @Operation(summary = "Получить файл по ID")
    @GetMapping("/{id}")
    public Mono<FileDto> getById(@PathVariable Integer id, Authentication auth) {
        if (auth == null) return Mono.error(new RuntimeException("Unauthorized"));
        return storage.getById(id);
    }

    @Operation(summary = "Список файлов (все/свои)")
    @GetMapping
    public Flux<FileDto> list(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User u))
            return Flux.error(new RuntimeException("Unauthorized"));

        boolean modOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));

        return storage.list(u, modOrAdmin);
    }

    @Operation(summary = "Переименовать файл", description = "Создаёт Event(UPDATED)")
    @PutMapping("/{id}")
    public Mono<FileDto> rename(@PathVariable Integer id,
                                @RequestBody @Validated FileUpdateRequest req,
                                Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof User u))
            return Mono.error(new RuntimeException("Unauthorized"));

        boolean modOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));

        return storage.rename(id, req.name(), u, modOrAdmin);
    }

    @Operation(summary = "Постраничный список файлов")
    @GetMapping("/paged")
    public Mono<PageResponse<FileDto>> listPaged(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        if (auth == null || !(auth.getPrincipal() instanceof User u))
            return Mono.error(new RuntimeException("Unauthorized"));
        boolean modOrAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MODERATOR"));
        return storage.listPaged(u, modOrAdmin, page, size, sort);
    }


}