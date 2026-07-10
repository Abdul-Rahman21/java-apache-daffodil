package com.example.dfdl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParseResponse {

    private boolean success;
    private String xml;
    /**
     * Mapped downstream seat-map request JSON (Request_seatmaprequest_2.txt shape).
     */
    private JsonNode json;
    /**
     * Raw DFDL infoset JSON before business mapping.
     */
    private JsonNode infoset;
    private String error;

    public ParseResponse() {
    }

    public static ParseResponse ok(String xml, JsonNode mappedJson, JsonNode infoset) {
        ParseResponse response = new ParseResponse();
        response.success = true;
        response.xml = xml;
        response.json = mappedJson;
        response.infoset = infoset;
        return response;
    }

    public static ParseResponse ok(String xml, JsonNode json) {
        return ok(xml, json, null);
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

    public JsonNode getInfoset() {
        return infoset;
    }

    public void setInfoset(JsonNode infoset) {
        this.infoset = infoset;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
