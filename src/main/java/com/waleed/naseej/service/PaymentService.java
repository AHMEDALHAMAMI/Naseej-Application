package com.waleed.naseej.service;

import com.waleed.naseej.dto.in.InitiateMoyasarPaymentRequest;
import com.waleed.naseej.dto.in.MoyasarCreatePaymentApiRequest;
import com.waleed.naseej.dto.in.MoyasarPaymentSourceApiRequest;
import com.waleed.naseej.dto.in.MoyasarWebhookRequest;
import com.waleed.naseej.dto.in.PaymentRequest;
import com.waleed.naseej.dto.out.ContractResponse;
import com.waleed.naseej.dto.out.InitiateMoyasarPaymentResponse;
import com.waleed.naseej.dto.out.MoyasarCreatePaymentApiResponse;
import com.waleed.naseej.dto.out.MoyasarPaymentStatusResponse;
import com.waleed.naseej.dto.out.MoyasarWebhookResponse;
import com.waleed.naseej.dto.out.PaymentResponse;
import com.waleed.naseej.enums.PaymentMethod;
import com.waleed.naseej.entity.Contract;
import com.waleed.naseej.entity.Payment;
import com.waleed.naseej.enums.ContractStatus;
import com.waleed.naseej.enums.PaymentPurpose;
import com.waleed.naseej.enums.PaymentStatus;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.DuplicateResourceException;
import com.waleed.naseej.exception.ResourceNotFoundException;
import com.waleed.naseej.repository.ContractRepository;
import com.waleed.naseej.repository.OpportunityAccessRepository;
import com.waleed.naseej.repository.PaymentRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final BigDecimal ACCESS_FEE_AMOUNT = new BigDecimal("1500.00");
    private static final BigDecimal ACCESS_FEE_VAT = new BigDecimal("225.00");
    private static final BigDecimal ACCESS_FEE_TOTAL = new BigDecimal("1725.00");
    private static final String MOYASAR_PAYMENTS_URL = "https://api.moyasar.com/v1/payments/";
    private static final String MOYASAR_CREATE_PAYMENT_URL = "https://api.moyasar.com/v1/payments";

    private final PaymentRepository paymentRepository;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final OpportunityAccessRepository opportunityAccessRepository;
    private final ContractRepository contractRepository;
    private final ContractService contractService;

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

    @Transactional
    public MoyasarWebhookResponse handleMoyasarWebhook(String webhookSecretHeader, MoyasarWebhookRequest webhook) {
        validateWebhookSecret(webhookSecretHeader);

        if (!"paid".equalsIgnoreCase(webhook.getStatus())) {
            throw new BadRequestException("Webhook ignored: payment status is not paid");
        }

        Payment payment = paymentRepository.findByMoyasarPaymentId(webhook.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found for moyasar id: " + webhook.getId()));

        return finalizePaidMoyasarPayment(payment);
    }

    @Transactional
    public InitiateMoyasarPaymentResponse initiateMoyasarPayment(InitiateMoyasarPaymentRequest request) {
        if (!PaymentPurpose.OPPORTUNITY_ACCESS.equals(request.getPurpose())) {
            throw new BadRequestException(
                    "Only OPPORTUNITY_ACCESS payments can be initiated via Moyasar for now");
        }

        String createApiKey = resolveCreatePaymentApiKey();
        String callbackUrl = StringUtils.hasText(request.getCallbackUrl())
                ? request.getCallbackUrl().trim()
                : moyasarCallbackUrl;
        if (!StringUtils.hasText(callbackUrl)) {
            throw new BadRequestException(
                    "callbackUrl is required (set MOYASAR_CALLBACK_URL or pass callbackUrl in the request)");
        }

        boolean useTestCard = request.getUseSandboxTestCard() == null
                || Boolean.TRUE.equals(request.getUseSandboxTestCard());
        if (useTestCard && !isTestMoyasarEnvironment()) {
            throw new BadRequestException(
                    "Sandbox test card is only allowed when MOYASAR_API_KEY or MOYASAR_PUBLISHABLE_KEY is a test key");
        }

        String givenId = UUID.randomUUID().toString();
        if (paymentRepository.existsByMoyasarPaymentId(givenId)) {
            throw new DuplicateResourceException("Payment already exists with moyasar payment id: " + givenId);
        }

        MoyasarCreatePaymentApiRequest apiRequest = MoyasarCreatePaymentApiRequest.builder()
                .givenId(givenId)
                .amount(toHalalas(ACCESS_FEE_TOTAL))
                .currency("SAR")
                .description("Naseej opportunity access fee")
                .callbackUrl(callbackUrl)
                .source(buildMoyasarSource(useTestCard))
                .build();

        MoyasarCreatePaymentApiResponse moyasarResponse = callMoyasarCreatePayment(createApiKey, apiRequest);
        String moyasarPaymentId = StringUtils.hasText(moyasarResponse.getId()) ? moyasarResponse.getId() : givenId;
        String transactionUrl = moyasarResponse.getSource() != null
                ? moyasarResponse.getSource().getTransactionUrl()
                : null;

        Payment payment = Payment.builder()
                .user(userRepository.findById(request.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "User not found with id: " + request.getUserId())))
                .purpose(PaymentPurpose.OPPORTUNITY_ACCESS)
                .amount(ACCESS_FEE_AMOUNT)
                .vat(ACCESS_FEE_VAT)
                .totalAmount(ACCESS_FEE_TOTAL)
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
        MoyasarPaymentStatusResponse remote = getPaymentStatus(payment.getMoyasarPaymentId());
        if (!"paid".equalsIgnoreCase(remote.getStatus())) {
            throw new BadRequestException(
                    "Moyasar payment status is '" + remote.getStatus()
                            + "'. Complete checkout or wait, then sync again.");
        }
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
        }

        return MoyasarWebhookResponse.builder()
                .paymentId(payment.getId())
                .paymentStatus(payment.getStatus())
                .contractId(contractId)
                .contractStatus(contractStatus)
                .message(message)
                .build();
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
        if (PaymentPurpose.OPPORTUNITY_ACCESS.equals(payment.getPurpose())) {
            if (payment.getAmount().compareTo(ACCESS_FEE_AMOUNT) != 0
                    || payment.getVat().compareTo(ACCESS_FEE_VAT) != 0
                    || payment.getTotalAmount().compareTo(ACCESS_FEE_TOTAL) != 0) {
                throw new BadRequestException(
                        "Opportunity access fee must be 1500 SAR + 225 VAT = 1725 SAR total");
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
