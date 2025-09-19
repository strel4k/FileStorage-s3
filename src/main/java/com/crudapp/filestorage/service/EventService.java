package com.crudapp.filestorage.service;

import com.crudapp.filestorage.dto.EventDto;
import com.crudapp.filestorage.dto.PageResponse;
import com.crudapp.filestorage.model.EventStatus;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.repository.EventRepository;
import com.crudapp.filestorage.repository.EventRepository.EventProjection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
public class EventService {
    private final EventRepository repo;

    public EventService(EventRepository repo) {
        this.repo = repo;
    }

    public Flux<EventDto> list(User current, boolean modOrAdmin) {
        boolean all = modOrAdmin;
        return Mono.fromCallable(() -> repo.findAllForUser(current.getId(), all))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(this::toDto);
    }

    public Mono<PageResponse<EventDto>> listPaged(User current, boolean modOrAdmin,
                                                  Integer userId, String status,
                                                  int page, int size, String sort) {
        return Mono.fromCallable(() -> {
                    Pageable pageable = PageRequest.of(page, size);
                    boolean all = modOrAdmin;

                    List<EventRepository.EventProjection> rows =
                            repo.findPaged(current.getId(), all, userId, status, pageable);
                    long total = repo.countPaged(current.getId(), all, userId, status);

                    List<EventDto> content = rows.stream().map(this::toDto).toList();
                    return new PageResponse<>(content, page, size, total);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private EventDto toDto(EventRepository.EventProjection p) {
        return new EventDto(
                p.getId(),
                p.getUserId(),
                p.getFileId(),
                p.getStatus(),
                null,
                p.getCreatedAt()
        );
    }
}