package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "founding_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_founding_member_user_idea",
                        columnNames = {"user_id", "idea_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FoundingMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @Column(nullable = false)
    private Integer memberNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ideaOwner = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean consentGiven = false;
}
