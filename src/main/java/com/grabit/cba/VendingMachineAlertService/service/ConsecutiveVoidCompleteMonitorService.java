package com.grabit.cba.VendingMachineAlertService.service;

import com.grabit.cba.VendingMachineAlertService.config.AllMachinesMonitorProperties;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertEmailConfig;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertHistory;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertType;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Merchants;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Partners;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Sales;
import com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine;
import com.grabit.cba.VendingMachineAlertService.database.repository.*;
import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;
import com.grabit.cba.VendingMachineAlertService.enums.TransactionTypes;
import com.grabit.cba.VendingMachineAlertService.util.EmailServiceUtils;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class ConsecutiveVoidCompleteMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsecutiveVoidCompleteMonitorService.class);
    private static final String CONSECUTIVE_VOID_ALERT_CODE = "VOID_COMPLETED";

    private final AllMachinesMonitorProperties allMachinesMonitorProperties;
    private final SalesRepository salesRepository;
    private final VMRepository vmRepository;
    private final MerchantsRepository merchantsRepository;
    private final PartnersRepository partnersRepository;
    private final AlertTypeRepository alertTypeRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertEmailConfigRepository alertEmailConfigRepository;
    private final EmailSender emailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String senderMail;

    @Value("${grabit.logo:}")
    private String grabitLogo;

    public ConsecutiveVoidCompleteMonitorService(AllMachinesMonitorProperties allMachinesMonitorProperties, SalesRepository salesRepository, VMRepository vmRepository,
                                               MerchantsRepository merchantsRepository, PartnersRepository partnersRepository, AlertTypeRepository alertTypeRepository,
                                               AlertHistoryRepository alertHistoryRepository,
                                               AlertEmailConfigRepository alertEmailConfigRepository, EmailSender emailSender, TemplateEngine templateEngine) {
        this.allMachinesMonitorProperties = allMachinesMonitorProperties;
        this.salesRepository = salesRepository;
        this.vmRepository = vmRepository;
        this.merchantsRepository = merchantsRepository;
        this.partnersRepository = partnersRepository;
        this.alertTypeRepository = alertTypeRepository;
        this.alertHistoryRepository = alertHistoryRepository;
        this.alertEmailConfigRepository = alertEmailConfigRepository;
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("ConsecutiveVoidCompleteMonitorService initialized. enabled={}, cron={}, transactionWindowSize={}, consecutiveVoidThreshold={}, voidPercentageThreshold={}%, alertCooldownMinutes={}",
                allMachinesMonitorProperties.getConsecutiveVoidComplete().isConsecutiveVoidCompleteEnabled(),
                allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteCron(),
                allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteTransactionWindowSize(),
                allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteConsecutiveVoidThreshold(),
                allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteVoidPercentageThreshold(),
                allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteAlertCooldownMinutes());
    }

    public void evaluateAllMachines() {
        if (!allMachinesMonitorProperties.getConsecutiveVoidComplete().isConsecutiveVoidCompleteEnabled()) {
            LOGGER.info("Consecutive void complete monitor disabled; skipping evaluation");
            return;
        }

        LOGGER.info("Consecutive void complete monitor evaluation start for time {}", LocalDateTime.now());

        // Load all partners once and reuse
        List<Partners> allPartners = partnersRepository.findAll();
        if (allPartners.isEmpty()) {
            LOGGER.warn("No partners found; skipping evaluation");
            return;
        }

        // Cache partners for lookups
        Map<Integer, Partners> partnersCache = new HashMap<>();
        for (Partners partner : allPartners) {
            partnersCache.put(partner.getId(), partner);
        }

        for (Partners partner : allPartners) {
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

                // Check each vending machine for consecutive void complete transactions
                for (VendingMachine vm : activeMachines) {
                    String serialNo = vm.getSerialNo();
                    try {
                        evaluateMachineVoidPattern(vm, partnersCache);
                    } catch (Exception e) {
                        LOGGER.error("Error evaluating machine {} void pattern for partner {}: {}", serialNo, partner.getName(), e.getMessage(), e);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Error while evaluating partner {}: {}", partner.getName(), ex.getMessage(), ex);
            }
        }

        LOGGER.info("Consecutive void complete monitor evaluation end for time {}", LocalDateTime.now());
    }

    private void evaluateMachineVoidPattern(VendingMachine vm, Map<Integer, Partners> partnersCache) {
        String serialNo = vm.getSerialNo();
        int windowSize = allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteTransactionWindowSize();
        // Get recent transactions for analysis
        List<Sales> recentTransactions = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, windowSize, Sort.by(Sort.Direction.DESC, "dateTime", "id")));
        if (recentTransactions == null || recentTransactions.isEmpty()) {
            LOGGER.debug("No transactions found for machine {}; skipping void pattern evaluation", serialNo);
            return;
        }

        // Analyze transaction patterns
        VoidAnalysisResult analysis = analyzeVoidPattern(recentTransactions);

        boolean shouldAlert = false;
        String alertReason = null;

        // Check if consecutive void threshold is exceeded
        if (analysis.getMaxConsecutiveVoids() >= allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteConsecutiveVoidThreshold()) {
            shouldAlert = true;
            alertReason = String.format("%d consecutive VOID_COMPLETE transactions", analysis.getMaxConsecutiveVoids());
        }

        // Check if void percentage threshold is exceeded
        if (analysis.getVoidPercentage() > allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteVoidPercentageThreshold()) {
            if (shouldAlert) {
                alertReason += String.format(" and %.1f%% void rate", analysis.getVoidPercentage());
            } else {
                shouldAlert = true;
                alertReason = String.format("%.1f%% VOID_COMPLETE rate (threshold: %.1f%%)",
                    analysis.getVoidPercentage(),
                    allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteVoidPercentageThreshold());
            }
        }

        if (shouldAlert) {
            LOGGER.info("Machine {} triggers void complete alert: {}", serialNo, alertReason);
            handleVoidCompleteAlert(vm, analysis, alertReason, partnersCache);
        } else {
            LOGGER.debug("Machine {} void pattern within thresholds: {} consecutive, {}% void rate",
                serialNo, analysis.getMaxConsecutiveVoids(), String.format("%.1f", analysis.getVoidPercentage()));
        }
    }

    private VoidAnalysisResult analyzeVoidPattern(List<Sales> transactions) {
        int totalTransactions = transactions.size();
        int voidCount = 0;
        int maxConsecutiveVoids = 0;
        int currentConsecutiveVoids = 0;

        for (Sales transaction : transactions) {
            String status = Optional.ofNullable(transaction.getTransactionStatus()).map(String::toUpperCase).orElse("");

            if (TransactionTypes.VOID_COMPLETED.name().equals(status)) {
                voidCount++;
                currentConsecutiveVoids++;
                maxConsecutiveVoids = Math.max(maxConsecutiveVoids, currentConsecutiveVoids);
            } else {
                currentConsecutiveVoids = 0;
            }
        }

        double voidPercentage = totalTransactions > 0 ? (voidCount * 100.0 / totalTransactions) : 0.0;

        return new VoidAnalysisResult(totalTransactions, voidCount, maxConsecutiveVoids, voidPercentage);
    }

    private void handleVoidCompleteAlert(VendingMachine vm, VoidAnalysisResult analysis, String alertReason, Map<Integer, Partners> partnersCache) {
        String serialNo = vm.getSerialNo();
        Integer vmId = vm.getId();

        // Get alert type
        AlertType alertType = alertTypeRepository.findByCode(CONSECUTIVE_VOID_ALERT_CODE).orElse(null);
        if (alertType == null) {
            LOGGER.warn("AlertType '{}' not found; skipping alert for machine {}", CONSECUTIVE_VOID_ALERT_CODE, serialNo);
            return;
        }

        // Check alert history cooldown
        Optional<AlertHistory> lastAlert = alertHistoryRepository.findLatestByVendingMachineIdAndAlertTypeId(vmId, alertType.getId());
        if (lastAlert.isEmpty()) {
            lastAlert = alertHistoryRepository.findLatestByVendingMachineSerialAndAlertTypeId(serialNo, alertType.getId());
        }

        if (lastAlert.isPresent()) {
            LocalDateTime lastSent = lastAlert.get().getLastSentAt();
            if (lastSent != null) {
                long cooldownMinutes = allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteAlertCooldownMinutes();
                LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                Duration elapsed = Duration.between(lastSent, now);
                if (elapsed.toMinutes() < cooldownMinutes) {
                    LOGGER.info("Suppressing duplicate void complete alert for machine {} (sent {} minutes ago, cooldown {} minutes)",
                            serialNo, elapsed.toMinutes(), cooldownMinutes);
                    return;
                }
            }
        }

        // Send void complete alert
        sendVoidCompleteAlert(vm, analysis, alertReason, alertType, partnersCache);
    }

    private void sendVoidCompleteAlert(VendingMachine vm, VoidAnalysisResult analysis, String alertReason, AlertType alertType, Map<Integer, Partners> partnersCache) {
        String serialNo = vm.getSerialNo();
        Integer vmId = vm.getId();

        // Determine partner for email configuration
        Partners machinePartner = null;
        if (vm.getMerchantId() != null) {
            try {
                Optional<Merchants> merchantOpt = merchantsRepository.findById(vm.getMerchantId());
                if (merchantOpt.isPresent() && merchantOpt.get().getPartnerId() != null) {
                    Integer partnerId = merchantOpt.get().getPartnerId();
                    machinePartner = partnersCache.get(partnerId);
                }
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve partner for machine {}: {}", serialNo, ex.getMessage());
            }
        }

        // Get email configuration
        MailDto mailDto = new MailDto();
        String[] toAddrs = null;
        String[] ccAddrs = null;
        String[] bccAddrs = null;

        Optional<AlertEmailConfig> optCfg = alertEmailConfigRepository.findFirstByAlertTypeAndPartners(alertType, machinePartner);
        if (optCfg.isPresent()) {
            AlertEmailConfig cfg = optCfg.get();
            toAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getTo());
            ccAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getCc());
            bccAddrs = EmailServiceUtils.commaSeparatedStringToArray(cfg.getBcc());
        }

        mailDto.setTo(toAddrs);
        mailDto.setCc(ccAddrs);
        mailDto.setBcc(bccAddrs);
        mailDto.setSubject(String.format("CRITICAL ALERT: Machine %s - Consecutive Void Complete Transactions", serialNo));
        mailDto.setFrom(senderMail);
        mailDto.setHtml(true);

        // Prepare template properties
        Map<String, Object> props = new HashMap<>();
        props.put("vendingSerialNumber", serialNo);
        props.put("vmName", vm.getName());
        props.put("alertReason", alertReason);
        props.put("totalTransactions", analysis.getTotalTransactions());
        props.put("voidCount", analysis.getVoidCount());
        props.put("maxConsecutiveVoids", analysis.getMaxConsecutiveVoids());
        props.put("consecutiveVoidCount", analysis.getMaxConsecutiveVoids()); // For template consistency
        props.put("voidPercentage", String.format("%.1f", analysis.getVoidPercentage()));
        props.put("thresholdConsecutive", allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteConsecutiveVoidThreshold());
        props.put("thresholdPercentage", String.format("%.1f", allMachinesMonitorProperties.getConsecutiveVoidComplete().getConsecutiveVoidCompleteVoidPercentageThreshold()));

        // Add VM specific fields
        props.put("terminateCode", vm.getTerminateCode() != null ? vm.getTerminateCode() : 0);
        props.put("productLockCount", vm.getProductLockCount() != null ? vm.getProductLockCount() : 0);

        // Get merchant information
        String merchantName = null;
        String merchantAddress = null;
        if (vm.getMerchantId() != null) {
            try {
                Optional<Merchants> merchantOpt = merchantsRepository.findById(vm.getMerchantId());
                if (merchantOpt.isPresent()) {
                    merchantName = merchantOpt.get().getName();
                    merchantAddress = merchantOpt.get().getAddress();
                }
            } catch (Exception ex) {
                LOGGER.debug("Could not resolve merchant info for serial {}: {}", serialNo, ex.getMessage());
            }
        }
        props.put("merchantName", merchantName);
        props.put("merchantAddress", merchantAddress);
        props.put("location", merchantAddress);
        props.put("year", Calendar.getInstance().get(Calendar.YEAR));

        try {
            Context context = new Context();
            context.setVariables(props);
            String htmlBody = templateEngine.process("Consecutive_void_complete", context);
            mailDto.setBody(htmlBody);

            boolean emailSent = emailSender.sendEmail(mailDto, null, null);
            if (emailSent) {
                String partnerNameLog = machinePartner != null ? machinePartner.getName() : "UNKNOWN";
                String toLog = (toAddrs != null && toAddrs.length > 0) ? String.join(",", toAddrs) : "<none>";
                LOGGER.info("Consecutive void complete alert email sent at {} to partner {} email {}",
                           LocalDateTime.now(ZoneId.systemDefault()), partnerNameLog, toLog);
                LOGGER.info("Consecutive void complete alert sent for machine {} - {}", serialNo, alertReason);

                // Persist AlertHistory ONLY if email was sent successfully - always create new record
                LocalDateTime sendTime = LocalDateTime.now(ZoneId.systemDefault());
                AlertHistory history = new AlertHistory();
                history.setVendingMachineId(vmId);
                history.setVendingMachineSerial(serialNo);
                if (history.getVendingMachineId() == null) {
                    LOGGER.warn("Could not resolve vendingMachineId for serial {}; AlertHistory will store null", serialNo);
                }
                history.setLastSentAt(sendTime);
                history.setAlertType(alertType);
                history.setPartnerName(machinePartner != null ? machinePartner.getName() : null);
                alertHistoryRepository.saveAndFlush(history);
                LOGGER.info("Inserted new AlertHistory for consecutive void complete machine {} at {} (history id={})", serialNo, history.getLastSentAt(), history.getId());
            } else {
                LOGGER.warn("Consecutive void complete alert email send failed for machine {}; skipping AlertHistory persist", serialNo);
            }
        } catch (Exception e) {
            LOGGER.error("Error sending consecutive void complete alert for machine {}: {}", serialNo, e.getMessage(), e);
        }
    }

    // Inner class to hold void analysis results
    @Getter
    public static class VoidAnalysisResult {
        private final int totalTransactions;
        private final int voidCount;
        private final int maxConsecutiveVoids;
        private final double voidPercentage;

        public VoidAnalysisResult(int totalTransactions, int voidCount, int maxConsecutiveVoids, double voidPercentage) {
            this.totalTransactions = totalTransactions;
            this.voidCount = voidCount;
            this.maxConsecutiveVoids = maxConsecutiveVoids;
            this.voidPercentage = voidPercentage;
        }
    }
}
