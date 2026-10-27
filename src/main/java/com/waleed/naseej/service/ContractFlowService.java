package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.SignContractRequest;
import com.waleed.naseej.dto.out.ExpiredContractsResponse;
import com.waleed.naseej.dto.out.MessageResponse;
import com.waleed.naseej.dto.out.ProjectResponse;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.ContractSignature;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.OpportunityAccess;
import com.waleed.naseej.entity.PartnershipAgreement;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.AccessType;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.IdeaStatus;
import com.waleed.naseej.enums.OtpPurpose;
import com.waleed.naseej.enums.PartnershipAgreementStatus;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ForbiddenException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.ContractSignatureRepository;
import com.waleed.naseej.repository.IdeaRepository;
import com.waleed.naseej.repository.PartnershipAgreementRepository;
import com.waleed.naseej.repository.UserRepository;
import com.waleed.naseej.repository.VoteRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

/**
 * The partnership contract after an investor buys (adopts) an idea, in three steps:
 * 1. The purchase creates a DRAFT contract (UnlockService).
 * 2. The investor signs (typed name + drawn signature + emailed code).
 * 3. The contract is ACTIVE right away, once the purchase is settled, and the 180-day opening deadline starts.
 * The idea owner doesn't sign and gets no discount; they're told when the contract becomes active.
 * Once, near the deadline, an investor who keeps posting progress can extend it by 60 days.
 * Contracts not signed within 14 days of the purchase, or not opened in time, are terminated and the idea goes
 * back to the other investors.
 */
@Service
@RequiredArgsConstructor
public class ContractFlowService {

    public static final String ROLE_INVESTOR = "INVESTOR";
    public static final String ROLE_IDEA_OWNER = "IDEA_OWNER";

    // The project opens within 180 days of activation; the investor can extend that once, by 60 days
    public static final int OPENING_DEADLINE_DAYS = 180;
    public static final int EXTENSION_DAYS = 60;
    // The extension can be asked for in the last 30 days before the deadline, by an investor who posted
    // a progress update in the last 30 days (ProjectService.extensionBlock)
    public static final int EXTENSION_WINDOW_DAYS = 30;
    public static final int RECENT_UPDATE_DAYS = 30;
    // Until the investor signs, an adopted idea is held for 14 days only
    public static final int SIGNING_DEADLINE_DAYS = 14;
    private static final List<String> IMAGE_PREFIXES = List.of("data:image/png;base64,", "data:image/jpeg;base64,");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ContractRepository contractRepository;
    private final ContractSignatureRepository contractSignatureRepository;
    private final PartnershipAgreementRepository partnershipAgreementRepository;
    private final UserRepository userRepository;
    private final IdeaRepository ideaRepository;
    private final VoteRepository voteRepository;
    private final OtpService otpService;
    private final N8nService n8nService;
    private final ProjectService projectService;
    private final NotificationService notificationService;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    // ---------- 1. Signing (the investor only) ----------

    /** Emails the 6-digit code the investor types on the signing page. */
    public MessageResponse sendSignOtp(User currentUser, Integer contractId) {
        User signer = getUser(currentUser.getId());
        Contract contract = getContract(contractId);
        signingRoleOf(contract, signer);
        otpService.sendOtp(signer, OtpPurpose.CONTRACT_SIGN);
        return new MessageResponse("We sent a 6-digit code to " + signer.getEmail() + ". Use it to sign the contract");
    }

    /**
     * Saves the signature with the evidence the document asks for: typed name, drawn signature, time, IP and
     * the result of the emailed code. The contract becomes ACTIVE right away (once the purchase is settled), and the
     * idea owner is told: they don't sign.
     */
    public ProjectResponse sign(User currentUser, Integer contractId, SignContractRequest request, String ipAddress) {
        User signer = getUser(currentUser.getId());
        Contract contract = getContract(contractId);
        String role = signingRoleOf(contract, signer);

        String typedName = request.getTypedName().trim();
        if (!normalizeName(typedName).equals(normalizeName(signer.getFullName()))) {
            throw new BadRequestException("Type your full name exactly as it appears on your account: "
                    + signer.getFullName());
        }
        String image = validSignatureImage(request.getSignatureImage());

        // Checked last, so a wrong name or image doesn't use up one of the code's attempts.
        // Not inside a transaction: a wrong code must still count as an attempt.
        otpService.verifyOtp(signer, OtpPurpose.CONTRACT_SIGN, request.getCode().trim());

        ContractSignature signature = ContractSignature.builder()
                .signer(signer)
                .signerRole(role)
                .typedName(typedName)
                .signatureImageData(image)
                .ipAddress(ipAddress)
                .otpVerified(true)
                .termsAccepted(true)
                .signedAt(LocalDateTime.now())
                .build();
        contract.sign(signature);
        contractSignatureRepository.save(signature);

        if (isReadyToActivate(contract)) {
            activate(contract);
        } else {
            contractRepository.save(contract);
        }
        return projectService.toResponseFor(signer, getContract(contractId));
    }

