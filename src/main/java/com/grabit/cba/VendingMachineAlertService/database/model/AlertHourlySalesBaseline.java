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

    // NOTE: Despite the column name "avgSalesCompleted", this field now stores
    // MEDIAN values (not average) for better outlier resistance
    @Column(name = "medianSalesCompleted")
    private Double medianSalesCompleted;

    @Column(name = "medianSalesFailed")
    private Double medianSalesFailed;

    @Column(name = "medianVoidCompleted")
    private Double medianVoidCompleted;

    @Column(name = "medianVoidFailed")
    private Double medianVoidFailed;

    @Column(name = "updated_at", columnDefinition = "datetime(6)")
    private LocalDateTime updatedAt;

}
