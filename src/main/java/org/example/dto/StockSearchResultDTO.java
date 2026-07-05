package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a stock search result from Yahoo Finance search API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockSearchResultDTO {
    private String symbol;
    private String name;
    private String exchange;
    private String sector;
    private String industry;
    private String quoteType;
    private boolean isYahooFinance;
}