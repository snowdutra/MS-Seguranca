package br.com.msav02.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JdbcUserDetailsManager jdbcUserDetailsManager(DataSource dataSource) {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        // Usa a tabela usuarios (fonte unica da verdade) e respeita o bloqueio.
        manager.setUsersByUsernameQuery("""
            SELECT u.username, u.senha AS password,
                   (u.enabled AND (la.locked IS NULL OR la.locked = FALSE)) AS enabled
            FROM usuarios u
            LEFT JOIN login_attempts la ON u.username = la.username
            WHERE u.username = ?
            """);
        manager.setAuthoritiesByUsernameQuery(
                "SELECT username, authority FROM authorities WHERE username = ?");
        manager.setUserExistsSql("SELECT username FROM usuarios WHERE username = ?");
        manager.setCreateUserSql("INSERT INTO usuarios (username, senha, enabled) VALUES (?, ?, ?)");
        manager.setUpdateUserSql("UPDATE usuarios SET senha = ?, enabled = ? WHERE username = ?");
        manager.setChangePasswordSql("UPDATE usuarios SET senha = ? WHERE username = ?");
        manager.setDeleteUserSql("DELETE FROM usuarios WHERE username = ?");
        return manager;
    }

    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService userDetailsService,
                                                        PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            DaoAuthenticationProvider provider) throws Exception {
        return http
            // REST sem cookies de sessao; para este laboratorio utiliza HTTP Basic + HTTPS em producao.
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(provider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/login").permitAll()
                .requestMatchers(HttpMethod.PUT, "/trocarsenha/*").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.POST, "/").hasAuthority("ROLE_MANUTENCAO")
                .requestMatchers(HttpMethod.GET, "/").hasAuthority("ROLE_MANUTENCAO")
                .requestMatchers(HttpMethod.PUT, "/unlock/*").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .build();
    }
}
