package com.example.asset.inventory.vo;

import lombok.Data;

@Data
public class InventoryResultVO {

    private Long id;
    private Long recordId;
    private String resultType;
    private String expectedValue;
    private String actualValue;
}