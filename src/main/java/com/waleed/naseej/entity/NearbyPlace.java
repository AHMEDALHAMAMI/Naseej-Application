package com.waleed.naseej.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "nearby_places",
        uniqueConstraints = @UniqueConstraint(columnNames = {"idea_id", "google_place_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NearbyPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @Column(name = "google_place_id", nullable = false)
    private String googlePlaceId;

    @Column(nullable = false)
    private String name;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private Double distanceKm;
}
