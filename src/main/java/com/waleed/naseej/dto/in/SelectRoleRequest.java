package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class SelectRoleRequest {

    // RESIDENT or INVESTOR
    @NotNull(message = "Role is required (RESIDENT or INVESTOR)")
    private Role role;

    // Required when choosing RESIDENT
    private Integer neighborhoodId;

    @Pattern(regexp = "^9665\\d{8}$", message = "Phone must be in the format 9665XXXXXXXX")
    private String phone;

    // ---------- Location from the browser (optional; a resident without a neighborhood gets the nearest one) ----------

    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    // ---------- Investors only, all optional: the CR is added before the first unlock,
    //            budget and fields come from the onboarding questions after sign-up ----------

    @Pattern(regexp = "^\\d{10}$", message = "Commercial registration number must be 10 digits")
    private String crNumber;

    @Pattern(regexp = "^(UP_TO_300K|FROM_300K_TO_700K|ABOVE_700K)$",
            message = "Investment budget must be UP_TO_300K, FROM_300K_TO_700K or ABOVE_700K")
    private String investmentBudget;

    // One or more of FOOD, RETAIL, HEALTH, FITNESS, HOME_SERVICES, EDUCATION separated by commas
    @Pattern(regexp = "^(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION)(,(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION))*$",
            message = "Investment fields must be a comma-separated list of FOOD, RETAIL, HEALTH, FITNESS, HOME_SERVICES, EDUCATION")
    private String investmentFields;
}
