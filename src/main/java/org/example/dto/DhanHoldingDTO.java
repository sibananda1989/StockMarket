package org.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DhanHoldingDTO {

    @JsonProperty("tradingSymbol")
    private String tradingSymbol;

    @JsonProperty("securityId")
    private String securityId;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("isin")
    private String isin;

    @JsonProperty("totalQty")
    private Double totalQty;

    @JsonProperty("availableQty")
    private Double availableQty;

    @JsonProperty("avgCostPrice")
    private Double avgCostPrice;

    @JsonProperty("lastTradedPrice")
    private Double lastTradedPrice;
}
