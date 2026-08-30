package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreParameterConfigDTO {
    private String paramKey;
    private String category;
    private String displayName;
    private String description;
    private boolean enabled;
    private String scoreSystem;
}
