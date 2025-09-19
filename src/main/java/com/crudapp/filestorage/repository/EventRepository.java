package com.crudapp.filestorage.repository;

import com.crudapp.filestorage.model.Event;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Integer> {

    interface EventProjection {
        Integer getId();
        Integer getUserId();
        Integer getFileId();
        String  getStatus();
        java.time.Instant getCreatedAt();
    }

    @Query("""
        select e.id as id,
               u.id as userId,
               f.id as fileId,
               e.status as status,
               e.createdAt as createdAt
        from Event e
        join e.user u
        join e.file f
        where (:all = true or u.id = :currentUserId)
        order by e.createdAt desc, e.id desc
        """)
    List<EventProjection> findAllForUser(@Param("currentUserId") Integer currentUserId,
                                         @Param("all") boolean all);

    @Query("""
        select e.id as id,
               u.id as userId,
               f.id as fileId,
               e.status as status,
               e.createdAt as createdAt
        from Event e
        join e.user u
        join e.file f
        where (:all = true or u.id = :currentUserId)
          and (:userId is null or u.id = :userId)
          and (:status is null or e.status = :status)
        order by e.createdAt desc, e.id desc
        """)
    List<EventProjection> findPaged(@Param("currentUserId") Integer currentUserId,
                                    @Param("all") boolean all,
                                    @Param("userId") Integer userId,
                                    @Param("status") String status,
                                    org.springframework.data.domain.Pageable pageable);

    @Query("""
        select count(e)
        from Event e
        join e.user u
        where (:all = true or u.id = :currentUserId)
          and (:userId is null or u.id = :userId)
          and (:status is null or e.status = :status)
        """)
    long countPaged(@Param("currentUserId") Integer currentUserId,
                    @Param("all") boolean all,
                    @Param("userId") Integer userId,
                    @Param("status") String status);

    boolean existsByUser_Id(Integer userId);
}