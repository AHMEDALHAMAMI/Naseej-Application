package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A resident's vote for an idea inside their voting radius.
 * The idea's creator is its only founding member; everyone else who supports it is a voter.
 */
@Entity
@Table(
        name = "votes",
        uniqueConstraints = @UniqueConstraint(name = "uk_vote_user_idea", columnNames = {"user_id", "idea_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    // Distance between the voter and the idea when the vote was cast
    private Double distanceKm;

    // The saved address that made the voter eligible (null for votes from before addresses existed)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private Address address;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
