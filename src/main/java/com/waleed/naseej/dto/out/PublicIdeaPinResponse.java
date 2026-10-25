package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Landing map pin: title and place only. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicIdeaPinResponse {

    private Integer id;
    private String title;
    private String category;
    private String neighborhoodName;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
}
