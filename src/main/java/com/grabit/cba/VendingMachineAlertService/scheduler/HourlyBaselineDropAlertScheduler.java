package com.grabit.cba.VendingMachineAlertService.scheduler;

import com.grabit.cba.VendingMachineAlertService.config.AllMachinesMonitorProperties;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertHourlySalesBaseline;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertType;
import com.grabit.cba.VendingMachineAlertService.database.model.AlertHistory;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Sales;
import com.grabit.cba.VendingMachineAlertService.database.model.other.VendingMachine;
import com.grabit.cba.VendingMachineAlertService.database.model.other.Partners;
import com.grabit.cba.VendingMachineAlertService.database.repository.*;
import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;
import com.grabit.cba.VendingMachineAlertService.service.EmailSender;
import com.grabit.cba.VendingMachineAlertService.util.EmailServiceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@EnableScheduling
public class HourlyBaselineDropAlertScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(HourlyBaselineDropAlertScheduler.class);

    @Value("${spring.mail.username}")
    private String senderMail;

    private final AllMachinesMonitorProperties monitorProperties;
    private final VMRepository vmRepository;
    private final SalesRepository salesRepository;
    private final AlertHourlySalesBaselineRepository baselineRepository;
    private final AlertTypeRepository alertTypeRepository;
    private final AlertHistoryRepository alertHistoryRepository;
    private final AlertEmailConfigRepository alertEmailConfigRepository;
    private final EmailSender emailSender;
    private final TemplateEngine templateEngine;
    private final PartnersRepository partnersRepository;
    private final MerchantsRepository merchantsRepository;

    // DTO for email rows with JavaBean getters for Thymeleaf
    private static class EmailRow {
        private String serial;
        private String vmName;
        private double baselineCompleted;
        private long nowCompleted;
        private long nowFailed;
        private long nowVoidCompleted;
        private long nowVoidFailed;
        public String getSerial() { return serial; }
        public void setSerial(String serial) { this.serial = serial; }
        public String getVmName() { return vmName; }
        public void setVmName(String vmName) { this.vmName = vmName; }
        public double getBaselineCompleted() { return baselineCompleted; }
        public void setBaselineCompleted(double baselineCompleted) { this.baselineCompleted = baselineCompleted; }
        public long getNowCompleted() { return nowCompleted; }
        public void setNowCompleted(long nowCompleted) { this.nowCompleted = nowCompleted; }
        public long getNowFailed() { return nowFailed; }
        public void setNowFailed(long nowFailed) { this.nowFailed = nowFailed; }
        public long getNowVoidCompleted() { return nowVoidCompleted; }
        public void setNowVoidCompleted(long nowVoidCompleted) { this.nowVoidCompleted = nowVoidCompleted; }
        public long getNowVoidFailed() { return nowVoidFailed; }
        public void setNowVoidFailed(long nowVoidFailed) { this.nowVoidFailed = nowVoidFailed; }
    }

    public HourlyBaselineDropAlertScheduler(AllMachinesMonitorProperties monitorProperties, VMRepository vmRepository,
                                            SalesRepository salesRepository, AlertHourlySalesBaselineRepository baselineRepository,
                                            AlertTypeRepository alertTypeRepository, AlertHistoryRepository alertHistoryRepository,
                                            AlertEmailConfigRepository alertEmailConfigRepository, EmailSender emailSender,
                                            TemplateEngine templateEngine, PartnersRepository partnersRepository, MerchantsRepository merchantsRepository) {
        this.monitorProperties = monitorProperties;
        this.vmRepository = vmRepository;
        this.salesRepository = salesRepository;
        this.baselineRepository = baselineRepository;
        this.alertTypeRepository = alertTypeRepository;
        this.alertHistoryRepository = alertHistoryRepository;
        this.alertEmailConfigRepository = alertEmailConfigRepository;
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.partnersRepository = partnersRepository;
        this.merchantsRepository = merchantsRepository;
    }

