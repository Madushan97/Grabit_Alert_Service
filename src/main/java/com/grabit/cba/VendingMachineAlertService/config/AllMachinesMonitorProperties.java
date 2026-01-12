package com.grabit.cba.VendingMachineAlertService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "monitor")
@Data
public class AllMachinesMonitorProperties {

    private FailedSales failedSales = new FailedSales();
    private Baseline baseline = new Baseline();
    private HourlyBaselineAlert hourlyBaselineAlert = new HourlyBaselineAlert();
    private VoidFailed voidFailed = new VoidFailed();

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

}
