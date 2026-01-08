package com.grabit.cba.VendingMachineAlertService.database.model;

import com.grabit.cba.VendingMachineAlertService.database.model.other.Partners;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.Instant;

@Entity
@Table(name = "Alert_Email_Configs")
@Data
public class AlertEmailConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alertTypeId", nullable = false)
    private AlertType alertType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partnerId")
    private Partners partners;

    @Column(name = "\"to\"")
    private String to;

    @Column(name = "cc")
    private String cc;

    @Column(name = "bcc")
    private String bcc;

    @Column(name = "createdAt")
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
