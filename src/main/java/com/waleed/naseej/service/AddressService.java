package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.AddressPreviewRequest;
import com.waleed.naseej.dto.in.AddressRequest;
import com.waleed.naseej.dto.out.AddressBookResponse;
import com.waleed.naseej.dto.out.AddressChangePreviewResponse;
import com.waleed.naseej.dto.out.AddressResponse;
import com.waleed.naseej.entity.Address;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.entity.Vote;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.AddressRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Saved addresses and the rules that keep voting honest:
 * - residents keep 1–2 addresses (one primary) and vote for ideas within 2 km of any of them; investors may keep
 *   one office address that is never used for voting
 * - a new address can be moved freely for 24 hours; after that, adding an address or moving a pin more than 100 m
 *   is "a change", allowed once every 30 days (smaller moves are accuracy fixes and always allowed)
 * - label, National Address fields, choosing the primary and deleting the second address are always free
 * - after a move or delete, votes on ideas still collecting votes that are no longer within 2 km of any address are
 *   withdrawn; votes on ideas that already reached their target stay
 * Accounts from before addresses existed get one from their saved point (or their neighborhood center, flagged so
 * they confirm the pin before voting).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    public static final int MAX_RESIDENT_ADDRESSES = 2;
    public static final int MAX_INVESTOR_ADDRESSES = 1;
    public static final int CHANGE_COOLDOWN_DAYS = 30;
    public static final int FREE_MOVE_HOURS = 24;
    public static final int FREE_MOVE_METERS = 100;

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final LocationService locationService;

    // Replaced in tests to check the time rules
    private Clock clock = Clock.systemDefaultZone();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    // ---------- Reading ----------

    /** The user's addresses, primary first. Residents from before addresses existed get one made on the spot. */
    @Transactional
    public List<Address> addressesOf(User user) {
        List<Address> list = addressRepository.findByUserIdOrderByPrimaryAddressDescIdAsc(user.getId());
        if (list.isEmpty() && user.getRole() == Role.RESIDENT && backfill(user) != null) {
            list = addressRepository.findByUserIdOrderByPrimaryAddressDescIdAsc(user.getId());
        }
        return list;
    }

    @Transactional
    public AddressBookResponse book(User currentUser) {
        User user = getUser(currentUser.getId());
        ensureCanHaveAddresses(user);
        return AddressBookResponse.builder()
                .addresses(addressesOf(user).stream().map(this::toResponse).toList())
                .maxAddresses(maxFor(user))
                .changeCooldownDays(CHANGE_COOLDOWN_DAYS)
                .freeMoveHours(FREE_MOVE_HOURS)
                .freeMoveMeters(FREE_MOVE_METERS)
                .votingRadiusKm(DiscoverService.VOTING_RADIUS_KM)
                .nextChangeAt(user.getRole() == Role.RESIDENT ? nextChangeAt(user) : null)
                .usedForVoting(user.getRole() == Role.RESIDENT)
                .build();
    }

    // ---------- Changing ----------

    /** Sign-up: the first address, with no change rules (it isn't a change, it's the starting point). */
    @Transactional
    public Address createFirst(User user, AddressRequest request) {
        Address address = Address.builder()
                .user(user)
                .primaryAddress(true)
                .movedAt(now())
                .build();
        applyFields(address, request);
        placePin(address, request.getLatitude(), request.getLongitude());
        Address saved = addressRepository.save(address);
        mirrorPrimary(user, saved);
        return saved;
    }

    @Transactional
    public AddressResponse add(User currentUser, AddressRequest request) {
        User user = getUser(currentUser.getId());
        ensureCanHaveAddresses(user);
        List<Address> existing = addressesOf(user);
        if (existing.size() >= maxFor(user)) {
            throw new BadRequestException(user.getRole() == Role.RESIDENT
                    ? "You can save up to " + MAX_RESIDENT_ADDRESSES + " addresses"
                    : "You can save one office address");
        }
        // The first address isn't a change; a second one widens where the resident can vote, so it is
        boolean countsAsChange = user.getRole() == Role.RESIDENT && !existing.isEmpty();
        if (countsAsChange) {
            ensureChangeAllowed(user);
        }
        Address address = Address.builder()
                .user(user)
                .primaryAddress(existing.isEmpty())
                .movedAt(now())
                .build();
        applyFields(address, request);
        placePin(address, request.getLatitude(), request.getLongitude());
        Address saved = addressRepository.save(address);
        if (countsAsChange) {
            markChange(user);
        }
        if (saved.getPrimaryAddress()) {
            mirrorPrimary(user, saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public AddressResponse update(User currentUser, Integer addressId, AddressRequest request) {
        User user = getUser(currentUser.getId());
        ensureCanHaveAddresses(user);
        Address address = getOwnAddress(user, addressId);
        double movedMeters = LocationService.distanceKm(address.getLatitude(), address.getLongitude(),
                request.getLatitude(), request.getLongitude()) * 1000;
        boolean confirming = Boolean.TRUE.equals(address.getNeedsConfirmation());
        boolean countsAsChange = countsAsChange(user, address, movedMeters);
        if (countsAsChange) {
            ensureChangeAllowed(user);
        }

        applyFields(address, request);
        if (movedMeters > 0.5) {
            placePin(address, request.getLatitude(), request.getLongitude());
            address.setMovedAt(now());
        }
        if (confirming) {
            address.setNeedsConfirmation(false);
        }
        Address saved = addressRepository.save(address);
        if (countsAsChange) {
            markChange(user);
        }
        if (saved.getPrimaryAddress()) {
            mirrorPrimary(user, saved);
        }
        withdrawVotesOutsideRange(user, addressesOf(user));
        return toResponse(saved);
    }

    /** Choosing the primary address is always free: it only changes what the map shows first. */
    @Transactional
    public AddressResponse makePrimary(User currentUser, Integer addressId) {
        User user = getUser(currentUser.getId());
        ensureCanHaveAddresses(user);
        Address chosen = getOwnAddress(user, addressId);
        for (Address a : addressesOf(user)) {
            a.setPrimaryAddress(a.getId().equals(chosen.getId()));
            addressRepository.save(a);
        }
        mirrorPrimary(user, chosen);
        return toResponse(chosen);
    }

    /** Deleting is free (it never widens where someone can vote), but residents keep at least one home. */
    @Transactional
    public void delete(User currentUser, Integer addressId) {
        User user = getUser(currentUser.getId());
        ensureCanHaveAddresses(user);
        Address address = getOwnAddress(user, addressId);
        List<Address> remaining = new ArrayList<>(addressesOf(user));
        remaining.removeIf(a -> a.getId().equals(address.getId()));
        if (user.getRole() == Role.RESIDENT && remaining.isEmpty()) {
            throw new BadRequestException("Keep at least one home address. You need it to vote");
        }
        withdrawVotesOutsideRange(user, remaining);
        voteRepository.clearAddress(address.getId());
        addressRepository.delete(address);
        if (Boolean.TRUE.equals(address.getPrimaryAddress()) && !remaining.isEmpty()) {
            Address next = remaining.get(0);
            next.setPrimaryAddress(true);
            addressRepository.save(next);
            mirrorPrimary(user, next);
        }
    }

    /** What saving would do, so the page can show it before the user confirms. Nothing is saved. */
    @Transactional
    public AddressChangePreviewResponse preview(User currentUser, AddressPreviewRequest request) {
        User user = getUser(currentUser.getId());
        ensureCanHaveAddresses(user);
        List<Address> current = addressesOf(user);
        boolean deleting = Boolean.TRUE.equals(request.getDelete());
        List<double[]> after = new ArrayList<>();
        boolean countsAsChange;
        String reason = null;

        if (request.getAddressId() == null) {
            // Adding: never withdraws votes
            countsAsChange = user.getRole() == Role.RESIDENT && !current.isEmpty();
            if (current.size() >= maxFor(user)) {
                reason = user.getRole() == Role.RESIDENT
                        ? "You can save up to " + MAX_RESIDENT_ADDRESSES + " addresses"
                        : "You can save one office address";
            }
            current.forEach(a -> after.add(new double[]{a.getLatitude(), a.getLongitude()}));
        } else {
            Address target = getOwnAddress(user, request.getAddressId());
            if (deleting) {
                countsAsChange = false;
                if (user.getRole() == Role.RESIDENT && current.size() <= 1) {
                    reason = "Keep at least one home address. You need it to vote";
                }
            } else {
                if (request.getLatitude() == null || request.getLongitude() == null) {
                    throw new BadRequestException("Place the pin on your home");
                }
                double meters = LocationService.distanceKm(target.getLatitude(), target.getLongitude(),
                        request.getLatitude(), request.getLongitude()) * 1000;
                countsAsChange = countsAsChange(user, target, meters);
            }
            for (Address a : current) {
                if (a.getId().equals(target.getId())) {
                    if (!deleting) {
                        after.add(new double[]{request.getLatitude(), request.getLongitude()});
                    }
                } else {
                    after.add(new double[]{a.getLatitude(), a.getLongitude()});
                }
            }
        }

        LocalDateTime next = user.getRole() == Role.RESIDENT ? nextChangeAt(user) : null;
        if (reason == null && countsAsChange && next != null) {
            reason = cooldownMessage(next);
        }
        List<AddressChangePreviewResponse.WithdrawnVote> withdrawn = user.getRole() == Role.RESIDENT
                ? votesOutside(user, after).stream()
                    .map(v -> AddressChangePreviewResponse.WithdrawnVote.builder()
                            .ideaId(v.getIdea().getId())
                            .title(v.getIdea().getTitle())
                            .neighborhoodName(v.getIdea().getNeighborhood().getName())
                            .build())
                    .toList()
                : List.of();
        return AddressChangePreviewResponse.builder()
                .allowed(reason == null)
                .countsAsChange(countsAsChange)
                .nextChangeAt(next)
                .reason(reason)
                .withdrawnVotes(withdrawn)
                .build();
    }

    // ---------- Accounts from before addresses ----------

    /**
     * Gives a resident without addresses a primary one: from their saved point, or from their neighborhood center
     * (flagged so they place the pin before voting). Their earlier votes are linked to it. Null when neither is known.
     */
    @Transactional
    public Address backfill(User user) {
        if (addressRepository.countByUserId(user.getId()) > 0) {
            return null;
        }
        Double lat = user.getLatitude();
        Double lng = user.getLongitude();
        boolean fromCenter = false;
        Neighborhood hood = user.getNeighborhood();
        if (lat == null || lng == null) {
            if (hood == null || hood.getLatitude() == null || hood.getLongitude() == null) {
                return null;
            }
            lat = hood.getLatitude();
            lng = hood.getLongitude();
            fromCenter = true;
        }
        if (hood == null) {
            hood = locationService.findNearestNeighborhood(lat, lng).orElse(null);
            if (hood == null) {
                return null;
            }
        }
        Address saved = addressRepository.save(Address.builder()
                .user(user)
                .latitude(lat)
                .longitude(lng)
                .neighborhood(hood)
                .primaryAddress(true)
                .needsConfirmation(fromCenter)
                .movedAt(now())
                .build());
        voteRepository.linkVotesToAddress(user.getId(), saved.getId());
        log.info("Created a {} home address for {}", fromCenter ? "to-confirm" : "saved-point", user.getEmail());
        return saved;
    }

    // ---------- Helpers ----------

    /** The address closest to a point, or null. */
    public static Address nearest(List<Address> addresses, double[] point) {
        Address best = null;
        double bestKm = Double.MAX_VALUE;
        for (Address a : addresses) {
            double km = LocationService.distanceKm(a.getLatitude(), a.getLongitude(), point[0], point[1]);
            if (km < bestKm) {
                best = a;
                bestKm = km;
            }
        }
        return best;
    }

    public static double distanceKm(Address a, double[] point) {
        return LocationService.distanceKm(a.getLatitude(), a.getLongitude(), point[0], point[1]);
    }

    private boolean countsAsChange(User user, Address address, double movedMeters) {
        if (user.getRole() != Role.RESIDENT || Boolean.TRUE.equals(address.getNeedsConfirmation())) {
            return false;
        }
        if (movedMeters <= FREE_MOVE_METERS) {
            return false;
        }
        LocalDateTime created = address.getCreatedAt() != null ? address.getCreatedAt() : now();
        return now().isAfter(created.plusHours(FREE_MOVE_HOURS));
    }

    private void ensureChangeAllowed(User user) {
        LocalDateTime next = nextChangeAt(user);
        if (next != null) {
            throw new BadRequestException(cooldownMessage(next));
        }
    }

    private String cooldownMessage(LocalDateTime next) {
        return "You can change your address once every " + CHANGE_COOLDOWN_DAYS
                + " days. Your next change is possible on " + next.toLocalDate();
    }

    private LocalDateTime nextChangeAt(User user) {
        if (user.getLastAddressChangeAt() == null) {
            return null;
        }
        LocalDateTime next = user.getLastAddressChangeAt().plusDays(CHANGE_COOLDOWN_DAYS);
        return next.isAfter(now()) ? next : null;
    }

    private void markChange(User user) {
        user.setLastAddressChangeAt(now());
        userRepository.save(user);
    }

    private void withdrawVotesOutsideRange(User user, List<Address> addresses) {
        if (user.getRole() != Role.RESIDENT) {
            return;
        }
        List<double[]> points = addresses.stream().map(a -> new double[]{a.getLatitude(), a.getLongitude()}).toList();
        List<Vote> outside = votesOutside(user, points);
        if (!outside.isEmpty()) {
            voteRepository.deleteAll(outside);
            log.info("Withdrew {} votes of {} after an address change", outside.size(), user.getEmail());
        }
    }

    // Votes on ideas still collecting votes that no address in the list is within the voting radius of
    private List<Vote> votesOutside(User user, List<double[]> points) {
        return voteRepository.findByUserIdAndIdeaStatus(user.getId(), IdeaStatus.PUBLISHED).stream()
                .filter(v -> {
                    double[] idea = LocationService.pointOf(v.getIdea());
                    return idea != null && points.stream().noneMatch(p ->
                            LocationService.distanceKm(p[0], p[1], idea[0], idea[1]) <= DiscoverService.VOTING_RADIUS_KM);
                })
                .toList();
    }

    private void placePin(Address address, Double latitude, Double longitude) {
        Neighborhood hood = locationService.resolveNeighborhood(latitude, longitude)
                .orElseThrow(() -> new BadRequestException(
                        "Naseej isn't available at this spot yet. Move the pin inside a neighborhood in Saudi Arabia"));
        address.setLatitude(latitude);
        address.setLongitude(longitude);
        address.setNeighborhood(hood);
    }

    private void applyFields(Address address, AddressRequest request) {
        address.setLabel(blankToNull(request.getLabel()));
        address.setBuildingNumber(blankToNull(request.getBuildingNumber()));
        address.setStreet(blankToNull(request.getStreet()));
        address.setDistrict(blankToNull(request.getDistrict()));
        address.setPostalCode(blankToNull(request.getPostalCode()));
        address.setAdditionalNumber(blankToNull(request.getAdditionalNumber()));
    }

    // Residents' saved point and neighborhood follow their primary address, so older code keeps working
    private void mirrorPrimary(User user, Address primary) {
        if (user.getRole() != Role.RESIDENT) {
            return;
        }
        user.setLatitude(primary.getLatitude());
        user.setLongitude(primary.getLongitude());
        user.setNeighborhood(primary.getNeighborhood());
        userRepository.save(user);
    }

    private void ensureCanHaveAddresses(User user) {
        if (user.getRole() != Role.RESIDENT && user.getRole() != Role.INVESTOR) {
            throw new ForbiddenException("Only residents and investors can save addresses");
        }
    }

    private int maxFor(User user) {
        return user.getRole() == Role.RESIDENT ? MAX_RESIDENT_ADDRESSES : MAX_INVESTOR_ADDRESSES;
    }

    private Address getOwnAddress(User user, Integer id) {
        return addressRepository.findById(id)
                .filter(a -> a.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + id));
    }

    private User getUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public AddressResponse toResponse(Address a) {
        Neighborhood n = a.getNeighborhood();
        return AddressResponse.builder()
                .id(a.getId())
                .label(a.getLabel())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .neighborhoodId(n.getId())
                .neighborhoodName(n.getName())
                .city(n.getCity())
                .buildingNumber(a.getBuildingNumber())
                .street(a.getStreet())
                .district(a.getDistrict())
                .postalCode(a.getPostalCode())
                .additionalNumber(a.getAdditionalNumber())
                .primary(a.getPrimaryAddress())
                .needsConfirmation(a.getNeedsConfirmation())
                .createdAt(a.getCreatedAt())
                .freeMoveUntil(a.getCreatedAt() != null ? a.getCreatedAt().plusHours(FREE_MOVE_HOURS) : null)
                .build();
    }
}
