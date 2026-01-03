package com.grabit.cba.VendingMachineAlertService.util;

public class EmailServiceUtils {

    public static String[] commaSeparatedStringToArray(String commaSeparatedString) {
        return commaSeparatedString == null || commaSeparatedString.isEmpty() ? new String[]{} : commaSeparatedString.trim().split("\\s*,\\s*");
    }
}
