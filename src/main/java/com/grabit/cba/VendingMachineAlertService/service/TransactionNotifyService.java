package com.grabit.cba.VendingMachineAlertService.service;

import com.grabit.cba.VendingMachineAlertService.dto.requestDto.FailedSalesRequestDto;
import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;

public interface TransactionNotifyService {

    void sendSaleFailedEmail(FailedSalesRequestDto failedSalesRequestDto) throws Exception;
}
