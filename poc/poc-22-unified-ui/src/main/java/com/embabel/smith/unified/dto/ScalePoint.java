package com.embabel.smith.unified.dto;

/** A point on the precision curve: the library size and precision@1 across the benchmark. */
public record ScalePoint(int librarySize, double precisionAt1) {}
