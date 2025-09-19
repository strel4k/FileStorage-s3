package com.crudapp.filestorage.repository;

import com.crudapp.filestorage.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Query("select u from User u left join fetch u.roles where u.username = :username")
    Optional<User> findWithRolesByUsername(@Param("username") String username);

    @Query("select u.id from User u where u.username = :username")
    Optional<Integer> findIdByUsername(@Param("username") String username);
}
