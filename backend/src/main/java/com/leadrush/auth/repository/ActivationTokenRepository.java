package com.leadrush.auth.repository;

import com.leadrush.auth.entity.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {

    Optional<ActivationToken> findByToken(String token);

    /** Delete all unused tokens for a user (cleanup when resending) */
    void deleteByUserIdAndUsedFalse(UUID userId);
}
