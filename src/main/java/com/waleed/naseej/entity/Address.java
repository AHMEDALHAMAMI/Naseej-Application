package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A saved address. For residents it decides where they can vote (ideas within 2 km of any of their addresses);
 * the device's live location is never used for that. Residents keep 1–2 addresses with exactly one primary,
 * investors may keep one optional office address that is never used for voting.
 * The pin is required; the neighborhood comes from the pin; the Saudi National Address fields are optional.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // e.g. "Home", "Family home"
    @Column(length = 40)
    private String label;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "neighborhood_id", nullable = false)
    private Neighborhood neighborhood;

    // ---------- Saudi National Address (optional, format-checked only) ----------

    @Column(length = 4)
    private String buildingNumber;

    @Column(length = 120)
    private String street;

    @Column(length = 100)
    private String district;

    @Column(length = 5)
    private String postalCode;

    @Column(length = 4)
    private String additionalNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean primaryAddress = false;

    // Made from an older account's neighborhood center: the owner must place the pin before voting
    @Column(nullable = false)
    @Builder.Default
    private Boolean needsConfirmation = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // When the pin was last moved
    private LocalDateTime movedAt;
}
