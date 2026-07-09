package com.example.dfdl.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Converts DFDL XML infoset text into a Jackson JSON tree.
 */
@Component
public class XmlJsonConverter {

    private final XmlMapper xmlMapper;
    private final ObjectMapper objectMapper;

    public XmlJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.xmlMapper = new XmlMapper();
    }

    public JsonNode xmlToJson(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("XML infoset is empty; cannot convert to JSON");
        }
        try {
            JsonNode node = xmlMapper.readTree(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // Re-read through ObjectMapper to ensure a standard JSON tree representation
            return objectMapper.readTree(objectMapper.writeValueAsString(node));
        } catch (IOException ex) {
            throw new IllegalArgumentException(
                    "Failed to convert XML infoset to JSON: " + ex.getMessage(), ex);
        }
    }
}
