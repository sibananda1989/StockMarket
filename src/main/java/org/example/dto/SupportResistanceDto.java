package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupportResistanceDto {

    private Long stockId;
    private String symbol;
    private LocalDate calculationDate;

    private List<SwingLevel> swingHighs;
    private List<SwingLevel> swingLows;
    private PivotLevels pivots;
    private List<MajorLevel> majorLevels;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SwingLevel {
        private BigDecimal price;
        private LocalDate date;
        private BigDecimal strength;
        private int order;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PivotLevels {
        private BigDecimal pivot;
        private BigDecimal s1;
        private BigDecimal s2;
        private BigDecimal s3;
        private BigDecimal r1;
        private BigDecimal r2;
        private BigDecimal r3;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MajorLevel {
        private BigDecimal price;
        private String type;       // "support" or "resistance"
        private int touches;
        private BigDecimal strength;
    }
}