    // ---------- 2. Contract document ----------

    /**
     * The contract as a print-ready HTML page (Arabic, right-to-left) for the two parties.
     * The browser's "Save as PDF" gives the PDF; n8n can also convert it before emailing it.
     */
    public String contractDocument(User currentUser, Integer contractId) {
        Contract contract = getContract(contractId);
        boolean investor = contract.getInvestor().getId().equals(currentUser.getId());
        boolean owner = contract.getIdeaOwner().getId().equals(currentUser.getId());
        if (!investor && !owner) {
            throw new ForbiddenException("Only the contract's parties can view it");
        }
        return renderContract(contract);
    }

    // ---------- 3. Deadlines: 14 days to sign, 180 days to open ----------

    /**
     * The idea owner no longer signs: contracts the investor signed before that change, still waiting for the
     * owner's signature, become ACTIVE (when the purchase is settled). Runs on startup and before the nightly expiry.
     * Returns how many were activated.
     */
    @Transactional
    public int activateSignedContracts() {
        int count = 0;
        for (Contract contract : contractRepository.findByStatus(ContractStatus.INVESTOR_SIGNED)) {
            if (isReadyToActivate(contract)) {
                activate(contract);
                count++;
            }
        }
        return count;
    }

    /**
     * Terminates every contract the investor didn't sign within SIGNING_DEADLINE_DAYS, or whose opening
     * deadline passed without the project opening, and gives the idea back: COMPLETED (open to other investors)
     * when it has its votes, otherwise PUBLISHED.
     * Runs every night (ContractExpiryJob) and can be triggered by the admin or n8n.
     */
    @Transactional
    public ExpiredContractsResponse expireOverdue() {
        // Contracts the investor already signed under the old flow (waiting for the idea owner) are active now
        activateSignedContracts();
        LocalDate today = LocalDate.now();
        Map<Integer, Contract> overdue = new LinkedHashMap<>();
        // Not opened in time (contracts made before the signing deadline existed also carry one while unsigned)
        contractRepository.findByStatusInAndOpenedAtIsNullAndOpeningDeadlineBefore(
                        List.of(ContractStatus.DRAFT, ContractStatus.INVESTOR_SIGNED, ContractStatus.ACTIVE), today)
                .forEach(contract -> overdue.put(contract.getId(), contract));
        // Not signed by the investor in time
        contractRepository.findByStatusInAndSigningDeadlineBefore(
                        List.of(ContractStatus.DRAFT, ContractStatus.INVESTOR_SIGNED), today)
                .forEach(contract -> overdue.putIfAbsent(contract.getId(), contract));

        List<String> numbers = new ArrayList<>();
        List<Integer> reopened = new ArrayList<>();
        for (Contract contract : overdue.values()) {
            String reason = contract.getStatus() == ContractStatus.ACTIVE ? "NOT_OPENED" : "NOT_SIGNED";
            Idea idea = endContract(contract);
            numbers.add(contract.getContractNumber());
            reopened.add(idea.getId());

            Map<String, Object> data = contractEventData(contract);
            data.put("reason", reason);
            data.put("ideaStatus", idea.getStatus().name());
            n8nService.sendEvent("CONTRACT_EXPIRED", data);
        }
        return ExpiredContractsResponse.builder()
                .expiredCount(numbers.size())
                .contractNumbers(numbers)
                .reopenedIdeaIds(reopened)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Terminates the contract and frees the idea for other investors. Used by the deadlines and by refunds.
     * The access and the contract stay in the database as a record; only live contracts block an idea.
     */
    public Idea endContract(Contract contract) {
        contract.terminate();
        contractRepository.save(contract);

        Idea idea = contract.getIdea();
        if (idea.getStatus() == IdeaStatus.ADOPTED) {
            long votes = voteRepository.countByIdeaId(idea.getId());
            idea.setStatus(votes >= DiscoverService.threshold(idea) ? IdeaStatus.COMPLETED : IdeaStatus.PUBLISHED);
            ideaRepository.save(idea);
        }
        return idea;
    }

    // ---------- 4. Extension ----------

    /**
     * The investor moves the opening deadline EXTENSION_DAYS later, once: in the last EXTENSION_WINDOW_DAYS before
     * it, with a progress update in the last RECENT_UPDATE_DAYS (ProjectService.extensionBlock). The idea owner is told.
     */
    public ProjectResponse extend(User currentUser, Integer contractId) {
        User investor = getUser(currentUser.getId());
        Contract contract = getContract(contractId);
        if (!contract.getInvestor().getId().equals(investor.getId())) {
            throw new ForbiddenException("Only the project's investor can extend the opening deadline");
        }
        String blocked = projectService.extensionBlock(contract);
        if (blocked != null) {
            throw new BadRequestException(blocked);
        }
        contract.setOpeningDeadline(contract.getOpeningDeadline().plusDays(EXTENSION_DAYS));
        contract.setExtendedAt(LocalDateTime.now());
        contractRepository.save(contract);
        if (!isSelfOwned(contract)) {
            notificationService.notify(contract.getIdeaOwner(), NotificationService.CONTRACT_EXTENDED,
                    contract.getIdea(), contract.getOpeningDeadline().toString(), "projects.html");
        }
        return projectService.toResponseFor(investor, contract);
    }

    // ---------- Rules ----------

    /** Only the investor signs; returns their role, or why the user can't sign. */
    private String signingRoleOf(Contract contract, User user) {
        boolean isInvestor = contract.getInvestor().getId().equals(user.getId());
        boolean isOwner = contract.getIdeaOwner().getId().equals(user.getId());
        if (!isInvestor && !isOwner) {
            throw new ForbiddenException("You aren't a party to this contract");
        }
        if (!isInvestor) {
            throw new BadRequestException("Only the investor signs this contract. You don't need to sign anything");
        }
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BadRequestException("This contract has ended");
        }
        if (contract.getStatus() == ContractStatus.ACTIVE || hasSigned(contract, ROLE_INVESTOR)) {
            throw new BadRequestException("You have already signed this contract");
        }
        return ROLE_INVESTOR;
    }

