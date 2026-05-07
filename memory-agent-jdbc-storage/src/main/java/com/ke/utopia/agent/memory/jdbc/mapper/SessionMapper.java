package com.ke.utopia.agent.memory.jdbc.mapper;

import com.ke.utopia.agent.memory.jdbc.entity.SessionEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.special.InsertListMapper;

import java.util.List;

public interface SessionMapper extends Mapper<SessionEntity>, InsertListMapper<SessionEntity> {

    @Select("SELECT * FROM ma_session WHERE session_id = #{sessionId}")
    SessionEntity findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM ma_session WHERE user_id = #{userId} ORDER BY started_at DESC")
    List<SessionEntity> findByUserId(@Param("userId") String userId);

    @Select("UPDATE ma_session SET status = 'CLOSED', ended_at = NOW(6) WHERE session_id = #{sessionId}")
    int updateStatusToClosed(@Param("sessionId") String sessionId);
}
