package com.waleed.naseej.controller;

import com.waleed.naseej.dto.in.AddressPreviewRequest;
import com.waleed.naseej.dto.in.AddressRequest;
import com.waleed.naseej.dto.in.ProfileUpdateRequest;
import com.waleed.naseej.dto.out.AddressBookResponse;
import com.waleed.naseej.dto.out.AddressChangePreviewResponse;
import com.waleed.naseej.dto.out.AddressResponse;
import com.waleed.naseej.dto.out.UserResponse;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.service.AddressService;
import com.waleed.naseej.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** The logged-in user's own account: saved addresses (and their profile). */
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AddressService addressService;
    private final ProfileService profileService;

    @GetMapping("/profile")
    public UserResponse profile(@AuthenticationPrincipal User user) {
        return profileService.get(user);
    }

    @PutMapping("/profile")
    public UserResponse updateProfile(@AuthenticationPrincipal User user,
                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.update(user, request);
    }

    @GetMapping("/addresses")
    public AddressBookResponse addresses(@AuthenticationPrincipal User user) {
        return addressService.book(user);
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(@AuthenticationPrincipal User user,
                                                      @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.add(user, request));
    }

    @PutMapping("/addresses/{id}")
    public AddressResponse updateAddress(@AuthenticationPrincipal User user, @PathVariable Integer id,
                                         @Valid @RequestBody AddressRequest request) {
        return addressService.update(user, id, request);
    }

    @PutMapping("/addresses/{id}/primary")
    public AddressResponse makePrimary(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        return addressService.makePrimary(user, id);
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@AuthenticationPrincipal User user, @PathVariable Integer id) {
        addressService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    // What a change would do (votes withdrawn, monthly change used) before the user confirms it
    @PostMapping("/addresses/preview")
    public AddressChangePreviewResponse preview(@AuthenticationPrincipal User user,
                                                @Valid @RequestBody AddressPreviewRequest request) {
        return addressService.preview(user, request);
    }
}