    // Active once the investor signed and the purchase is settled; the idea owner doesn't sign
    private boolean isReadyToActivate(Contract contract) {
        return hasSigned(contract, ROLE_INVESTOR) && isAccessSettled(contract.getAccess());
    }

    // The access condition from the document: the fee is paid, or the success partnership is signed
    private boolean isAccessSettled(OpportunityAccess access) {
        if (access.getAccessType() == AccessType.PAID) {
            Payment payment = access.getPayment();
            return payment != null && payment.getStatus() == PaymentStatus.PAID;
        }
        if (access.getAccessType() == AccessType.PARTNERSHIP) {
            return partnershipAgreementRepository.findByAccessId(access.getId())
                    .map(agreement -> PartnershipAgreementStatus.SIGNED.name().equals(agreement.getStatus()))
                    .orElse(false);
        }
        return true; // OWNER: an investor adopting their own idea pays nothing
    }

    private void activate(Contract contract) {
        contract.activate();
        contract.setOpeningDeadline(LocalDate.now().plusDays(OPENING_DEADLINE_DAYS));
        contract.setPdfUrl(contextPath + "/projects/" + contract.getId() + "/contract");
        contractRepository.save(contract);
        n8nService.sendEvent("CONTRACT_ACTIVE", contractEventData(contract));
        // The idea owner doesn't sign: they hear that an investor bought their idea and the contract is active
        if (!isSelfOwned(contract)) {
            notificationService.notify(contract.getIdeaOwner(), NotificationService.CONTRACT_SIGNED,
                    contract.getIdea(), "OWNER_ACTIVE", "contract.html?id=" + contract.getId());
        }
    }

    private boolean hasSigned(Contract contract, String role) {
        return contractSignatureRepository.existsByContractIdAndSignerRoleAndOtpVerifiedTrueAndTermsAcceptedTrue(
                contract.getId(), role);
    }

    private static boolean isSelfOwned(Contract contract) {
        return Objects.equals(contract.getInvestor().getId(), contract.getIdeaOwner().getId());
    }

