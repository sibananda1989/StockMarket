package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsvImportResultDTO {

    private List<StockDTO> results;
    private List<SkippedRecordDTO> skippedRecords;
    private int totalProcessed;
    private int totalSkipped;
}
