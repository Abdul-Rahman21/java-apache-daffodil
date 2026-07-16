package com.example.dfdl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Human-readable JSON view of an EBCDIC SMPRES binary after segment extraction.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractedBinaryJson {

    private String source;
    private String encoding;
    private int sizeBytes;
    private int segmentCount;
    private Map<String, Object> envelope = new LinkedHashMap<>();
    private Map<String, Object> flight;
    private Map<String, Object> aircraft;
    private Map<String, Object> cabin;
    private List<Map<String, Object>> rows = new ArrayList<>();
    private Map<String, Integer> tagCounts = new LinkedHashMap<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public int getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(int sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public int getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(int segmentCount) {
        this.segmentCount = segmentCount;
    }

    public Map<String, Object> getEnvelope() {
        return envelope;
    }

    public void setEnvelope(Map<String, Object> envelope) {
        this.envelope = envelope;
    }

    public Map<String, Object> getFlight() {
        return flight;
    }

    public void setFlight(Map<String, Object> flight) {
        this.flight = flight;
    }

    public Map<String, Object> getAircraft() {
        return aircraft;
    }

    public void setAircraft(Map<String, Object> aircraft) {
        this.aircraft = aircraft;
    }

    public Map<String, Object> getCabin() {
        return cabin;
    }

    public void setCabin(Map<String, Object> cabin) {
        this.cabin = cabin;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    public Map<String, Integer> getTagCounts() {
        return tagCounts;
    }

    public void setTagCounts(Map<String, Integer> tagCounts) {
        this.tagCounts = tagCounts;
    }
}
