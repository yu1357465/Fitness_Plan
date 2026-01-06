package com.example.fitness_plan.data;

import androidx.room.Embedded;

/**
 * 历史记录 + 动作详情的关联查询结果
 * 用于 UI 层显示历史记录时需要动作名称
 */
public class HistoryWithDetail {

    @Embedded
    public HistoryEntity history;  // 包含所有 HistoryEntity 字段

    public String exerciseName;    // 从 ExerciseBaseEntity JOIN 来的动作名称
    public String category;        // 训练部位

    // 便捷访问方法
    public int getId() {
        return history != null ? history.id : 0;
    }

    public long getDate() {
        return history != null ? history.date : 0;
    }

    public String getDateStr() {
        return history != null ? history.dateStr : "";
    }

    public String getWorkoutName() {
        return history != null ? history.workoutName : "";
    }

    public long getBaseId() {
        return history != null ? history.baseId : 0;
    }

    public double getWeight() {
        return history != null ? history.weight : 0;
    }

    public int getReps() {
        return history != null ? history.reps : 0;
    }

    public int getSets() {
        return history != null ? history.sets : 0;
    }
}
