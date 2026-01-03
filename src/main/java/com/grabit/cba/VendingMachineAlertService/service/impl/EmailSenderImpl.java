package com.grabit.cba.VendingMachineAlertService.service.impl;

import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;
import com.grabit.cba.VendingMachineAlertService.exception.ClientErrorException;
import com.grabit.cba.VendingMachineAlertService.service.EmailSender;
import jakarta.activation.DataSource;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailSenderImpl implements EmailSender {

    @Autowired
    private JavaMailSender javaMailSender;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSenderImpl.class);

    public void sendEmail(MailDto mailDto, String logo, String signData) throws Exception {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            // Validate 'to' addresses
            if (mailDto.getTo() == null || mailDto.getTo().length == 0) {
                throw new IllegalArgumentException("To address array must not be null or empty");
            }
            helper.setTo(mailDto.getTo());
            // Only set CC/BCC when provided (MimeMessageHelper requires non-null arrays)
            if (mailDto.getCc() != null && mailDto.getCc().length > 0) {
                helper.setCc(mailDto.getCc());
            }
            if (mailDto.getBcc() != null && mailDto.getBcc().length > 0) {
                helper.setBcc(mailDto.getBcc());
            }
            helper.setText(mailDto.getBody(), mailDto.isHtml());
            helper.setSubject(mailDto.getSubject());
            helper.setFrom(mailDto.getFrom());

            if (logo != null && !logo.isEmpty()) {
                DataSource logoDs = new ByteArrayDataSource(Base64.decodeBase64(logo), "image/png");
                helper.addInline("bank-logo", logoDs);
            }
//            if (signData != null && !signData.isEmpty()) {
//                DataSource signatureDs= new ByteArrayDataSource(Hex.decodeHex(signData), "image/png");
//                helper.addInline("signature", signatureDs);
//            }
            javaMailSender.send(message);
        } catch (MailSendException mse) {
            LOGGER.error("Error occurred while sending email: {}", mse.getMessage(), mse);
            throw new ClientErrorException(mse.getMessageExceptions()[0].getMessage(), mse);
        } catch (IllegalArgumentException iae) {
            LOGGER.error("Error occurred while sending email: {}", iae.getMessage(), iae);
            throw new ClientErrorException(iae.getMessage(), iae);
        } catch (Exception e) {
            LOGGER.error("Error occurred while sending email", e);
            throw e;
        }
    }

    public void sendEmailWithAttachment(MailDto mailDto, byte[] fileContent, String fileName, String logo) throws Exception {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            // Validate 'to' addresses
            if (mailDto.getTo() == null || mailDto.getTo().length == 0) {
                throw new IllegalArgumentException("To address array must not be null or empty");
            }
            helper.setTo(mailDto.getTo());
            if (mailDto.getCc() != null && mailDto.getCc().length > 0) {
                helper.setCc(mailDto.getCc());
            }
            if (mailDto.getBcc() != null && mailDto.getBcc().length > 0) {
                helper.setBcc(mailDto.getBcc());
            }
            helper.setText(mailDto.getBody(), mailDto.isHtml());
            helper.setSubject(mailDto.getSubject());
            helper.setFrom(mailDto.getFrom());
            helper.addAttachment(fileName, new ByteArrayResource(fileContent));

            if (logo != null && !logo.isEmpty()) {
                DataSource logoDs = new ByteArrayDataSource(Base64.decodeBase64(logo), "image/png");
                helper.addInline("grabit-logo", logoDs);
            }
            javaMailSender.send(message);
        } catch (MailSendException mse) {
            LOGGER.error("Error occurred while sending email: {}", mse.getMessage(), mse);
            throw new ClientErrorException(mse.getMessageExceptions()[0].getMessage(), mse);
        } catch (IllegalArgumentException iae) {
            LOGGER.error("Error occurred while sending email: {}", iae.getMessage(), iae);
            throw new ClientErrorException(iae.getMessage(), iae);
        } catch (Exception e) {
            LOGGER.error("Error occurred while sending email", e);
            throw e;
        }
    }
}
