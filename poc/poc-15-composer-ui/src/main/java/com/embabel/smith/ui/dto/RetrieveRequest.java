package com.embabel.smith.ui.dto;

/** A retrieval query: the natural-language task and how many skills to return. */
public record RetrieveRequest(String query, Integer k) {

    public int kOrDefault() {
        return k == null || k <= 0 ? 5 : k;
    }
}
