package com.grabit.cba.VendingMachineAlertService.service.impl;

import com.grabit.cba.VendingMachineAlertService.dto.requestDto.FailedSalesRequestDto;
import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;
import com.grabit.cba.VendingMachineAlertService.service.EmailSender;
import com.grabit.cba.VendingMachineAlertService.service.TransactionNotifyService;
import com.grabit.cba.VendingMachineAlertService.util.EmailServiceUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class TransactionNotifyServiceImpl implements TransactionNotifyService {

    Logger LOGGER = LoggerFactory.getLogger(TransactionNotifyServiceImpl.class);
    private final EmailSender emailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    String senderMail;

    @Value("${grabit.logo}")
    String grabitLogo;

    @Override
    public void sendSaleFailedEmail(FailedSalesRequestDto failedSalesRequestDto) throws Exception {

        MailDto mailDto = new MailDto();
        mailDto.setTo(EmailServiceUtils.commaSeparatedStringToArray(failedSalesRequestDto.getTo()));
        mailDto.setCc(EmailServiceUtils.commaSeparatedStringToArray(failedSalesRequestDto.getCc()));
        mailDto.setBcc(EmailServiceUtils.commaSeparatedStringToArray(failedSalesRequestDto.getBcc()));
        mailDto.setSubject(failedSalesRequestDto.getSubject());
        mailDto.setFrom(senderMail);
        mailDto.setHtml(true);
        LOGGER.debug("MailDto populated with To: {}, CC: {}, BCC: {}, Subject: {}", failedSalesRequestDto.getTo(), failedSalesRequestDto.getCc(), failedSalesRequestDto.getBcc(), failedSalesRequestDto.getSubject());

        Map<String, Object> properties = new HashMap<>();
        properties.put("vendingSerialNumber", failedSalesRequestDto.getVendingSerialNumber());
        properties.put("vmName", failedSalesRequestDto.getVmName());
        properties.put("merchantName", failedSalesRequestDto.getMerchantName());
        properties.put("terminateCode", failedSalesRequestDto.getTerminateCode());
        properties.put("productLockCount", failedSalesRequestDto.getProductLockCount());
        properties.put("location", failedSalesRequestDto.getLocation());

        LOGGER.debug("Email properties populated: {}", properties);

        Context context = new Context();
        context.setVariables(properties);
        mailDto.setBody(templateEngine.process("Sale_failed", context));
        emailSender.sendEmail(mailDto, grabitLogo, null);
        LOGGER.info("Email with signature sent successfully");

    }

}
