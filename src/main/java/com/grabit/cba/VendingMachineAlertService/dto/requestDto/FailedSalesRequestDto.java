package com.grabit.cba.VendingMachineAlertService.dto.requestDto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class FailedSalesRequestDto {

    private String vendingSerialNumber;
    private String vmName;
    private String merchantName;
    private String terminateCode;
    private String productLockCount;
    private String location;

    @NotBlank(message = "To address is required")
    private String to;
    private String cc;
    private String bcc;

    @NotBlank(message = "Email subject is required")
    private String subject;
}

