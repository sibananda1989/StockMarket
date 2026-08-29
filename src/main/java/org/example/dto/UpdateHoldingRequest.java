package org.example.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for the PATCH /api/portfolios/{id}/holdings/{holdingId} endpoint.
 * All fields are optional — only the supplied fields are applied as a silent direct edit.
 * No adjustment transaction is recorded.
 */
@Data
public class UpdateHoldingRequest {

    private Integer quantity;

    private BigDecimal avgPrice;
}
