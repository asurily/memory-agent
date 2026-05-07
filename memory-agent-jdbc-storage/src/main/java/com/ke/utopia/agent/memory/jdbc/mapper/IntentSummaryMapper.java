package com.ke.utopia.agent.memory.jdbc.mapper;

import com.ke.utopia.agent.memory.jdbc.entity.IntentSummaryEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.special.InsertListMapper;

import java.util.List;

public interface IntentSummaryMapper extends Mapper<IntentSummaryEntity>, InsertListMapper<IntentSummaryEntity> {

    @Select("SELECT * FROM ma_intent_summary WHERE summary_id = #{summaryId}")
    IntentSummaryEntity findBySummaryId(@Param("summaryId") String summaryId);

    @Select("SELECT * FROM ma_intent_summary WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<IntentSummaryEntity> findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM ma_intent_summary WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<IntentSummaryEntity> findByUserId(@Param("userId") String userId);
}
