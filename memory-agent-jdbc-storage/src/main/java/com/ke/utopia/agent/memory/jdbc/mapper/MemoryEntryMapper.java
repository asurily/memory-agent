package com.ke.utopia.agent.memory.jdbc.mapper;

import com.ke.utopia.agent.memory.jdbc.entity.MemoryEntryEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import tk.mybatis.mapper.common.Mapper;
import tk.mybatis.mapper.common.special.InsertListMapper;

import java.util.List;

public interface MemoryEntryMapper extends Mapper<MemoryEntryEntity>, InsertListMapper<MemoryEntryEntity> {

    @Select("SELECT * FROM ma_memory_entry WHERE memory_id = #{memoryId}")
    MemoryEntryEntity findByMemoryId(@Param("memoryId") String memoryId);

    @Select("SELECT * FROM ma_memory_entry WHERE user_id = #{userId} AND type = #{type}")
    List<MemoryEntryEntity> findByUserIdAndType(@Param("userId") String userId, @Param("type") String type);

    @Select("SELECT * FROM ma_memory_entry WHERE user_id = #{userId} AND tier = #{tier}")
    List<MemoryEntryEntity> findByUserIdAndTier(@Param("userId") String userId, @Param("tier") String tier);

    @Select("SELECT COUNT(*) FROM ma_memory_entry WHERE user_id = #{userId} AND tier = #{tier}")
    int countByUserIdAndTier(@Param("userId") String userId, @Param("tier") String tier);

    @Select("SELECT COUNT(*) FROM ma_memory_entry WHERE user_id = #{userId} AND content = #{content}")
    int countByUserIdAndContent(@Param("userId") String userId, @Param("content") String content);

    @Select("SELECT * FROM ma_memory_entry WHERE memory_id = #{memoryId} AND user_id = #{userId}")
    MemoryEntryEntity findByMemoryIdAndUserId(@Param("memoryId") String memoryId, @Param("userId") String userId);

    @Select("UPDATE ma_memory_entry SET tier = #{tier} WHERE memory_id = #{memoryId}")
    int updateTierByMemoryId(@Param("memoryId") String memoryId, @Param("tier") String tier);

    @Select("DELETE FROM ma_memory_entry WHERE memory_id = #{memoryId}")
    int deleteByMemoryId(@Param("memoryId") String memoryId);

    @Select("SELECT DISTINCT user_id FROM ma_memory_entry")
    List<String> findAllUserIds();
}
