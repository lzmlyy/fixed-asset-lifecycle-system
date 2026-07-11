package com.example.asset.dashboard.service;

import com.example.asset.asset.mapper.AssetMapper;
import com.example.asset.dashboard.vo.DashboardStatsVO;
import com.example.asset.dashboard.vo.DepartmentRankingVO;
import com.example.asset.dashboard.vo.NameValueVO;
import com.example.asset.dashboard.vo.TrendPointVO;
import com.example.asset.depreciation.mapper.DepreciationReportMapper;
import com.example.asset.depreciation.vo.DepreciationTrendVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AssetMapper assetMapper;
    private final DepreciationReportMapper depreciationReportMapper;

    public DashboardService(AssetMapper assetMapper, DepreciationReportMapper depreciationReportMapper) {
        this.assetMapper = assetMapper;
        this.depreciationReportMapper = depreciationReportMapper;
    }

    public DashboardStatsVO stats() {
        DashboardStatsVO statsVO = assetMapper.selectDashboardStats();
        if (statsVO.getTotalOriginalValue() == null) {
            statsVO.setTotalOriginalValue(BigDecimal.ZERO);
        }
        if (statsVO.getTotalAccumulatedDepreciation() == null) {
            statsVO.setTotalAccumulatedDepreciation(BigDecimal.ZERO);
        }
        if (statsVO.getTotalNetValue() == null) {
            statsVO.setTotalNetValue(BigDecimal.ZERO);
        }
        return statsVO;
    }

    public List<NameValueVO> categoryDistribution() {
        return assetMapper.selectCategoryDistribution();
    }

    public List<DepartmentRankingVO> departmentRanking() {
        return assetMapper.selectDepartmentRanking();
    }

    public List<TrendPointVO> depreciationTrend() {
        // 生成最近 12 个月的月份列表
        List<String> months = new ArrayList<>();
        YearMonth current = YearMonth.now();
        for (int i = 11; i >= 0; i--) {
            months.add(current.minusMonths(i).format(MONTH_FORMATTER));
        }

        // 从 depreciation_record 表获取真实趋势数据
        List<DepreciationTrendVO> dbTrend = depreciationReportMapper.selectDepreciationTrend(months);
        Map<String, DepreciationTrendVO> dbMap = dbTrend.stream()
                .collect(Collectors.toMap(DepreciationTrendVO::getMonth, v -> v));

        // 计算当前折旧额
        BigDecimal currentMonthlyDep = depreciationReportMapper.selectComputedMonthlyDepreciation();
        if (currentMonthlyDep == null) {
            currentMonthlyDep = BigDecimal.ZERO;
        }
        currentMonthlyDep = currentMonthlyDep.setScale(2, RoundingMode.HALF_UP);

        List<TrendPointVO> points = new ArrayList<>();
        for (String month : months) {
            DepreciationTrendVO existing = dbMap.get(month);
            TrendPointVO point = new TrendPointVO();
            point.setMonth(month);
            if (existing != null) {
                point.setMonthlyDepreciation(existing.getMonthlyDepreciation());
                point.setAccumulatedDepreciation(existing.getAccumulatedDepreciation());
                point.setValue(existing.getMonthlyDepreciation());
            } else {
                point.setMonthlyDepreciation(currentMonthlyDep);
                point.setAccumulatedDepreciation(BigDecimal.ZERO);
                point.setValue(currentMonthlyDep);
            }
            points.add(point);
        }
        return points;
    }
}
