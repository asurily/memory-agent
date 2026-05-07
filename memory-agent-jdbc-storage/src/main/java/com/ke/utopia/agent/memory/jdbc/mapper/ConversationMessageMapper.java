package com.ke.utopia.agent.memory.jdbc.mapper;

import com.ke.utopia.agent.memory.jdbc.entity.ConversationMessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.special.InsertListMapper;

import java.util.List;

public interface ConversationMessageMapper extends Mapper<ConversationMessageEntity>, InsertListMapper<ConversationMessageEntity> {

    @Select("SELECT * FROM ma_conversation_message WHERE message_id = #{messageId}")
    ConversationMessageEntity findByMessageId(@Param("messageId") String messageId);

    @Select("SELECT * FROM ma_conversation_message WHERE session_id = #{sessionId} ORDER BY timestamp ASC")
    List<ConversationMessageEntity> findBySessionIdOrderByTimestamp(@Param("sessionId") String sessionId);

    @Select("SELECT * FROM ma_conversation_message WHERE session_id = #{sessionId} ORDER BY timestamp DESC LIMIT #{limit}")
    List<ConversationMessageEntity> findRecentBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ma_conversation_message WHERE session_id = #{sessionId}")
    int countBySessionId(@Param("sessionId") String sessionId);
}
