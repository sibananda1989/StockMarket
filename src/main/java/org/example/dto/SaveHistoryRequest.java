package org.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SaveHistoryRequest {
    @NotBlank(message = "Symbol is required")
    private String symbol;
    
    private String name;
    
    private String sector;
    
    @NotEmpty(message = "History data is required")
    private List<StockHistoryDTO> history;
}
