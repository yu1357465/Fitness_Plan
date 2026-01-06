package com.example.fitness_plan.data;

import androidx.room.Embedded;
import androidx.room.Ignore;

/**
 * 今日训练 + 动作详情的关联查询结果
 * 用于 UI 层直接使用，包含所有需要的字段
 */
public class ExerciseWithDetail {

    @Embedded
    public ExerciseEntity exercise;  // 包含所有 ExerciseEntity 字段

    public String exerciseName;      // 从 ExerciseBaseEntity JOIN 来的动作名称
    public String category;          // 训练部位
    public String defaultUnit;       // 默认单位

    // 内存标记字段（与原 ExerciseEntity 保持一致）
    @Ignore
    public boolean isDeleteConfirmMode = false;

    @Ignore
    public boolean isExpanded = false;

    // 便捷访问方法
    public long getBaseId() {
        return exercise != null ? exercise.baseId : 0;
    }

    public int getId() {
        return exercise != null ? exercise.id : 0;
    }

    public double getWeight() {
        return exercise != null ? exercise.weight : 0;
    }

    public int getSets() {
        return exercise != null ? exercise.sets : 0;
    }

    public int getReps() {
        return exercise != null ? exercise.reps : 0;
    }

    public boolean isCompleted() {
        return exercise != null && exercise.isCompleted;
    }

    public int getSortOrder() {
        return exercise != null ? exercise.sortOrder : 0;
    }

    public boolean isLbs() {
        return exercise != null && exercise.isLbs;
    }

    public String getColor() {
        return exercise != null ? exercise.color : "#FFFFFF";
    }
}
