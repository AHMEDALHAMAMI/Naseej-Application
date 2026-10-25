package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(min = 3, max = 100, message = "Full name must be between 3 and 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email is not valid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
    private String password;

    // Optional, Saudi mobile format: 9665XXXXXXXX
    @Pattern(regexp = "^9665\\d{8}$", message = "Phone must be in the format 9665XXXXXXXX")
    private String phone;

    // Required for residents, optional for investors
    private Integer neighborhoodId;

    // Only RESIDENT or INVESTOR are accepted at sign-up
    @NotNull(message = "Role is required (RESIDENT or INVESTOR)")
    private Role role;

    private Boolean whatsappOptIn;

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
