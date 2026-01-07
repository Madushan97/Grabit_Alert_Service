package com.grabit.cba.VendingMachineAlertService.service;

import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;

public interface EmailSender {

    public boolean sendEmail(MailDto mailDto, String logo, String signData) throws Exception;

    public boolean sendEmailWithAttachment(MailDto mailDto, byte[] fileContent, String fileName, String logo) throws Exception;
}
