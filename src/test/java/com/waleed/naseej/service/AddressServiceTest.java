package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.AddressPreviewRequest;
import com.waleed.naseej.dto.in.AddressRequest;
import com.waleed.naseej.dto.out.AddressChangePreviewResponse;
import com.waleed.naseej.entity.Address;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.entity.Vote;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.repository.AddressRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The address rules that keep voting tied to where people live (no database needed). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AddressServiceTest {

    // Al Narjis, Riyadh, and a spot about 1.1 km east of it
    private static final double LAT = 24.8270;
    private static final double LNG = 46.6540;
    private static final double LNG_1KM_EAST = 46.6649;
    // A spot about 55 m away (an accuracy fix)
    private static final double LNG_55M = 46.65455;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 12, 0);

    @Mock AddressRepository addressRepository;
    @Mock UserRepository userRepository;
    @Mock VoteRepository voteRepository;
    @Mock LocationService locationService;

    private AddressService service;
    private User resident;
    private Neighborhood hood;
    private final List<Address> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new AddressService(addressRepository, userRepository, voteRepository, locationService);
        service.setClock(Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        hood = Neighborhood.builder().id(1).name("Al Narjis").city("Riyadh").latitude(LAT).longitude(LNG).build();
        resident = User.builder().id(7).email("r@test").role(Role.RESIDENT).build();

        when(userRepository.findById(7)).thenReturn(Optional.of(resident));
        when(locationService.resolveNeighborhood(anyDouble(), anyDouble())).thenReturn(Optional.of(hood));
        when(addressRepository.findByUserIdOrderByPrimaryAddressDescIdAsc(7)).thenAnswer(inv -> new ArrayList<>(saved));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
            Address a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(100 + saved.size());
                saved.add(a);
            }
            return a;
        });
        when(addressRepository.findById(anyInt())).thenAnswer(inv ->
                saved.stream().filter(a -> a.getId().equals(inv.getArgument(0))).findFirst());
        when(voteRepository.findByUserIdAndIdeaStatus(anyInt(), eq(IdeaStatus.PUBLISHED))).thenReturn(List.of());
    }

    private Address home(LocalDateTime createdAt) {
        Address a = Address.builder().id(100 + saved.size()).user(resident).latitude(LAT).longitude(LNG)
                .neighborhood(hood).primaryAddress(saved.isEmpty()).needsConfirmation(false).createdAt(createdAt).build();
        saved.add(a);
        return a;
    }

    private AddressRequest pin(double lat, double lng) {
        return AddressRequest.builder().latitude(lat).longitude(lng).build();
    }

    @Test
    void thirdAddressIsRefused() {
        home(NOW.minusDays(90));
        home(NOW.minusDays(60));
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.add(resident, pin(LAT, LNG_1KM_EAST)));
        assertEquals("You can save up to 2 addresses", e.getMessage());
    }

    @Test
    void secondAddressUsesTheMonthlyChange() {
        home(NOW.minusDays(90));
        service.add(resident, pin(LAT, LNG_1KM_EAST));
        assertEquals(NOW, resident.getLastAddressChangeAt());
    }

    @Test
    void secondAddressInsideTheCooldownIsRefused() {
        home(NOW.minusDays(90));
        resident.setLastAddressChangeAt(NOW.minusDays(10));
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.add(resident, pin(LAT, LNG_1KM_EAST)));
        assertTrue(e.getMessage().startsWith("You can change your address once every 30 days"));
        assertTrue(e.getMessage().endsWith("2026-11-14"));
    }

    @Test
    void smallMoveIsAlwaysFree() {
        Address a = home(NOW.minusDays(90));
        resident.setLastAddressChangeAt(NOW.minusDays(1));
        service.update(resident, a.getId(), pin(LAT, LNG_55M));
        assertEquals(NOW.minusDays(1), resident.getLastAddressChangeAt());
        assertEquals(LNG_55M, a.getLongitude());
    }

    @Test
    void bigMoveWithinTheFirstDayIsFree() {
        Address a = home(NOW.minusHours(5));
        resident.setLastAddressChangeAt(NOW.minusDays(1));
        service.update(resident, a.getId(), pin(LAT, LNG_1KM_EAST));
        assertEquals(NOW.minusDays(1), resident.getLastAddressChangeAt());
    }

    @Test
    void bigMoveLaterIsAChangeAndRespectsTheCooldown() {
        Address a = home(NOW.minusDays(90));
        resident.setLastAddressChangeAt(NOW.minusDays(5));
        assertThrows(BadRequestException.class, () -> service.update(resident, a.getId(), pin(LAT, LNG_1KM_EAST)));
        assertEquals(LNG, a.getLongitude());

        resident.setLastAddressChangeAt(NOW.minusDays(31));
        service.update(resident, a.getId(), pin(LAT, LNG_1KM_EAST));
        assertEquals(NOW, resident.getLastAddressChangeAt());
    }

    @Test
    void confirmingAnOldAccountsPinIsNotAChange() {
        Address a = home(NOW.minusDays(90));
        a.setNeedsConfirmation(true);
        resident.setLastAddressChangeAt(NOW.minusDays(2));
        service.update(resident, a.getId(), pin(LAT, LNG_1KM_EAST));
        assertFalse(a.getNeedsConfirmation());
        assertEquals(NOW.minusDays(2), resident.getLastAddressChangeAt());
    }

    @Test
    void movingAwayWithdrawsVotesOnIdeasStillCollecting() {
        Address a = home(NOW.minusHours(1));
        // An idea 300 m from home: in range now, about 3 km away after moving 3.3 km east
        Idea near = Idea.builder().id(5).title("Pharmacy").status(IdeaStatus.PUBLISHED)
                .latitude(LAT).longitude(46.6570).neighborhood(hood).build();
        Vote vote = Vote.builder().id(9).user(resident).idea(near).build();
        when(voteRepository.findByUserIdAndIdeaStatus(7, IdeaStatus.PUBLISHED)).thenReturn(List.of(vote));

        AddressChangePreviewResponse preview = service.preview(resident,
                AddressPreviewRequest.builder().addressId(a.getId()).latitude(LAT).longitude(46.6870).build());
        assertEquals(1, preview.getWithdrawnVotes().size());
        assertEquals(5, preview.getWithdrawnVotes().get(0).getIdeaId());

        service.update(resident, a.getId(), pin(LAT, 46.6870));
        verify(voteRepository).deleteAll(List.of(vote));
    }

    @Test
    void smallMoveKeepsVotesInRange() {
        Address a = home(NOW.minusDays(90));
        Idea near = Idea.builder().id(5).title("Pharmacy").status(IdeaStatus.PUBLISHED)
                .latitude(LAT).longitude(46.6570).neighborhood(hood).build();
        when(voteRepository.findByUserIdAndIdeaStatus(7, IdeaStatus.PUBLISHED))
                .thenReturn(List.of(Vote.builder().id(9).user(resident).idea(near).build()));
        service.update(resident, a.getId(), pin(LAT, LNG_55M));
        verify(voteRepository, never()).deleteAll(anyList());
    }

    @Test
    void residentsKeepAtLeastOneAddress() {
        Address a = home(NOW.minusDays(90));
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.delete(resident, a.getId()));
        assertEquals("Keep at least one home address. You need it to vote", e.getMessage());
    }

    @Test
    void deletingThePrimaryPromotesTheOther() {
        Address first = home(NOW.minusDays(90));
        Address second = home(NOW.minusDays(60));
        service.delete(resident, first.getId());
        assertTrue(second.getPrimaryAddress());
        assertEquals(second.getLongitude(), resident.getLongitude());
        verify(voteRepository).clearAddress(first.getId());
    }

    @Test
    void someoneElsesAddressIsNotFound() {
        User other = User.builder().id(8).role(Role.RESIDENT).build();
        Address theirs = Address.builder().id(300).user(other).latitude(LAT).longitude(LNG).neighborhood(hood).build();
        saved.add(theirs);
        assertThrows(com.waleed.naseej.exception.ResourceNotFoundException.class,
                () -> service.update(resident, 300, pin(LAT, LNG)));
    }

    @Test
    void oldAccountWithOnlyANeighborhoodGetsAnAddressToConfirm() {
        resident.setNeighborhood(hood);
        when(addressRepository.countByUserId(7)).thenReturn(0L);
        Address made = service.backfill(resident);
        assertNotNull(made);
        assertTrue(made.getNeedsConfirmation());
        verify(voteRepository).linkVotesToAddress(7, made.getId());
    }
}
