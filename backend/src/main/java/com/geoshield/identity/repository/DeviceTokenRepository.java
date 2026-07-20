package com.geoshield.identity.repository;

import com.geoshield.identity.entity.DeviceToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByFcmToken(String fcmToken);
    Optional<DeviceToken> findByFcmTokenAndUserId(String fcmToken, UUID userId);

    @Modifying
    @Query("update DeviceToken token set token.active = false where token.user.id = :userId and token.active = true")
    void deactivateAllForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("update DeviceToken token set token.active = false where token.user.id = :userId and token.fcmToken <> :fcmToken and token.active = true")
    void deactivateOtherTokensForUser(@Param("userId") UUID userId, @Param("fcmToken") String fcmToken);
}