//    @Scheduled(cron = "${monitor.hourlyBaselineAlertCron:0 5 * * * *}")
//    @Scheduled(cron = "0 * * * * *")
    public void evaluateHourlyDrops() {
        if (!monitorProperties.isHourlyBaselineAlertEnabled()) {
            LOGGER.info("Hourly baseline drop alert disabled; skipping");
            return;
        }
        final LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
        final int currentHour = now.getHour();
        final LocalDateTime windowStart = now.withMinute(0).withSecond(0).withNano(0);
        final LocalDateTime windowEnd = windowStart.plusHours(1);
        final LocalDateTime endExclusive = windowEnd.minusNanos(1);
        final LocalDateTime prevWindowStart = windowStart.minusHours(1);
        final LocalDateTime prevWindowEndExclusive = windowStart.minusNanos(1);
        final int prevHourOfDay = (currentHour + 23) % 24; // previous hour within 0-23
        final double threshold = monitorProperties.getBaselineDropThresholdPercent();
        final int requiredConsecutive = Math.max(1, monitorProperties.getBaselineConsecutiveHoursRequired());

        LOGGER.info("Hourly baseline drop evaluation start for hour {} ({} - {})", currentHour, windowStart, windowEnd);

        // Fetch all partners and process per-partner
        List<Partners> partners = partnersRepository.findAll();
        if (partners == null || partners.isEmpty()) {
            LOGGER.warn("No partners found; skipping hourly baseline drop evaluation");
            return;
        }

        // Cooldown and AlertType lookup once
        String alertCode = "HOURLY_SALES_BASELINE_DROP";
        AlertType alertType = alertTypeRepository.findByCode(alertCode).orElse(null);
        if (alertType == null) {
            LOGGER.warn("AlertType '{}' not found; cannot send baseline drop alerts (DB-backed cooldown required)", alertCode);
            return;
        }

        for (Partners partner : partners) {
            Integer partnerId = partner.getId();
            String partnerName = partner.getName();
            if (partnerId == null) {
                LOGGER.warn("Skipping partner with null id (name={})", partnerName);
                continue;
            }

            // Resolve merchants for this partner
            List<Integer> merchantIds = merchantsRepository.findIdsByPartnerIds(Collections.singletonList(partnerId));
            if (merchantIds == null || merchantIds.isEmpty()) {
                LOGGER.info("Partner={} (id={}) has no merchants; skipping", partnerName, partnerId);
                continue;
            }

            // Resolve active vending machines for merchants
            List<VendingMachine> machines = vmRepository.findActiveByMerchantIds(merchantIds);
            LOGGER.info("Partner={} (id={}) resolved merchantIds={} vmsFound={}", partnerName, partnerId, merchantIds, machines == null ? 0 : machines.size());
            if (machines == null || machines.isEmpty()) {
                LOGGER.info("Partner={} has no active VMs; skipping", partnerName);
                continue;
            }

            // Collect anomalies for this partner
            List<EmailRow> rows = new ArrayList<>();

            for (VendingMachine vm : machines) {
                Integer vmId = vm.getId();
                if (vmId == null) continue;

                // Baseline for current hour
                AlertHourlySalesBaseline.Id curId = new AlertHourlySalesBaseline.Id(vmId, currentHour);
                Optional<AlertHourlySalesBaseline> curBaselineOpt = baselineRepository.findById(curId);
                if (curBaselineOpt.isEmpty()) continue; // no baseline
                AlertHourlySalesBaseline curBaseline = curBaselineOpt.get();

                // Baseline eligibility: avgSalesCompleted must be >= 1.0
                Double baselineCompleted = curBaseline.getAvgSalesCompleted();
                if (baselineCompleted == null || baselineCompleted < 1.0) continue;

                // Current hour sales window counts
                List<Sales> lastHour = salesRepository.findByMachineSerialAndDateBetween(vm.getSerialNo(), windowStart, endExclusive);
                long nowCompleted = lastHour.stream().filter(s -> "SALE_COMPLETED".equalsIgnoreCase(String.valueOf(s.getTransactionStatus()))).count();
                long nowFailed = lastHour.stream().filter(s -> "SALE_FAILED".equalsIgnoreCase(String.valueOf(s.getTransactionStatus()))).count();
                long nowVoidCompleted = lastHour.stream().filter(s -> "VOID_COMPLETE".equalsIgnoreCase(String.valueOf(s.getTransactionStatus()))).count();
                long nowVoidFailed = lastHour.stream().filter(s -> "VOID_FAILED".equalsIgnoreCase(String.valueOf(s.getTransactionStatus()))).count();

                boolean currentDrop = nowCompleted < baselineCompleted * threshold;
                if (!currentDrop) {
                    continue; // no anomaly in current hour
                }

//            boolean consecutiveSatisfied = requiredConsecutive <= 1; // (if 1, current drop is enough)
                boolean consecutiveSatisfied = true;
                if (!consecutiveSatisfied) {
                    // Evaluate previous hour drop using its baseline and sales window
                    AlertHourlySalesBaseline.Id prevId = new AlertHourlySalesBaseline.Id(vmId, prevHourOfDay);
                    Optional<AlertHourlySalesBaseline> prevBaselineOpt = baselineRepository.findById(prevId);
                    if (prevBaselineOpt.isPresent()) {
                        AlertHourlySalesBaseline prevBaseline = prevBaselineOpt.get();
                        Double prevBaselineCompleted = prevBaseline.getAvgSalesCompleted();
                        if (prevBaselineCompleted != null && prevBaselineCompleted >= 1.0) {
                            List<Sales> prevHourSales = salesRepository.findByMachineSerialAndDateBetween(vm.getSerialNo(), prevWindowStart, prevWindowEndExclusive);
                            long prevCompleted = prevHourSales.stream().filter(s -> "SALE_COMPLETED".equalsIgnoreCase(String.valueOf(s.getTransactionStatus()))).count();
                            consecutiveSatisfied = prevCompleted < prevBaselineCompleted * threshold;
                        }
                    }
                }

                if (consecutiveSatisfied) {
                    EmailRow r = new EmailRow();
                    r.setSerial(vm.getSerialNo());
                    r.setVmName(vm.getName());
//                r.setBaselineCompleted(baselineCompleted);
                    r.setNowCompleted(nowCompleted);
                    r.setNowFailed(nowFailed);
                    r.setNowVoidCompleted(nowVoidCompleted);
                    r.setNowVoidFailed(nowVoidFailed);
                    rows.add(r);
                }
            }

            if (rows.isEmpty()) {
                LOGGER.info("Partner={} has no SALE_COMPLETED baseline-drop anomalies for hour {}", partnerName, currentHour);
                continue;
            }

            // Filter rows by cooldown per machine via AlertHistory
            List<EmailRow> rowsToAlert = new ArrayList<>();
            for (EmailRow r : rows) {
                Optional<VendingMachine> vmOpt = vmRepository.findBySerialNo(r.getSerial());
                Integer vmId = vmOpt.map(VendingMachine::getId).orElse(null);
                Optional<AlertHistory> lastHist = Optional.empty();
                if (vmId != null)
                    lastHist = alertHistoryRepository.findLatestByVendingMachineAndAlertType(vmId, alertType);
                if (lastHist.isEmpty())
                    lastHist = alertHistoryRepository.findLatestByVendingMachineSerialAndAlertType(r.getSerial(), alertType);

                boolean withinCooldown = false;
                if (lastHist.isPresent() && lastHist.get().getLastSentAt() != null) {
                    long minutes = java.time.Duration.between(lastHist.get().getLastSentAt(), now).toMinutes();
                    withinCooldown = minutes < monitorProperties.getAlertCooldownMinutes();
                }
                if (!withinCooldown) {
                    rowsToAlert.add(r);
                } else {
                    LOGGER.info("Partner={} skipping alert for {} due to cooldown (last sent at {})", partnerName, r.getSerial(), lastHist.get().getLastSentAt());
                }
            }

            if (rowsToAlert.isEmpty()) {
                LOGGER.info("Partner={} anomalies suppressed by cooldown window", partnerName);
                continue;
            }

            // Build and send email summary for this partner
            try {
                Context context = new Context();
                Map<String, Object> vars = new HashMap<>();
                vars.put("partner", partnerName);
                vars.put("hourOfDay", currentHour);
                vars.put("windowStart", windowStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                vars.put("windowEnd", windowEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                vars.put("thresholdPercent", String.format(Locale.US, "%.0f%%", threshold * 100));
                vars.put("consecutiveHours", requiredConsecutive);
                vars.put("rows", rowsToAlert);
                vars.put("count", rowsToAlert.size());
                vars.put("now", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                context.setVariables(vars);

                String html = templateEngine.process("Hourly_baseline_drop", context);

                MailDto mail = new MailDto();
                mail.setHtml(true);
                mail.setFrom(senderMail);
                mail.setSubject(String.format("ALERT: Hourly SALE_COMPLETED drop vs baseline (hour %02d)", currentHour));
                mail.setBody(html);

                // Recipients: REQUIRE partner-specific AlertEmailConfig by alert type; do NOT fallback to global
                alertEmailConfigRepository.findFirstByAlertTypeAndPartners(alertType, partner).ifPresent(cfg -> {
                    String[] t = EmailServiceUtils.commaSeparatedStringToArray(cfg.getTo());
                    String[] c = EmailServiceUtils.commaSeparatedStringToArray(cfg.getCc());
                    String[] b = EmailServiceUtils.commaSeparatedStringToArray(cfg.getBcc());
                    if (t.length > 0) mail.setTo(t);
                    if (c.length > 0) mail.setCc(c);
                    if (b.length > 0) mail.setBcc(b);
                });

                if (mail.getTo() == null || mail.getTo().length == 0) {
                    LOGGER.warn("Partner={} no email recipients configured for alertType={}; skipping alert send", partnerName, alertCode);
                    continue; // ❗ do not send, do not save history
                }

                boolean sent = false;
                try {
                    sent = emailSender.sendEmail(mail, null, null);
                } catch (Exception ex) {
                    LOGGER.error("Partner={} email send failed: {}", partnerName, ex.getMessage(), ex);
                    sent = false;
                }
                if (!sent) {
                    LOGGER.warn("Partner={} email not sent; skipping AlertHistory upsert", partnerName);
                    continue;
                }
                String timestamp = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                String recipients = (mail.getTo() != null && mail.getTo().length > 0) ? String.join(",", mail.getTo()) : "<none>";
                LOGGER.info("Partner={} sent baseline drop alert email at {} to {} for {} machines", partnerName, timestamp, recipients, rowsToAlert.size());

                // Upsert AlertHistory for each machine alerted
                for (EmailRow r : rowsToAlert) {
                    Integer vmId = vmRepository.findBySerialNo(r.getSerial()).map(VendingMachine::getId).orElse(null);
                    Optional<AlertHistory> lastHist = Optional.empty();
                    if (vmId != null) lastHist = alertHistoryRepository.findLatestByVendingMachineAndAlertType(vmId, alertType);
                    if (lastHist.isEmpty()) lastHist = alertHistoryRepository.findLatestByVendingMachineSerialAndAlertType(r.getSerial(), alertType);

                    if (lastHist.isPresent()) {
                        AlertHistory h = lastHist.get();
                        h.setVendingMachineId(vmId);
                        h.setVendingMachineSerial(r.getSerial());
                        h.setLastSentAt(now);
                        h.setAlertType(alertType);
                        alertHistoryRepository.saveAndFlush(h);
                    } else {
                        AlertHistory h = new AlertHistory();
                        h.setVendingMachineId(vmId);
                        h.setVendingMachineSerial(r.getSerial());
                        h.setLastSentAt(now);
                        h.setAlertType(alertType);
                        h.setPartnerName(partnerName);
                        alertHistoryRepository.saveAndFlush(h);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Partner={} failed to send baseline drop alert email: {}", partnerName, ex.getMessage(), ex);
            }
        }

        LOGGER.info("Hourly baseline drop evaluation end");
    }
}
