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
import com.grabit.cba.VendingMachineAlertService.util.EmailServiceUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class HeartbeatMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(HeartbeatMonitorService.class);
    private static final String OFFLINE_ALERT_CODE = "OFFLINE_VM";

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


    public HeartbeatMonitorService(AllMachinesMonitorProperties allMachinesMonitorProperties,
                                   SalesRepository salesRepository,
                                   VMRepository vmRepository,
                                   MerchantsRepository merchantsRepository,
                                   PartnersRepository partnersRepository,
                                   AlertTypeRepository alertTypeRepository,
                                   AlertHistoryRepository alertHistoryRepository,
                                   AlertEmailConfigRepository alertEmailConfigRepository,
                                   EmailSender emailSender,
                                   TemplateEngine templateEngine) {
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
        LOGGER.info("HeartbeatMonitorService initialized. enabled={}, cron={}, offlineMachineThresholdMinutes={}, alertCooldownMinutes={}",
                allMachinesMonitorProperties.getHeartbeatMonitor().isEnabled(),
                allMachinesMonitorProperties.getHeartbeatMonitor().getCron(),
                allMachinesMonitorProperties.getHeartbeatMonitor().getOfflineMachineThresholdMinutes(),
                allMachinesMonitorProperties.getHeartbeatMonitor().getAlertCooldownMinutes());
    }

    public void evaluateAllMachines() {
        if (!allMachinesMonitorProperties.getHeartbeatMonitor().isEnabled()) {
            LOGGER.info("Vending Machine Heartbeat monitor disabled; skipping evaluation");
            return;
        }

        LOGGER.info("Vending Machine Heartbeat monitor evaluation start for time {}", LocalDateTime.now());

        // Only monitors machines with status=0 (offline) for extended offline duration
        // AlertHistory is used to persist notification state across service restarts

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

                List<VendingMachine> offlineMachines = vmRepository.findOfflineByMerchantIds(merchantIds);
                if (offlineMachines == null || offlineMachines.isEmpty()) {
                    LOGGER.debug("Skipping partner {} (id={}) due to no offline vending machines", partner.getName(), partnerId);
                    continue;
                }

                // Check each offline vending machine's status duration
                for (VendingMachine vm : offlineMachines) {
                    String serialNo = vm.getSerialNo();
                    try {
                        evaluateMachineHeartbeat(vm, partnersCache);
                    } catch (Exception e) {
                        LOGGER.error("Error evaluating machine {} heartbeat for partner {}: {}", serialNo, partner.getName(), e.getMessage(), e);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Error while evaluating partner {}: {}", partner.getName(), ex.getMessage(), ex);
            }
        }

        LOGGER.info("Heartbeat monitor evaluation end for time {}", LocalDateTime.now());
    }

    private void evaluateMachineHeartbeat(VendingMachine vm, Map<Integer, Partners> partnersCache) {
        String serialNo = vm.getSerialNo();
        Integer machineStatus = vm.getStatus();

        // Only process offline machines (status = 0)
        if (machineStatus == null || machineStatus != 0) {
            LOGGER.debug("Machine {} has status {} (not offline=0); skipping heartbeat evaluation", serialNo, machineStatus);
            return;
        }

        // Get latest transaction to determine how long the machine has been offline
        List<Sales> latestTransactions = salesRepository.findLatestByMachineSerial(serialNo, PageRequest.of(0, 1));

        LocalDateTime lastActivity = null;
        if (!latestTransactions.isEmpty()) {
            Sales lastTransaction = latestTransactions.get(0);
            lastActivity = lastTransaction.getDateTime();
        }

        LocalDateTime now = LocalDateTime.now();
        boolean shouldAlert;
        long minutesSinceActivity;

        if (lastActivity != null) {
            Duration offlineDuration = Duration.between(lastActivity, now);
            minutesSinceActivity = offlineDuration.toMinutes();
            long thresholdMinutes = allMachinesMonitorProperties.getHeartbeatMonitor().getOfflineMachineThresholdMinutes();
            shouldAlert = minutesSinceActivity >= thresholdMinutes;
        } else {
            // No transactions found - machine has been offline for unknown duration, alert
            shouldAlert = true;
            minutesSinceActivity = Long.MAX_VALUE;
        }

        if (shouldAlert) {
            LOGGER.info("Offline machine {} has been offline for {} minutes (threshold: {} minutes); triggering alert",
                       serialNo, minutesSinceActivity == Long.MAX_VALUE ? "unknown" : minutesSinceActivity,
                       allMachinesMonitorProperties.getHeartbeatMonitor().getOfflineMachineThresholdMinutes());
            handleOfflineMachine(vm, lastActivity, minutesSinceActivity, partnersCache);
        } else {
            LOGGER.debug("Offline machine {} has been offline for {} minutes (below threshold: {} minutes); no alert needed",
                        serialNo, minutesSinceActivity,
                        allMachinesMonitorProperties.getHeartbeatMonitor().getOfflineMachineThresholdMinutes());
        }
    }

    private void handleOfflineMachine(VendingMachine vm, LocalDateTime lastActivityTime, long minutesSinceActivity, Map<Integer, Partners> partnersCache) {
        String serialNo = vm.getSerialNo();
        Integer vmId = vm.getId();

        // Get alert type for offline machines
        AlertType alertType = alertTypeRepository.findByCode(OFFLINE_ALERT_CODE).orElse(null);
        if (alertType == null) {
            LOGGER.warn("AlertType '{}' not found; skipping alert for machine {}", OFFLINE_ALERT_CODE, serialNo);
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
                if (lastActivityTime != null && !lastSent.isBefore(lastActivityTime)) {
                    LOGGER.info("Suppressing alert because previous AlertHistory (id={}) was sent at {} which is >= lastActivityTime {}",
                            lastAlert.get().getId(), lastSent, lastActivityTime);
                    return;
                }

                long cooldownMinutes = allMachinesMonitorProperties.getHeartbeatMonitor().getAlertCooldownMinutes();
                LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                Duration elapsed = Duration.between(lastSent, now);
                if (elapsed.toMinutes() < cooldownMinutes) {
                    LOGGER.info("Suppressing duplicate alert for offline machine {} (sent {} minutes ago, cooldown {} minutes)",
                            serialNo, elapsed.toMinutes(), cooldownMinutes);
                    return;
                }
            }
        }

        // Send offline machine alert
        sendOfflineMachineAlert(vm, lastActivityTime, minutesSinceActivity, alertType, partnersCache);
    }

    private void sendOfflineMachineAlert(VendingMachine vm, LocalDateTime lastActivityTime, long minutesSinceActivity, AlertType alertType, Map<Integer, Partners> partnersCache) {
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
        mailDto.setSubject(String.format("ALERT: Vending Machine %s - Extended Offline Status", serialNo));
        mailDto.setFrom(senderMail);
        mailDto.setHtml(true);

        // Prepare template properties for offline machine
        Map<String, Object> props = new HashMap<>();
        props.put("vendingSerialNumber", serialNo);
        props.put("vmName", vm.getName());
        props.put("machineStatus", vm.getStatus());
        props.put("machineStatusDescription", "Offline");
        props.put("alertReason", "Extended Offline Status");

        // Format last activity time
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String lastActivityFormatted = lastActivityTime != null ? lastActivityTime.atZone(ZoneId.systemDefault()).format(dtf) : "No transactions found";
        props.put("offlineSince", lastActivityFormatted);

        // Format duration since last activity (how long offline)
        String offlineDuration;
        if (minutesSinceActivity == Long.MAX_VALUE) {
            offlineDuration = "Unknown duration";
        } else {
            long hours = minutesSinceActivity / 60;
            long remainingMinutes = minutesSinceActivity % 60;
            if (hours > 0) {
                offlineDuration = String.format("%d hours %d minutes", hours, remainingMinutes);
            } else {
                offlineDuration = String.format("%d minutes", remainingMinutes);
            }
        }
        props.put("offlineDuration", offlineDuration);

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
            String htmlBody = templateEngine.process("Offline_vm", context);
            mailDto.setBody(htmlBody);

            boolean emailSent = emailSender.sendEmail(mailDto, null, null);
            if (emailSent) {
                String partnerNameLog = machinePartner != null ? machinePartner.getName() : "UNKNOWN";
                String toLog = (toAddrs != null && toAddrs.length > 0) ? String.join(",", toAddrs) : "<none>";
                LOGGER.info("Offline Machine Alert email sent at {} to partner {} email {}", LocalDateTime.now(ZoneId.systemDefault()), partnerNameLog, toLog);
                LOGGER.info("Offline Machine Alert sent for machine {} (offline for {} minutes)",
                           serialNo, minutesSinceActivity == Long.MAX_VALUE ? "unknown" : minutesSinceActivity);

                // Persist/update AlertHistory ONLY if email was sent successfully
                LocalDateTime sendTime = LocalDateTime.now(ZoneId.systemDefault());
                if (alertHistoryRepository.findLatestByVendingMachineIdAndAlertTypeId(vmId, alertType.getId()).isPresent()) {
                    AlertHistory existing = alertHistoryRepository.findLatestByVendingMachineIdAndAlertTypeId(vmId, alertType.getId()).get();
                    existing.setVendingMachineId(vmId);
                    existing.setVendingMachineSerial(serialNo);
                    existing.setLastSentAt(sendTime);
                    existing.setAlertType(alertType);
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
                    history.setAlertType(alertType);
                    history.setPartnerName(machinePartner != null ? machinePartner.getName() : null);
                    alertHistoryRepository.saveAndFlush(history);
                    LOGGER.info("Inserted AlertHistory for offline machine {} at {} (history id={})", serialNo, history.getLastSentAt(), history.getId());
                }
            } else {
                LOGGER.warn("Offline Machine Alert email send failed for machine {}; skipping AlertHistory persist", serialNo);
            }
        } catch (Exception e) {
            LOGGER.error("Error sending offline machine alert for machine {}: {}", serialNo, e.getMessage(), e);
        }
    }

}
