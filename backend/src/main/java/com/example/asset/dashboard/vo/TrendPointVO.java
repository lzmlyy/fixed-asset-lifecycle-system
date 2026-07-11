package com.example.asset.dashboard.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrendPointVO {

    private String month;
    private BigDecimal value;
    private BigDecimal monthlyDepreciation;
    private BigDecimal accumulatedDepreciation;
}
