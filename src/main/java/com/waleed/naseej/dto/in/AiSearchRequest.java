package com.waleed.naseej.dto.in;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
public class AiSearchRequest {

    @NotBlank(message = "Type what you are looking for")
    @Size(min = 2, max = 200, message = "Type 2 to 200 characters to search")
    private String query;

    // Optional preferences chosen above the search box
    @Size(max = 60, message = "City must be at most 60 characters")
    private String city;

    private Integer neighborhoodId;

    @PositiveOrZero(message = "Budget can't be negative")
    private BigDecimal budgetMin;

    @PositiveOrZero(message = "Budget can't be negative")
    private BigDecimal budgetMax;
}
