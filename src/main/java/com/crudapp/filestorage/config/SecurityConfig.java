package com.crudapp.filestorage.config;

import com.crudapp.filestorage.repository.UserRepository;
import com.crudapp.filestorage.security.BearerTokenServerAuthenticationConverter;
import com.crudapp.filestorage.security.JwtReactiveAuthenticationManager;
import com.crudapp.filestorage.security.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;

@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            JwtTokenService tokenService,
                                                            UserRepository userRepository,
                                                            BearerTokenServerAuthenticationConverter converter) {
        var authManager = new JwtReactiveAuthenticationManager(tokenService, userRepository);

        AuthenticationWebFilter bearer = new AuthenticationWebFilter(authManager);
        bearer.setServerAuthenticationConverter(converter);

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .authorizeExchange(ex -> ex

                        .pathMatchers("/auth/**").permitAll()
                        .pathMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .pathMatchers("/users/me").authenticated()

                        .pathMatchers(HttpMethod.GET, "/users/**").hasAnyRole("ADMIN","MODERATOR")
                        .pathMatchers("/users/**").hasRole("ADMIN")

                        .anyExchange().authenticated()
                )
                .addFilterAt(bearer, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}