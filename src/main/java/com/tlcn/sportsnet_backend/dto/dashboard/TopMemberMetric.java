package com.tlcn.sportsnet_backend.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopMemberMetric {
    private String id;
    private String slug;
    private String name;
    private double value;
}
