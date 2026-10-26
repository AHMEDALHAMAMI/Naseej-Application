package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.InitiateMoyasarPaymentRequest;
import com.waleed.naseej.dto.in.MoyasarCreatePaymentApiRequest;
import com.waleed.naseej.dto.in.MoyasarPaymentSourceApiRequest;
import com.waleed.naseej.dto.in.MoyasarWebhookRequest;
import com.waleed.naseej.dto.in.PaymentRequest;
import com.waleed.naseej.dto.out.CheckoutResponse;
import com.waleed.naseej.dto.out.ContractResponse;
import com.waleed.naseej.dto.out.InitiateMoyasarPaymentResponse;
import com.waleed.naseej.dto.out.MoyasarCreatePaymentApiResponse;
import com.waleed.naseej.dto.out.MoyasarPaymentStatusResponse;
import com.waleed.naseej.dto.out.MoyasarWebhookResponse;
import com.waleed.naseej.dto.out.PaymentResponse;
import com.waleed.naseej.enums.PaymentMethod;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.Idea;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.entity.Subscription;
import com.waleed.naseej.entity.User;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.PaymentPurpose;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ExternalServiceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PaymentRepository;
import com.waleed.naseej.repository.SubscriptionRepository;
import com.waleed.naseej.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final BigDecimal ACCESS_FEE_AMOUNT = new BigDecimal("1500.00");
    private static final BigDecimal VAT_RATE = new BigDecimal("0.15");
    private static final String MOYASAR_PAYMENTS_URL = "https://api.moyasar.com/v1/payments/";
    private static final String MOYASAR_CREATE_PAYMENT_URL = "https://api.moyasar.com/v1/payments";

    // A payment opened for the payment form has this id until Moyasar's own id is linked
    static final String PENDING_PREFIX = "pending-";
    static final String METADATA_PAYMENT_ID = "naseej_payment_id";

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final ContractRepository contractRepository;
    private final ContractService contractService;
    private final SubscriptionRepository subscriptionRepository;

    @Value("${moyasar.webhook.secret:}")
    private String moyasarWebhookSecret;

    @Value("${moyasar.api.key:}")
    private String moyasarApiKey;

    @Value("${moyasar.api.publishable-key:}")
    private String moyasarPublishableKey;

    @Value("${moyasar.callback.url:}")
    private String moyasarCallbackUrl;

    @PostConstruct
    void initMoyasarConfig() {
        moyasarWebhookSecret = normalizeConfigValue(moyasarWebhookSecret);
        moyasarApiKey = normalizeConfigValue(moyasarApiKey);
        moyasarPublishableKey = normalizeConfigValue(moyasarPublishableKey);
        moyasarCallbackUrl = normalizeConfigValue(moyasarCallbackUrl);
    }

    public List<PaymentResponse> getAll() {
        return paymentRepository.findAll().stream().map(this::toResponse).toList();
    }

    public PaymentResponse create(PaymentRequest request) {
        if (StringUtils.hasText(request.getMoyasarPaymentId())
                && paymentRepository.existsByMoyasarPaymentId(request.getMoyasarPaymentId())) {
            throw new DuplicateResourceException(
                    "Payment already exists with moyasar payment id: " + request.getMoyasarPaymentId());
        }
        Payment entity = Payment.builder()
                .user(userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found with id: " + request.getUserId())))
                .purpose(request.getPurpose())
                .amount(request.getAmount())
                .vat(request.getVat())
                .totalAmount(request.getTotalAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "SAR")
                .method(request.getMethod())
                .moyasarPaymentId(request.getMoyasarPaymentId())
                .status(request.getStatus() != null ? request.getStatus() : PaymentStatus.INITIATED)
                .invoiceUrl(request.getInvoiceUrl())
                .paidAt(request.getPaidAt())
                .build();
        return toResponse(paymentRepository.save(entity));
    }

    /**
     * Moyasar's webhook. The secret comes in the body (secret_token) or the X-Webhook-Secret header. The event itself
     * is never trusted: the payment is fetched from Moyasar and must be paid, for the right amount, before it counts.
     * A payment made in the payment form may not be linked yet (the investor closed the page): it is found by the
     * naseej_payment_id Naseej put in its metadata.
     */
    @Transactional
    public MoyasarWebhookResponse handleMoyasarWebhook(String webhookSecretHeader, MoyasarWebhookRequest webhook) {
        validateWebhookSecret(StringUtils.hasText(webhookSecretHeader) ? webhookSecretHeader : webhook.getSecretToken());
        String moyasarId = webhook.paymentId();
        if (!StringUtils.hasText(moyasarId)) {
            throw new BadRequestException("Webhook has no payment id");
        }
        MoyasarPaymentStatusResponse remote = getPaymentStatus(moyasarId);
        if (!"paid".equalsIgnoreCase(remote.getStatus())) {
            return MoyasarWebhookResponse.builder().message("Ignored: the payment is " + remote.getStatus()).build();
        }
        Payment payment = paymentRepository.findByMoyasarPaymentId(moyasarId)
                .orElseGet(() -> paymentRepository.findById(naseejPaymentId(remote))
                        .orElseThrow(() -> new ResourceNotFoundException("Payment not found for moyasar id: " + moyasarId)));
        if (!moyasarId.equals(payment.getMoyasarPaymentId())) {
            attachMoyasarPayment(payment, moyasarId);
        }
        return finalizePaidMoyasarPayment(payment);
    }

    // ---------- Moyasar payment form (the card details never reach Naseej) ----------

    /**
     * Opens a checkout for Moyasar's payment form: a Naseej payment row (INITIATED) remembers the investor,
     * the idea and the amount, and the form carries its id in the metadata. Nothing is charged here.
     */
    @Transactional
    public CheckoutResponse startFormCheckout(User user, Idea idea, BigDecimal amount, String description) {
        return startFormCheckout(user, idea, amount, description, PaymentPurpose.OPPORTUNITY_ACCESS);
    }

    /** The same checkout for another purpose (the owner launch fee). amount is before VAT. */
    @Transactional
    public CheckoutResponse startFormCheckout(User user, Idea idea, BigDecimal amount, String description, String purpose) {
        if (!StringUtils.hasText(moyasarPublishableKey) || !StringUtils.hasText(moyasarApiKey)) {
            throw new BadRequestException("Online payment isn't set up yet. Try again later");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Unlock amount must be greater than zero");
        }
        BigDecimal price = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal vat = vatOf(price);
        BigDecimal total = price.add(vat);
        Payment payment = paymentRepository.save(Payment.builder()
                .user(user)
                .purpose(purpose)
                .idea(idea)
                .amount(price)
                .vat(vat)
                .totalAmount(total)
                .currency("SAR")
                .method(PaymentMethod.CREDIT_CARD)
                // Replaced by Moyasar's id once the form has made the payment
                .moyasarPaymentId(PENDING_PREFIX + UUID.randomUUID())
                .status(PaymentStatus.INITIATED)
                .build());
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(METADATA_PAYMENT_ID, String.valueOf(payment.getId()));
        metadata.put("idea_id", String.valueOf(idea.getId()));
        return CheckoutResponse.builder()
                .paymentId(payment.getId())
                .ideaId(idea.getId())
                .amount(price)
                .vat(vat)
                .total(total)
                .amountHalalas(toHalalas(total))
                .currency("SAR")
                .description(description)
                .publishableKey(moyasarPublishableKey)
                .testMode(isTestMoyasarEnvironment())
                .metadata(metadata)
                .build();
    }

    /**
     * Links a Naseej payment to the Moyasar payment the form made, after checking with Moyasar that it is this
     * payment (metadata), for the same amount and currency, and not already linked elsewhere.
     */
    @Transactional
    public void attachMoyasarPayment(Payment payment, String moyasarId) {
        String id = moyasarId == null ? "" : moyasarId.trim();
        if (id.isEmpty() || id.startsWith(PENDING_PREFIX)) {
            throw new BadRequestException("Moyasar payment id is required");
        }
        if (id.equals(payment.getMoyasarPaymentId())) {
            return;
        }
        if (!payment.getMoyasarPaymentId().startsWith(PENDING_PREFIX)) {
            throw new BadRequestException("This payment is already linked to another Moyasar payment");
        }
        if (paymentRepository.existsByMoyasarPaymentId(id)) {
            throw new DuplicateResourceException("This Moyasar payment was already used");
        }
        MoyasarPaymentStatusResponse remote = getPaymentStatus(id);
        Integer ours = payment.getId();
        if (!ours.equals(naseejPaymentId(remote))) {
            throw new BadRequestException("This Moyasar payment belongs to another checkout");
        }
        checkAmount(payment, remote);
        payment.setMoyasarPaymentId(id);
        paymentRepository.save(payment);
    }

    private static Integer naseejPaymentId(MoyasarPaymentStatusResponse remote) {
        Object value = remote.getMetadata() == null ? null : remote.getMetadata().get(METADATA_PAYMENT_ID);
        try {
            return value == null ? -1 : Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void checkAmount(Payment payment, MoyasarPaymentStatusResponse remote) {
        if (remote.getAmount() == null || remote.getAmount() != toHalalas(payment.getTotalAmount())
                || !"SAR".equalsIgnoreCase(remote.getCurrency())) {
            throw new BadRequestException("The paid amount doesn't match this checkout");
        }
    }

    @Transactional
    public InitiateMoyasarPaymentResponse initiateMoyasarPayment(InitiateMoyasarPaymentRequest request) {
        if (!PaymentPurpose.OPPORTUNITY_ACCESS.equals(request.getPurpose())) {
            throw new BadRequestException(
                    "Only OPPORTUNITY_ACCESS payments can be initiated via Moyasar for now");
        }
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + request.getUserId()));
        return createMoyasarPayment(user, PaymentPurpose.OPPORTUNITY_ACCESS, null, ACCESS_FEE_AMOUNT,
                "Naseej opportunity access fee", request.getCallbackUrl(), request.getUseSandboxTestCard());
    }

    /**
     * Unlock payment for one idea, priced by the AI quote (amount before VAT).
     * Used by the investor checkout; the access is granted after the payment is confirmed as PAID.
     */
    @Transactional
    public InitiateMoyasarPaymentResponse initiateUnlockPayment(User investor, Idea idea, BigDecimal amount,
                                                                String description, String callbackUrl,
                                                                Boolean useSandboxTestCard) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Unlock amount must be greater than zero");
        }
        return createMoyasarPayment(investor, PaymentPurpose.OPPORTUNITY_ACCESS, idea,
                amount.setScale(2, RoundingMode.HALF_UP), description, callbackUrl, useSandboxTestCard);
    }

    /**
     * Monthly subscription payment (plan price before VAT). The subscription becomes ACTIVE when this payment is
     * confirmed: right away if Moyasar answers "paid", otherwise from the webhook or a sync.
     */
    @Transactional
    public InitiateMoyasarPaymentResponse initiateSubscriptionPayment(User user, BigDecimal amount, String description,
                                                                      String callbackUrl, Boolean useSandboxTestCard) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Subscription amount must be greater than zero");
        }
        return createMoyasarPayment(user, PaymentPurpose.SUBSCRIPTION, null,
                amount.setScale(2, RoundingMode.HALF_UP), description, callbackUrl, useSandboxTestCard);
    }

    /** The logged-in user's payments and invoices, newest first. */
    public List<PaymentResponse> myPayments(User currentUser) {
        return paymentRepository.findByUserIdOrderByIdDesc(currentUser.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Refunds the whole payment through Moyasar's Refund API (secret key), then marks it REFUNDED.
     * What the payment unlocked is undone by RefundService.
     */
    @Transactional
    public PaymentResponse refundOnMoyasar(Integer id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BadRequestException("Only paid payments can be refunded (this one is " + payment.getStatus() + ")");
        }
        if (!StringUtils.hasText(moyasarApiKey)) {
            throw new BadRequestException("Set MOYASAR_API_KEY (the secret key) to refund through Moyasar");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(moyasarApiKey, "");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                Map.of("amount", toHalalas(payment.getTotalAmount())), headers);

        MoyasarPaymentStatusResponse remote;
        try {
            remote = restTemplate.exchange(
                    MOYASAR_PAYMENTS_URL + payment.getMoyasarPaymentId() + "/refund",
                    HttpMethod.POST,
                    request,
                    MoyasarPaymentStatusResponse.class).getBody();
        } catch (HttpClientErrorException ex) {
            throw new BadRequestException("Moyasar refused the refund: " + ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Moyasar didn't answer the refund request: " + ex.getMessage());
        }
        if (remote == null || !"refunded".equalsIgnoreCase(remote.getStatus())) {
            throw new ExternalServiceException("Moyasar didn't confirm the refund (status: "
                    + (remote != null ? remote.getStatus() : "empty") + ")");
        }

        try {
            payment.refund();
        } catch (IllegalStateException ex) {
            throw new BadRequestException(ex.getMessage());
        }
        return toResponse(paymentRepository.save(payment));
    }

    // idea is null for payments that aren't for one idea (the old access fee, subscriptions)
    private InitiateMoyasarPaymentResponse createMoyasarPayment(User user, String purpose, Idea idea, BigDecimal amount,
                                                                String description, String requestCallbackUrl,
                                                                Boolean useSandboxTestCard) {
        String createApiKey = resolveCreatePaymentApiKey();
        String callbackUrl = StringUtils.hasText(requestCallbackUrl)
                ? requestCallbackUrl.trim()
                : moyasarCallbackUrl;
        if (!StringUtils.hasText(callbackUrl)) {
            throw new BadRequestException(
                    "callbackUrl is required (set MOYASAR_CALLBACK_URL or pass callbackUrl in the request)");
        }

        boolean useTestCard = useSandboxTestCard == null || Boolean.TRUE.equals(useSandboxTestCard);
        if (useTestCard && !isTestMoyasarEnvironment()) {
            throw new BadRequestException(
                    "Sandbox test card is only allowed when MOYASAR_API_KEY or MOYASAR_PUBLISHABLE_KEY is a test key");
        }

        BigDecimal vat = vatOf(amount);
        BigDecimal total = amount.add(vat);

        String givenId = UUID.randomUUID().toString();
        if (paymentRepository.existsByMoyasarPaymentId(givenId)) {
            throw new DuplicateResourceException("Payment already exists with moyasar payment id: " + givenId);
        }

        MoyasarCreatePaymentApiRequest apiRequest = MoyasarCreatePaymentApiRequest.builder()
                .givenId(givenId)
                .amount(toHalalas(total))
                .currency("SAR")
                .description(description)
                .callbackUrl(callbackUrl)
                .source(buildMoyasarSource(useTestCard))
                .build();

        MoyasarCreatePaymentApiResponse moyasarResponse = callMoyasarCreatePayment(createApiKey, apiRequest);
        String moyasarPaymentId = StringUtils.hasText(moyasarResponse.getId()) ? moyasarResponse.getId() : givenId;
        String transactionUrl = moyasarResponse.getSource() != null
                ? moyasarResponse.getSource().getTransactionUrl()
                : null;

        Payment payment = Payment.builder()
                .user(user)
                .purpose(purpose)
                .idea(idea)
                .amount(amount)
                .vat(vat)
                .totalAmount(total)
                .currency("SAR")
                .method(PaymentMethod.CREDIT_CARD)
                .moyasarPaymentId(moyasarPaymentId)
                .status(PaymentStatus.INITIATED)
                .invoiceUrl(transactionUrl)
                .build();

        payment = paymentRepository.save(payment);

        String message;
        if ("paid".equalsIgnoreCase(moyasarResponse.getStatus())) {
            MoyasarWebhookResponse finalized = finalizePaidMoyasarPayment(payment);
            payment = paymentRepository.findById(finalized.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Payment not found with id: " + finalized.getPaymentId()));
            message = finalized.getMessage();
        } else if (StringUtils.hasText(transactionUrl)) {
            message = "Open transactionUrl in a browser to complete 3DS checkout, then call POST /payments/sync-moyasar/{id}";
        } else {
            message = "Payment initiated on Moyasar with status: " + moyasarResponse.getStatus();
        }

        return InitiateMoyasarPaymentResponse.builder()
                .payment(toResponse(payment))
                .moyasarStatus(moyasarResponse.getStatus())
                .transactionUrl(transactionUrl)
                .message(message)
                .build();
    }

    // Saudi VAT 15%, rounded to halalas
    private static BigDecimal vatOf(BigDecimal amount) {
        return amount.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public MoyasarWebhookResponse syncMoyasarPayment(Integer id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        return syncMoyasarPaymentForEntity(payment);
    }

    @Transactional
    public MoyasarWebhookResponse syncMoyasarPaymentByMoyasarId(String moyasarPaymentId) {
        Payment payment = paymentRepository.findByMoyasarPaymentId(moyasarPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for moyasar id: " + moyasarPaymentId));
        return syncMoyasarPaymentForEntity(payment);
    }

    public InitiateMoyasarPaymentResponse buildInitiateResponseFromWebhook(MoyasarWebhookResponse webhook) {
        PaymentResponse payment = getById(webhook.getPaymentId());
        return InitiateMoyasarPaymentResponse.builder()
                .payment(payment)
                .moyasarStatus(payment.getStatus() == PaymentStatus.PAID ? "paid" : payment.getStatus().name())
                .transactionUrl(payment.getInvoiceUrl())
                .message(webhook.getMessage())
                .build();
    }

    public PaymentResponse update(Integer id, PaymentRequest request) {
        Payment entity = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        entity.setUser(userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + request.getUserId())));
        entity.setPurpose(request.getPurpose());
        entity.setAmount(request.getAmount());
        entity.setVat(request.getVat());
        entity.setTotalAmount(request.getTotalAmount());
        entity.setCurrency(request.getCurrency());
        entity.setMethod(request.getMethod());
        entity.setMoyasarPaymentId(request.getMoyasarPaymentId());
        entity.setStatus(request.getStatus());
        entity.setInvoiceUrl(request.getInvoiceUrl());
        entity.setPaidAt(request.getPaidAt());
        return toResponse(paymentRepository.save(entity));
    }

    public void delete(Integer id) {
        paymentRepository.delete(paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id)));
    }

    public PaymentResponse getById(Integer id) {
        return toResponse(paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id)));
    }

    public MoyasarPaymentStatusResponse getPaymentStatus(String moyasarPaymentId) {
        if (!StringUtils.hasText(moyasarApiKey)) {
            throw new BadRequestException("Moyasar API key is not configured");
        }
        if (!StringUtils.hasText(moyasarPaymentId)) {
            throw new BadRequestException("Moyasar payment id is required");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(moyasarApiKey, "");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<MoyasarPaymentStatusResponse> response = restTemplate.exchange(
                    MOYASAR_PAYMENTS_URL + moyasarPaymentId,
                    HttpMethod.GET,
                    request,
                    MoyasarPaymentStatusResponse.class);
            MoyasarPaymentStatusResponse body = response.getBody();
            if (body == null) {
                throw new BadRequestException("Empty response from Moyasar for payment id: " + moyasarPaymentId);
            }
            return body;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Moyasar payment not found: " + moyasarPaymentId);
        } catch (RestClientException ex) {
            throw new BadRequestException("Failed to fetch Moyasar payment status: " + ex.getMessage());
        }
    }

    public PaymentResponse confirm(Integer id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        try {
            payment.confirm();
        } catch (IllegalStateException ex) {
            throw new BadRequestException(ex.getMessage());
        }
        return toResponse(paymentRepository.save(payment));
    }

    public PaymentResponse refund(Integer id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        try {
            payment.refund();
        } catch (IllegalStateException ex) {
            throw new BadRequestException(ex.getMessage());
        }
        return toResponse(paymentRepository.save(payment));
    }

    private void validateWebhookSecret(String webhookSecretHeader) {
        if (!StringUtils.hasText(moyasarWebhookSecret)) {
            return;
        }
        if (!StringUtils.hasText(webhookSecretHeader)) {
            throw new BadRequestException("Missing X-Webhook-Secret header");
        }
        if (!moyasarWebhookSecret.equals(webhookSecretHeader.trim())) {
            throw new BadRequestException("Invalid webhook secret");
        }
    }

    private static String normalizeConfigValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'")))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private MoyasarWebhookResponse syncMoyasarPaymentForEntity(Payment payment) {
        if (!StringUtils.hasText(payment.getMoyasarPaymentId())) {
            throw new BadRequestException("Payment has no Moyasar payment id");
        }
        if (payment.getMoyasarPaymentId().startsWith(PENDING_PREFIX)) {
            throw new BadRequestException("The payment wasn't completed");
        }
        MoyasarPaymentStatusResponse remote = getPaymentStatus(payment.getMoyasarPaymentId());
        if ("failed".equalsIgnoreCase(remote.getStatus())) {
            String reason = remote.getSource() != null && StringUtils.hasText(remote.getSource().getMessage())
                    ? remote.getSource().getMessage() : "The card was declined";
            throw new BadRequestException("The payment failed: " + reason);
        }
        if (!"paid".equalsIgnoreCase(remote.getStatus())) {
            throw new BadRequestException(
                    "Moyasar payment status is '" + remote.getStatus()
                            + "'. Complete checkout or wait, then sync again.");
        }
        // Paid, and for exactly what this payment was opened for
        checkAmount(payment, remote);
        return finalizePaidMoyasarPayment(payment);
    }

    private MoyasarWebhookResponse finalizePaidMoyasarPayment(Payment payment) {
        validatePaymentPurposeAndAmounts(payment);

        if (payment.getStatus() != PaymentStatus.PAID) {
            try {
                payment.confirm();
            } catch (IllegalStateException ex) {
                throw new BadRequestException(ex.getMessage());
            }
            paymentRepository.save(payment);
        }

        Integer contractId = null;
        ContractStatus contractStatus = null;
        String message = "Payment confirmed";

        if (PaymentPurpose.OPPORTUNITY_ACCESS.equals(payment.getPurpose())) {
            Contract contract = opportunityAccessRepository.findByPaymentId(payment.getId())
                    .flatMap(access -> contractRepository.findByAccessId(access.getId()))
                    .orElse(null);

            if (contract != null) {
                ContractResponse activated = contractService.activateAfterVerifiedPayment(contract.getId());
                contractId = activated.getId();
                contractStatus = activated.getStatus();
                message = "Payment confirmed; contract activation evaluated";
            } else {
                message = "Payment confirmed; no contract linked to this access payment yet";
            }
        } else if (PaymentPurpose.SUCCESS_PARTNERSHIP.equals(payment.getPurpose())) {
            message = "Success partnership payment confirmed";
        } else if (PaymentPurpose.SUBSCRIPTION.equals(payment.getPurpose())) {
            message = subscriptionRepository.findByPaymentId(payment.getId())
                    .map(this::activateSubscription)
                    .orElse("Payment confirmed; no subscription linked to this payment yet");
        } else if (PaymentPurpose.OWNER_LAUNCH.equals(payment.getPurpose())) {
            // The project starts when the owner's browser comes back (UnlockService.confirmOwnerLaunch)
            message = "Owner launch fee confirmed";
        }

        return MoyasarWebhookResponse.builder()
                .paymentId(payment.getId())
                .paymentStatus(payment.getStatus())
                .contractId(contractId)
                .contractStatus(contractStatus)
                .message(message)
                .build();
    }

    // A paid month starts today; a subscription that is already active (webhook and sync both arriving) is left alone
    private String activateSubscription(Subscription subscription) {
        if (SubscriptionService.STATUS_ACTIVE.equals(subscription.getStatus())) {
            return "Subscription already active";
        }
        if (!SubscriptionService.STATUS_PENDING.equals(subscription.getStatus())) {
            return "Payment confirmed, but the subscription is " + subscription.getStatus();
        }
        LocalDate today = LocalDate.now();
        subscription.setStatus(SubscriptionService.STATUS_ACTIVE);
        subscription.setStartsAt(today);
        subscription.setEndsAt(today.plusMonths(1));
        subscriptionRepository.save(subscription);
        return "Payment confirmed; subscription active until " + subscription.getEndsAt();
    }

    private MoyasarCreatePaymentApiResponse callMoyasarCreatePayment(
            String createApiKey, MoyasarCreatePaymentApiRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(createApiKey, "");
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<MoyasarCreatePaymentApiRequest> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<MoyasarCreatePaymentApiResponse> response = restTemplate.exchange(
                    MOYASAR_CREATE_PAYMENT_URL,
                    HttpMethod.POST,
                    entity,
                    MoyasarCreatePaymentApiResponse.class);
            MoyasarCreatePaymentApiResponse responseBody = response.getBody();
            if (responseBody == null || !StringUtils.hasText(responseBody.getId())) {
                throw new BadRequestException("Empty or invalid response from Moyasar create payment");
            }
            return responseBody;
        } catch (HttpClientErrorException ex) {
            throw new BadRequestException("Moyasar create payment failed: " + ex.getResponseBodyAsString());
        } catch (RestClientException ex) {
            throw new BadRequestException("Moyasar create payment failed: " + ex.getMessage());
        }
    }

    private MoyasarPaymentSourceApiRequest buildMoyasarSource(boolean useSandboxTestCard) {
        if (useSandboxTestCard) {
            return MoyasarPaymentSourceApiRequest.builder()
                    .type("creditcard")
                    .name("Naseej Test User")
                    .number("4111111111111111")
                    .month("12")
                    .year("2030")
                    .cvc("123")
                    .build();
        }
        return MoyasarPaymentSourceApiRequest.builder()
                .type("creditcard")
                .build();
    }

    private String resolveCreatePaymentApiKey() {
        if (StringUtils.hasText(moyasarPublishableKey)) {
            return moyasarPublishableKey;
        }
        if (isTestMoyasarEnvironment() && StringUtils.hasText(moyasarApiKey)) {
            return moyasarApiKey;
        }
        throw new BadRequestException(
                "Set MOYASAR_PUBLISHABLE_KEY (pk_test_...) from Moyasar Dashboard → Settings, "
                        + "or use a sk_test_ secret key in test mode");
    }

    private boolean isTestMoyasarEnvironment() {
        return (StringUtils.hasText(moyasarPublishableKey) && moyasarPublishableKey.startsWith("pk_test_"))
                || (StringUtils.hasText(moyasarApiKey) && moyasarApiKey.startsWith("sk_test_"));
    }

    private static int toHalalas(BigDecimal amountSar) {
        return amountSar.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private void validatePaymentPurposeAndAmounts(Payment payment) {
        // The unlock price is quoted per idea by the AI, so only the VAT math is checked here
        if (PaymentPurpose.OPPORTUNITY_ACCESS.equals(payment.getPurpose())
                || PaymentPurpose.SUBSCRIPTION.equals(payment.getPurpose())
                || PaymentPurpose.OWNER_LAUNCH.equals(payment.getPurpose())) {
            if (payment.getAmount() == null || payment.getAmount().signum() <= 0
                    || payment.getVat() == null
                    || payment.getTotalAmount() == null
                    || payment.getVat().compareTo(vatOf(payment.getAmount())) != 0
                    || payment.getTotalAmount().compareTo(payment.getAmount().add(payment.getVat())) != 0) {
                throw new BadRequestException(
                        "Opportunity access payment must be amount + 15% VAT = total");
            }
        }
        if (PaymentPurpose.SUCCESS_PARTNERSHIP.equals(payment.getPurpose())) {
            if (payment.getAmount() == null
                    || payment.getVat() == null
                    || payment.getTotalAmount() == null) {
                throw new BadRequestException("Success partnership payment amounts are required");
            }
        }
    }

    private PaymentResponse toResponse(Payment entity) {
        return PaymentResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .purpose(entity.getPurpose())
                .amount(entity.getAmount())
                .vat(entity.getVat())
                .totalAmount(entity.getTotalAmount())
                .currency(entity.getCurrency())
                .method(entity.getMethod())
                .moyasarPaymentId(entity.getMoyasarPaymentId())
                .status(entity.getStatus())
                .invoiceUrl(entity.getInvoiceUrl())
                .paidAt(entity.getPaidAt())
                .build();
    }
}
