package com.grabit.cba.VendingMachineAlertService.database.model.other;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Partners")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partners {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "email", length = 50, nullable = false, unique = true)
    private String email;

    @Column(name = "contactNo", length = 15)
    private String contactNo;

    @Column(name = "isDeleted")
    private Boolean isDeleted = Boolean.FALSE;

}
