package com.example.dfdl.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Downstream seat-map request payload (matches Request_seatmaprequest_2.txt).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeatMapRequest {

    @JsonProperty("ChannelId")
    private String channelId;

    @JsonProperty("ChannelName")
    private String channelName;

    @JsonProperty("CurrencyCode")
    private String currencyCode;

    @JsonProperty("RecordLocator")
    private String recordLocator = "";

    @JsonProperty("ProductCode")
    private String productCode = "";

    @JsonProperty("FlightSegments")
    private List<FlightSegment> flightSegments = new ArrayList<>();

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getRecordLocator() {
        return recordLocator;
    }

    public void setRecordLocator(String recordLocator) {
        this.recordLocator = recordLocator != null ? recordLocator : "";
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode != null ? productCode : "";
    }

    public List<FlightSegment> getFlightSegments() {
        return flightSegments;
    }

    public void setFlightSegments(List<FlightSegment> flightSegments) {
        this.flightSegments = flightSegments != null ? flightSegments : new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FlightSegment {

        @JsonProperty("DepartureAirport")
        private Airport departureAirport;

        @JsonProperty("ArrivalAirport")
        private Airport arrivalAirport;

        @JsonProperty("DepartureDateTime")
        private String departureDateTime;

        @JsonProperty("MarketingAirlineCode")
        private String marketingAirlineCode;

        @JsonProperty("OperatingAirlineCode")
        private String operatingAirlineCode;

        @JsonProperty("FlightNumber")
        private Integer flightNumber;

        @JsonProperty("OperatingFlightNumber")
        private Integer operatingFlightNumber;

        @JsonProperty("ClassOfService")
        private String classOfService;

        @JsonProperty("Pricing")
        private String pricing;

        public Airport getDepartureAirport() {
            return departureAirport;
        }

        public void setDepartureAirport(Airport departureAirport) {
            this.departureAirport = departureAirport;
        }

        public Airport getArrivalAirport() {
            return arrivalAirport;
        }

        public void setArrivalAirport(Airport arrivalAirport) {
            this.arrivalAirport = arrivalAirport;
        }

        public String getDepartureDateTime() {
            return departureDateTime;
        }

        public void setDepartureDateTime(String departureDateTime) {
            this.departureDateTime = departureDateTime;
        }

        public String getMarketingAirlineCode() {
            return marketingAirlineCode;
        }

        public void setMarketingAirlineCode(String marketingAirlineCode) {
            this.marketingAirlineCode = marketingAirlineCode;
        }

        public String getOperatingAirlineCode() {
            return operatingAirlineCode;
        }

        public void setOperatingAirlineCode(String operatingAirlineCode) {
            this.operatingAirlineCode = operatingAirlineCode;
        }

        public Integer getFlightNumber() {
            return flightNumber;
        }

        public void setFlightNumber(Integer flightNumber) {
            this.flightNumber = flightNumber;
        }

        public Integer getOperatingFlightNumber() {
            return operatingFlightNumber;
        }

        public void setOperatingFlightNumber(Integer operatingFlightNumber) {
            this.operatingFlightNumber = operatingFlightNumber;
        }

        public String getClassOfService() {
            return classOfService;
        }

        public void setClassOfService(String classOfService) {
            this.classOfService = classOfService;
        }

        public String getPricing() {
            return pricing;
        }

        public void setPricing(String pricing) {
            this.pricing = pricing;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Airport {

        @JsonProperty("IataCode")
        private String iataCode;

        public Airport() {
        }

        public Airport(String iataCode) {
            this.iataCode = iataCode;
        }

        public String getIataCode() {
            return iataCode;
        }

        public void setIataCode(String iataCode) {
            this.iataCode = iataCode;
        }
    }
}
