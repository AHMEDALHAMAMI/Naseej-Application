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
import com.waleed.naseej.service.VoteTarget;
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
 * Everything people read is in Arabic (neighborhoods, names, ideas, AI text, project notes). Cities stay in English
 * because they are the key the website translates and groups by (cityLabel), and categories and project stages stay
 * as their keys (cat.* / stage.* in strings.js).
 *
 * The data follows the same rules as the real flow: votes come only from residents of the neighborhood
 * (never the idea's owner), voting stops when the target is reached, and an adopted idea has a paid
 * access, the investor's signature and an ACTIVE contract with its 180 days, in that order.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    public static final String PASSWORD = "Demo12345";
    private static final String DOMAIN = "@demo.naseej.sa";
    private static final String MARKER_EMAIL = "investor1" + DOMAIN;

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
            new Hood("النرجس", "Riyadh", 24.8650, 46.6600),
            new Hood("الياسمين", "Riyadh", 24.8238, 46.6400),
            new Hood("الملقا", "Riyadh", 24.8095, 46.6050),
            new Hood("حطين", "Riyadh", 24.7650, 46.6000),
            new Hood("الصحافة", "Riyadh", 24.8020, 46.6380),
            new Hood("العقيق", "Riyadh", 24.7730, 46.6290),
            new Hood("النخيل", "Riyadh", 24.7480, 46.6370),
            new Hood("العليا", "Riyadh", 24.6930, 46.6850),
            new Hood("السليمانية", "Riyadh", 24.7040, 46.7000),
            new Hood("الورود", "Riyadh", 24.7230, 46.6740),
            new Hood("المروج", "Riyadh", 24.7540, 46.6650),
            new Hood("الربوة", "Riyadh", 24.6950, 46.7600),
            new Hood("الملز", "Riyadh", 24.6650, 46.7330),
            new Hood("الروضة", "Riyadh", 24.7330, 46.7750),
            new Hood("قرطبة", "Riyadh", 24.8100, 46.7400),
            new Hood("الندى", "Riyadh", 24.8120, 46.6900),
            new Hood("العارض", "Riyadh", 24.8800, 46.6150),
            new Hood("المحمدية", "Riyadh", 24.7350, 46.6550),
            new Hood("لبن", "Riyadh", 24.6400, 46.5600),
            new Hood("السويدي", "Riyadh", 24.6000, 46.6650),
            new Hood("النسيم", "Riyadh", 24.7350, 46.8250),
            new Hood("الروضة", "Jeddah", 21.5650, 39.1550),
            new Hood("الزهراء", "Jeddah", 21.5880, 39.1400),
            new Hood("السلامة", "Jeddah", 21.5950, 39.1550),
            new Hood("النعيم", "Jeddah", 21.6200, 39.1500),
            new Hood("الصفا", "Jeddah", 21.5800, 39.2000),
            new Hood("الحمراء", "Jeddah", 21.5250, 39.1650),
            new Hood("أبحر الشمالية", "Jeddah", 21.7300, 39.1000),
            new Hood("الشاطئ", "Dammam", 26.4500, 50.1200),
            new Hood("الفيصلية", "Dammam", 26.4100, 50.0400),
            new Hood("المزروعية", "Dammam", 26.4420, 50.0550),
            new Hood("العليا", "Khobar", 26.2800, 50.1950),
            new Hood("العقربية", "Khobar", 26.2950, 50.2000),
            new Hood("قباء", "Madinah", 24.4400, 39.6170),
            new Hood("العزيزية", "Makkah", 21.4000, 39.8650));

    /**
     * One idea: owner is "R" (a resident of the neighborhood) or "I" + an investor number.
     * adoptedBy/stage: which demo investor adopted it and the project stage they reached (a ProjectService.STAGES name).
     * threshold/votes are hand-written progress (e.g. 22 of 30); the real target comes from the capital like any
     * idea (target()), and the votes keep the same share of it (votes()).
     */
    record Seed(String title, String description, String category, String hood, String city, int threshold, int votes,
                IdeaStatus status, String owner, Integer adoptedBy, String stage, int score, int competitors,
                double radiusKm, String placeType, long capitalMin, long capitalMax, int priceSar) {

        int target() {
            return VoteTarget.forCapital(BigDecimal.valueOf(capitalMin), BigDecimal.valueOf(capitalMax));
        }

        int votesForTarget() {
            int target = target();
            if (votes >= threshold) {
                return target;
            }
            return Math.min(target - 1, (int) Math.round(votes * (double) target / threshold));
        }
    }

    private static final List<Seed> IDEAS = List.of(
            new Seed("صيدلية 24 ساعة قريبة من الجامع",
                    "أقرب صيدلية تقفل الساعة 12 بالليل، والعوايل اللي عندهم أطفال يضطرون يروحون لطريق الملك فهد. صيدلية 24 ساعة جنب مواقف الجامع تخدم شمال الحي كله.",
                    "Pharmacy", "النرجس", "Riyadh", 30, 22, IdeaStatus.PUBLISHED, "R", null, null,
                    82, 2, 1.0, "pharmacy", 450_000, 750_000, 14_000),
            new Seed("قهوة مختصة مع ركن هادئ للمذاكرة",
                    "الطلاب واللي يشتغلون عن بعد ما عندهم مكان هادي يجلسون فيه. نبي قهوة مختصة فيها منطقة هادية للمذاكرة وإنترنت قوي وتفتح لوقت متأخر.",
                    "Cafe", "النرجس", "Riyadh", 25, 25, IdeaStatus.COMPLETED, "R", null, null,
                    76, 4, 1.0, "cafe", 380_000, 620_000, 12_000),
            new Seed("نادي نسائي بحصص مسائية",
                    "ما فيه نادي نسائي بحدود 4 كم. كثير منا تبي حصص مسائية ومسبح صغير وغرفة للأطفال وقت التمرين.",
                    "Women's gym", "الياسمين", "Riyadh", 30, 30, IdeaStatus.COMPLETED, "R", null, null,
                    88, 1, 3.0, "gym", 900_000, 1_600_000, 28_000),
            new Seed("مركز ألعاب وأنشطة داخلي للأطفال",
                    "العصريات بالصيف حارة والحدائق ما تنفع. نبي مركز ألعاب داخلي فيه ألعاب ناعمة وجدار تسلق وقاعات لأعياد الميلاد.",
                    "Kids activities", "الياسمين", "Riyadh", 35, 14, IdeaStatus.PUBLISHED, "R", null, null,
                    79, 1, 3.0, "child_care_agency", 700_000, 1_200_000, 18_000),
            new Seed("مخبز طازج بفطور سعودي",
                    "مخبز يفتح الساعة 5 الفجر، فيه خبز طازج وصامولي وفول وتميس للي رايحين الدوامات والمدارس.",
                    "Bakery", "الملقا", "Riyadh", 20, 20, IdeaStatus.ADOPTED, "R", 1, "Fit-out",
                    84, 2, 1.0, "bakery", 300_000, 520_000, 9_000),
            new Seed("مغسلة ملابس باستلام وتوصيل",
                    "العوايل اللي تشتغل تحتاج مغسلة تستلم وتوصّل عن طريق تطبيق، مع كوي للثياب والعبايات.",
                    "Laundry", "الملقا", "Riyadh", 25, 9, IdeaStatus.PUBLISHED, "R", null, null,
                    71, 3, 1.0, "laundry", 180_000, 320_000, 6_000),
            new Seed("ملاهي عائلية للأطفال الصغار",
                    "ملاهي صغيرة مفتوحة فيها عجلة دوّارة صغيرة ودوّامة وجلسات مظللة للأهل، تفتح بالمساء.",
                    "Kids activities", "حطين", "Riyadh", 40, 31, IdeaStatus.PUBLISHED, "R", null, null,
                    86, 0, 5.0, "child_care_agency", 2_500_000, 4_000_000, 45_000),
            new Seed("عيادة أطفال تفتح نهاية الأسبوع",
                    "الأهل ينتظرون ساعات في المستشفى يوم الجمعة. نبي عيادة أطفال تفتح نهاية الأسبوع وبالمساء.",
                    "Clinic", "حطين", "Riyadh", 30, 30, IdeaStatus.ADOPTED, "R", 2, "Licensing",
                    90, 1, 3.0, "doctor", 1_200_000, 2_000_000, 32_000),
            new Seed("سوبرماركت للحي بخضار طازجة",
                    "أقرب سوبرماركت ورا الطريق السريع. نحتاج سوق متوسط فيه خضار ولحوم طازجة واحتياجات البيت اليومية.",
                    "Grocery", "الصحافة", "Riyadh", 30, 18, IdeaStatus.PUBLISHED, "R", null, null,
                    80, 2, 1.0, "supermarket", 1_000_000, 1_800_000, 22_000),
            new Seed("مطعم أكل صحي سريع",
                    "موظفين المكاتب حول العليا يبون غدا صحي وسريع: بولات ومشويات وسلطات، مع توصيل سريع.",
                    "Restaurant", "العليا", "Riyadh", 25, 25, IdeaStatus.OPENED, "R", 1, "Opened",
                    78, 9, 1.0, "restaurant", 600_000, 950_000, 15_000),
            new Seed("حلاق بحجز من الجوال",
                    "محل حلاقة نظيف وحديث تحجز موعدك فيه من جوالك بدل الانتظار.",
                    "Barber", "النخيل", "Riyadh", 20, 7, IdeaStatus.PUBLISHED, "R", null, null,
                    68, 3, 1.5, "barber_shop", 120_000, 220_000, 4_000),
            new Seed("مغسلة سيارات وخدمة سريعة",
                    "مغسلة سيارات فيها تغيير زيت وخدمة كفرات عشان ما نضطر نروح للصناعية.",
                    "Car services", "قرطبة", "Riyadh", 25, 12, IdeaStatus.PUBLISHED, "R", null, null,
                    73, 1, 2.0, "car_wash", 400_000, 700_000, 8_000),
            new Seed("قرطاسية ومركز طباعة",
                    "قبل كل فصل دراسي الأهل يروحون بعيد عشان القرطاسية والطباعة. محل قريب من المدارس بيوفر وقت كثير.",
                    "School supplies", "الروضة", "Riyadh", 20, 16, IdeaStatus.PUBLISHED, "R", null, null,
                    70, 1, 1.5, "book_store", 150_000, 260_000, 4_500),
            new Seed("مغسلة سيارات متنقلة",
                    "خدمة غسيل سيارات تأتي إلى البيت بموعد مسبق، بدون الحاجة للانتظار في المغاسل البعيدة.",
                    "Car services", "الياسمين", "Riyadh", 20, 6, IdeaStatus.PUBLISHED, "R", null, null,
                    66, 2, 2.0, "car_wash", 90_000, 160_000, 3_000),
            new Seed("مخبز فرنسي وحلويات",
                    "مخبز يقدم الكرواسون والخبز الفرنسي والحلويات الطازجة مع جلسات صغيرة للعائلات.",
                    "Bakery", "حطين", "Riyadh", 25, 13, IdeaStatus.PUBLISHED, "R", null, null,
                    74, 2, 1.0, "bakery", 350_000, 600_000, 9_500),
            new Seed("مقهى عمل مشترك",
                    "مقهى فيه كبائن خاصة وغرف اجتماعات واشتراك شهري للي يشتغلون لحسابهم في شمال الرياض.",
                    "Cafe", "الملقا", "Riyadh", 30, 10, IdeaStatus.PUBLISHED, "I1", null, null,
                    77, 3, 1.0, "cafe", 700_000, 1_100_000, 16_000),
            new Seed("ركن عصيرات وحلى",
                    "العوايل اللي يتمشّون على الكورنيش بالمساء يبون عصير طازج وحلى قريب من البيت.",
                    "Cafe", "الزهراء", "Jeddah", 25, 11, IdeaStatus.PUBLISHED, "R", null, null,
                    72, 3, 1.0, "juice_shop", 200_000, 350_000, 6_000),
            new Seed("عيادة علاج طبيعي",
                    "كثير من كبار السن والرياضيين يحتاجون علاج طبيعي، وأقرب مركز قائمة الانتظار فيه ثلاث أسابيع.",
                    "Clinic", "السلامة", "Jeddah", 30, 30, IdeaStatus.COMPLETED, "R", null, null,
                    85, 1, 3.0, "physiotherapist", 800_000, 1_300_000, 24_000),
            new Seed("صالون نسائي مع خدمة منزلية",
                    "صالون نسائي يرسل مختصات للبيوت وقت الزواجات والمناسبات.",
                    "Salon", "الروضة", "Jeddah", 25, 19, IdeaStatus.PUBLISHED, "R", null, null,
                    75, 4, 1.5, "beauty_salon", 250_000, 450_000, 7_000),
            new Seed("صيدلية بتوصيل للبيت",
                    "صيدلية توصّل الوصفات للباب خلال ساعة، للعوايل وكبار السن.",
                    "Pharmacy", "الشاطئ", "Dammam", 25, 8, IdeaStatus.PUBLISHED, "R", null, null,
                    74, 2, 1.0, "pharmacy", 420_000, 700_000, 11_000),
            new Seed("صالة ترامبولين داخلية",
                    "المراهقين ما عندهم شي يسوونه بنهاية الأسبوع. صالة ترامبولين ومسار نينجا بتكون زحمة كل مساء.",
                    "Kids activities", "العليا", "Khobar", 35, 21, IdeaStatus.PUBLISHED, "R", null, null,
                    83, 0, 5.0, "sports_club", 1_800_000, 3_000_000, 38_000));

    private static final String[] FIRST = {"عبدالله", "سارة", "محمد", "نورة", "فهد", "ريم", "خالد", "لمى",
            "سعود", "حصة", "تركي", "مها", "سلطان", "دانة", "ناصر", "جود", "عمر", "لجين", "يوسف", "غادة",
            "فيصل", "أمل", "بندر", "شهد", "ماجد", "رنا", "حمد", "وجدان", "راكان", "ديمة"};
    private static final String[] LAST = {"العتيبي", "القحطاني", "الشهري", "الحربي", "الدوسري",
            "الغامدي", "الزهراني", "المطيري", "العنزي", "السبيعي", "الشمري", "المالكي"};
    // Made-up shop names for the competitors the AI check "found" ("صيدلية الريان")
    private static final String[] COMPETITOR_NAME = {"الريان", "الواحة", "الركن", "المدينة", "الأسرة", "اللؤلؤة",
            "النخبة", "الصفوة", "النجمة"};
    // The shop word in a competitor's name, by idea category
    private static final Map<String, String> SHOP_WORD = Map.ofEntries(
            Map.entry("Pharmacy", "صيدلية"), Map.entry("Cafe", "مقهى"), Map.entry("Women's gym", "نادي"),
            Map.entry("Gym", "نادي"), Map.entry("Kids activities", "مركز"), Map.entry("Bakery", "مخبز"),
            Map.entry("Laundry", "مغسلة"), Map.entry("Clinic", "عيادة"), Map.entry("Grocery", "أسواق"),
            Map.entry("Restaurant", "مطعم"), Map.entry("Barber", "صالون"), Map.entry("Salon", "مشغل"),
            Map.entry("Car services", "مغسلة"), Map.entry("School supplies", "مكتبة"));
    // What the idea is, in Arabic, for the business model text
    private static final Map<String, String> CATEGORY_AR = Map.ofEntries(
            Map.entry("Pharmacy", "صيدلية"), Map.entry("Cafe", "مقهى"), Map.entry("Women's gym", "نادي رياضي نسائي"),
            Map.entry("Gym", "نادي رياضي"), Map.entry("Kids activities", "مكان لأنشطة الأطفال"), Map.entry("Bakery", "مخبز"),
            Map.entry("Laundry", "مغسلة ملابس"), Map.entry("Clinic", "عيادة"), Map.entry("Grocery", "سوبرماركت"),
            Map.entry("Restaurant", "مطعم"), Map.entry("Barber", "حلاق"), Map.entry("Salon", "صالون نسائي"),
            Map.entry("Car services", "خدمات سيارات"), Map.entry("School supplies", "قرطاسية"));
    // ProjectService.STAGES in Arabic, for the project notes
    private static final Map<String, String> STAGE_AR = Map.of(
            "Unlocked", "فتح الفرصة", "Licensing", "التراخيص", "Location secured", "تأمين الموقع",
            "Fit-out", "التجهيز", "Hiring", "التوظيف", "Soft opening", "الافتتاح التجريبي", "Opened", "الافتتاح");

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
                            + "or resident1{} (resident in النرجس), password {}",
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
                investor(1, "فيصل القحطاني", "ABOVE_700K", "FOOD,HEALTH,FITNESS", InvestorStatus.VERIFIED, hash, now),
                investor(2, "نورة الحربي", "FROM_300K_TO_700K", "HEALTH,EDUCATION", InvestorStatus.VERIFIED, hash, now),
                investor(3, "ماجد الدوسري", "UP_TO_300K", "RETAIL,HOME_SERVICES", InvestorStatus.PENDING, hash, now),
                investor(4, "رنا المالكي", "UP_TO_300K", "FOOD", InvestorStatus.REJECTED, hash, now));

        // Enough residents in each neighborhood with ideas to cast the votes (plus the idea owners)
        Map<String, Integer> needed = new HashMap<>();
        for (Seed s : IDEAS) {
            needed.merge(key(s.hood(), s.city()), s.votesForTarget() + 3, Math::max);
        }
        List<String> hoodKeys = new ArrayList<>(needed.keySet());
        hoodKeys.sort(String::compareTo);
        // النرجس first so resident1 is easy to find
        hoodKeys.remove(key("النرجس", "Riyadh"));
        hoodKeys.add(0, key("النرجس", "Riyadh"));

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
            boolean reached = s.votesForTarget() >= s.target();
            LocalDateTime confirmed = reached ? published.plusDays(8) : null;

            Idea idea = ideaRepository.save(Idea.builder()
                    .owner(owner)
                    .neighborhood(n)
                    .title(s.title())
                    .description(s.description())
                    .category(s.category())
                    .status(s.status())
                    .membershipThreshold(s.target())
                    .latitude(spot[0])
                    .longitude(spot[1])
                    .publishedAt(published)
                    .confirmedAt(confirmed)
                    .unlockPrice(BigDecimal.valueOf(s.priceSar()).setScale(2))
                    .executionScore(Math.min(95, s.score() - 4))
                    .successScore(Math.min(95, s.score() + 2))
                    .opportunityScore(Math.min(98, s.score() + (reached ? 6 : 0)))
                    .priceReasoning("طلب موثّق وقوي ومنافسين قليلين حول المكان. السعر يعكس رأس المال المتوقع وحجم الحي.")
                    .priceQuotedAt(published.plusDays(1))
                    .build());

            foundingMemberRepository.save(FoundingMember.builder()
                    .user(owner).idea(idea).memberNumber(1).ideaOwner(true).consentGiven(true).build());

            // Votes from neighbors (never the owner), cast between publishing and the target being reached
            List<Vote> votes = new ArrayList<>();
            for (User voter : residents) {
                if (votes.size() >= s.votesForTarget()) {
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
                        .memberGrowthPercent(Math.min(100.0, s.votesForTarget() * 100.0 / s.target()))
                        .peakDemandTime("المساء ونهاية الأسبوع")
                        .recommendation("الطلب موثّق والمنافسة محدودة في نطاق " + km(s.radiusKm())
                                + " كم. الأفضل التجهيز للافتتاح خلال 6 شهور من توقيع العقد.")
                        .risks("الإيجارات في المنطقة ترتفع، فالأفضل توقيع عقد الإيجار قبل التجهيز.")
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
                .crNumber(String.valueOf(7000000000L + number * 12345L))
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
                .feasibilitySummary(similarCount(s.competitors()) + " في نطاق " + km(s.radiusKm())
                        + " كم، أقل من متوسط المدينة، وما فيه فكرة مشابهة مفتوحة قريب. يناسبها محل من 120 إلى 250 م² "
                        + "على شارع رئيسي، وتحتاج رخصة بلدية.")
                .build());
        List<NearbyPlace> places = new ArrayList<>();
        for (int k = 0; k < s.competitors(); k++) {
            places.add(NearbyPlace.builder()
                    .idea(idea)
                    .googlePlaceId("demo:" + idea.getId() + "-" + k)
                    .name(SHOP_WORD.getOrDefault(s.category(), "محل") + " "
                            + COMPETITOR_NAME[(index + k) % COMPETITOR_NAME.length])
                    .category(s.placeType())
                    .distanceKm(LocationService.round2(s.radiusKm() * (0.55 + k * 0.12)))
                    .build());
        }
        nearbyPlaceRepository.saveAll(places);
    }

    private void seedBusinessModel(Idea idea, Seed s) {
        businessModelRepository.save(BusinessModel.builder()
                .idea(idea)
                .valueProposition("يلبّي طلب " + s.votesForTarget() + " من سكان " + s.hood() + " على "
                        + CATEGORY_AR.getOrDefault(s.category(), "خدمة") + " في حيّهم.")
                .targetCustomers("العوايل والموظفين من سكان " + s.hood() + " والشوارع اللي حوله.")
                .revenueStreams("البيع المباشر، والطلبات من التطبيقات والتوصيل، واشتراكات أو باقات للعملاء الدائمين.")
                .costStructure("إيجار محل من 120 إلى 250 م²، والتجهيز والمعدات، ورواتب الموظفين، والفواتير والتراخيص.")
                .channels("الأعضاء المؤسسين في نسيج، وقروبات واتساب الحي، وتطبيقات التوصيل، وخرائط قوقل.")
                .estimatedCapitalMin(BigDecimal.valueOf(s.capitalMin()))
                .estimatedCapitalMax(BigDecimal.valueOf(s.capitalMax()))
                .risks("ممكن المنافسين ينزّلون أسعارهم وقت الافتتاح، والإيجارات في المنطقة ترتفع.")
                .build());
    }

    /**
     * The state the real flow ends in (UnlockService + ContractFlowService): the investor pays the unlock fee,
     * signs, and the contract becomes ACTIVE with 180 days to open (the idea owner doesn't sign).
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
                .ideaOwnerDiscount(BigDecimal.ZERO)
                .signingDeadline(adoptedAt.toLocalDate().plusDays(ContractFlowService.SIGNING_DEADLINE_DAYS))
                .openingDeadline(activatedAt.toLocalDate().plusDays(ContractFlowService.OPENING_DEADLINE_DAYS))
                .build();
        contract.activate();
        contract.setActivatedAt(activatedAt);
        contract.setEmailSentAt(activatedAt);
        contract.setOpenedAt(openedAt);
        contract = contractRepository.save(contract);
        contract.setPdfUrl(contextPath + "/projects/" + contract.getId() + "/contract");

        sign(contract, investor, ContractFlowService.ROLE_INVESTOR, activatedAt);

        // One update per stage after "Unlocked", spread from activation until today (or the opening day)
        LocalDateTime end = opened ? openedAt : now.minusHours(6);
        for (int k = 1; k <= last; k++) {
            String stage = ProjectService.STAGES.get(k);
            ProjectUpdate update = projectUpdateRepository.save(contract.addUpdate(stage,
                    "تحديث تجريبي: خلّصنا مرحلة " + STAGE_AR.getOrDefault(stage, stage) + "."));
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

    // "1.5" / "3" for a distance in km (no trailing ".0")
    private static String km(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    // "ما فيه منشآت مشابهة" / "فيه منشأة مشابهة وحدة بس" / "فيه 3 منشآت مشابهة بس"
    private static String similarCount(int n) {
        if (n == 0) {
            return "ما فيه أي منشأة مشابهة";
        }
        if (n == 1) {
            return "فيه منشأة مشابهة وحدة بس";
        }
        if (n == 2) {
            return "فيه منشأتين مشابهة بس";
        }
        return "فيه " + n + (n <= 10 ? " منشآت" : " منشأة") + " مشابهة بس";
    }

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
