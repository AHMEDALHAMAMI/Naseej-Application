package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Something that happened to a member's idea or project: an investor adopted it, it reached its vote target,
 * the project moved to a new stage. The text is written by the website in the reader's language from the type,
 * so only the facts are stored (the idea's title and the stage at that moment).
 */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_user", columnList = "user_id, read_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // IDEA_ADOPTED, VOTE_TARGET_REACHED, PROJECT_STAGE
    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "idea_id")
    private Integer ideaId;

    @Column(name = "idea_title", length = 200)
    private String ideaTitle;

    // e.g. the project stage ("Licensing") or how the idea was taken (PAID / PARTNERSHIP)
    @Column(length = 120)
    private String detail;

    // Where the notification leads, a page of the website (e.g. "projects.html")
    @Column(length = 200)
    private String link;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
