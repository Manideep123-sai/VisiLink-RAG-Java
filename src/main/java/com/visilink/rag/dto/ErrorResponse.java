package com.visilink.rag.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response DTO matching the {"detail": "..."} schema
 * expected by the React frontend (Analyzer.jsx and ResultDetail.jsx).
 */
public class ErrorResponse {

    @JsonProperty("detail")
    private String detail;

    public ErrorResponse() {
    }

    public ErrorResponse(String detail) {
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
