package com.grabit.cba.VendingMachineAlertService.database.model.other;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "Merchants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Immutable
@Builder
public class Merchants {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "email", length = 50, nullable = false, unique = true)
    private String email;

    @Column(name = "contactNo", length = 15)
    private String contactNo;

    @Column(name = "address", length = 255, nullable = false)
    private String address;

    @Column(name = "partnerId")
    private Integer partnerId;

    @Column(name = "isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

}
