package com.grabit.cba.VendingMachineAlertService.dto.responseDto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class VMResponse {

    private Integer id;
    private String serialNo;
    private Integer merchantId;
    private Boolean isDeleted = Boolean.FALSE;
    private String name;
    private Integer status;
    private Integer terminateCode;
    private Integer productLockCount;
    private Double longitude;
    private Double latitude;
    private String color;
}
