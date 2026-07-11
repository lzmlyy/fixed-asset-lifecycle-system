package com.example.asset.inventory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("inventory_result")
public class InventoryResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private String resultType;
    private String expectedValue;
    private String actualValue;
}