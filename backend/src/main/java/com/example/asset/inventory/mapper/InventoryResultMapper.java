package com.example.asset.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.asset.inventory.entity.InventoryResult;
import com.example.asset.inventory.vo.InventoryResultVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface InventoryResultMapper extends BaseMapper<InventoryResult> {

    @Select("SELECT id, record_id AS recordId, result_type AS resultType, " +
            "expected_value AS expectedValue, actual_value AS actualValue " +
            "FROM inventory_result WHERE record_id = #{recordId}")
    List<InventoryResultVO> findByRecordId(@Param("recordId") Long recordId);

    @Select("SELECT id, record_id AS recordId, result_type AS resultType, " +
            "expected_value AS expectedValue, actual_value AS actualValue " +
            "FROM inventory_result WHERE record_id IN " +
            "(SELECT id FROM inventory_record WHERE task_id = #{taskId})")
    List<InventoryResultVO> findByTaskId(@Param("taskId") Long taskId);
}