    private static String validSignatureImage(String image) {
        String value = image.trim();
        String prefix = IMAGE_PREFIXES.stream().filter(value::startsWith).findFirst()
                .orElseThrow(() -> new BadRequestException("The signature must be a PNG or JPEG image (data URI)"));
        try {
            if (Base64.getDecoder().decode(value.substring(prefix.length())).length == 0) {
                throw new BadRequestException("The signature image is empty");
            }
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("The signature image isn't valid");
        }
        return value;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> contractEventData(Contract contract) {
        Idea idea = contract.getIdea();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contractId", contract.getId());
        data.put("contractNumber", contract.getContractNumber());
        data.put("status", contract.getStatus().name());
        data.put("ideaId", idea.getId());
        data.put("ideaTitle", idea.getTitle());
        data.put("investor", party(contract.getInvestor()));
        data.put("ideaOwner", party(contract.getIdeaOwner()));
        data.put("openingDeadline", contract.getOpeningDeadline() != null ? contract.getOpeningDeadline().toString() : null);
        data.put("signingDeadline", contract.getSigningDeadline() != null ? contract.getSigningDeadline().toString() : null);
        data.put("extended", contract.getExtendedAt() != null);
        data.put("documentUrl", contract.getPdfUrl());
        return data;
    }

    private static Map<String, Object> party(User user) {
        Map<String, Object> party = new LinkedHashMap<>();
        party.put("id", user.getId());
        party.put("fullName", user.getFullName());
        party.put("email", user.getEmail());
        party.put("phone", user.getPhone());
        party.put("whatsappOptIn", user.getWhatsappOptIn());
        return party;
    }

    private User getUser(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private Contract getContract(Integer id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found with id: " + id));
    }

    // ---------- Contract page ----------

    private String renderContract(Contract contract) {
        Idea idea = contract.getIdea();
        User investor = contract.getInvestor();
        User owner = contract.getIdeaOwner();
        OpportunityAccess access = contract.getAccess();
        List<ContractSignature> signatures = contractSignatureRepository.findByContractIdOrderBySignedAtAsc(contract.getId());

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
                .append("<title>").append(esc("عقد شراكة " + contract.getContractNumber())).append("</title>")
                .append("<style>")
                .append("body{font-family:Tahoma,'Segoe UI','Noto Naskh Arabic',sans-serif;color:#1f2a24;")
                .append("background:#fff;margin:0;padding:32px;line-height:1.8}")
                .append(".page{max-width:820px;margin:0 auto}")
                .append("h1{font-size:24px;margin:0 0 4px;color:#1d5a43}h2{font-size:17px;margin:28px 0 8px;")
                .append("color:#1d5a43;border-bottom:1px solid #d9e4dd;padding-bottom:4px}")
                .append(".muted{color:#5f6f66;font-size:13px}")
                .append("table{width:100%;border-collapse:collapse;margin:6px 0}")
                .append("td,th{border:1px solid #d9e4dd;padding:8px 10px;vertical-align:top;text-align:right}")
                .append("th{background:#f2f7f4;width:32%;font-weight:600}")
                .append(".sig{display:inline-block;width:48%;vertical-align:top;margin-top:8px}")
                .append(".sig img{max-width:220px;max-height:90px;display:block;margin:6px 0;border-bottom:1px solid #999}")
                .append(".note{margin-top:28px;padding:10px 12px;background:#fff8e6;border:1px solid #f0dca8;font-size:13px}")
                .append("@media print{body{padding:0}.note{break-inside:avoid}}")
                .append("</style></head><body><div class=\"page\">");

        html.append("<h1>عقد شراكة — نسيج</h1>")
                .append("<div class=\"muted\">رقم العقد: ").append(esc(contract.getContractNumber()))
                .append(" · الحالة: ").append(esc(statusLabel(contract.getStatus())))
                .append(" · تاريخ الإصدار: ").append(esc(LocalDate.now().toString())).append("</div>");

        html.append("<h2>الأطراف</h2><table>")
                .append(row("المستثمر", investor.getFullName() + " — " + investor.getEmail()
                        + (investor.getCrNumber() != null ? " — سجل تجاري " + investor.getCrNumber() : "")))
                .append(row("صاحب الفكرة", owner.getFullName() + " — " + owner.getEmail()))
                .append(row("المنصة", "نسيج، بصفتها منسّقًا وشاهدًا على الاتفاق"))
                .append("</table>");

        html.append("<h2>الفرصة</h2><table>")
                .append(row("الفكرة", idea.getTitle()))
                .append(row("النشاط", Objects.toString(idea.getCategory(), "-")))
                .append(row("الحي", idea.getNeighborhood().getName() + "، " + idea.getNeighborhood().getCity()))
                .append(row("طريقة الاطلاع", accessLabel(access)))
                .append("</table>");

        html.append("<h2>البنود</h2><table>")
                .append(row("التزامات المستثمر", "الافتتاح خلال " + OPENING_DEADLINE_DAYS
                        + " يومًا من تفعيل العقد (قابلة للتمديد مرة واحدة " + EXTENSION_DAYS
                        + " يومًا إذا كان المستثمر يحدّث حالة المشروع بانتظام)، "
                        + (isSelfOwned(contract) ? "" : "ونسب الفكرة لصاحبها، ")
                        + "وتحديث حالة المشروع في المنصة كل أسبوعين"))
                .append(row("حقوق صاحب الفكرة", "لقب «صاحب الفكرة» في المشروع"))
                .append(row("أحكام عامة", "يسري العقد بتوقيع المستثمر بعد إتمام الشراء، ولا يلزم توقيع صاحب الفكرة. "
                        + "ويُفسخ العقد تلقائيًا إذا لم يوقّعه المستثمر خلال " + SIGNING_DEADLINE_DAYS
                        + " يومًا من الشراء، أو إذا لم يُفتتح المشروع في المدة، "
                        + "ورسوم المنصة حسب طريقة الاطلاع، ولا تُشارك بيانات الأعضاء إلا مجمّعة"));
        boolean activated = contract.getActivatedAt() != null && contract.getOpeningDeadline() != null;
        if (!activated && contract.getSigningDeadline() != null) {
            html.append(row("آخر موعد للتوقيع", contract.getSigningDeadline().toString()));
        }
        html.append(row("آخر موعد للافتتاح", activated
                        ? contract.getOpeningDeadline() + (contract.getExtendedAt() != null ? " (بعد التمديد)" : "")
                        : OPENING_DEADLINE_DAYS + " يومًا من تفعيل العقد"))
                .append("</table>");

        html.append("<h2>التوقيعات</h2>");
        if (signatures.isEmpty()) {
            html.append("<p class=\"muted\">لم يوقّع المستثمر بعد.</p>");
        }
        for (ContractSignature signature : signatures) {
            html.append("<div class=\"sig\"><strong>")
                    .append(esc(ROLE_INVESTOR.equals(signature.getSignerRole()) ? "توقيع المستثمر" : "توقيع صاحب الفكرة (عقد سابق)"))
                    .append("</strong>");
            if (signature.getSignatureImageData() != null) {
                html.append("<img alt=\"signature\" src=\"").append(esc(signature.getSignatureImageData())).append("\">");
            }
            html.append("<div>").append(esc(signature.getTypedName())).append("</div>")
                    .append("<div class=\"muted\">")
                    .append(esc(signature.getSignedAt() != null ? signature.getSignedAt().format(DATE_TIME) : "-"))
                    .append(" · IP ").append(esc(Objects.toString(signature.getIpAddress(), "-")))
                    .append(" · ").append(signature.isOtpVerified() ? "تم التحقق برمز البريد" : "بدون رمز")
                    .append("</div></div>");
        }

        html.append("<div class=\"note\">صيغة العقد توضيحية وتحتاج مراجعة محامٍ قبل الإطلاق.</div>")
                .append("</div></body></html>");
        return html.toString();
    }

    private String accessLabel(OpportunityAccess access) {
        if (access.getAccessType() == AccessType.PAID && access.getPayment() != null) {
            Payment payment = access.getPayment();
            return "رسوم اطلاع مدفوعة: " + payment.getAmount().toPlainString() + " ر.س + ضريبة "
                    + payment.getVat().toPlainString() + " = " + payment.getTotalAmount().toPlainString() + " ر.س";
        }
        if (access.getAccessType() == AccessType.PARTNERSHIP) {
            return partnershipAgreementRepository.findByAccessId(access.getId())
                    .map((PartnershipAgreement agreement) -> "شراكة نجاح: "
                            + agreement.getProfitSharePercent().stripTrailingZeros().toPlainString()
                            + "% من صافي الأرباح لمدة " + agreement.getDurationYears() + " سنوات بعد الافتتاح")
                    .orElse("شراكة نجاح");
        }
        return "فكرة المستثمر نفسه، بدون رسوم اطلاع";
    }

    private static String statusLabel(ContractStatus status) {
        return switch (status) {
            case DRAFT -> "مسودة";
            case INVESTOR_SIGNED -> "وقّع المستثمر، بانتظار تأكيد الشراء";
            case ACTIVE -> "ساري";
            case TERMINATED -> "مفسوخ";
        };
    }

    private static String row(String label, String value) {
        return "<tr><th>" + esc(label) + "</th><td>" + esc(value) + "</td></tr>";
    }

    private static String esc(String value) {
        // UTF-8: only the HTML special characters are escaped, Arabic text stays as it is
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
