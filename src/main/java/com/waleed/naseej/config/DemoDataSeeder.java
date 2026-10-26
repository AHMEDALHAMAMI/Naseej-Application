package com.waleed.naseej.config;

import com.waleed.naseej.entity.BusinessModel;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.ContractSignature;
import com.waleed.naseej.entity.FeasibilityReport;
import com.waleed.naseej.entity.FoundingMember;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.IdeaCheck;
import com.waleed.naseej.entity.NearbyPlace;
import com.waleed.naseej.entity.Neighborhood;
import com.waleed.naseej.entity.OpportunityAccess;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.entity.ProjectUpdate;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.entity.Vote;
import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.AuthProvider;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.InvestorStatus;
import com.waleed.naseej.enums.PaymentMethod;
import com.waleed.naseej.enums.PaymentPurpose;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.enums.Role;
import com.waleed.naseej.enums.ScreeningDecision;
import com.waleed.naseej.repository.BusinessModelRepository;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ContractSignatureRepository;
import com.waleed.naseej.repository.FeasibilityReportRepository;
import com.waleed.naseej.repository.FoundingMemberRepository;
import com.waleed.naseej.repository.IdeaCheckRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.NearbyPlaceRepository;
import com.waleed.naseej.repository.NeighborhoodRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.ProjectUpdateRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import com.waleed.naseej.service.ContractFlowService;
import com.waleed.naseej.service.LocationService;
import com.waleed.naseej.service.ProjectService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo data for trying the website: neighborhoods in six cities, residents who voted, investors in every
 * verification state, and ideas at every stage (voting, target reached, adopted with project progress, opened).
 * Runs once on startup when DEMO_DATA is true (the default) and investor1@demo.naseej.sa doesn't exist yet.
 * Everything is saved in one transaction, so a failed run leaves nothing behind and runs again next startup.
 * Every demo account uses the password Demo12345. Set DEMO_DATA=false on a real deployment.
 *
 * The data follows the same rules as the real flow: votes come only from residents of the neighborhood
 * (never the idea's owner), voting stops when the target is reached, and an adopted idea has a paid
 * access, both signatures, founding-member benefits and an ACTIVE contract with its 90 days, in that order.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    public static final String PASSWORD = "Demo12345";
    private static final String DOMAIN = "@demo.naseej.sa";
    private static final String MARKER_EMAIL = "investor1" + DOMAIN;
    // Same as ContractFlowService: the 90 days start when the contract becomes ACTIVE
    private static final int OPENING_DEADLINE_DAYS = 90;

    private final UserRepository userRepository;
    private final NeighborhoodRepository neighborhoodRepository;
    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;
    private final FoundingMemberRepository foundingMemberRepository;
    private final IdeaCheckRepository ideaCheckRepository;
    private final NearbyPlaceRepository nearbyPlaceRepository;
    private final BusinessModelRepository businessModelRepository;
    private final FeasibilityReportRepository feasibilityReportRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final PaymentRepository paymentRepository;
    private final ContractRepository contractRepository;
    private final ContractSignatureRepository contractSignatureRepository;
    private final ProjectUpdateRepository projectUpdateRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.demo.enabled:true}")
    private boolean enabled;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    // ---------- The data ----------

    record Hood(String name, String city, double lat, double lng) {
    }

    // Approximate district centers
    private static final List<Hood> HOODS = List.of(
            new Hood("Al Narjis", "Riyadh", 24.8650, 46.6600),
            new Hood("Al Yasmin", "Riyadh", 24.8238, 46.6400),
            new Hood("Al Malqa", "Riyadh", 24.8095, 46.6050),
            new Hood("Hittin", "Riyadh", 24.7650, 46.6000),
            new Hood("Al Sahafa", "Riyadh", 24.8020, 46.6380),
            new Hood("Al Aqiq", "Riyadh", 24.7730, 46.6290),
            new Hood("Al Nakheel", "Riyadh", 24.7480, 46.6370),
            new Hood("Al Olaya", "Riyadh", 24.6930, 46.6850),
            new Hood("Al Sulimaniyah", "Riyadh", 24.7040, 46.7000),
            new Hood("Al Wurud", "Riyadh", 24.7230, 46.6740),
            new Hood("Al Murooj", "Riyadh", 24.7540, 46.6650),
            new Hood("Al Rabwah", "Riyadh", 24.6950, 46.7600),
            new Hood("Al Malaz", "Riyadh", 24.6650, 46.7330),
            new Hood("Al Rawdah", "Riyadh", 24.7330, 46.7750),
            new Hood("Qurtubah", "Riyadh", 24.8100, 46.7400),
            new Hood("Al Nada", "Riyadh", 24.8120, 46.6900),
            new Hood("Al Arid", "Riyadh", 24.8800, 46.6150),
            new Hood("Al Muhammadiyah", "Riyadh", 24.7350, 46.6550),
            new Hood("Laban", "Riyadh", 24.6400, 46.5600),
            new Hood("Al Suwaidi", "Riyadh", 24.6000, 46.6650),
            new Hood("Al Naseem", "Riyadh", 24.7350, 46.8250),
            new Hood("Al Rawdah", "Jeddah", 21.5650, 39.1550),
            new Hood("Al Zahra", "Jeddah", 21.5880, 39.1400),
            new Hood("Al Salamah", "Jeddah", 21.5950, 39.1550),
            new Hood("Al Naeem", "Jeddah", 21.6200, 39.1500),
            new Hood("Al Safa", "Jeddah", 21.5800, 39.2000),
            new Hood("Al Hamra", "Jeddah", 21.5250, 39.1650),
            new Hood("Obhur Al Shamaliyah", "Jeddah", 21.7300, 39.1000),
            new Hood("Al Shati", "Dammam", 26.4500, 50.1200),
            new Hood("Al Faisaliyah", "Dammam", 26.4100, 50.0400),
            new Hood("Al Mazruiyah", "Dammam", 26.4420, 50.0550),
            new Hood("Al Ulaya", "Khobar", 26.2800, 50.1950),
            new Hood("Al Aqrabiyah", "Khobar", 26.2950, 50.2000),
            new Hood("Quba", "Madinah", 24.4400, 39.6170),
            new Hood("Al Aziziyah", "Makkah", 21.4000, 39.8650));

    /**
     * One idea: owner is "R" (a resident of the neighborhood) or "I" + an investor number.
     * adoptedBy/stage: which demo investor adopted it and the project stage they reached (a ProjectService.STAGES name).
     */
    record Seed(String title, String description, String category, String hood, String city, int threshold, int votes,
                IdeaStatus status, String owner, Integer adoptedBy, String stage, int score, int competitors,
                double radiusKm, String placeType, long capitalMin, long capitalMax, int priceSar) {
    }

    private static final List<Seed> IDEAS = List.of(
            new Seed("24-hour pharmacy near the mosque",
                    "The nearest pharmacy closes at midnight and families with small children have to drive to King Fahd Road at night. A 24-hour pharmacy next to the mosque parking would serve the whole north side.",
                    "Pharmacy", "Al Narjis", "Riyadh", 30, 22, IdeaStatus.PUBLISHED, "R", null, null,
                    82, 2, 1.0, "pharmacy", 450_000, 750_000, 14_000),
            new Seed("Specialty coffee with a quiet study corner",
                    "Students and remote workers here have nowhere quiet to sit. A specialty coffee shop with a calm study area, good Wi-Fi and late hours.",
                    "Cafe", "Al Narjis", "Riyadh", 25, 25, IdeaStatus.COMPLETED, "R", null, null,
                    76, 4, 1.0, "cafe", 380_000, 620_000, 12_000),
            new Seed("Women's gym with evening classes",
                    "There is no women's gym within 4 km. Many of us want evening classes, a small pool and a kids' room while we train.",
                    "Women's gym", "Al Yasmin", "Riyadh", 30, 30, IdeaStatus.COMPLETED, "R", null, null,
                    88, 1, 3.0, "gym", 900_000, 1_600_000, 28_000),
            new Seed("Kids' indoor play and activity center",
                    "Summer afternoons are too hot for the parks. An indoor play center with soft play, a climbing wall and birthday rooms.",
                    "Kids activities", "Al Yasmin", "Riyadh", 35, 14, IdeaStatus.PUBLISHED, "R", null, null,
                    79, 1, 3.0, "child_care_agency", 700_000, 1_200_000, 18_000),
            new Seed("Fresh bakery with Saudi breakfast",
                    "A bakery that opens at 5 am with fresh bread, samoli, ful and tamees for people going to work and school.",
                    "Bakery", "Al Malqa", "Riyadh", 20, 20, IdeaStatus.ADOPTED, "R", 1, "Fit-out",
                    84, 2, 1.0, "bakery", 300_000, 520_000, 9_000),
            new Seed("Laundry with pickup and delivery",
                    "Working families need a laundry that picks up and delivers through an app, with ironing for thobes and abayas.",
                    "Laundry", "Al Malqa", "Riyadh", 25, 9, IdeaStatus.PUBLISHED, "R", null, null,
                    71, 3, 1.0, "laundry", 180_000, 320_000, 6_000),
            new Seed("Family amusement park for small kids",
                    "A small outdoor amusement park with a mini Ferris wheel, carousel and shaded seating for parents, open in the evenings.",
                    "Kids activities", "Hittin", "Riyadh", 40, 31, IdeaStatus.PUBLISHED, "R", null, null,
                    86, 0, 5.0, "child_care_agency", 2_500_000, 4_000_000, 45_000),
            new Seed("Pediatric clinic open on weekends",
                    "Parents wait hours at the hospital on Fridays. A pediatric clinic that is open on weekends and evenings.",
                    "Clinic", "Hittin", "Riyadh", 30, 30, IdeaStatus.ADOPTED, "R", 2, "Licensing",
                    90, 1, 3.0, "doctor", 1_200_000, 2_000_000, 32_000),
            new Seed("Neighborhood supermarket with fresh produce",
                    "The closest supermarket is across the highway. We need a mid-size market with fresh vegetables, meat and daily needs.",
                    "Grocery", "Al Sahafa", "Riyadh", 30, 18, IdeaStatus.PUBLISHED, "R", null, null,
                    80, 2, 1.0, "supermarket", 1_000_000, 1_800_000, 22_000),
            new Seed("Healthy fast-casual restaurant",
                    "Office workers around Olaya want quick healthy lunches: bowls, grills and salads, with good delivery.",
                    "Restaurant", "Al Olaya", "Riyadh", 25, 25, IdeaStatus.OPENED, "R", 1, "Opened",
                    78, 9, 1.0, "restaurant", 600_000, 950_000, 15_000),
            new Seed("Barber shop with online booking",
                    "A clean, modern barber shop where you can book a time from your phone instead of waiting.",
                    "Barber", "Al Nakheel", "Riyadh", 20, 7, IdeaStatus.PUBLISHED, "R", null, null,
                    68, 3, 1.5, "barber_shop", 120_000, 220_000, 4_000),
            new Seed("Car wash and quick service",
                    "A car wash with oil change and tire service so we don't have to go to the industrial area.",
                    "Car services", "Qurtubah", "Riyadh", 25, 12, IdeaStatus.PUBLISHED, "R", null, null,
                    73, 1, 2.0, "car_wash", 400_000, 700_000, 8_000),
            new Seed("School supplies and print shop",
                    "Before every term parents drive far for stationery and printing. A shop near the schools would save a lot of time.",
                    "School supplies", "Al Rawdah", "Riyadh", 20, 16, IdeaStatus.PUBLISHED, "R", null, null,
                    70, 1, 1.5, "book_store", 150_000, 260_000, 4_500),
            new Seed("مغسلة سيارات متنقلة",
                    "خدمة غسيل سيارات تأتي إلى البيت بموعد مسبق، بدون الحاجة للانتظار في المغاسل البعيدة.",
                    "Car services", "Al Yasmin", "Riyadh", 20, 6, IdeaStatus.PUBLISHED, "R", null, null,
                    66, 2, 2.0, "car_wash", 90_000, 160_000, 3_000),
            new Seed("مخبز فرنسي وحلويات",
                    "مخبز يقدم الكرواسون والخبز الفرنسي والحلويات الطازجة مع جلسات صغيرة للعائلات.",
                    "Bakery", "Hittin", "Riyadh", 25, 13, IdeaStatus.PUBLISHED, "R", null, null,
                    74, 2, 1.0, "bakery", 350_000, 600_000, 9_500),
            new Seed("Premium co-working cafe",
                    "A café with private booths, meeting rooms and a membership plan for freelancers in north Riyadh.",
                    "Cafe", "Al Malqa", "Riyadh", 30, 10, IdeaStatus.PUBLISHED, "I1", null, null,
                    77, 3, 1.0, "cafe", 700_000, 1_100_000, 16_000),
            new Seed("Juice and dessert corner",
                    "Families walking on the corniche in the evening want fresh juice and desserts closer to home.",
                    "Cafe", "Al Zahra", "Jeddah", 25, 11, IdeaStatus.PUBLISHED, "R", null, null,
                    72, 3, 1.0, "juice_shop", 200_000, 350_000, 6_000),
            new Seed("Physiotherapy clinic",
                    "Many older residents and athletes need physiotherapy but the nearest center has a three-week waiting list.",
                    "Clinic", "Al Salamah", "Jeddah", 30, 30, IdeaStatus.COMPLETED, "R", null, null,
                    85, 1, 3.0, "physiotherapist", 800_000, 1_300_000, 24_000),
            new Seed("Ladies' salon with home service",
                    "A ladies' salon that also sends stylists to homes for weddings and events.",
                    "Salon", "Al Rawdah", "Jeddah", 25, 19, IdeaStatus.PUBLISHED, "R", null, null,
                    75, 4, 1.5, "beauty_salon", 250_000, 450_000, 7_000),
            new Seed("Pharmacy with home delivery",
                    "A pharmacy that delivers prescriptions to the door within an hour, for families and elderly residents.",
                    "Pharmacy", "Al Shati", "Dammam", 25, 8, IdeaStatus.PUBLISHED, "R", null, null,
                    74, 2, 1.0, "pharmacy", 420_000, 700_000, 11_000),
            new Seed("Indoor trampoline park",
                    "Teenagers have nothing to do on weekends. A trampoline and ninja course park would be packed every evening.",
                    "Kids activities", "Al Ulaya", "Khobar", 35, 21, IdeaStatus.PUBLISHED, "R", null, null,
                    83, 0, 5.0, "sports_club", 1_800_000, 3_000_000, 38_000));

    private static final String[] FIRST = {"Abdullah", "Sara", "Mohammed", "Noura", "Fahad", "Reem", "Khalid", "Lama",
            "Saud", "Hessa", "Turki", "Maha", "Sultan", "Dana", "Nasser", "Joud", "Omar", "Lujain", "Yousef", "Ghada",
            "Faisal", "Amal", "Bandar", "Shahad", "Majed", "Rana", "Hamad", "Wejdan", "Rakan", "Deema"};
    private static final String[] LAST = {"Al-Otaibi", "Al-Qahtani", "Al-Shehri", "Al-Harbi", "Al-Dosari",
            "Al-Ghamdi", "Al-Zahrani", "Al-Mutairi", "Al-Anazi", "Al-Subaie", "Al-Shammari", "Al-Malki"};
    private static final String[] COMPETITOR_PREFIX = {"Al Nahdi", "Al Dawaa", "Corner", "City", "Family", "Golden", "Green", "Prime", "Star"};

    /** What the seed produced, plus the real dates for rows whose created_at Hibernate stamps as "now". */
    record Result(int ideas, int residents, List<Object[]> voteDates, List<Object[]> updateDates) {
    }

    // ---------- Seeding ----------

    @Override
    public void run(String... args) {
        if (!enabled || userRepository.existsByEmail(MARKER_EMAIL)) {
            return;
        }
        try {
            Result result = Objects.requireNonNull(transactionTemplate.execute(status -> seed()));
            backdate(result);
            log.info("Demo data created: {} ideas and {} residents. Log in as investor1{} (verified investor) "
                            + "or resident1{} (resident in Al Narjis), password {}",
                    result.ideas(), result.residents(), DOMAIN, DOMAIN, PASSWORD);
        } catch (RuntimeException e) {
            log.warn("Demo data wasn't created, it will be tried again on the next startup: {}", e.getMessage(), e);
        }
    }

    private Result seed() {
        String hash = passwordEncoder.encode(PASSWORD);
        LocalDateTime now = LocalDateTime.now();

        // Reuse neighborhoods that already exist (name + city is unique in the table), create the rest
        Map<String, Neighborhood> hoods = new HashMap<>();
        for (Neighborhood n : neighborhoodRepository.findAll()) {
            hoods.put(key(n.getName(), n.getCity()), n);
        }
        for (Hood h : HOODS) {
            Neighborhood n = hoods.get(key(h.name(), h.city()));
            if (n == null) {
                hoods.put(key(h.name(), h.city()), neighborhoodRepository.save(Neighborhood.builder()
                        .name(h.name()).city(h.city()).latitude(h.lat()).longitude(h.lng()).build()));
            } else if (n.getLatitude() == null || n.getLongitude() == null) {
                // An existing row without a location can't place residents or ideas on the map
                n.setLatitude(h.lat());
                n.setLongitude(h.lng());
            }
        }

        List<User> investors = List.of(
                investor(1, "Faisal Al-Qahtani", "ABOVE_700K", "FOOD,HEALTH,FITNESS", InvestorStatus.VERIFIED, hash, now),
                investor(2, "Nora Al-Harbi", "FROM_300K_TO_700K", "HEALTH,EDUCATION", InvestorStatus.VERIFIED, hash, now),
                investor(3, "Majed Al-Dosari", "UP_TO_300K", "RETAIL,HOME_SERVICES", InvestorStatus.PENDING, hash, now),
                investor(4, "Rana Al-Malki", "UP_TO_300K", "FOOD", InvestorStatus.REJECTED, hash, now));

        // Enough residents in each neighborhood with ideas to cast the votes (plus the idea owners)
        Map<String, Integer> needed = new HashMap<>();
        for (Seed s : IDEAS) {
            needed.merge(key(s.hood(), s.city()), s.votes() + 3, Math::max);
        }
        List<String> hoodKeys = new ArrayList<>(needed.keySet());
        hoodKeys.sort(String::compareTo);
        // Al Narjis first so resident1 is easy to find
        hoodKeys.remove(key("Al Narjis", "Riyadh"));
        hoodKeys.add(0, key("Al Narjis", "Riyadh"));

        Map<String, List<User>> residentsByHood = new HashMap<>();
        List<User> allResidents = new ArrayList<>();
        int number = 0;
        for (String hoodKey : hoodKeys) {
            Neighborhood n = hood(hoods, hoodKey);
            List<User> list = new ArrayList<>();
            for (int i = 0; i < needed.get(hoodKey); i++) {
                number++;
                double[] spot = jitter(n.getLatitude(), n.getLongitude(), number, 0.9);
                list.add(User.builder()
                        .fullName(FIRST[number % FIRST.length] + " " + LAST[(number / FIRST.length + number) % LAST.length])
                        .email("resident" + number + DOMAIN)
                        .password(hash)
                        .authProvider(AuthProvider.LOCAL)
                        .role(Role.RESIDENT)
                        .neighborhood(n)
                        .latitude(spot[0])
                        .longitude(spot[1])
                        .emailVerified(true)
                        .enabled(true)
                        .build());
            }
            residentsByHood.put(hoodKey, list);
            allResidents.addAll(list);
        }
        userRepository.saveAll(allResidents);

        Map<String, Integer> ownersUsed = new HashMap<>();
        List<Object[]> voteDates = new ArrayList<>();
        List<Object[]> updateDates = new ArrayList<>();
        int index = 0;
        for (Seed s : IDEAS) {
            index++;
            String hoodKey = key(s.hood(), s.city());
            Neighborhood n = hood(hoods, hoodKey);
            List<User> residents = residentsByHood.get(hoodKey);
            User owner = s.owner().startsWith("I")
                    ? investors.get(Integer.parseInt(s.owner().substring(1)) - 1)
                    : residents.get(ownersUsed.merge(hoodKey, 1, Integer::sum) - 1);
            double[] spot = jitter(n.getLatitude(), n.getLongitude(), index * 7, 0.5);
            LocalDateTime published = now.minusDays(10 + index * 2L);
            boolean reached = s.votes() >= s.threshold();
            LocalDateTime confirmed = reached ? published.plusDays(8) : null;

            Idea idea = ideaRepository.save(Idea.builder()
                    .owner(owner)
                    .neighborhood(n)
                    .title(s.title())
                    .description(s.description())
                    .category(s.category())
                    .status(s.status())
                    .membershipThreshold(s.threshold())
                    .latitude(spot[0])
                    .longitude(spot[1])
                    .publishedAt(published)
                    .confirmedAt(confirmed)
                    .unlockPrice(BigDecimal.valueOf(s.priceSar()).setScale(2))
                    .executionScore(Math.min(95, s.score() - 4))
                    .successScore(Math.min(95, s.score() + 2))
                    .opportunityScore(Math.min(98, s.score() + (reached ? 6 : 0)))
                    .priceReasoning("Strong verified demand and few competitors nearby. The price reflects the expected capital and the size of the neighborhood.")
                    .priceQuotedAt(published.plusDays(1))
                    .build());

            foundingMemberRepository.save(FoundingMember.builder()
                    .user(owner).idea(idea).memberNumber(1).ideaOwner(true).consentGiven(true).build());

            // Votes from neighbors (never the owner), cast between publishing and the target being reached
            List<Vote> votes = new ArrayList<>();
            for (User voter : residents) {
                if (votes.size() >= s.votes()) {
                    break;
                }
                if (voter.getId().equals(owner.getId())) {
                    continue;
                }
                votes.add(Vote.builder().user(voter).idea(idea).distanceKm(LocationService.round2(
                        LocationService.distanceKm(voter.getLatitude(), voter.getLongitude(), spot[0], spot[1]))).build());
            }
            voteRepository.saveAll(votes);
            LocalDateTime votingEnds = reached ? confirmed : now;
            for (int j = 0; j < votes.size(); j++) {
                // When the target is reached the last vote lands exactly on confirmedAt
                int steps = reached ? votes.size() : votes.size() + 1;
                voteDates.add(row(between(published, votingEnds, j + 1, steps), votes.get(j).getId()));
            }

            seedCheck(idea, s, index);
            seedBusinessModel(idea, s);
            if (reached || s.owner().startsWith("I")) {
                feasibilityReportRepository.save(FeasibilityReport.builder()
                        .idea(idea)
                        .gapScore(Math.min(96, s.score() + 3))
                        .memberGrowthPercent(Math.min(100.0, s.votes() * 100.0 / s.threshold()))
                        .peakDemandTime("Evenings and weekends")
                        .recommendation("Demand is verified and competition is limited within " + s.radiusKm()
                                + " km. Open within 90 days and offer the founding members an opening discount.")
                        .risks("Rent in the area is rising; secure a lease before fit-out.")
                        .build());
            }
            if (s.adoptedBy() != null) {
                if (confirmed == null) {
                    throw new IllegalStateException("'" + s.title() + "' is adopted but never reached its target");
                }
                seedAdoption(idea, investors.get(s.adoptedBy() - 1), s, confirmed, now, updateDates);
            }
        }
        return new Result(IDEAS.size(), allResidents.size(), voteDates, updateDates);
    }

    private User investor(int number, String name, String budget, String fields, InvestorStatus status, String hash,
                          LocalDateTime now) {
        return userRepository.save(User.builder()
                .fullName(name)
                .email("investor" + number + DOMAIN)
                .password(hash)
                .authProvider(AuthProvider.LOCAL)
                .role(Role.INVESTOR)
                .crNumber(String.valueOf(1010000000L + number * 12345L))
                .investmentBudget(budget)
                .investmentFields(fields)
                .investorStatus(status)
                .investorReviewedAt(status == InvestorStatus.PENDING ? null : now.minusDays(60))
                .latitude(24.7136)
                .longitude(46.6753)
                .emailVerified(true)
                .enabled(true)
                .build());
    }

    private void seedCheck(Idea idea, Seed s, int index) {
        ideaCheckRepository.save(IdeaCheck.builder()
                .idea(idea)
                .score(s.score())
                .decision(ScreeningDecision.FIT.name())
                .searchRadiusKm(s.radiusKm())
                .competitorsCount(s.competitors())
                .nearestCompetitorKm(s.competitors() == 0 ? null : LocationService.round2(s.radiusKm() * 0.55))
                .scarcityFound(true)
                .placeType(s.placeType())
                .feasibilitySummary("Only " + s.competitors() + " similar businesses within " + s.radiusKm()
                        + " km, below the city average, and no similar idea is open nearby. A shop of 120 to 250 m² "
                        + "on a main street fits; it needs a municipal license.")
                .build());
        List<NearbyPlace> places = new ArrayList<>();
        for (int k = 0; k < s.competitors(); k++) {
            places.add(NearbyPlace.builder()
                    .idea(idea)
                    .googlePlaceId("demo:" + idea.getId() + "-" + k)
                    .name(COMPETITOR_PREFIX[(index + k) % COMPETITOR_PREFIX.length] + " " + s.category())
                    .category(s.placeType())
                    .distanceKm(LocationService.round2(s.radiusKm() * (0.55 + k * 0.12)))
                    .build());
        }
        nearbyPlaceRepository.saveAll(places);
    }

    private void seedBusinessModel(Idea idea, Seed s) {
        businessModelRepository.save(BusinessModel.builder()
                .idea(idea)
                .valueProposition("A " + s.category().toLowerCase(Locale.ROOT) + " the neighborhood is missing, backed by "
                        + s.votes() + " residents who asked for it.")
                .targetCustomers("Families and working residents of " + s.hood() + " and the streets around it.")
                .revenueStreams("Walk-in sales, app orders and delivery, and memberships or packages for regulars.")
                .costStructure("Rent for a 120–250 m² shop, fit-out and equipment, staff salaries, utilities and licenses.")
                .channels("Founding members on Naseej, neighborhood WhatsApp groups, delivery apps and Google Maps.")
                .estimatedCapitalMin(BigDecimal.valueOf(s.capitalMin()))
                .estimatedCapitalMax(BigDecimal.valueOf(s.capitalMax()))
                .risks("Competitors may lower prices when it opens; rent increases in the area.")
                .build());
    }

    /**
     * The state the real flow ends in (UnlockService + ContractFlowService): the investor pays the unlock fee,
     * sets the founding members' benefits, both parties sign, and the contract becomes ACTIVE with 90 days to open.
     */
    private void seedAdoption(Idea idea, User investor, Seed s, LocalDateTime confirmed, LocalDateTime now,
                              List<Object[]> updateDates) {
        LocalDateTime adoptedAt = confirmed.plusDays(2);
        LocalDateTime activatedAt = adoptedAt.plusDays(1);
        int last = ProjectService.STAGES.indexOf(s.stage());
        if (last < 0) {
            throw new IllegalStateException("Unknown project stage '" + s.stage() + "', use one of " + ProjectService.STAGES);
        }
        boolean opened = last == ProjectService.STAGES.size() - 1;
        LocalDateTime openedAt = opened ? now.minusDays(3) : null;

        BigDecimal amount = BigDecimal.valueOf(s.priceSar()).setScale(2);
        BigDecimal vat = amount.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
        Payment payment = paymentRepository.save(Payment.builder()
                .user(investor)
                .purpose(PaymentPurpose.OPPORTUNITY_ACCESS)
                .amount(amount)
                .vat(vat)
                .totalAmount(amount.add(vat))
                .method(PaymentMethod.MADA)
                .moyasarPaymentId("demo-" + idea.getId())
                .status(PaymentStatus.PAID)
                .paidAt(adoptedAt)
                .build());
        OpportunityAccess access = opportunityAccessRepository.save(OpportunityAccess.builder()
                .investor(investor)
                .idea(idea)
                .payment(payment)
                .accessType(AccessType.PAID)
                .build());

        Contract contract = Contract.builder()
                .contractNumber("NSJ-DEMO-" + idea.getId())
                .idea(idea)
                .investor(investor)
                .ideaOwner(idea.getOwner())
                .access(access)
                // The real flow requires a members' discount or benefits note, and the owner's discount >= the members'
                .foundingMemberDiscount(new BigDecimal("10.00"))
                .ideaOwnerDiscount(new BigDecimal("15.00"))
                .benefitsNote("Founding members get 10% off for the first year and priority booking at the opening.")
                .benefitsSetAt(adoptedAt.plusHours(2))
                .openingDeadline(activatedAt.toLocalDate().plusDays(OPENING_DEADLINE_DAYS))
                .build();
        contract.activate();
        contract.setActivatedAt(activatedAt);
        contract.setEmailSentAt(activatedAt);
        contract.setOpenedAt(openedAt);
        contract = contractRepository.save(contract);
        contract.setPdfUrl(contextPath + "/projects/" + contract.getId() + "/contract");

        sign(contract, investor, ContractFlowService.ROLE_INVESTOR, adoptedAt.plusHours(3));
        if (!Objects.equals(investor.getId(), idea.getOwner().getId())) {
            sign(contract, idea.getOwner(), ContractFlowService.ROLE_IDEA_OWNER, activatedAt);
        }

        // One update per stage after "Unlocked", spread from activation until today (or the opening day)
        LocalDateTime end = opened ? openedAt : now.minusHours(6);
        for (int k = 1; k <= last; k++) {
            String stage = ProjectService.STAGES.get(k);
            ProjectUpdate update = projectUpdateRepository.save(contract.addUpdate(stage,
                    "Demo update: " + stage.toLowerCase(Locale.ROOT) + " done."));
            updateDates.add(row(between(activatedAt, end, k, last), update.getId()));
        }
    }

    private void sign(Contract contract, User signer, String role, LocalDateTime at) {
        contractSignatureRepository.save(ContractSignature.builder()
                .contract(contract)
                .signer(signer)
                .signerRole(role)
                .typedName(signer.getFullName())
                .ipAddress("127.0.0.1")
                .otpVerified(true)
                .termsAccepted(true)
                .signedAt(at)
                .build());
    }

    // Votes and project updates are stamped "now" by @CreationTimestamp on insert; set the dates they would
    // really have, so the demand momentum and the project timeline look real. Plain SQL, works on any database.
    private void backdate(Result result) {
        try {
            jdbcTemplate.batchUpdate("UPDATE votes SET created_at = ? WHERE id = ?", result.voteDates());
            jdbcTemplate.batchUpdate("UPDATE project_updates SET created_at = ? WHERE id = ?", result.updateDates());
        } catch (RuntimeException e) {
            log.warn("Demo votes and project updates keep today's date: {}", e.getMessage());
        }
    }

    // ---------- Helpers ----------

    private static String key(String name, String city) {
        return (name.trim() + "|" + city.trim()).toLowerCase(Locale.ROOT);
    }

    private static Neighborhood hood(Map<String, Neighborhood> hoods, String hoodKey) {
        Neighborhood n = hoods.get(hoodKey);
        if (n == null) {
            throw new IllegalStateException("An idea uses a neighborhood that isn't in HOODS: " + hoodKey);
        }
        return n;
    }

    // The point step/steps of the way from start to end
    private static LocalDateTime between(LocalDateTime start, LocalDateTime end, int step, int steps) {
        long seconds = Duration.between(start, end).getSeconds();
        return start.plusSeconds(seconds * step / Math.max(1, steps));
    }

    private static Object[] row(LocalDateTime at, Integer id) {
        return new Object[]{Timestamp.valueOf(at), id};
    }

    // A point up to maxKm from the center, the same for the same seed
    private static double[] jitter(double lat, double lng, int seed, double maxKm) {
        double angle = Math.toRadians((seed * 97.3) % 360);
        double km = maxKm * (0.25 + ((seed * 37) % 75) / 100.0);
        double dLat = km / 111.32 * Math.cos(angle);
        double dLng = km / (111.32 * Math.cos(Math.toRadians(lat))) * Math.sin(angle);
        return new double[]{lat + dLat, lng + dLng};
    }
}
