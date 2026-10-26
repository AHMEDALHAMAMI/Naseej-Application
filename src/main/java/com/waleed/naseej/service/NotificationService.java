package com.waleed.naseej.service;

import com.waleed.naseej.dto.out.NotificationListResponse;
import com.waleed.naseej.dto.out.NotificationResponse;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Notification;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * In-site notifications (the bell in the header). Sending one never breaks the action that caused it:
 * if saving fails, it is logged and the adoption, vote or project update still goes through.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String IDEA_ADOPTED = "IDEA_ADOPTED";
    // To a resident who voted for the idea (IDEA_ADOPTED goes to the person who proposed it)
    public static final String VOTED_IDEA_ADOPTED = "VOTED_IDEA_ADOPTED";
    public static final String VOTE_TARGET_REACHED = "VOTE_TARGET_REACHED";
    public static final String PROJECT_STAGE = "PROJECT_STAGE";

    private final NotificationRepository notificationRepository;

    public void notify(User user, String type, Idea idea, String detail, String link) {
        if (user == null) {
            return;
        }
        try {
            notificationRepository.save(Notification.builder()
                    .user(user)
                    .type(type)
                    .ideaId(idea != null ? idea.getId() : null)
                    .ideaTitle(idea != null ? idea.getTitle() : null)
                    .detail(detail)
                    .link(link)
                    .createdAt(LocalDateTime.now())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Couldn't save a {} notification for user {}: {}", type, user.getId(), ex.getMessage());
        }
    }

    public NotificationListResponse mine(User currentUser) {
        return NotificationListResponse.builder()
                .unread(notificationRepository.countByUserIdAndReadAtIsNull(currentUser.getId()))
                .items(notificationRepository.findTop30ByUserIdOrderByCreatedAtDesc(currentUser.getId()).stream()
                        .map(NotificationService::toResponse)
                        .toList())
                .build();
    }

    public long unread(User currentUser) {
        return notificationRepository.countByUserIdAndReadAtIsNull(currentUser.getId());
    }

    @Transactional
    public void markAllRead(User currentUser) {
        notificationRepository.markAllRead(currentUser.getId(), LocalDateTime.now());
    }

    @Transactional
    public void markRead(User currentUser, Integer id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        if (!n.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("This notification belongs to another member");
        }
        if (n.getReadAt() == null) {
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
    }

    private static NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .ideaId(n.getIdeaId())
                .ideaTitle(n.getIdeaTitle())
                .detail(n.getDetail())
                .link(n.getLink())
                .read(n.getReadAt() != null)
                .createdAt(n.getCreatedAt())
                .build();
    }
}
