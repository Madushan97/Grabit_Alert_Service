package com.grabit.cba.VendingMachineAlertService.dto.requestDto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MailDto {
    private String from;
    private String[] to;
    private String[] cc;
    private String[] bcc;
    private String subject;
    private String body;
    private boolean isHtml;

}
