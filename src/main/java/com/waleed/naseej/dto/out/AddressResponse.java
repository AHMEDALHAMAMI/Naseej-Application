package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {
    private Integer id;
    private String label;
    private Double latitude;
    private Double longitude;
    private Integer neighborhoodId;
    private String neighborhoodName;
    private String city;
    private String buildingNumber;
    private String street;
    private String district;
    private String postalCode;
    private String additionalNumber;
    private Boolean primary;
    private Boolean needsConfirmation;
    private LocalDateTime createdAt;
    // Until this time the pin can be moved freely (fixing a mistake right after adding it)
    private LocalDateTime freeMoveUntil;
}
