package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDetailsDTO {
    private StockDTO stock;
    private List<DailyPriceDTO> recentPrices;
    private List<RsiDTO> recentRsiValues;
}
