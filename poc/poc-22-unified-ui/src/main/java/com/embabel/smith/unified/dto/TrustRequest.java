package com.embabel.smith.unified.dto;

/** A request to check a skill's tool groups against a trust tier. */
public record TrustRequest(String skillName, String tier) {}
