package com.visilink.rag.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TranscriptSegmentDto {

    private String text;

    @JsonProperty("start_time_in_seconds")
    @JsonAlias({"startTimeInSeconds", "start"})
    private double startTimeInSeconds;

    @JsonProperty("end_time_in_seconds")
    @JsonAlias({"endTimeInSeconds", "end"})
    private double endTimeInSeconds;

    public TranscriptSegmentDto() {
    }

    public TranscriptSegmentDto(String text, double startTimeInSeconds, double endTimeInSeconds) {
        this.text = text;
        this.startTimeInSeconds = startTimeInSeconds;
        this.endTimeInSeconds = endTimeInSeconds;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public double getStartTimeInSeconds() {
        return startTimeInSeconds;
    }

    public void setStartTimeInSeconds(double startTimeInSeconds) {
        this.startTimeInSeconds = startTimeInSeconds;
    }

    public double getEndTimeInSeconds() {
        return endTimeInSeconds;
    }

    public void setEndTimeInSeconds(double endTimeInSeconds) {
        this.endTimeInSeconds = endTimeInSeconds;
    }
}
