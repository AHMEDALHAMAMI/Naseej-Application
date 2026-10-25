package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.IdeaStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.time.LocalDateTime;
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
public class IdeaRequest {

    private Integer ownerId;
    private Integer neighborhoodId;
    private String title;
    private String description;
    private String category;
    private IdeaStatus status;
    private Integer membershipThreshold;
    private LocalDateTime publishedAt;
    private LocalDateTime confirmedAt;

    // The exact spot (optional). Without it the neighborhood center is used for the map and the voting radius
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;
}
