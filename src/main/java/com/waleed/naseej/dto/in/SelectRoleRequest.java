package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import com.waleed.naseej.service.PhoneNumbers;
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

    // Saudi mobile: 05XXXXXXXX (9665XXXXXXXX also accepted). Required when choosing RESIDENT
    @Pattern(regexp = PhoneNumbers.REGEX, message = PhoneNumbers.MESSAGE)
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

    @Pattern(regexp = "^7\\d{9}$", message = "Commercial registration number must be 10 digits starting with 7")
    private String crNumber;

    @Pattern(regexp = "^(UP_TO_300K|FROM_300K_TO_700K|ABOVE_700K)$",
            message = "Investment budget must be UP_TO_300K, FROM_300K_TO_700K or ABOVE_700K")
    private String investmentBudget;

    // One or more of FOOD, RETAIL, HEALTH, FITNESS, HOME_SERVICES, EDUCATION separated by commas
    @Pattern(regexp = "^(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION)(,(FOOD|RETAIL|HEALTH|FITNESS|HOME_SERVICES|EDUCATION))*$",
            message = "Investment fields must be a comma-separated list of FOOD, RETAIL, HEALTH, FITNESS, HOME_SERVICES, EDUCATION")
    private String investmentFields;

    // Budget range in SAR (instead of investmentBudget). budgetMax empty means "and above"
    @PositiveOrZero(message = "Budget can't be negative")
    private BigDecimal budgetMin;

    @PositiveOrZero(message = "Budget can't be negative")
    private BigDecimal budgetMax;

    @Size(max = 120, message = "Company name must be at most 120 characters")
    private String companyName;

    // ---------- Everyone ----------

    @Pattern(regexp = "^(ar|en)$", message = "Language must be ar or en")
    private String preferredLanguage;

    // Residents: the home address (map pin required). When sent, it decides the neighborhood
    @Valid
    private AddressRequest address;
}
