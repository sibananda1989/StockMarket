package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonDTO {
    private String factor;        // "RSI", "MACD", "Volume"
    private Object value;         // 32, "bullish_cross", 1.8
    private String interpretation; // "Oversold — potential reversal"
}
