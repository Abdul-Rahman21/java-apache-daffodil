package com.example.dfdl.dto;

public class HealthResponse {

    private String status;
    private boolean schemaCompiled;
    private String schema;

    public HealthResponse() {
    }

    public HealthResponse(String status, boolean schemaCompiled, String schema) {
        this.status = status;
        this.schemaCompiled = schemaCompiled;
        this.schema = schema;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isSchemaCompiled() {
        return schemaCompiled;
    }

    public void setSchemaCompiled(boolean schemaCompiled) {
        this.schemaCompiled = schemaCompiled;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
