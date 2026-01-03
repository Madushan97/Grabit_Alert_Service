package com.grabit.cba.VendingMachineAlertService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "monitor")
@Data
public class AllMachinesMonitorProperties {

    /******************************************************* SALE FAILED monitoring ***************************************************/
    private boolean enabled = true;
    private String cron = "0 */5 * * * *"; // every 5 minutes (Spring cron with seconds)
    private int windowSize = 10;            // Number of latest transactions to inspect per machine
    private int failureThreshold = 3;       // Consecutive failure threshold
    private List<String> recipients;        // Email recipients
    private int alertCooldownMinutes = 60;  // Minutes to suppress repeated alerts for the same machine and alert type
    private int slidingWindowSize = 10;     // Sliding window size (number of latest transactions to inspect for non-consecutive failure check)
    private int slidingFailureThreshold = 5;    // Sliding window failure threshold (e.g., 5 failures within slidingWindowSize)

    /****************************************************** SALES ALERT configuration *****************************************************/
    private boolean reportEnabled = true;
    private String reportCron = "0 0 * * * *"; // every hour at minute 0
    private int reportWindowHours = 1; // window to report (hours)
    private int reportBaselineHours = 24; // baseline period hours for average
//    private List<String> reportRecipients; // optional override recipients for reports

    /****************************************************** HOURLY SALES BASELINE COMPUTATION *****************************************************/
    private boolean baselineEnabled = true;
    private String baselineCron = "0 30 2 * * *"; // daily at 02:30 AM
    private Integer lookbackPeriodsMonths = 1; // look back period to compute baseline

    /***************************************************** HOURLY BASELINE DROP ALERT CONFIGURATION *****************************************************/
    private boolean hourlyBaselineAlertEnabled = true; // enable hourly baseline drop alerting
    private String hourlyBaselineAlertCron = "0 5 * * * *"; // run 5 minutes after each hour
    private double baselineDropThresholdPercent = 0.30; // 30% of baseline
    private int baselineConsecutiveHoursRequired = 2; // require persistence at least 2 consecutive hours
}
