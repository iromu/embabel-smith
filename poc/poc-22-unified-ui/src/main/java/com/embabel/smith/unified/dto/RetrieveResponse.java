package com.embabel.smith.unified.dto;

import java.util.List;

/** The ranked retrieval results and the name of the scorer that produced them. */
public record RetrieveResponse(String scorer, List<RetrieveResult> results) {}
