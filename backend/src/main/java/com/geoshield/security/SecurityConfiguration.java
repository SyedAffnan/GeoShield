package com.geoshield.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.geoshield.config.SecurityProperties;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenService jwtTokenService) { return new JwtAuthenticationFilter(jwtTokenService); }
    @Bean RateLimitFilter rateLimitFilter() { return new RateLimitFilter(); }
    @Bean PasswordEncoder passwordEncoder(SecurityProperties securityProperties) { return new BCryptPasswordEncoder(securityProperties.bcryptStrength()); }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter, RateLimitFilter rateLimitFilter) throws Exception {
        return http.csrf(csrf -> csrf.disable()).sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/v1/auth/**").permitAll().anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class).build();
    }
}
