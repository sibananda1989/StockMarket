package org.example.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockHistoryDTO {

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
