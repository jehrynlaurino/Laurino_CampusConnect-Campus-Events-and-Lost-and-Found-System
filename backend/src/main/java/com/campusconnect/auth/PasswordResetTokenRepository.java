package com.campusconnect.auth;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken token set token.usedAt = :usedAt where token.user.id = :userId and token.usedAt is null")
    int invalidateActiveByUserId(@Param("userId") Long userId, @Param("usedAt") Instant usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PasswordResetToken token set token.usedAt = :usedAt where token.id = :id and token.usedAt is null and token.expiresAt > :now")
    int consumeIfActive(@Param("id") Long id, @Param("now") Instant now, @Param("usedAt") Instant usedAt);
}
