package com.example.fitness_plan.data;

import java.util.List;

public class HistorySession {
    public String dateStr;
    public String workoutName;

    // ✅ 核心修改：现在列表里存的是 HistoryEntity (历史记录)，而不是 ExerciseEntity (今日训练)
    public List<HistoryEntity> exercises;

    // 无参构造函数 (HistoryAdapter 分组逻辑需要用到)
    public HistorySession() {
    }

    // 带参构造函数
    public HistorySession(String dateStr, String workoutName, List<HistoryEntity> exercises) {
        this.dateStr = dateStr;
        this.workoutName = workoutName;
        this.exercises = exercises;
    }
}