package com.crudapp.filestorage.config;

import com.crudapp.filestorage.model.Role;
import com.crudapp.filestorage.model.User;
import com.crudapp.filestorage.model.UserStatus;
import com.crudapp.filestorage.repository.RoleRepository;
import com.crudapp.filestorage.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
public class DataInit {

    @Bean
    CommandLineRunner seedAdmin(UserRepository users, RoleRepository roles, PasswordEncoder enc) {
        return args -> {
            if (users.findByUsername("admin").isEmpty()) {
                Role adminRole = roles.findByName("ADMIN").orElseThrow();
                users.save(User.builder()
                        .username("admin")
                        .passwordHash(enc.encode("admin123"))
                        .status(UserStatus.ACTIVE)
                        .roles(Set.of(adminRole))
                        .build());
                System.out.println("[INIT] Admin created: admin/admin123");
            }
        };
    }
}