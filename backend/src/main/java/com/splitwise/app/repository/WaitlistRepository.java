package com.splitwise.app.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.splitwise.app.entity.Waitlist;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    boolean existsByEmail(String email);

    Optional<Waitlist> findByEmail(String email);
}
