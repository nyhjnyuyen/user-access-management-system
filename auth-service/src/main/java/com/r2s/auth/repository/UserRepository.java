package com.r2s.auth.repository;

import com.r2s.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Transactional
    void deleteByUsername(String username);
    Optional<User> findByEmail(String email);
    List<User> findByEnabledFalseAndCreatedAtBefore(LocalDateTime time);
}

