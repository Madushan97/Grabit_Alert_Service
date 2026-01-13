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

    @Column(name = "vendingMachineSerial", nullable = false, unique = true)
    private String vendingMachineSerial;

    @Column(name = "lastCheckedTransactionId")
    private Integer lastCheckedTransactionId;

    @Column(name = "lastCheckedDatetime")
    private LocalDateTime lastCheckedDatetime;

    @Column(name = "createdAt", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updatedAt", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
