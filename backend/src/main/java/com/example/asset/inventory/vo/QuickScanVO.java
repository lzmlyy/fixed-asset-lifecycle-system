package com.example.asset.inventory.vo;

import java.math.BigDecimal;

public class QuickScanVO {
    private Long assetId;
    private String assetCode;
    private String assetName;
    private String categoryName;
    private String department;
    private String keeper;
    private String location;
    private String status;
    private BigDecimal originalValue;
    private BigDecimal netValue;
    private String remark;
    private boolean alreadyScanned;

    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getKeeper() { return keeper; }
    public void setKeeper(String keeper) { this.keeper = keeper; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getOriginalValue() { return originalValue; }
    public void setOriginalValue(BigDecimal originalValue) { this.originalValue = originalValue; }
    public BigDecimal getNetValue() { return netValue; }
    public void setNetValue(BigDecimal netValue) { this.netValue = netValue; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public boolean isAlreadyScanned() { return alreadyScanned; }
    public void setAlreadyScanned(boolean alreadyScanned) { this.alreadyScanned = alreadyScanned; }
}
