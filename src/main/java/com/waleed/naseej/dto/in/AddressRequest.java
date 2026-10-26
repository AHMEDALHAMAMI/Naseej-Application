package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A home (or office) address: the map pin is required, the rest is optional. The neighborhood comes from the pin. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    @Size(max = 40, message = "Label must be at most 40 characters")
    private String label;

    @NotNull(message = "Place the pin on your home")
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Place the pin on your home")
    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    // ---------- Saudi National Address (optional) ----------

    @Pattern(regexp = "^\\d{4}$", message = "Building number must be 4 digits")
    private String buildingNumber;

    @Size(max = 120, message = "Street must be at most 120 characters")
    private String street;

    @Size(max = 100, message = "District must be at most 100 characters")
    private String district;

    @Pattern(regexp = "^\\d{5}$", message = "Postal code must be 5 digits")
    private String postalCode;

    @Pattern(regexp = "^\\d{4}$", message = "Additional number must be 4 digits")
    private String additionalNumber;
}
