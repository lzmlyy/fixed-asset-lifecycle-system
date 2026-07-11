package com.example.asset.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.asset.asset.entity.Asset;
import com.example.asset.asset.mapper.AssetMapper;
import com.example.asset.common.BusinessException;
import com.example.asset.common.PageResult;
import com.example.asset.common.ResultCode;
import com.example.asset.context.UserContext;
import com.example.asset.inventory.dto.InventoryRecordUpdateRequest;
import com.example.asset.inventory.dto.InventoryTaskCreateRequest;
import com.example.asset.inventory.dto.InventoryTaskQueryRequest;
import com.example.asset.inventory.dto.UpdateInventoryRecordRequest;
import com.example.asset.inventory.entity.InventoryRecord;
import com.example.asset.inventory.entity.InventoryResult;
import com.example.asset.inventory.entity.InventoryTask;
import com.example.asset.inventory.mapper.InventoryRecordMapper;
import com.example.asset.inventory.mapper.InventoryResultMapper;
import com.example.asset.inventory.mapper.InventoryTaskMapper;
import com.example.asset.inventory.vo.InventoryReportVO;
import com.example.asset.inventory.vo.InventoryRecordVO;
import com.example.asset.inventory.vo.InventoryResultVO;
import com.example.asset.inventory.vo.InventoryTaskVO;
import com.example.asset.inventory.vo.LookupVO;
import com.example.asset.inventory.vo.QuickScanVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InventoryService {

    private static final DateTimeFormatter TASK_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryTaskMapper inventoryTaskMapper;
    private final InventoryRecordMapper inventoryRecordMapper;
    private final InventoryResultMapper inventoryResultMapper;
    private final AssetMapper assetMapper;

    public InventoryService(InventoryTaskMapper inventoryTaskMapper,
                            InventoryRecordMapper inventoryRecordMapper,
                            InventoryResultMapper inventoryResultMapper,
                            AssetMapper assetMapper) {
        this.inventoryTaskMapper = inventoryTaskMapper;
        this.inventoryRecordMapper = inventoryRecordMapper;
        this.inventoryResultMapper = inventoryResultMapper;
        this.assetMapper = assetMapper;
    }

    public PageResult<InventoryTaskVO> page(InventoryTaskQueryRequest query) {
        Page<InventoryTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<InventoryTask> wrapper = new LambdaQueryWrapper<InventoryTask>()
                .eq(query.getStatus() != null && !query.getStatus().isEmpty(),
                        InventoryTask::getStatus, query.getStatus())
                .eq(query.getScopeType() != null && !query.getScopeType().isEmpty(),
                        InventoryTask::getScopeType, query.getScopeType())
                .orderByDesc(InventoryTask::getCreatedAt);
        Page<InventoryTask> result = inventoryTaskMapper.selectPage(page, wrapper);
        List<InventoryTaskVO> vos = result.getRecords().stream()
                .map(this::toTaskVO)
                .collect(Collectors.toList());
        return new PageResult<>(vos, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(InventoryTaskCreateRequest request) {
        validateScope(request);

        String prefix = "PD" + LocalDateTime.now().format(TASK_CODE_FORMATTER);
        String taskCode = generateTaskCode(prefix);

        InventoryTask task = new InventoryTask();
        task.setTaskCode(taskCode);
        task.setTaskName(request.getTaskName());
        task.setScopeType(request.getScopeType());
        task.setDepartment(request.getDepartment());
        task.setLocation(request.getLocation());
        task.setStatus("IN_PROGRESS");
        task.setStartTime(LocalDateTime.now());
        task.setCreatedBy(UserContext.getUserId());
        inventoryTaskMapper.insert(task);

        LambdaQueryWrapper<Asset> assetWrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getDeleted, 0);
        if ("DEPARTMENT".equals(request.getScopeType())) {
            assetWrapper.eq(Asset::getDepartment, request.getDepartment());
        } else if ("LOCATION".equals(request.getScopeType())) {
            assetWrapper.eq(Asset::getLocation, request.getLocation());
        }
        List<Asset> assets = assetMapper.selectList(assetWrapper);

        for (Asset asset : assets) {
            InventoryRecord record = new InventoryRecord();
            record.setTaskId(task.getId());
            record.setAssetId(asset.getId());
            record.setExpectedLocation(asset.getLocation());
            record.setExpectedKeeper(asset.getKeeper());
            inventoryRecordMapper.insert(record);
        }

        return task.getId();
    }

    public InventoryTaskVO detail(Long id) {
        InventoryTask task = inventoryTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点任务不存在");
        }
        return toTaskVO(task);
    }

    public List<InventoryRecordVO> getRecords(Long taskId) {
        List<InventoryRecordVO> records = inventoryRecordMapper.selectRecordsByTaskId(taskId);
        // 加载每个记录的盘点结果
        for (InventoryRecordVO record : records) {
            record.setResults(inventoryResultMapper.findByRecordId(record.getId()));
        }
        return records;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRecord(Long recordId, InventoryRecordUpdateRequest request) {
        InventoryRecord record = inventoryRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点明细不存在");
        }
        record.setActualLocation(request.getActualLocation());
        record.setActualKeeper(request.getActualKeeper());
        record.setResult(request.getResult());
        record.setRemark(request.getRemark());
        record.setScannedAt(LocalDateTime.now());
        inventoryRecordMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long taskId) {
        InventoryTask task = inventoryTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点任务不存在");
        }
        if (!"IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "任务状态不允许完成");
        }
        int unrecorded = inventoryRecordMapper.countUnrecorded(taskId);
        if (unrecorded > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "存在 " + unrecorded + " 条未录入结果的明细，无法完成");
        }
        task.setStatus("COMPLETED");
        task.setEndTime(LocalDateTime.now());
        inventoryTaskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startTask(Long taskId) {
        InventoryTask task = inventoryTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点任务不存在");
        }
        if (!"DRAFT".equals(task.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅草稿状态的任务可以开始");
        }

        List<Asset> assets = queryAssetsByScope(task.getScopeType(), task.getDepartment(), task.getLocation());
        if (assets.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "盘点范围内无资产");
        }

        for (Asset asset : assets) {
            InventoryRecord record = new InventoryRecord();
            record.setTaskId(taskId);
            record.setAssetId(asset.getId());
            record.setExpectedLocation(asset.getLocation());
            record.setExpectedKeeper(asset.getKeeper());
            record.setResult("PENDING");
            inventoryRecordMapper.insert(record);
        }

        task.setStatus("IN_PROGRESS");
        task.setStartTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        inventoryTaskMapper.updateById(task);
    }

    @Transactional(rollbackFor = Exception.class)
    public void scanRecord(UpdateInventoryRecordRequest req) {
        InventoryRecord record = inventoryRecordMapper.selectById(req.getRecordId());
        if (record == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点记录不存在");
        }

        InventoryTask task = inventoryTaskMapper.selectById(record.getTaskId());
        if (task == null || !"IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "盘点任务不在执行中状态");
        }

        if (req.getActualLocation() != null) {
            record.setActualLocation(req.getActualLocation());
        }
        if (req.getActualKeeper() != null) {
            record.setActualKeeper(req.getActualKeeper());
        }

        // 清除旧结果
        inventoryResultMapper.delete(new LambdaQueryWrapper<InventoryResult>()
                .eq(InventoryResult::getRecordId, req.getRecordId()));

        if (req.getResult() != null) {
            record.setResult(req.getResult());
            // 手动设置结果时，生成单个结果
            InventoryResult result = new InventoryResult();
            result.setRecordId(req.getRecordId());
            result.setResultType(req.getResult());
            result.setExpectedValue("");
            result.setActualValue("");
            inventoryResultMapper.insert(result);
        } else {
            String expLoc = record.getExpectedLocation();
            String expKeeper = record.getExpectedKeeper();
            String actLoc = req.getActualLocation();
            String actKeeper = req.getActualKeeper();

            boolean locationChecked = actLoc != null && !actLoc.isEmpty();
            boolean keeperChecked = actKeeper != null && !actKeeper.isEmpty();
            boolean locationOk = !locationChecked || actLoc.trim().equals(expLoc == null ? "" : expLoc.trim());
            boolean keeperOk = !keeperChecked || actKeeper.trim().equals(expKeeper == null ? "" : expKeeper.trim());

            List<InventoryResult> results = new ArrayList<>();

            if (locationOk && keeperOk) {
                record.setResult("NORMAL");
                InventoryResult r = new InventoryResult();
                r.setRecordId(req.getRecordId());
                r.setResultType("NORMAL");
                r.setExpectedValue((expLoc != null ? expLoc : "") + " / " + (expKeeper != null ? expKeeper : ""));
                r.setActualValue((actLoc != null ? actLoc : "") + " / " + (actKeeper != null ? actKeeper : ""));
                results.add(r);
            } else {
                if (!locationOk) {
                    InventoryResult r = new InventoryResult();
                    r.setRecordId(req.getRecordId());
                    r.setResultType("LOCATION_MISMATCH");
                    r.setExpectedValue(expLoc);
                    r.setActualValue(actLoc);
                    results.add(r);
                }
                if (!keeperOk) {
                    InventoryResult r = new InventoryResult();
                    r.setRecordId(req.getRecordId());
                    r.setResultType("KEEPER_MISMATCH");
                    r.setExpectedValue(expKeeper);
                    r.setActualValue(actKeeper);
                    results.add(r);
                }
                // 汇总结果
                if (!locationOk && !keeperOk) {
                    record.setResult("MISMATCH");
                } else if (!locationOk) {
                    record.setResult("LOCATION_MISMATCH");
                } else {
                    record.setResult("KEEPER_MISMATCH");
                }
            }

            for (InventoryResult r : results) {
                inventoryResultMapper.insert(r);
            }
        }

        record.setScannedAt(LocalDateTime.now());
        record.setRemark(req.getRemark());
        inventoryRecordMapper.updateById(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public void batchScanPending(Long taskId) {
        InventoryTask task = inventoryTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点任务不存在");
        }
        if (!"IN_PROGRESS".equals(task.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅执行中的任务可以批量扫描");
        }

        LambdaQueryWrapper<InventoryRecord> wrapper = new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTaskId, taskId)
                .eq(InventoryRecord::getResult, "PENDING");
        List<InventoryRecord> pending = inventoryRecordMapper.selectList(wrapper);

        for (InventoryRecord r : pending) {
            r.setActualLocation(r.getExpectedLocation());
            r.setActualKeeper(r.getExpectedKeeper());
            r.setResult("NORMAL");
            r.setScannedAt(LocalDateTime.now());
            r.setRemark("批量扫描 - 自动确认");
            inventoryRecordMapper.updateById(r);

            // 清除旧结果，生成正常结果
            inventoryResultMapper.delete(new LambdaQueryWrapper<InventoryResult>()
                    .eq(InventoryResult::getRecordId, r.getId()));
            InventoryResult result = new InventoryResult();
            result.setRecordId(r.getId());
            result.setResultType("NORMAL");
            result.setExpectedValue((r.getExpectedLocation() != null ? r.getExpectedLocation() : "") + " / " + (r.getExpectedKeeper() != null ? r.getExpectedKeeper() : ""));
            result.setActualValue((r.getActualLocation() != null ? r.getActualLocation() : "") + " / " + (r.getActualKeeper() != null ? r.getActualKeeper() : ""));
            inventoryResultMapper.insert(result);
        }
    }

    public InventoryReportVO getReport(Long taskId) {
        InventoryTaskVO taskVO = detail(taskId);
        List<InventoryRecordVO> records = getRecords(taskId);

        InventoryReportVO report = new InventoryReportVO();
        report.setTask(taskVO);
        report.setTotalCount(records.size());
        report.setScannedCount(records.stream().filter(r -> !"PENDING".equals(r.getResult())).count());
        report.setNormalCount(records.stream().filter(r -> "NORMAL".equals(r.getResult())).count());
        report.setLocationMismatchCount(records.stream().filter(r -> "LOCATION_MISMATCH".equals(r.getResult()) || "MISMATCH".equals(r.getResult())).count());
        report.setKeeperMismatchCount(records.stream().filter(r -> "KEEPER_MISMATCH".equals(r.getResult()) || "MISMATCH".equals(r.getResult())).count());
        report.setMissingCount(records.stream().filter(r -> "MISSING".equals(r.getResult())).count());
        report.setDetails(records);
        return report;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long taskId) {
        InventoryTask task = inventoryTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点任务不存在");
        }

        LambdaQueryWrapper<InventoryRecord> recordWrapper = new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTaskId, taskId);
        List<InventoryRecord> records = inventoryRecordMapper.selectList(recordWrapper);
        for (InventoryRecord r : records) {
            LambdaQueryWrapper<InventoryResult> resultWrapper = new LambdaQueryWrapper<InventoryResult>()
                    .eq(InventoryResult::getRecordId, r.getId());
            inventoryResultMapper.delete(resultWrapper);
        }
        inventoryRecordMapper.delete(recordWrapper);
        inventoryTaskMapper.deleteById(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restartTask(Long taskId) {
        InventoryTask task = inventoryTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "盘点任务不存在");
        }
        if (!"COMPLETED".equals(task.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "仅已完成的任务可以重新盘点");
        }

        LambdaQueryWrapper<InventoryRecord> recordWrapper = new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTaskId, taskId);
        List<InventoryRecord> records = inventoryRecordMapper.selectList(recordWrapper);
        for (InventoryRecord r : records) {
            r.setActualLocation(null);
            r.setActualKeeper(null);
            r.setResult("PENDING");
            r.setScannedAt(null);
            r.setRemark(null);
            inventoryRecordMapper.updateById(r);

            LambdaQueryWrapper<InventoryResult> resultWrapper = new LambdaQueryWrapper<InventoryResult>()
                    .eq(InventoryResult::getRecordId, r.getId());
            inventoryResultMapper.delete(resultWrapper);
        }

        task.setStatus("IN_PROGRESS");
        task.setEndTime(null);
        task.setStartTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        inventoryTaskMapper.updateById(task);
    }

    public List<String> getDistinctDepartments() {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .select(Asset::getDepartment)
                .isNotNull(Asset::getDepartment)
                .ne(Asset::getDepartment, "")
                .eq(Asset::getDeleted, 0)
                .groupBy(Asset::getDepartment);
        List<Asset> assets = assetMapper.selectList(wrapper);
        List<String> result = new ArrayList<>();
        for (Asset a : assets) {
            if (a.getDepartment() != null && !a.getDepartment().isEmpty()) {
                result.add(a.getDepartment());
            }
        }
        return result;
    }

    public List<String> getDistinctLocations() {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .select(Asset::getLocation)
                .isNotNull(Asset::getLocation)
                .ne(Asset::getLocation, "")
                .eq(Asset::getDeleted, 0)
                .groupBy(Asset::getLocation);
        List<Asset> assets = assetMapper.selectList(wrapper);
        List<String> result = new ArrayList<>();
        for (Asset a : assets) {
            if (a.getLocation() != null && !a.getLocation().isEmpty()) {
                result.add(a.getLocation());
            }
        }
        return result;
    }

    public LookupVO lookupRecord(Long taskId, String assetCode) {
        Asset asset = assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getAssetCode, assetCode)
                .eq(Asset::getDeleted, 0));

        if (asset == null) {
            return null;
        }

        InventoryRecord record = inventoryRecordMapper.selectOne(new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTaskId, taskId)
                .eq(InventoryRecord::getAssetId, asset.getId()));

        if (record == null) {
            return new LookupVO(null, asset.getAssetCode(), asset.getAssetName(),
                    null, null, "NOT_IN_SCOPE", false);
        }

        String resultStatus = record.getResult();
        if (resultStatus == null || "PENDING".equals(resultStatus)) {
            resultStatus = "PENDING";
        }

        boolean scanned = resultStatus != null && !"PENDING".equals(resultStatus);

        return new LookupVO(record.getId(), asset.getAssetCode(), asset.getAssetName(),
                record.getExpectedLocation(), record.getExpectedKeeper(),
                resultStatus, scanned);
    }

    public QuickScanVO quickLookup(String assetCode) {
        Asset asset = assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getAssetCode, assetCode)
                .eq(Asset::getDeleted, 0));

        if (asset == null) {
            return null;
        }

        QuickScanVO vo = new QuickScanVO();
        vo.setAssetId(asset.getId());
        vo.setAssetCode(asset.getAssetCode());
        vo.setAssetName(asset.getAssetName());
        vo.setDepartment(asset.getDepartment());
        vo.setKeeper(asset.getKeeper());
        vo.setLocation(asset.getLocation());
        vo.setStatus(asset.getStatus());
        vo.setOriginalValue(asset.getOriginalValue());
        vo.setNetValue(asset.getNetValue());
        vo.setRemark(asset.getRemark());

        InventoryRecord existing = inventoryRecordMapper.selectOne(
                new LambdaQueryWrapper<InventoryRecord>()
                        .eq(InventoryRecord::getAssetId, asset.getId())
                        .orderByDesc(InventoryRecord::getScannedAt)
                        .last("LIMIT 1"));
        vo.setAlreadyScanned(existing != null && existing.getScannedAt() != null);

        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void quickScan(String assetCode, String actualLocation, String actualKeeper, String remark) {
        Asset asset = assetMapper.selectOne(new LambdaQueryWrapper<Asset>()
                .eq(Asset::getAssetCode, assetCode)
                .eq(Asset::getDeleted, 0));

        if (asset == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "资产不存在");
        }

        InventoryTask quickTask = inventoryTaskMapper.selectOne(
                new LambdaQueryWrapper<InventoryTask>()
                        .eq(InventoryTask::getTaskName, "快速盘点")
                        .eq(InventoryTask::getStatus, "IN_PROGRESS")
                        .orderByDesc(InventoryTask::getCreatedAt)
                        .last("LIMIT 1"));

        if (quickTask == null) {
            quickTask = new InventoryTask();
            quickTask.setTaskCode(generateTaskCode("QP"));
            quickTask.setTaskName("快速盘点");
            quickTask.setScopeType("ALL");
            quickTask.setStatus("IN_PROGRESS");
            quickTask.setStartTime(LocalDateTime.now());
            quickTask.setCreatedBy(UserContext.getUserId());
            inventoryTaskMapper.insert(quickTask);
        }

        InventoryRecord record = inventoryRecordMapper.selectOne(
                new LambdaQueryWrapper<InventoryRecord>()
                        .eq(InventoryRecord::getTaskId, quickTask.getId())
                        .eq(InventoryRecord::getAssetId, asset.getId()));

        if (record == null) {
            record = new InventoryRecord();
            record.setTaskId(quickTask.getId());
            record.setAssetId(asset.getId());
            record.setExpectedLocation(asset.getLocation());
            record.setExpectedKeeper(asset.getKeeper());
        }

        record.setActualLocation(actualLocation != null ? actualLocation : asset.getLocation());
        record.setActualKeeper(actualKeeper != null ? actualKeeper : asset.getKeeper());
        record.setResult("NORMAL");
        record.setScannedAt(LocalDateTime.now());
        record.setRemark(remark);
        if (record.getId() != null) {
            inventoryRecordMapper.updateById(record);
        } else {
            inventoryRecordMapper.insert(record);
        }
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String OCR_SERVICE_URL = "http://127.0.0.1:8866/";

    public String performOcr(MultipartFile imageFile) {
        try {
            byte[] imageBytes = imageFile.getBytes();
            URL url = URI.create(OCR_SERVICE_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(imageBytes);
            }
            String response = new String(conn.getInputStream().readAllBytes());
            JsonNode node = objectMapper.readTree(response);
            if (node.get("success").asBoolean()) {
                String text = node.get("text").asText();
                log.info("PaddleOCR completed, text length: {}", text.length());
                return text.trim();
            }
            String error = node.has("error") ? node.get("error").asText() : "unknown";
            log.error("PaddleOCR failed: {}", error);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "OCR 识别失败: " + error);
        } catch (IOException e) {
            log.error("OCR service call failed", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "OCR 服务不可用，请确认 PaddleOCR 服务已启动");
        }
    }

    private void validateScope(InventoryTaskCreateRequest request) {
        if ("DEPARTMENT".equals(request.getScopeType())
                && (request.getDepartment() == null || request.getDepartment().isEmpty())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "按部门盘点时部门不能为空");
        }
        if ("LOCATION".equals(request.getScopeType())
                && (request.getLocation() == null || request.getLocation().isEmpty())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "按地点盘点时地点不能为空");
        }
    }

    private InventoryTaskVO toTaskVO(InventoryTask task) {
        InventoryTaskVO vo = new InventoryTaskVO();
        vo.setId(task.getId());
        vo.setTaskCode(task.getTaskCode());
        vo.setTaskName(task.getTaskName());
        vo.setScopeType(task.getScopeType());
        vo.setDepartment(task.getDepartment());
        vo.setLocation(task.getLocation());
        vo.setStatus(task.getStatus());
        vo.setStartTime(task.getStartTime());
        vo.setEndTime(task.getEndTime());
        vo.setCreatedBy(task.getCreatedBy());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());

        LambdaQueryWrapper<InventoryRecord> totalWrapper = new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTaskId, task.getId());
        Long total = inventoryRecordMapper.selectCount(totalWrapper);
        vo.setTotalRecords(total != null ? total.intValue() : 0);

        LambdaQueryWrapper<InventoryRecord> completedWrapper = new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTaskId, task.getId())
                .isNotNull(InventoryRecord::getResult);
        Long completed = inventoryRecordMapper.selectCount(completedWrapper);
        vo.setCompletedRecords(completed != null ? completed.intValue() : 0);

        vo.setTotalCount(total != null ? total : 0L);
        LambdaQueryWrapper<InventoryRecord> scannedWrapper = new LambdaQueryWrapper<InventoryRecord>()
                .eq(InventoryRecord::getTaskId, task.getId())
                .isNotNull(InventoryRecord::getResult)
                .ne(InventoryRecord::getResult, "PENDING");
        Long scanned = inventoryRecordMapper.selectCount(scannedWrapper);
        vo.setScannedCount(scanned != null ? scanned : 0L);

        return vo;
    }

    private String generateTaskCode(String prefix) {
        LambdaQueryWrapper<InventoryTask> wrapper = new LambdaQueryWrapper<InventoryTask>()
                .likeRight(InventoryTask::getTaskCode, prefix)
                .orderByDesc(InventoryTask::getTaskCode)
                .last("LIMIT 1");
        InventoryTask latest = inventoryTaskMapper.selectOne(wrapper);
        int nextSequence = 1;
        if (latest != null && latest.getTaskCode() != null
                && latest.getTaskCode().length() >= prefix.length() + 4) {
            String sequencePart = latest.getTaskCode().substring(prefix.length());
            nextSequence = Integer.parseInt(sequencePart) + 1;
        }
        return prefix + String.format("%04d", nextSequence);
    }

    private List<Asset> queryAssetsByScope(String scopeType, String department, String location) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getDeleted, 0);

        if (scopeType != null) {
            switch (scopeType) {
                case "DEPARTMENT":
                    if (department != null && !department.isEmpty()) {
                        wrapper.eq(Asset::getDepartment, department);
                    }
                    break;
                case "LOCATION":
                    if (location != null && !location.isEmpty()) {
                        wrapper.eq(Asset::getLocation, location);
                    }
                    break;
                case "ALL":
                    break;
            }
        }
        return assetMapper.selectList(wrapper);
    }
}
