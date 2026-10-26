package com.waleed.naseej.repository;

import com.waleed.naseej.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Integer> {

    List<Notification> findTop30ByUserIdOrderByCreatedAtDesc(Integer userId);

    long countByUserIdAndReadAtIsNull(Integer userId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Integer userId, @Param("now") LocalDateTime now);
}
