package com.example.orderservice.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminBootstrap(AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${security.bootstrap.username:}") String username,
                          @Value("${security.bootstrap.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            log.warn("No bootstrap administrator configured; create one through an out-of-band database provisioning process");
            return;
        }
        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Both AUTH_BOOTSTRAP_USERNAME and AUTH_BOOTSTRAP_PASSWORD must be configured");
        }
        AuthService.validatePassword(password);
        String normalizedUsername = username.trim().toLowerCase();
        if (userRepository.existsByUsername(normalizedUsername)) return;
        userRepository.save(new AppUser(normalizedUsername, passwordEncoder.encode(password), UserRole.ADMIN));
        log.info("Bootstrapped administrator account username={}", normalizedUsername);
    }
}