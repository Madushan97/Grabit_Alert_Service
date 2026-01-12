package com.grabit.cba.VendingMachineAlertService.database.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Alert_Void_Failed_Tracking")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoidFailedTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "vending_machine_serial", nullable = false, unique = true)
    private String vendingMachineSerial;

    @Column(name = "last_checked_transaction_id")
    private Integer lastCheckedTransactionId;

    @Column(name = "last_checked_datetime")
    private LocalDateTime lastCheckedDatetime;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
