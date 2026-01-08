package com.grabit.cba.VendingMachineAlertService.service;

import com.grabit.cba.VendingMachineAlertService.config.AllMachinesMonitorProperties;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertEmailConfig;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertHistory;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertType;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Sales;
import com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Partners;
import com.grabit.cba.VendingMachineAlertService.database.repository.*;
import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;
import com.grabit.cba.VendingMachineAlertService.util.EmailServiceUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AllMachineSaleFailedHealthMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllMachineSaleFailedHealthMonitorService.class);

    private final AllMachinesMonitorProperties allMachinesMonitorProperties;
    private final SalesRepository salesRepository;
    private final VMRepository vmRepository;
    private final EmailSender emailSender;
    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertTypeRepository alertTypeRepository;
    private final AlertEmailConfigRepository alertEmailConfigRepository;
    private final TemplateEngine templateEngine;
    private final com.grabit.cba.VendingMachineAlertService.database.repository.MerchantsRepository merchantsRepository;
    private final com.grabit.cba.VendingMachineAlertService.database.repository.PartnersRepository partnersRepository;

    @Value("${spring.mail.username}")
    private String senderMail;

    @Value("${grabit.logo:}")
    private String grabitLogo;

    // Keep track of machines that are currently considered unhealthy to avoid duplicate alerts
    private final Map<String, LocalDateTime> unhealthyMachinesLastFailure = new HashMap<>();

    public AllMachineSaleFailedHealthMonitorService(AllMachinesMonitorProperties allMachinesMonitorProperties, SalesRepository salesRepository, VMRepository vmRepository, EmailSender emailSender,
                                                    AlertHistoryRepository alertHistoryRepository, AlertTypeRepository alertTypeRepository, AlertEmailConfigRepository alertEmailConfigRepository,
                                                    TemplateEngine templateEngine, com.grabit.cba.VendingMachineAlertService.database.repository.MerchantsRepository merchantsRepository,
                                                    com.grabit.cba.VendingMachineAlertService.database.repository.PartnersRepository partnersRepository) {
        this.allMachinesMonitorProperties = allMachinesMonitorProperties;
        this.salesRepository = salesRepository;
        this.vmRepository = vmRepository;
        this.emailSender = emailSender;
        this.alertHistoryRepository = alertHistoryRepository;
        this.alertTypeRepository = alertTypeRepository;
        this.alertEmailConfigRepository = alertEmailConfigRepository;
        this.templateEngine = templateEngine;
        this.merchantsRepository = merchantsRepository;
        this.partnersRepository = partnersRepository;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("MachineHealthMonitorService initialized. enabled={}, cron={}, windowSize={}, failureThreshold={}",
                allMachinesMonitorProperties.isEnabled(), allMachinesMonitorProperties.getCron(), allMachinesMonitorProperties.getWindowSize(), allMachinesMonitorProperties.getFailureThreshold());
    }

    public void evaluateAllMachines() {
        if (!allMachinesMonitorProperties.isEnabled()) {
            LOGGER.info("Monitor disabled; skipping evaluation");
            return;
        }
        LOGGER.info("Monitor evaluation start for time {}", LocalDateTime.now());
        // Evaluate by partner -> merchants -> active vending machines
        List<Partners> partners = partnersRepository.findAll();
        if (partners == null || partners.isEmpty()) {
            LOGGER.warn("No partners found; skipping evaluation");
        } else {
            for (Partners partner : partners) {
                try {
                    Integer partnerId = partner.getId();
                    List<Integer> merchantIds = merchantsRepository.findIdsByPartnerIds(Collections.singletonList(partnerId));
                    if (merchantIds == null || merchantIds.isEmpty()) {
                        LOGGER.debug("Skipping partner {} (id={}) due to no merchants", partner.getName(), partnerId);
                        continue;
                    }
                    List<VendingMachine> activeMachines = vmRepository.findActiveByMerchantIds(merchantIds);
                    if (activeMachines == null || activeMachines.isEmpty()) {
                        LOGGER.debug("Skipping partner {} (id={}) due to no active vending machines", partner.getName(), partnerId);
                        continue;
                    }
                    // Evaluate same logic per machine
                    for (VendingMachine vm : activeMachines) {
                        String serial = vm.getSerialNo();
                        try {
                            evaluateMachine(serial);
                        } catch (Exception e) {
                            LOGGER.error("Error evaluating machine {} for partner {}: {}", serial, partner.getName(), e.getMessage(), e);
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.error("Error while evaluating partner {}: {}", partner.getName(), ex.getMessage(), ex);
                }
            }
        }
        LOGGER.info("Monitor evaluation end for time {}", LocalDateTime.now());
    }

    public void evaluateMachine(String serialNo) {
        List<Sales> latest = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, allMachinesMonitorProperties.getWindowSize()));
        if (latest.isEmpty()) {
            LOGGER.debug("No transactions found for machine {}", serialNo);
            return;
        }

        Set<String> failureStatuses;
        try {
            List<AlertType> alertTypes = alertTypeRepository.findAll();
            if (alertTypes.isEmpty()) {
                failureStatuses = new HashSet<>(Arrays.asList(
                        "SALE_FAILED"
//                        "TIMEOUT",
//                        "VOID_FAILED"
                ));
            } else {
                failureStatuses = alertTypes.stream()
                        .map(AlertType::getCode)
                        .filter(Objects::nonNull)
                        .map(String::toUpperCase)
                        .collect(Collectors.toSet());
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not load failure statuses from AlertType table, using defaults: {}", ex.getMessage());
            failureStatuses = new HashSet<>(Arrays.asList("SALE_FAILED", "TIMEOUT", "VOID_FAILED"));
        }

        int consecutiveFailures = 0;
        LocalDateTime lastFailureTime = null;
        List<String> failureTypesSeen = new ArrayList<>();

        // Consecutive failures check (as before)
        for (Sales s : latest) {
            String status = Optional.ofNullable(s.getTransactionStatus()).map(String::toUpperCase).orElse("");
            if (failureStatuses.contains(status)) {
                consecutiveFailures++;
                lastFailureTime = s.getDateTime();
                failureTypesSeen.add(status);
            } else {
                // success or other: break the chain
                break;
            }
        }

        // Sliding window check: count failures within the configured slidingWindowSize
        int slidingWindowSize = allMachinesMonitorProperties.getSlidingWindowSize();
        int slidingFailureThreshold = allMachinesMonitorProperties.getSlidingFailureThreshold();
        int failuresInWindow = 0;
        List<Sales> slidingList = latest.size() <= slidingWindowSize ? latest : latest.subList(0, slidingWindowSize);
        for (Sales s : slidingList) {
            String status = Optional.ofNullable(s.getTransactionStatus()).map(String::toUpperCase).orElse("");
            if (failureStatuses.contains(status)) {
                failuresInWindow++;
            }
        }

        boolean consecutiveTriggered = consecutiveFailures >= allMachinesMonitorProperties.getFailureThreshold();
        boolean slidingTriggered = failuresInWindow >= slidingFailureThreshold;

        if (consecutiveTriggered || slidingTriggered) {
            // build issue descriptions for email and pass both counts to handler
            List<String> detectedIssues = new ArrayList<>();
            if (consecutiveTriggered) {
                detectedIssues.add(String.format("%d consecutive failed transactions", consecutiveFailures));
            }
            if (slidingTriggered) {
                detectedIssues.add(String.format("%d failures within last %d transactions", failuresInWindow, slidingWindowSize));
            }
            handleUnhealthyWithIssues(serialNo, consecutiveFailures, lastFailureTime, failureTypesSeen, detectedIssues, failuresInWindow);
        } else {
            // recovery: if previously marked unhealthy, clear state
            if (unhealthyMachinesLastFailure.containsKey(serialNo)) {
                LOGGER.info("Machine {} recovered (consecutiveFailures={}); clearing unhealthy state", serialNo, consecutiveFailures);
                unhealthyMachinesLastFailure.remove(serialNo);
            }
        }
    }

    private void handleUnhealthyWithIssues(String serialNo, int consecutiveFailures, LocalDateTime lastFailureTime, List<String> failureTypesSeen,
                                           List<String> detectedIssues, int failuresInWindow) {
        // reuse previous handleUnhealthy logic but combine the issues into the email body
        LocalDateTime alreadyNotifiedAt = unhealthyMachinesLastFailure.get(serialNo);
        if (alreadyNotifiedAt != null && Objects.equals(alreadyNotifiedAt, lastFailureTime)) {
            LOGGER.debug("Duplicate alert suppressed for machine {} at {}", serialNo, lastFailureTime);
            return;
        }

        final String alertCode = "SALE_FAILED";
        AlertType selectedAlertType = alertTypeRepository.findByCode(alertCode).orElse(null);
        if (selectedAlertType == null) {
            LOGGER.warn("AlertType '{}' not found; skipping alert for machine {}", alertCode, serialNo);
            return;
        }

        // Resolve vmId and check AlertHistory cooldown as before
        Integer vmId = vmRepository.findAll().stream()
                .filter(vmObj -> serialNo.equals(vmObj.getSerialNo()))
                .map(VendingMachine::getId)
                .findFirst().orElse(null);

        Optional<AlertHistory> last = Optional.empty();
        Integer alertTypeId = selectedAlertType.getId();
        if (vmId != null) {
            last = alertHistoryRepository.findLatestByVendingMachineIdAndAlertTypeId(vmId, alertTypeId);
            LOGGER.info("Checking AlertHistory by vmId={}, alertTypeId={}", vmId, alertTypeId);
        }
        if (last.isEmpty()) {
            last = alertHistoryRepository.findLatestByVendingMachineSerialAndAlertTypeId(serialNo, alertTypeId);
            LOGGER.info("Checking AlertHistory by vendingMachineSerial='{}', alertTypeId={}", serialNo, alertTypeId);
        }
        if (last.isEmpty()) {
            last = alertHistoryRepository.findLatestByVendingMachineSerial(serialNo);
            LOGGER.info("Fallback: checking any AlertHistory by vendingMachineSerial='{}'", serialNo);
        }
        if (last.isPresent()) {
            java.time.LocalDateTime lastSent = last.get().getLastSentAt();
            if (lastSent != null) {
                if (lastFailureTime != null && !lastSent.isBefore(lastFailureTime)) {
                    LOGGER.info("Suppressing alert because previous AlertHistory (id={}) was sent at {} which is >= lastFailureTimestamp {}", last.get().getId(), lastSent, lastFailureTime);
                    unhealthyMachinesLastFailure.put(serialNo, lastFailureTime);
                    return;
                }
                long cooldownMinutes = allMachinesMonitorProperties.getAlertCooldownMinutes();
                java.time.LocalDateTime now = java.time.LocalDateTime.now(ZoneId.systemDefault());
                java.time.Duration elapsed = java.time.Duration.between(lastSent, now);
                if (elapsed.toMinutes() < cooldownMinutes) {
                    LOGGER.info("Suppressing duplicate alert for machine {} and alertType {} (sent {} minutes ago, cooldown {} minutes)", serialNo, alertCode, elapsed.toMinutes(), cooldownMinutes);
                    unhealthyMachinesLastFailure.put(serialNo, lastFailureTime);
                    return;
                }
            }
        }

        // Build single email with combined issue text
        MailDto mailDto = new MailDto();
        String[] toAddrs = null;
        String[] ccAddrs = null;
        String[] bccAddrs = null;

        // Determine partner for the machine to select correct AlertEmailConfig
        Partners machinePartner = null;
        VendingMachine vmForPartner = vmRepository.findBySerialNo(serialNo).orElse(null);
        if (vmForPartner != null && vmForPartner.getMerchantId() != null) {
            try {
                Optional<com.grabit.cba.VendingMachineAlertService.database.model.other.Merchants> merchantOpt = merchantsRepository.findById(vmForPartner.getMerchantId());
                if (merchantOpt.isPresent() && merchantOpt.get().getPartnerId() != null) {
                    Integer partnerId = merchantOpt.get().getPartnerId();
                    try {
                        machinePartner = partnersRepository.findById(partnerId).orElse(null);
                    } catch (Exception ignore) { }
                }
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve partner for machine {}: {}", serialNo, ex.getMessage());
            }
        }

        java.util.Optional<AlertEmailConfig> optCfg;
        optCfg = alertEmailConfigRepository.findFirstByAlertTypeAndPartners(selectedAlertType, machinePartner);

        if (optCfg.isPresent()) {
            AlertEmailConfig cfg = optCfg.get();
            toAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getTo());
            ccAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getCc());
            bccAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getBcc());
        }

        mailDto.setTo(toAddrs);
        mailDto.setCc(ccAddrs);
        mailDto.setBcc(bccAddrs);
        mailDto.setSubject(String.format("ALERT: Machine %s unhealthy - %s", serialNo, String.join(", ", detectedIssues)));
        mailDto.setFrom(senderMail);
        mailDto.setHtml(true);

        // prepare template props and include detectedIssues
        Map<String, Object> props = new HashMap<>();
        props.put("vendingSerialNumber", serialNo);
        props.put("failureCount", consecutiveFailures);
        props.put("lastFailureTimestamp", lastFailureTime == null ? null : lastFailureTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        props.put("failureTypes", failureTypesSeen.stream().distinct().collect(Collectors.joining(", ")));
        props.put("detectedIssues", detectedIssues);

        // format lastFailureTime as a human-readable string with microsecond precision
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
        String lastFailureTimeFormatted = lastFailureTime == null ? null : lastFailureTime.atZone(ZoneId.systemDefault()).format(dtf);
        props.put("lastFailureTimeFormatted", lastFailureTimeFormatted);

        // Attempt to resolve vending machine details to populate vmName, terminateCode, productLockCount, location
        VendingMachine vm = null;
        if (vmId != null) {
            vm = vmRepository.findById(vmId).orElse(null);
        }
        if (vm == null) {
            vm = vmRepository.findBySerialNo(serialNo).orElse(null);
        }
        if (vm != null) {
            props.put("vmName", vm.getName());
            // merchantName/address: prefer lookup from Merchants table via vm.getMerchantId(); fallback to latest sale.TranMerchantID
            String merchantName = null;
            String merchantAddress = null;
            try {
                if (vm.getMerchantId() != null) {
                    try {
                        java.util.Optional<com.grabit.cba.VendingMachineAlertService.database.model.other.Merchants> optMerchant = merchantsRepository.findById(vm.getMerchantId());
                        if (optMerchant.isPresent()) {
                            merchantName = optMerchant.get().getName();
                            merchantAddress = optMerchant.get().getAddress();
                        }
                    } catch (Exception ex) {
                        // ignore and fallback to TranMerchantID
                    }
                }
                if (merchantName == null) {
                    Sales latestSale = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
                    if (latestSale != null && latestSale.getTranMerchantID() != null) {
                        merchantName = latestSale.getTranMerchantID();
                    }
                }
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve merchant info for serial {}: {}", serialNo, ex.getMessage());
            }
            props.put("merchantName", merchantName);
            props.put("terminateCode", vm.getTerminateCode());
            props.put("productLockCount", vm.getProductLockCount());
            // location: use merchant.address explicitly per request
            props.put("location", merchantAddress);
            LOGGER.info("Using merchant address as location for machine {} -> '{}'", serialNo, merchantAddress);
        } else {
            props.put("vmName", null);
            props.put("merchantName", null);
            props.put("terminateCode", null);
            props.put("productLockCount", null);
            props.put("location", null);
        }

        props.put("year", Calendar.getInstance().get(Calendar.YEAR));

        try {
            Context context = new Context();
            context.setVariables(props);
            String htmlBody = templateEngine.process("Sale_failed", context);
            mailDto.setBody(htmlBody);

            boolean emailSent = emailSender.sendEmail(mailDto, grabitLogo, null);
            if (emailSent) {
                String partnerNameLog = machinePartner != null ? machinePartner.getName() : "UNKNOWN";
                String toLog = (toAddrs != null && toAddrs.length > 0) ? String.join(",", toAddrs) : "<none>";
                LOGGER.info("Email has been sent at {} to partner {} email {}", java.time.LocalDateTime.now(java.time.ZoneId.systemDefault()), partnerNameLog, toLog);
                LOGGER.info("Alert email sent for machine {} (consecutiveFailures={}, failuresInWindow={})", serialNo, consecutiveFailures, failuresInWindow);

                // persist/update AlertHistory ONLY if email was sent successfully
                java.time.LocalDateTime sendTime = java.time.LocalDateTime.now(ZoneId.systemDefault());
                if (last.isPresent()) {
                    AlertHistory existing = last.get();
                    existing.setVendingMachineId(vmId);
                    existing.setVendingMachineSerial(serialNo);
                    existing.setLastSentAt(sendTime);
                    existing.setAlertType(selectedAlertType);
                    // store partner name if available
                    if (machinePartner != null) {
                        existing.setPartnerName(machinePartner.getName());
                    }
                    alertHistoryRepository.saveAndFlush(existing);
                    LOGGER.info("Updated existing AlertHistory id={} for machine {} with lastSentAt={}", existing.getId(), serialNo, existing.getLastSentAt());
                } else {
                    AlertHistory history = new AlertHistory();
                    history.setVendingMachineId(vmId);
                    history.setVendingMachineSerial(serialNo);
                    if (history.getVendingMachineId() == null) {
                        LOGGER.warn("Could not resolve vendingMachineId for serial {}; AlertHistory will store null", serialNo);
                    }
                    history.setLastSentAt(sendTime);
                    history.setAlertType(selectedAlertType);
                    history.setPartnerName(machinePartner != null ? machinePartner.getName() : null);
                    alertHistoryRepository.saveAndFlush(history);
                    LOGGER.info("Inserted AlertHistory for machine {} at {} (history id={})", serialNo, history.getLastSentAt(), history.getId());
                }

                unhealthyMachinesLastFailure.put(serialNo, lastFailureTime);
            } else {
                LOGGER.warn("Email send returned false for machine {}; skipping AlertHistory persist", serialNo);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send alert email for machine {}: {}", serialNo, e.getMessage(), e);
        }
    }
}
