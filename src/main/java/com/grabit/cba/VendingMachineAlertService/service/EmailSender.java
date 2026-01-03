package com.grabit.cba.VendingMachineAlertService.service;

import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;

public interface EmailSender {

    public void sendEmail(MailDto mailDto, String logo, String signData) throws Exception;

    public void sendEmailWithAttachment(MailDto mailDto, byte[] fileContent, String fileName, String logo) throws Exception;
}
