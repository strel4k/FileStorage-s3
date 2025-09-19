package com.crudapp.filestorage.service;

import com.crudapp.filestorage.config.S3Props;
import com.crudapp.filestorage.dto.FileDto;
import com.crudapp.filestorage.dto.PageResponse;
import com.crudapp.filestorage.model.Event;
import com.crudapp.filestorage.model.EventStatus;
import com.crudapp.filestorage.model.FileStatus;
import com.crudapp.filestorage.model.StorageFile;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.repository.EventRepository;
import com.crudapp.filestorage.repository.StorageFileRepository;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileStorageService {

    private final S3AsyncClient s3;
    private final S3Presigner presigner;
    private final S3Props props;
    private final StorageFileRepository files;
    private final EventRepository events;

    public FileStorageService(S3AsyncClient s3, S3Presigner presigner, S3Props props,
                              StorageFileRepository files, EventRepository events) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
        this.files = files;
        this.events = events;
    }

    public Mono<FileDto> getById(Integer id) {
        return Mono.fromCallable(() -> files.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.<Mono<StorageFile>>map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"))))
                .map(this::toDto);
    }

    public Mono<FileDto> upload(FilePart filePart, User currentUser) {
        String originalName = StringUtils.cleanPath(filePart.filename());
        if (!StringUtils.hasText(originalName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty filename"));
        }

        String key = buildKey(currentUser.getId(), originalName);
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        var request = PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(contentType)
                .build();

        var bodyPublisher = filePart.content().map(db -> {
            ByteBuffer bb = db.asByteBuffer();
            DataBufferUtils.release(db);
            return bb;
        });

        return Mono.fromFuture(s3.putObject(request, AsyncRequestBody.fromPublisher(bodyPublisher)))
                .onErrorMap(e -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "S3 upload failed: " + e.getMessage(), e))
                .flatMap(resp -> {
                    String location = props.getPublicBaseUrl().replaceAll("/+$", "")
                            + "/" + props.getBucket() + "/" + key;

                    return Mono.<StorageFile>fromCallable(() -> persistFileAndEvent(originalName, location, currentUser))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .map(this::toDto);
    }

    @Transactional
    protected StorageFile persistFileAndEvent(String name, String location, User user) {
        StorageFile f = StorageFile.builder()
                .name(name)
                .location(location)
                .status(FileStatus.ACTIVE)
                .owner(user)
                .build();
        StorageFile saved = files.save(f);

        Event ev = Event.builder()
                .user(user)
                .file(saved)
                .status(EventStatus.CREATED)
                .createdAt(Instant.now())
                .build();
        events.save(ev);

        return saved;
    }

    public Mono<Void> delete(Integer fileId, User currentUser, boolean moderatorOrAdmin) {
        return Mono.fromCallable(() -> files.findById(fileId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.<Mono<StorageFile>>map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"))))
                .flatMap(sf -> {
                    if (!moderatorOrAdmin) {
                        if (sf.getOwner() == null || !sf.getOwner().getId().equals(currentUser.getId())) {
                            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner"));
                        }
                    }
                    String key = extractKeyFromLocation(sf.getLocation());
                    var req = DeleteObjectRequest.builder()
                            .bucket(props.getBucket())
                            .key(key)
                            .build();

                    return Mono.fromFuture(s3.deleteObject(req))
                            .onErrorResume(e -> Mono.empty())
                            .then(Mono.fromCallable(() -> markDeletedAndEvent(sf, currentUser))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .then();
                });
    }

    public Mono<FileDto> rename(Integer id, String newName, User currentUser, boolean moderatorOrAdmin) {
        if (!StringUtils.hasText(newName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty name"));
        }

        return Mono.fromCallable(() -> files.findById(id))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.<Mono<StorageFile>>map(Mono::just)
                        .orElseGet(() -> Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"))))
                .flatMap(sf -> {
                    if (!moderatorOrAdmin) {
                        if (sf.getOwner() == null || !sf.getOwner().getId().equals(currentUser.getId())) {
                            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner"));
                        }
                    }
                    sf.setName(newName);
                    return Mono.<StorageFile>fromCallable(() -> {
                                files.save(sf);
                                events.save(Event.builder()
                                        .user(currentUser)
                                        .file(sf)
                                        .status(EventStatus.UPDATED)
                                        .createdAt(Instant.now())
                                        .build());
                                return sf;
                            })
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .map(this::toDto);
    }

    public Mono<PageResponse<FileDto>> listPaged(User currentUser, boolean moderatorOrAdmin,
                                                 int page, int size, String sort) {
        return Mono.fromCallable(() -> {
                    Sort s = (!StringUtils.hasText(sort))
                            ? Sort.by("id").descending()
                            : Sort.by(Sort.Order.by(sort));
                    PageRequest pr = PageRequest.of(page, size, s);
                    Page<StorageFile> p = moderatorOrAdmin
                            ? files.findAll(pr)
                            : files.findAllByOwner_Id(currentUser.getId(), pr);

                    var content = p.getContent().stream().map(this::toDto).toList();
                    long total = p.getTotalElements();
                    return new PageResponse<>(content, page, size, total);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<String> presignDownload(Integer id, User currentUser, boolean moderatorOrAdmin, Duration ttl) {
        return Mono.fromCallable(() -> files.findById(id).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found")))
                .flatMap(sf -> {
                    if (!moderatorOrAdmin) {
                        if (sf.getOwner() == null || !sf.getOwner().getId().equals(currentUser.getId())) {
                            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner"));
                        }
                    }
                    String key = extractKeyFromLocation(sf.getLocation());
                    var getReq = GetObjectRequest.builder()
                            .bucket(props.getBucket())
                            .key(key)
                            .build();
                    var presignReq = GetObjectPresignRequest.builder()
                            .getObjectRequest(getReq)
                            .signatureDuration(ttl)
                            .build();
                    URL url = presigner.presignGetObject(presignReq).url();
                    return Mono.just(url.toString());
                });
    }

    public Flux<FileDto> list(User currentUser, boolean moderatorOrAdmin) {
        return Mono.fromCallable(() -> moderatorOrAdmin
                        ? files.findAll()
                        : files.findAllByOwner_Id(currentUser.getId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(this::toDto);
    }

    @Transactional
    protected Void markDeletedAndEvent(StorageFile f, User user) {
        f.setStatus(FileStatus.ARCHIVED);
        files.save(f);
        events.save(Event.builder()
                .user(user)
                .file(f)
                .status(EventStatus.DELETED)
                .createdAt(Instant.now())
                .build());
        return null;
    }

    private FileDto toDto(StorageFile f) {
        return new FileDto(f.getId(), f.getName(), f.getLocation(), f.getStatus().name());
    }

    private String buildKey(Integer userId, String originalName) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String safe = originalName.replaceAll("[^\\w\\-.]", "_");
        return "u" + userId + "/" + date + "/" + UUID.randomUUID() + "_" + safe;
    }

    private String extractKeyFromLocation(String location) {
        String base = props.getPublicBaseUrl().replaceAll("/+$", "") + "/" + props.getBucket() + "/";
        if (location.startsWith(base)) {
            return location.substring(base.length());
        }
        int i = location.indexOf(props.getBucket() + "/");
        if (i >= 0) return location.substring(i + props.getBucket().length() + 1);
        return location;
    }
}