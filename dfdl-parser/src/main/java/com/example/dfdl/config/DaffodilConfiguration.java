package com.example.dfdl.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DaffodilConfiguration.DaffodilProperties.class)
public class DaffodilConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DaffodilConfiguration.class);

    public DaffodilConfiguration(DaffodilProperties properties) {
        log.info("Application startup: Daffodil request schema path configured as '{}'", properties.getSchema());
        log.info("Application startup: Daffodil response schema path configured as '{}'", properties.getResponseSchema());
        log.info("Application startup: Samples directory configured as '{}'", properties.getSamplesDir());
        log.info("Application startup: Seat-map API URL configured as '{}'", properties.getSeatMapApiUrl());
    }

    @ConfigurationProperties(prefix = "daffodil")
    public static class DaffodilProperties {

        /**
         * Absolute path to the DFDL request schema XSD (SMPREQ parse).
         */
        private String schema = "/app/schema/CYO_SMPREQ.xsd";

        /**
         * Absolute path to the DFDL response schema XSD (SMPRES unparse).
         */
        private String responseSchema = "/app/schema/CYO_SMPRES.xsd";

        /**
         * Directory containing binary sample files available for parse-by-name.
         */
        private String samplesDir = "/app/samples";

        /**
         * Default ChannelId for mapped seat-map JSON (not present in EDIFACT SMPREQ).
         */
        private String channelId = "4101";

        private String defaultChannelName = "1A";

        private String defaultCurrencyCode = "USD";

        private String defaultClassOfService = "Y";

        private String defaultPricing = "true";

        /**
         * External seat-map API URL used by {@code POST /process}.
         * From Docker Desktop use {@code http://host.docker.internal:9000/api/seatmap}.
         */
        private String seatMapApiUrl = "http://localhost:9000/api/seatmap";

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getResponseSchema() {
            return responseSchema;
        }

        public void setResponseSchema(String responseSchema) {
            this.responseSchema = responseSchema;
        }

        public String getSamplesDir() {
            return samplesDir;
        }

        public void setSamplesDir(String samplesDir) {
            this.samplesDir = samplesDir;
        }

        public String getChannelId() {
            return channelId;
        }

        public void setChannelId(String channelId) {
            this.channelId = channelId;
        }

        public String getDefaultChannelName() {
            return defaultChannelName;
        }

        public void setDefaultChannelName(String defaultChannelName) {
            this.defaultChannelName = defaultChannelName;
        }

        public String getDefaultCurrencyCode() {
            return defaultCurrencyCode;
        }

        public void setDefaultCurrencyCode(String defaultCurrencyCode) {
            this.defaultCurrencyCode = defaultCurrencyCode;
        }

        public String getDefaultClassOfService() {
            return defaultClassOfService;
        }

        public void setDefaultClassOfService(String defaultClassOfService) {
            this.defaultClassOfService = defaultClassOfService;
        }

        public String getDefaultPricing() {
            return defaultPricing;
        }

        public void setDefaultPricing(String defaultPricing) {
            this.defaultPricing = defaultPricing;
        }

        public String getSeatMapApiUrl() {
            return seatMapApiUrl;
        }

        public void setSeatMapApiUrl(String seatMapApiUrl) {
            this.seatMapApiUrl = seatMapApiUrl;
        }
    }
}
