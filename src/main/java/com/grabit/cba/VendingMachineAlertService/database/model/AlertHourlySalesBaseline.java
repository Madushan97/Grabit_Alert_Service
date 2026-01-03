package com.grabit.cba.VendingMachineAlertService.database.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "Alert_Hourly_Sales_Baseline")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertHourlySalesBaseline {

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "machineId")
        private Integer machineId;

        @Column(name = "hourOfDay")
        private Integer hourOfDay; // 0-23
    }

    @EmbeddedId
    private Id id;

    @Column(name = "avgSalesCompleted")
    private Double avgSalesCompleted;

    @Column(name = "avgSalesFailed")
    private Double avgSalesFailed;

    @Column(name = "avgVoidCompleted")
    private Double avgVoidCompleted;

    @Column(name = "avgVoidFailed")
    private Double avgVoidFailed;

    @Column(name = "updated_at", columnDefinition = "datetime(6)")
    private LocalDateTime updatedAt;

}
