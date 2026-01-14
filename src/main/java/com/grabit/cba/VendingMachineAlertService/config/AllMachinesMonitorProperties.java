package com.grabit.cba.VendingMachineAlertService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;


@Configuration
@ConfigurationProperties(prefix = "monitor")
@Data
public class AllMachinesMonitorProperties {

    private FailedSales failedSales = new FailedSales();
    private Baseline baseline = new Baseline();
    private HourlyBaselineAlert hourlyBaselineAlert = new HourlyBaselineAlert();
    private VoidFailed voidFailed = new VoidFailed();
    private ConsecutiveVoidComplete consecutiveVoidComplete = new ConsecutiveVoidComplete();
    private TimeoutMonitor timeout = new TimeoutMonitor();
    private HeartbeatMonitor heartbeat = new HeartbeatMonitor();

    @Data
    public static class FailedSales {
        private boolean enabled = true;
        private String cron = "0 */5 * * * *";
        private int windowSize = 10;
        private int failureThreshold = 3;
        private int alertCooldownMinutes = 60;
        private int slidingWindowSize = 10;
        private int slidingFailureThreshold = 5;
    }

    @Data
    public static class Baseline {
        private boolean baselineEnabled = true;
        private String baselineCron = "0 30 2 * * *";
        private int lookbackPeriodsMonths = 1;
    }

    @Data
    public static class HourlyBaselineAlert {
        private boolean hourlyBaselineAlertEnabled = true;
        private String hourlyBaselineAlertCron = "0 5 * * * *";
        private double baselineDropThresholdPercent = 0.30;
        private int baselineConsecutiveHoursRequired = 2;
        private int alertCooldownMinutes = 60;
    }

    @Data
    public static class VoidFailed {
        private boolean voidFailedEnabled = true;
        private String voidFailedCron = "0 */5 * * * *";
        private int voidFailureThreshold = 5;
        private int alertCooldownMinutes = 60;
    }

    @Data
    public static class ConsecutiveVoidComplete {
        private boolean consecutiveVoidCompleteEnabled = true;
        private String consecutiveVoidCompleteCron = "0 */5 * * * *"; // every 5 minutes
        private int consecutiveVoidCompleteTransactionWindowSize = 10; // check last 10 transactions
        private int consecutiveVoidCompleteConsecutiveVoidThreshold = 3; // alert if 3+ consecutive void_completed
        private double consecutiveVoidCompleteVoidPercentageThreshold = 50.0; // alert if >50% are void_completed
        private int consecutiveVoidCompleteAlertCooldownMinutes = 60; // cooldown between alerts
    }

    @Data
    public static class HeartbeatMonitor {
        private boolean heartbeatMonitoringEnabled = true;
        private String heartbeatMonitoringCron = "0 */10 * * * *"; // every 10 minutes
        private int heartbeatMonitoringOfflineMachineThresholdMinutes = 120; // alert if offline machine stays offline for 120+ minutes (2 hours)
        private int heartbeatMonitoringAlertCooldownMinutes = 60; // cooldown between alerts
    }

    @Data
    public static class TimeoutMonitor {
        private boolean timeoutMonitoringEnabled = true;
        private String timeoutMonitoringCron = "0 */5 * * * *"; // every 5 minutes
        private int timeoutMonitoringTransactionWindowSize = 10; // check last 10 transactions
        private int timeoutMonitoringConsecutiveTimeoutThreshold = 3; // alert if 3+ consecutive timeouts
        private double timeoutMonitoringTimeoutPercentageThreshold = 50.0; // alert if >50% are timeouts
        private int timeoutMonitoringAlertCooldownMinutes = 60; // cooldown between alerts
    }

}
