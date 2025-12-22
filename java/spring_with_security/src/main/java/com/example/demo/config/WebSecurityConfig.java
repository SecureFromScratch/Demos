package com.example.demo.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class WebSecurityConfig {


    @Bean
    @Profile("dev")
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        http

        .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/", "/home", "/register", "/setup").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")                        
                        .requestMatchers("/upload").authenticated()  // Explicitly allow authenticated users
                        .anyRequest().authenticated())
                .formLogin((form) -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/tasks", true)
                        .permitAll())

                 .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())                    
                    // Spring Security is using XOR CSRF tokens by default in newer versions, 
                    // but Swagger is sending the raw token from the cookie instead of the XOR-encoded version.
                    // Configure Spring Security to use the simple CSRF token (not XOR):
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())  

                )               
                .logout((logout) -> logout.permitAll());
                

        return http.build();
    }

    @Bean
    @Profile("!dev")
    public SecurityFilterChain prodSecurityFilterChain(HttpSecurity http) throws Exception {
        http

        .authorizeHttpRequests((requests) -> requests
                        .requestMatchers("/", "/home", "/register", "/setup").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")                        
                        .anyRequest().authenticated())
                .formLogin((form) -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/tasks", true)
                        .permitAll())
                .logout((logout) -> logout.permitAll());

        return http.build();
    }
    

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Reasonable defaults for web apps
        int saltLength = 16; // bytes
        int hashLength = 32; // bytes
        int parallelism = 1; // currently 1 thread
        int memory = 1 << 16; // 64 MB
        int iterations = 3;

        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
                saltLength,
                hashLength,
                parallelism,
                memory,
                iterations);

        String idForEncode = "argon2";
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put(idForEncode, argon2);

        return new DelegatingPasswordEncoder(idForEncode, encoders);
    }
}