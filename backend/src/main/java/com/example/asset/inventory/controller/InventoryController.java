package com.example.asset.inventory.controller;

import com.example.asset.common.PageResult;
import com.example.asset.common.Result;
import com.example.asset.inventory.dto.InventoryRecordUpdateRequest;
import com.example.asset.inventory.dto.InventoryTaskCreateRequest;
import com.example.asset.inventory.dto.InventoryTaskQueryRequest;
import com.example.asset.inventory.dto.UpdateInventoryRecordRequest;
import com.example.asset.inventory.service.InventoryService;
import com.example.asset.inventory.vo.InventoryReportVO;
import com.example.asset.inventory.vo.InventoryRecordVO;
import com.example.asset.inventory.vo.InventoryTaskVO;
import com.example.asset.inventory.vo.LookupVO;
import com.example.asset.inventory.vo.QuickScanVO;
import com.example.asset.permission.annotation.RequirePermission;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

// TODO: 缺少 inventory:execute 和 inventory:delete 权限码，执行/删除操作暂用 inventory:create 权限兜底
@RestController
@RequestMapping("/api/inventory")
@Validated
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/tasks/page")
    @RequirePermission("inventory:view")
    public Result<PageResult<InventoryTaskVO>> page(@Valid InventoryTaskQueryRequest query) {
        return Result.success(inventoryService.page(query));
    }

    @PostMapping("/tasks")
    @RequirePermission("inventory:create")
    public Result<Long> create(@Valid @RequestBody InventoryTaskCreateRequest request) {
        return Result.success(inventoryService.create(request));
    }

    @GetMapping("/tasks/{id}")
    @RequirePermission("inventory:view")
    public Result<InventoryTaskVO> detail(@PathVariable Long id) {
        return Result.success(inventoryService.detail(id));
    }

    @GetMapping("/tasks/{id}/records")
    @RequirePermission("inventory:view")
    public Result<List<InventoryRecordVO>> records(@PathVariable Long id) {
        return Result.success(inventoryService.getRecords(id));
    }

    @PutMapping("/records/{recordId}")
    @RequirePermission("inventory:create")
    public Result<Void> updateRecord(@PathVariable Long recordId,
                                     @Valid @RequestBody InventoryRecordUpdateRequest request) {
        inventoryService.updateRecord(recordId, request);
        return Result.success();
    }

    @PutMapping("/tasks/{id}/complete")
    @RequirePermission("inventory:create")
    public Result<Void> complete(@PathVariable Long id) {
        inventoryService.complete(id);
        return Result.success();
    }

    @PostMapping("/tasks/{id}/start")
    @RequirePermission("inventory:create")
    public Result<Void> startTask(@PathVariable Long id) {
        inventoryService.startTask(id);
        return Result.success();
    }

    @PutMapping("/records")
    @RequirePermission("inventory:create")
    public Result<Void> scanRecord(@Valid @RequestBody UpdateInventoryRecordRequest request) {
        inventoryService.scanRecord(request);
        return Result.success();
    }

    @PostMapping("/tasks/{id}/batch-scan")
    @RequirePermission("inventory:create")
    public Result<Void> batchScan(@PathVariable Long id) {
        inventoryService.batchScanPending(id);
        return Result.success();
    }

    @GetMapping("/tasks/{id}/report")
    @RequirePermission("inventory:view")
    public Result<InventoryReportVO> getReport(@PathVariable Long id) {
        return Result.success(inventoryService.getReport(id));
    }

    @DeleteMapping("/tasks/{id}")
    @RequirePermission("inventory:create")
    public Result<Void> deleteTask(@PathVariable Long id) {
        inventoryService.deleteTask(id);
        return Result.success();
    }

    @PostMapping("/tasks/{id}/restart")
    @RequirePermission("inventory:create")
    public Result<Void> restartTask(@PathVariable Long id) {
        inventoryService.restartTask(id);
        return Result.success();
    }

    @GetMapping("/departments")
    public Result<List<String>> departments() {
        return Result.success(inventoryService.getDistinctDepartments());
    }

    @GetMapping("/locations")
    @RequirePermission("inventory:view")
    public Result<List<String>> locations() {
        return Result.success(inventoryService.getDistinctLocations());
    }

    @GetMapping("/records/lookup")
    @RequirePermission("inventory:view")
    public Result<LookupVO> lookup(@RequestParam Long taskId,
                                   @RequestParam String assetCode) {
        LookupVO vo = inventoryService.lookupRecord(taskId, assetCode);
        if (vo == null) {
            return Result.success(null);
        }
        return Result.success(vo);
    }

    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission("inventory:create")
    public Result<Map<String, String>> ocr(@RequestPart("image") MultipartFile image) {
        String text = inventoryService.performOcr(image);
        return Result.success(Map.of("text", text));
    }

    @GetMapping("/quick-lookup")
    @RequirePermission("inventory:view")
    public Result<QuickScanVO> quickLookup(@RequestParam String assetCode) {
        QuickScanVO vo = inventoryService.quickLookup(assetCode);
        if (vo == null) {
            return Result.success(null);
        }
        return Result.success(vo);
    }

    @PostMapping("/quick-scan")
    @RequirePermission("inventory:create")
    public Result<Void> quickScan(@RequestParam String assetCode,
                                  @RequestParam(required = false) String actualLocation,
                                  @RequestParam(required = false) String actualKeeper,
                                  @RequestParam(required = false) String remark) {
        inventoryService.quickScan(assetCode, actualLocation, actualKeeper, remark);
        return Result.success();
    }
}
