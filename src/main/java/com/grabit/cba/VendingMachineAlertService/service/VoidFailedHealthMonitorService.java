package com.grabit.cba.VendingMachineAlertService.service;

import com.grabit.cba.VendingMachineAlertService.config.AllMachinesMonitorProperties;
import com.grabit.cba.VendingMachineAlertService.database.model.*;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Sales;
import com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Partners;
import com.grabit.cba.VendingMachineAlertService.database.repository.*;
import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;
import com.grabit.cba.VendingMachineAlertService.enums.TransactionTypes;
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
public class VoidFailedHealthMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VoidFailedHealthMonitorService.class);

    private final AllMachinesMonitorProperties allMachinesMonitorProperties;
    private final SalesRepository salesRepository;
    private final VMRepository vmRepository;
    private final EmailSender emailSender;
    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertTypeRepository alertTypeRepository;
    private final AlertEmailConfigRepository alertEmailConfigRepository;
    private final VoidFailedTrackingRepository voidFailedTrackingRepository;
    private final TemplateEngine templateEngine;
    private final com.grabit.cba.VendingMachineAlertService.database.repository.MerchantsRepository merchantsRepository;
    private final com.grabit.cba.VendingMachineAlertService.database.repository.PartnersRepository partnersRepository;

    @Value("${spring.mail.username}")
    private String senderMail;

    @Value("${grabit.logo:}")
    private String grabitLogo;

    // Keep track of machines that are currently considered unhealthy to avoid duplicate alerts
    private final Map<String, LocalDateTime> unhealthyMachinesLastFailure = new HashMap<>();

    public VoidFailedHealthMonitorService(AllMachinesMonitorProperties allMachinesMonitorProperties,
                                        SalesRepository salesRepository,
                                        VMRepository vmRepository,
                                        EmailSender emailSender,
                                        AlertHistoryRepository alertHistoryRepository,
                                        AlertTypeRepository alertTypeRepository,
                                        AlertEmailConfigRepository alertEmailConfigRepository,
                                        VoidFailedTrackingRepository voidFailedTrackingRepository,
                                        TemplateEngine templateEngine,
                                        com.grabit.cba.VendingMachineAlertService.database.repository.MerchantsRepository merchantsRepository,
                                        com.grabit.cba.VendingMachineAlertService.database.repository.PartnersRepository partnersRepository) {
        this.allMachinesMonitorProperties = allMachinesMonitorProperties;
        this.salesRepository = salesRepository;
        this.vmRepository = vmRepository;
        this.emailSender = emailSender;
        this.alertHistoryRepository = alertHistoryRepository;
        this.alertTypeRepository = alertTypeRepository;
        this.alertEmailConfigRepository = alertEmailConfigRepository;
        this.voidFailedTrackingRepository = voidFailedTrackingRepository;
        this.templateEngine = templateEngine;
        this.merchantsRepository = merchantsRepository;
        this.partnersRepository = partnersRepository;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("VoidFailedHealthMonitorService initialized. enabled={}, cron={}, voidFailureThreshold={}",
                allMachinesMonitorProperties.getVoidFailed().isVoidFailedEnabled(),
                allMachinesMonitorProperties.getVoidFailed().getVoidFailedCron(),
                allMachinesMonitorProperties.getVoidFailed().getVoidFailureThreshold());
    }

    public void evaluateAllMachines() {
        if (!allMachinesMonitorProperties.getVoidFailed().isVoidFailedEnabled()) {
            LOGGER.info("Void failed monitor disabled; skipping evaluation");
            return;
        }
        LOGGER.info("Void failed monitor evaluation start for time {}", LocalDateTime.now());

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
                    // Evaluate void failed logic per machine
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
        LOGGER.info("Void failed monitor evaluation end for time {}", LocalDateTime.now());
    }

    public void evaluateMachine(String serialNo) {
        // Get tracking info for this machine to determine where to start checking
        Optional<VoidFailedTracking> trackingOpt = voidFailedTrackingRepository.findByVendingMachineSerial(serialNo);

        List<Sales> latestTransactions;
        int threshold = allMachinesMonitorProperties.getVoidFailed().getVoidFailureThreshold();

        if (trackingOpt.isPresent()) {
            VoidFailedTracking tracking = trackingOpt.get();
            // Get transactions newer than the last checked transaction to avoid duplicates
            if (tracking.getLastCheckedTransactionId() != null) {
                latestTransactions = salesRepository.findLatestByMachineSerialAfterTransactionId(
                    serialNo,
                    tracking.getLastCheckedTransactionId(),
                    PageRequest.of(0, threshold)
                );
            } else if (tracking.getLastCheckedDatetime() != null) {
                latestTransactions = salesRepository.findLatestByMachineSerialAfterDatetime(
                    serialNo,
                    tracking.getLastCheckedDatetime(),
                    PageRequest.of(0, threshold)
                );
            } else {
                // Fallback: get latest transactions
                latestTransactions = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, threshold));
            }
        } else {
            // First time checking this machine, get the latest threshold number of transactions
            latestTransactions = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, threshold));
        }

        if (latestTransactions.isEmpty()) {
            LOGGER.debug("No new transactions found for machine {}", serialNo);
            return;
        }

        // Check if any of the latest transactions are VOID_FAILED
        boolean hasVoidFailed = false;
        LocalDateTime newestTransactionTime = null;
        Integer newestTransactionId = null;
        List<Sales> voidFailedTransactions = new ArrayList<>();

        for (Sales s : latestTransactions) {
            String status = Optional.ofNullable(s.getTransactionStatus()).map(String::toUpperCase).orElse("");

            // Update newest transaction info
            if (newestTransactionTime == null || (s.getDateTime() != null && s.getDateTime().isAfter(newestTransactionTime))) {
                newestTransactionTime = s.getDateTime();
                newestTransactionId = s.getId();
            }

            if (TransactionTypes.VOID_FAILED.name().equals(status)) {
                hasVoidFailed = true;
                voidFailedTransactions.add(s);
            }
        }

        // Update tracking record
        updateTrackingRecord(serialNo, newestTransactionId, newestTransactionTime);

        // If void failed detected, trigger alert
        if (hasVoidFailed) {
            LOGGER.info("Void failed transaction detected for machine {}. Found {} void failed transactions",
                       serialNo, voidFailedTransactions.size());
            handleVoidFailedAlert(serialNo, voidFailedTransactions);
        } else {
            // Recovery: if previously marked unhealthy, clear state
            if (unhealthyMachinesLastFailure.containsKey(serialNo)) {
                LOGGER.info("Machine {} recovered from void failed issues; clearing unhealthy state", serialNo);
                unhealthyMachinesLastFailure.remove(serialNo);
            }
        }
    }

    private void updateTrackingRecord(String serialNo, Integer newestTransactionId, LocalDateTime newestTransactionTime) {
        Optional<VoidFailedTracking> trackingOpt = voidFailedTrackingRepository.findByVendingMachineSerial(serialNo);

        VoidFailedTracking tracking;
        if (trackingOpt.isPresent()) {
            tracking = trackingOpt.get();
        } else {
            tracking = VoidFailedTracking.builder()
                .vendingMachineSerial(serialNo)
                .build();
        }

        tracking.setLastCheckedTransactionId(newestTransactionId);
        tracking.setLastCheckedDatetime(newestTransactionTime);

        voidFailedTrackingRepository.saveAndFlush(tracking);
        LOGGER.debug("Updated tracking for machine {}: lastTransactionId={}, lastDateTime={}",
                    serialNo, newestTransactionId, newestTransactionTime);
    }

    private void handleVoidFailedAlert(String serialNo, List<Sales> voidFailedTransactions) {
        // Get the most recent void failed transaction for alert details
        Sales mostRecentVoidFailed = voidFailedTransactions.stream()
            .max(Comparator.comparing(Sales::getDateTime))
            .orElse(null);

        LocalDateTime lastFailureTime = mostRecentVoidFailed != null ? mostRecentVoidFailed.getDateTime() : null;

        // Check if we already notified for this failure
        LocalDateTime alreadyNotifiedAt = unhealthyMachinesLastFailure.get(serialNo);
        if (alreadyNotifiedAt != null && Objects.equals(alreadyNotifiedAt, lastFailureTime)) {
            LOGGER.debug("Duplicate void failed alert suppressed for machine {} at {}", serialNo, lastFailureTime);
            return;
        }

        // Try to use VOID_FAILED alert type first, fallback to SALE_FAILED, or create a generic one
        String alertCode = TransactionTypes.VOID_FAILED.name();
        AlertType selectedAlertType = alertTypeRepository.findByCode(alertCode).orElse(null);

        if (selectedAlertType == null) {
            // Fallback to SALE_FAILED alert type as it's a similar critical failure
            alertCode = TransactionTypes.SALE_FAILED.name();
            selectedAlertType = alertTypeRepository.findByCode(alertCode).orElse(null);
            LOGGER.info("VOID_FAILED alert type not found, using SALE_FAILED as fallback for machine {}", serialNo);
        }

        if (selectedAlertType == null) {
            LOGGER.warn("No suitable AlertType found ('{}' or 'SALE_FAILED'); skipping alert for machine {}",
                       TransactionTypes.VOID_FAILED.name(), serialNo);
            return;
        }

        // Resolve vmId and check AlertHistory cooldown
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
            LocalDateTime lastSent = last.get().getLastSentAt();
            if (lastSent != null) {
                if (lastFailureTime != null && !lastSent.isBefore(lastFailureTime)) {
                    LOGGER.info("Suppressing alert because previous AlertHistory (id={}) was sent at {} which is >= lastFailureTimestamp {}",
                               last.get().getId(), lastSent, lastFailureTime);
                    unhealthyMachinesLastFailure.put(serialNo, lastFailureTime);
                    return;
                }
                long cooldownMinutes = allMachinesMonitorProperties.getVoidFailed().getAlertCooldownMinutes();
                LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                java.time.Duration elapsed = java.time.Duration.between(lastSent, now);
                if (elapsed.toMinutes() < cooldownMinutes) {
                    LOGGER.info("Suppressing duplicate alert for machine {} and alertType {} (sent {} minutes ago, cooldown {} minutes)",
                               serialNo, alertCode, elapsed.toMinutes(), cooldownMinutes);
                    unhealthyMachinesLastFailure.put(serialNo, lastFailureTime);
                    return;
                }
            }
        }

        // Send alert email
        sendVoidFailedAlert(serialNo, voidFailedTransactions, selectedAlertType, vmId, lastFailureTime);
    }

    private void sendVoidFailedAlert(String serialNo, List<Sales> voidFailedTransactions, AlertType selectedAlertType,
                                   Integer vmId, LocalDateTime lastFailureTime) {
        MailDto mailDto = new MailDto();
        String[] toAddrs = null;
        String[] ccAddrs = null;
        String[] bccAddrs = null;

        // Determine partner for the machine to select correct AlertEmailConfig
        Partners machinePartner = null;
        VendingMachine vmForPartner = vmRepository.findBySerialNo(serialNo).orElse(null);
        if (vmForPartner != null && vmForPartner.getMerchantId() != null) {
            try {
                Optional<com.grabit.cba.VendingMachineAlertService.database.model.other.Merchants> merchantOpt =
                    merchantsRepository.findById(vmForPartner.getMerchantId());
                if (merchantOpt.isPresent() && merchantOpt.get().getPartnerId() != null) {
                    Integer partnerId = merchantOpt.get().getPartnerId();
                    machinePartner = partnersRepository.findById(partnerId).orElse(null);
                }
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve partner for machine {}: {}", serialNo, ex.getMessage());
            }
        }

        Optional<AlertEmailConfig> optCfg = alertEmailConfigRepository.findFirstByAlertTypeAndPartners(selectedAlertType, machinePartner);

        if (optCfg.isPresent()) {
            AlertEmailConfig cfg = optCfg.get();
            toAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getTo());
            ccAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getCc());
            bccAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getBcc());
        }

        mailDto.setTo(toAddrs);
        mailDto.setCc(ccAddrs);
        mailDto.setBcc(bccAddrs);
        mailDto.setSubject(String.format("CRITICAL ALERT: Machine %s - Void Transaction Failed", serialNo));
        mailDto.setFrom(senderMail);
        mailDto.setHtml(true);

        // Prepare template properties
        Map<String, Object> props = new HashMap<>();
        props.put("vendingSerialNumber", serialNo);
        props.put("voidFailedCount", voidFailedTransactions.size());
        props.put("lastFailureTimestamp", lastFailureTime == null ? null :
                  lastFailureTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        // Format lastFailureTime as a human-readable string
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
        String lastFailureTimeFormatted = lastFailureTime == null ? null :
                                        lastFailureTime.atZone(ZoneId.systemDefault()).format(dtf);
        props.put("lastFailureTimeFormatted", lastFailureTimeFormatted);

        // Get vending machine details
        VendingMachine vm = null;
        if (vmId != null) {
            vm = vmRepository.findById(vmId).orElse(null);
        }
        if (vm == null) {
            vm = vmRepository.findBySerialNo(serialNo).orElse(null);
        }
        if (vm != null) {
            props.put("vmName", vm.getName());

            // Get merchant info
            String merchantName = null;
            String merchantAddress = null;
            try {
                if (vm.getMerchantId() != null) {
                    Optional<com.grabit.cba.VendingMachineAlertService.database.model.other.Merchants> optMerchant =
                        merchantsRepository.findById(vm.getMerchantId());
                    if (optMerchant.isPresent()) {
                        merchantName = optMerchant.get().getName();
                        merchantAddress = optMerchant.get().getAddress();
                    }
                }
                if (merchantName == null && !voidFailedTransactions.isEmpty()) {
                    Sales latestSale = voidFailedTransactions.get(0);
                    if (latestSale.getTranMerchantID() != null) {
                        merchantName = latestSale.getTranMerchantID();
                    }
                }
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve merchant info for serial {}: {}", serialNo, ex.getMessage());
            }
            props.put("merchantName", merchantName);
            props.put("terminateCode", vm.getTerminateCode());
            props.put("productLockCount", vm.getProductLockCount());
            props.put("location", merchantAddress);
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
            // Use the specific void failed template
            String htmlBody = templateEngine.process("Void_failed", context);
            mailDto.setBody(htmlBody);

            boolean emailSent = emailSender.sendEmail(mailDto, grabitLogo, null);
            if (emailSent) {
                String partnerNameLog = machinePartner != null ? machinePartner.getName() : "UNKNOWN";
                String toLog = (toAddrs != null && toAddrs.length > 0) ? String.join(",", toAddrs) : "<none>";
                LOGGER.info("Void failed alert email sent at {} to partner {} email {}",
                           LocalDateTime.now(ZoneId.systemDefault()), partnerNameLog, toLog);
                LOGGER.info("Void failed alert email sent for machine {} ({} void failed transactions)",
                           serialNo, voidFailedTransactions.size());

                // Persist/update AlertHistory
                LocalDateTime sendTime = LocalDateTime.now(ZoneId.systemDefault());
                Optional<AlertHistory> existingHistory = alertHistoryRepository
                    .findLatestByVendingMachineSerialAndAlertTypeId(serialNo, selectedAlertType.getId());

                if (existingHistory.isPresent()) {
                    AlertHistory existing = existingHistory.get();
                    existing.setVendingMachineId(vmId);
                    existing.setVendingMachineSerial(serialNo);
                    existing.setLastSentAt(sendTime);
                    existing.setAlertType(selectedAlertType);
                    if (machinePartner != null) {
                        existing.setPartnerName(machinePartner.getName());
                    }
                    alertHistoryRepository.saveAndFlush(existing);
                    LOGGER.info("Updated existing AlertHistory id={} for machine {} with lastSentAt={}",
                               existing.getId(), serialNo, existing.getLastSentAt());
                } else {
                    AlertHistory history = new AlertHistory();
                    history.setVendingMachineId(vmId);
                    history.setVendingMachineSerial(serialNo);
                    history.setLastSentAt(sendTime);
                    history.setAlertType(selectedAlertType);
                    history.setPartnerName(machinePartner != null ? machinePartner.getName() : null);
                    alertHistoryRepository.saveAndFlush(history);
                    LOGGER.info("Inserted AlertHistory for machine {} at {} (history id={})",
                               serialNo, history.getLastSentAt(), history.getId());
                }

                unhealthyMachinesLastFailure.put(serialNo, lastFailureTime);
            } else {
                LOGGER.warn("Email send returned false for machine {}; skipping AlertHistory persist", serialNo);
            }

        } catch (Exception e) {
            LOGGER.error("Failed to send void failed alert for machine {}: {}", serialNo, e.getMessage(), e);
        }
    }
}
