package com.example.dfdl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParseResponse {

    private boolean success;
    private String xml;
    private JsonNode json;
    private String error;

    public ParseResponse() {
    }

    public static ParseResponse ok(String xml, JsonNode json) {
        ParseResponse response = new ParseResponse();
        response.success = true;
        response.xml = xml;
        response.json = json;
        return response;
    }

    public static ParseResponse failure(String error) {
        ParseResponse response = new ParseResponse();
        response.success = false;
        response.error = error;
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public JsonNode getJson() {
        return json;
    }

    public void setJson(JsonNode json) {
        this.json = json;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
