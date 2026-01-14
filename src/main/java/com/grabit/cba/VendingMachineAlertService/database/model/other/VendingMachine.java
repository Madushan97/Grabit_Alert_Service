package com.grabit.cba.VendingMachineAlertService.database.model.other;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.util.Objects;

@Entity
@Table(name = "VendingMachines")
@Data
@Immutable
public class VendingMachine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "serialNo", length = 30, nullable = false, unique = true)
    private String serialNo;

    @Column(name = "merchantId")
    private Integer merchantId;

    @Column(name = "isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "status")
    private Integer status;

    @Column(name = "terminateCode")
    private Integer terminateCode = 0;

    @Column(name = "productLockCount")
    private Integer productLockCount = 0;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "color")
    private String color;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VendingMachine that = (VendingMachine) o;
        return Objects.equals(id, that.id) && Objects.equals(serialNo, that.serialNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, serialNo);
    }

}
