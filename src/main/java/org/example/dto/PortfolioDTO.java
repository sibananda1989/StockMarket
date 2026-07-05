package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioDTO {

    private Long id;
    private String name;
    private String description;
    private boolean isDefault;
    private long holdingsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<HoldingDTO> holdings;
}
