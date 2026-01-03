package com.grabit.cba.VendingMachineAlertService.database.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "Alert_History")
@Data
public class AlertHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "vendingMachineId", nullable = true)
    private Integer vendingMachineId;

    // store machine serial as fallback for cases where vendingMachineId isn't resolvable
    @Column(name = "vendingMachineSerial")
    private String vendingMachineSerial;

    // store the send time with microsecond precision
    @Column(name = "lastSentAt", columnDefinition = "datetime(6)")
    private LocalDateTime lastSentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alertTypeId", nullable = false)
    private AlertType alertType;

    @Column(name = "partnerName")
    private String partnerName;

    @Column(name = "createdAt", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updatedAt")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

}
