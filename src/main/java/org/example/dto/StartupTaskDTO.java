package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for startup task metadata displayed in the landing page popup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartupTaskDTO {

    private String id;
    private String name;
    private String description;
    private String category;
    private boolean defaultEnabled;
    private boolean required;
}
