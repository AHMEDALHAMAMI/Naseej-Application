package com.waleed.naseej.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"idea_id", "google_place_id"}))
public class NearbyPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // FK -> Idea.id (an idea has many competitors)
    @NotNull(message = "Idea id is required")
    @Column(name = "idea_id", nullable = false)
    private Integer ideaId;

    @NotEmpty(message = "Google place id is required")
    @Column(name = "google_place_id", nullable = false)
    private String googlePlaceId;

    @NotEmpty(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @Size(max = 50, message = "Category must be at most 50 characters")
    @Column(length = 50)
    private String category;

    @NotNull(message = "Distance is required")
    @PositiveOrZero(message = "Distance can't be negative")
    @Column(nullable = false)
    private Double distanceKm;
}
