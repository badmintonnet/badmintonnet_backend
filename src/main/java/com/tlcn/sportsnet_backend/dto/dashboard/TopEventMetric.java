package com.tlcn.sportsnet_backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopEventMetric {
    private String id;
    private String slug;
    private String title;
    private double value;
}
