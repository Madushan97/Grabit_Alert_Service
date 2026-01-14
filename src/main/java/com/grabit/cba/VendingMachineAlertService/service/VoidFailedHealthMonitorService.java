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
import org.springframework.data.domain.Sort;
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

    // Cache for vending machines to avoid repeated lookups
    private final Map<String, VendingMachine> vendingMachineCache = new HashMap<>();
    private final Map<Integer, Partners> partnersCache = new HashMap<>();

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
        LOGGER.info("VoidFailedHealthMonitorService initialized. enabled={}, cron={}, voidFailureThreshold={}, alertCooldownMinutes={}",
                allMachinesMonitorProperties.getVoidFailed().isVoidFailedEnabled(),
                allMachinesMonitorProperties.getVoidFailed().getVoidFailedCron(),
                allMachinesMonitorProperties.getVoidFailed().getVoidFailureThreshold(),
                allMachinesMonitorProperties.getVoidFailed().getAlertCooldownMinutes());
    }

    public void evaluateAllMachines() {
        if (!allMachinesMonitorProperties.getVoidFailed().isVoidFailedEnabled()) {
            LOGGER.info("Void failed monitor disabled; skipping evaluation");
            return;
        }
        LOGGER.info("Void failed monitor evaluation start for time {}", LocalDateTime.now());

        // Clear cache at the beginning of each evaluation cycle
        vendingMachineCache.clear();
        partnersCache.clear();

        // Pre-load partners cache
        List<Partners> allPartners = partnersRepository.findAll();
        partnersCache.putAll(allPartners.stream().collect(Collectors.toMap(Partners::getId, p -> p)));

        // Reuse the already loaded partners list instead of making another DB call
        List<Partners> partners = allPartners;
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

                    // Pre-load vending machine cache for this partner
                    for (VendingMachine vm : activeMachines) {
                        vendingMachineCache.put(vm.getSerialNo(), vm);
                    }

                    // Evaluate void failed logic per machine
                    for (VendingMachine vm : activeMachines) {
                        String serial = vm.getSerialNo();
                        try {
                            evaluateMachine(serial, vm);
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

    /**
     * Evaluate individual machine for VOID_FAILED transactions and send individual alerts
     */
    public void evaluateMachine(String serialNo, VendingMachine vm) {
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
                    PageRequest.of(0, threshold, Sort.by(Sort.Direction.DESC, "dateTime", "id"))
                );
            } else if (tracking.getLastCheckedDatetime() != null) {
                latestTransactions = salesRepository.findLatestByMachineSerialAfterDatetime(
                    serialNo,
                    tracking.getLastCheckedDatetime(),
                    PageRequest.of(0, threshold, Sort.by(Sort.Direction.DESC, "dateTime", "id"))
                );
            } else {
                // Fallback: get latest transactions
                latestTransactions = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, threshold, Sort.by(Sort.Direction.DESC, "dateTime", "id")));
            }
        } else {
            // First time checking this machine, get the latest threshold number of transactions
            latestTransactions = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, threshold));
        }

        if (latestTransactions.isEmpty()) {
            LOGGER.debug("No new transactions found for machine {}", serialNo);
            return;
        }

        // Filter for VOID_FAILED transactions and process each individually
        LocalDateTime newestTransactionTime = null;
        Integer newestTransactionId = null;
        int voidFailedCount = 0;

        for (Sales transaction : latestTransactions) {
            String status = Optional.ofNullable(transaction.getTransactionStatus()).map(String::toUpperCase).orElse("");

            // Update newest transaction info
            if (newestTransactionTime == null || (transaction.getDateTime() != null && transaction.getDateTime().isAfter(newestTransactionTime))) {
                newestTransactionTime = transaction.getDateTime();
                newestTransactionId = transaction.getId();
            }

            if (TransactionTypes.VOID_FAILED.name().equals(status)) {
                voidFailedCount++;
                // Process individual void failed transaction
                processVoidFailedTransaction(serialNo, transaction, vm);
            }
        }

        // Update tracking record
        updateTrackingRecord(serialNo, newestTransactionId, newestTransactionTime);

        if (voidFailedCount > 0) {
            LOGGER.info("Processed {} void failed transactions for machine {}", voidFailedCount, serialNo);
        }
    }

    /**
     * Process individual VOID_FAILED transaction
     */
    private void processVoidFailedTransaction(String serialNo, Sales transaction, VendingMachine vm) {
        Integer transactionId = transaction.getId();
        LocalDateTime transactionDateTime = transaction.getDateTime();

        LOGGER.debug("Processing void failed transaction {} for machine {}", transactionId, serialNo);

        // Get AlertType for VOID_FAILED
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

        // Check if we already have an alert record for this specific transaction
        Optional<AlertHistory> existingAlertOpt = alertHistoryRepository.findLatestByTransactionIdAndAlertTypeId(transactionId, selectedAlertType.getId());

        if (existingAlertOpt.isPresent()) {
            AlertHistory existingAlert = existingAlertOpt.get();

            // Check if already alerted recently within cooldown period
            if (existingAlert.getLastSentAt() != null) {
                long cooldownMinutes = allMachinesMonitorProperties.getVoidFailed().getAlertCooldownMinutes();
                LocalDateTime cooldownThreshold = existingAlert.getLastSentAt().plusMinutes(cooldownMinutes);

                if (LocalDateTime.now().isBefore(cooldownThreshold)) {
                    LOGGER.debug("Transaction {} was recently alerted at {}, within cooldown period",
                               transactionId, existingAlert.getLastSentAt());
                    return;
                }
            }
        }

        // Send individual alert email for this transaction
        boolean emailSent = sendIndividualVoidFailedAlert(serialNo, transaction, vm);

        if (emailSent) {
            LOGGER.info("Successfully sent alert for void failed transaction {} on machine {}",
                       transactionId, serialNo);
        } else {
            LOGGER.warn("Failed to send alert for void failed transaction {} on machine {}", transactionId, serialNo);
        }
    }

    /**
     * Send individual alert email for a specific void failed transaction
     */
    private boolean sendIndividualVoidFailedAlert(String serialNo, Sales transaction, VendingMachine vm) {
        try {
            // Get or use cached AlertType
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
                return false;
            }

            // Use cached vending machine if available
            VendingMachine vendingMachine = vm != null ? vm : vendingMachineCache.get(serialNo);
            if (vendingMachine == null) {
                vendingMachine = vmRepository.findBySerialNo(serialNo).orElse(null);
                if (vendingMachine != null) {
                    vendingMachineCache.put(serialNo, vendingMachine);
                }
            }

            // Determine partner for the machine to select correct AlertEmailConfig
            Partners machinePartner = null;
            if (vendingMachine != null && vendingMachine.getMerchantId() != null) {
                try {
                    Optional<com.grabit.cba.VendingMachineAlertService.database.model.other.Merchants> merchantOpt =
                        merchantsRepository.findById(vendingMachine.getMerchantId());
                    if (merchantOpt.isPresent() && merchantOpt.get().getPartnerId() != null) {
                        Integer partnerId = merchantOpt.get().getPartnerId();
                        machinePartner = partnersCache.get(partnerId);
                        if (machinePartner == null) {
                            machinePartner = partnersRepository.findById(partnerId).orElse(null);
                            if (machinePartner != null) {
                                partnersCache.put(partnerId, machinePartner);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOGGER.debug("Could not resolve partner for machine {}: {}", serialNo, ex.getMessage());
                }
            }

            // Setup email addresses
            MailDto mailDto = new MailDto();
            String[] toAddrs = null;
            String[] ccAddrs = null;
            String[] bccAddrs = null;

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
            mailDto.setSubject(String.format("CRITICAL ALERT: Machine %s - Void Transaction Failed (ID: %d)",
                                            serialNo, transaction.getId()));
            mailDto.setFrom(senderMail);
            mailDto.setHtml(true);

            // Prepare template properties with transaction-specific details
            Map<String, Object> props = prepareTemplateProperties(serialNo, transaction, vendingMachine, machinePartner);

            // Generate email content
            Context context = new Context();
            context.setVariables(props);
            String htmlBody = templateEngine.process("Void_failed", context);
            mailDto.setBody(htmlBody);

            // Send email
            boolean emailSent = emailSender.sendEmail(mailDto, null, null);

            if (emailSent) {
                String partnerNameLog = machinePartner != null ? machinePartner.getName() : "UNKNOWN";
                String toLog = (toAddrs != null && toAddrs.length > 0) ? String.join(",", toAddrs) : "<none>";
                LOGGER.info("Individual void failed alert email sent at {} to partner {} email {} for transaction {}",
                           LocalDateTime.now(ZoneId.systemDefault()), partnerNameLog, toLog, transaction.getId());

                // Persist AlertHistory for this individual transaction
                persistAlertHistory(serialNo, vendingMachine, selectedAlertType, machinePartner, transaction);
            }

            return emailSent;

        } catch (Exception e) {
            LOGGER.error("Failed to send individual void failed alert for transaction {} on machine {}: {}",
                        transaction.getId(), serialNo, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Prepare template properties for individual transaction alert
     */
    private Map<String, Object> prepareTemplateProperties(String serialNo, Sales transaction,
                                                         VendingMachine vm, Partners machinePartner) {
        Map<String, Object> props = new HashMap<>();

        // Transaction-specific properties
        props.put("transactionId", transaction.getId());
        props.put("transactionDateTime", formatDateTime(transaction.getDateTime()));
        props.put("transactionAmount", formatAmount(transaction.getAmount()));
        props.put("tranInvoiceNo", transaction.getTranInvoiceNo());
        props.put("tranBatchNo", transaction.getTranBatchNo());

        // Machine properties
        props.put("vendingSerialNumber", serialNo);

        if (vm != null) {
            props.put("vmName", vm.getName());
            props.put("terminateCode", vm.getTerminateCode());
            props.put("productLockCount", vm.getProductLockCount());

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
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve merchant info for serial {}: {}", serialNo, ex.getMessage());
            }
            props.put("merchantName", merchantName);
            props.put("location", merchantAddress);
        } else {
            props.put("vmName", null);
            props.put("merchantName", null);
            props.put("terminateCode", null);
            props.put("productLockCount", null);
            props.put("location", null);
        }

        props.put("year", Calendar.getInstance().get(Calendar.YEAR));

        return props;
    }

    /**
     * Persist AlertHistory for individual transaction
     */
    private void persistAlertHistory(String serialNo, VendingMachine vm, AlertType selectedAlertType,
                                   Partners machinePartner, Sales transaction) {
        try {
            LocalDateTime sendTime = LocalDateTime.now(ZoneId.systemDefault());

            // Create new AlertHistory for this specific transaction
            AlertHistory history = new AlertHistory();
            history.setVendingMachineId(vm != null ? vm.getId() : null);
            history.setVendingMachineSerial(serialNo);
            history.setTransactionId(transaction.getId()); // Track the specific transaction
            history.setLastSentAt(sendTime);
            history.setAlertType(selectedAlertType);
            history.setPartnerName(machinePartner != null ? machinePartner.getName() : null);

            alertHistoryRepository.saveAndFlush(history);
            LOGGER.info("Inserted AlertHistory for individual transaction {} on machine {} at {} (history id={})",
                       transaction.getId(), serialNo, history.getLastSentAt(), history.getId());

        } catch (Exception e) {
            LOGGER.error("Failed to persist AlertHistory for transaction {} on machine {}: {}",
                        transaction.getId(), serialNo, e.getMessage(), e);
        }
    }

    /**
     * Format date time for display
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }

    /**
     * Format amount for display
     */
    private String formatAmount(Integer amount) {
        if (amount == null) return null;
        return String.format("%.2f", amount / 100.0); // Assuming amount is in cents
    }

    /**
     * Update machine tracking record
     */
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

    // Legacy method for backward compatibility - now calls the new overloaded version
    public void evaluateMachine(String serialNo) {
        VendingMachine vm = vendingMachineCache.get(serialNo);
        if (vm == null) {
            vm = vmRepository.findBySerialNo(serialNo).orElse(null);
            if (vm != null) {
                vendingMachineCache.put(serialNo, vm);
            }
        }
        evaluateMachine(serialNo, vm);
    }
}
