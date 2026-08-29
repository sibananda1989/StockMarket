package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RebuildHoldingsResponseDTO {

    private int beforeCount;
    private int afterCount;
    private int added;
    private int updated;
    private int deleted;
}
