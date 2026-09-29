package com.example.orderservice.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Username lookup backs login; existence lookup makes bootstrap/provisioning idempotent and duplicate-safe. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}