package com.example.asset.inventory.vo;

public class LookupVO {
    private Long recordId;
    private String assetCode;
    private String assetName;
    private String expectedLocation;
    private String expectedKeeper;
    private String result;
    private boolean scanned;

    public LookupVO() {}

    public LookupVO(Long recordId, String assetCode, String assetName,
                    String expectedLocation, String expectedKeeper,
                    String result, boolean scanned) {
        this.recordId = recordId;
        this.assetCode = assetCode;
        this.assetName = assetName;
        this.expectedLocation = expectedLocation;
        this.expectedKeeper = expectedKeeper;
        this.result = result;
        this.scanned = scanned;
    }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }
    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getExpectedLocation() { return expectedLocation; }
    public void setExpectedLocation(String expectedLocation) { this.expectedLocation = expectedLocation; }
    public String getExpectedKeeper() { return expectedKeeper; }
    public void setExpectedKeeper(String expectedKeeper) { this.expectedKeeper = expectedKeeper; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public boolean isScanned() { return scanned; }
    public void setScanned(boolean scanned) { this.scanned = scanned; }
}
