package org.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DhanCandleResponseDTO {

    @JsonProperty("open")
    private List<Double> open;

    @JsonProperty("high")
    private List<Double> high;

    @JsonProperty("low")
    private List<Double> low;

    @JsonProperty("close")
    private List<Double> close;

    @JsonProperty("volume")
    private List<Long> volume;

    @JsonProperty("timestamp")
    private List<String> timestamp;
}
