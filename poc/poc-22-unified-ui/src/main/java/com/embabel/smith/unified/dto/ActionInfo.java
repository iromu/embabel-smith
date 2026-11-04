package com.embabel.smith.unified.dto;

import java.util.List;

/** One action in a compiled draft: its name, the types it consumes, and the type it produces. */
public record ActionInfo(String name, List<String> inputTypes, String outputType) {}
