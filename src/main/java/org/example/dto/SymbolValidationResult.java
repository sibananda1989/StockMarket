package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of validating a stock symbol against Yahoo Finance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SymbolValidationResult {
 private boolean valid;
 private String yahooSymbol;
 private String companyName;
 private String sector;
}
