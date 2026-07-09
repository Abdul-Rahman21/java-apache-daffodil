package com.example.dfdl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagnosticResponse {

    private boolean compileSuccess;
    private List<String> compileDiagnostics = new ArrayList<>();
    private Boolean parseSuccess;
    private List<String> parseDiagnostics = new ArrayList<>();
    private String schemaPath;
    private long elapsedMillis;
    private String exceptionMessage;
    private String exceptionType;
    private String notes;

    public DiagnosticResponse() {
    }

    public boolean isCompileSuccess() {
        return compileSuccess;
    }

    public void setCompileSuccess(boolean compileSuccess) {
        this.compileSuccess = compileSuccess;
    }

    public List<String> getCompileDiagnostics() {
        return compileDiagnostics;
    }

    public void setCompileDiagnostics(List<String> compileDiagnostics) {
        this.compileDiagnostics = compileDiagnostics != null ? compileDiagnostics : new ArrayList<>();
    }

    public Boolean getParseSuccess() {
        return parseSuccess;
    }

    public void setParseSuccess(Boolean parseSuccess) {
        this.parseSuccess = parseSuccess;
    }

    public List<String> getParseDiagnostics() {
        return parseDiagnostics;
    }

    public void setParseDiagnostics(List<String> parseDiagnostics) {
        this.parseDiagnostics = parseDiagnostics != null ? parseDiagnostics : new ArrayList<>();
    }

    public String getSchemaPath() {
        return schemaPath;
    }

    public void setSchemaPath(String schemaPath) {
        this.schemaPath = schemaPath;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public String getExceptionMessage() {
        return exceptionMessage;
    }

    public void setExceptionMessage(String exceptionMessage) {
        this.exceptionMessage = exceptionMessage;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
