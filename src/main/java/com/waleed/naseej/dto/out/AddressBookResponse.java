package com.waleed.naseej.dto.out;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** The user's addresses and the rules that apply to them, so the page can explain them before anything is saved. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressBookResponse {
    private List<AddressResponse> addresses;
    private Integer maxAddresses;
    private Integer changeCooldownDays;
    private Integer freeMoveHours;
    private Integer freeMoveMeters;
    private Double votingRadiusKm;
    // Null when a change is allowed now
    private LocalDateTime nextChangeAt;
    // Residents vote from these addresses; investors' addresses are never used for voting
    private Boolean usedForVoting;
}
