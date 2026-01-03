package com.grabit.cba.VendingMachineAlertService.controller;

import com.grabit.cba.VendingMachineAlertService.exception.AlreadyExistsException;
import com.grabit.cba.VendingMachineAlertService.exception.NotFoundException;
import com.grabit.cba.VendingMachineAlertService.util.StandardResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<StandardResponse> handleNotFound(NotFoundException ex) {
        return new ResponseEntity<>(
                new StandardResponse(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage(),
                        null
                ),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<StandardResponse> handleAlreadyExists(AlreadyExistsException ex) {
        return new ResponseEntity<>(
                new StandardResponse(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage(),
                        null
                ),
                HttpStatus.CONFLICT
        );
    }


}
