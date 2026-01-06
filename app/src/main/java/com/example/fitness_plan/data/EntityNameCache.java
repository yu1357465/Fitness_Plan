package com.example.fitness_plan.data;

import java.util.HashMap;
import java.util.Map;

/**
 * 实体名称缓存
 * 用于临时缓存 baseId -> name 的映射，减少重复查询
 * 这是过渡期的辅助工具，帮助旧代码快速适配
 */
public class EntityNameCache {
    private static EntityNameCache INSTANCE;
    private final Map<Long, String> cache = new HashMap<>();
    private WorkoutDao dao;

    private EntityNameCache() {}

    public static EntityNameCache getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EntityNameCache();
        }
        return INSTANCE;
    }

    public void setDao(WorkoutDao dao) {
        this.dao = dao;
    }

    /**
     * 获取动作名称（带缓存）
     */
    public String getExerciseName(long baseId) {
        // 特殊处理幽灵卡片
        if (baseId == -1) {
            return "新动作";
        }

        // 特殊处理系统占位符
        if (baseId == AppDatabase.SYSTEM_PLACEHOLDER_ID) {
            return "点击修改动作名称";
        }

        // 先查缓存
        if (cache.containsKey(baseId)) {
            return cache.get(baseId);
        }

        // 缓存未命中，查数据库
        if (dao != null) {
            ExerciseBaseEntity base = dao.getExerciseBaseById(baseId);
            if (base != null) {
                cache.put(baseId, base.name);
                return base.name;
            }
        }

        return "未知动作";
    }

    /**
     * 预加载所有动作名称到缓存
     */
    public void preloadAll(WorkoutDao dao) {
        this.dao = dao;
        cache.clear();
        for (ExerciseBaseEntity base : dao.getAllExerciseBases()) {
            cache.put(base.baseId, base.name);
        }
    }

    /**
     * 清空缓存
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 更新缓存中的名称
     */
    public void updateCache(long baseId, String newName) {
        cache.put(baseId, newName);
    }
}
