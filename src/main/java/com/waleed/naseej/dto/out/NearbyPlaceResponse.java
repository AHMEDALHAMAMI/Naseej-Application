package com.waleed.naseej.dto.out;

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
public class NearbyPlaceResponse {
    private Integer id;
    private Integer ideaId;
    private String googlePlaceId;
    private String name;
    private String category;
    private Double distanceKm;
}
