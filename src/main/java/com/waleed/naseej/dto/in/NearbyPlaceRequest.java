package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NearbyPlaceRequest {

    @NotNull(message = "Idea is required")
    private Integer ideaId;

    @NotEmpty(message = "Google place id is required")
    private String googlePlaceId;

    @NotEmpty(message = "Name is required")
    private String name;

    @Size(max = 50, message = "Category must be at most 50 characters")
    private String category;

    @NotNull(message = "Distance is required")
    @PositiveOrZero(message = "Distance can't be negative")
    private Double distanceKm;
}
