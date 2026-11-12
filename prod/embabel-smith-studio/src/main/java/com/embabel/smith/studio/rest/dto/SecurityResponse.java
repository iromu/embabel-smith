package com.embabel.smith.studio.rest.dto;

/** The result of scanning a agent spec (the Security pane): the scan report, or the error. */
public record SecurityResponse(String error, ScanInfo scan) {

    public static SecurityResponse ok(ScanInfo scan) {
        return new SecurityResponse(null, scan);
    }

    public static SecurityResponse error(String error) {
        return new SecurityResponse(error, null);
    }
}
