package com.embabel.smith.unified.dto;

/** A request to check a spec's tool groups against a trust tier. */
public record TrustRequest(String specName, String tier) {}
