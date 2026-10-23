package com.waleed.naseej.dto.in;

import com.waleed.naseej.enums.Role;
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
}