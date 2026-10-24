package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A city bubble on the investor map. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityStatResponse {

    private String city;
    private Double latitude;
    private Double longitude;
    private Long opportunities;
    private Integer neighborhoods;
}
