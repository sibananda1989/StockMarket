package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntryZoneDTO {
    private BigDecimal low;   // currentPrice - ATR(14)
    private BigDecimal high;  // currentPrice + ATR(14)
}
