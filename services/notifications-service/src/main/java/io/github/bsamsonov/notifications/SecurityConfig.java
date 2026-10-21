package io.github.bsamsonov.notifications;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. The whole access policy of the service is in this one method.
 * <p>
 * Each request carries an access token (JWT) issued by Keycloak. Spring checks its signature, issuer,
 * audience and expiry (settings in application.yaml) and turns the token's {@code permissions} claim
 * into authorities, e.g. {@code notifications:read}. No token or an invalid one: 401. Missing permission: 403.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        // probes and metrics scraping come without a token
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.GET, "/notifications/**").hasAuthority("notifications:read")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(server -> server.jwt(Customizer.withDefaults()))
                // a stateless API: no session, no cookies, so no CSRF protection is needed
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }
}
