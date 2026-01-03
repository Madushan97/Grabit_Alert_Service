//package com.grabit.cba.VendingMachineAlertService.controller;
//
//import com.grabit.cba.VendingMachineAlertService.dto.requestDto.FailedSalesRequestDto;
//import com.grabit.cba.VendingMachineAlertService.dto.requestDto.MailDto;
//import com.grabit.cba.VendingMachineAlertService.dto.responseDto.VMResponse;
//import com.grabit.cba.VendingMachineAlertService.service.TransactionNotifyService;
//import com.grabit.cba.VendingMachineAlertService.service.VMService;
//import com.grabit.cba.VendingMachineAlertService.util.StandardResponse;
//import lombok.RequiredArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/vm")
//public class VendingMachineController {
//
//    Logger LOGGER = LoggerFactory.getLogger(VendingMachineController.class);
//
//    private final VMService vMService;
//    private final TransactionNotifyService transactionNotifyService;
//
//    @GetMapping("/{id}")
//    public ResponseEntity<StandardResponse> getById(@PathVariable Integer id) {
//        LOGGER.info("🎯 Request came to get vm by ID: {}", id);
//        VMResponse brandResponse = vMService.getVMService(id);
//        return new ResponseEntity<>(
//                new StandardResponse(
//                        HttpStatus.OK.value(),
//                        "VM retrieved successfully ✅",
//                        brandResponse
//                ),
//                HttpStatus.OK
//        );
//    }
//
//    @PostMapping("/email")
//    public ResponseEntity<StandardResponse> sendEmail(@RequestBody FailedSalesRequestDto failedSalesRequestDto) throws Exception {
//
//        transactionNotifyService.sendSaleFailedEmail(failedSalesRequestDto);
//        return new ResponseEntity<>(
//                new StandardResponse(
//                        HttpStatus.OK.value(),
//                        "Email has been sent ✅",
//                        null
//                ),
//                HttpStatus.OK
//        );
//    }
//}
