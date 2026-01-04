package com.example.fitness_plan.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// 【关键】表名必须是 history_table，为了匹配你的 DAO
@Entity(tableName = "history_table")
public class HistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public long date;           // 时间戳
    public String dateStr;      // 显示用日期，如 "2023年10月27日"
    public String workoutName;  // 计划名，如 "推力日"

    public String exerciseName; // 动作名
    public double weight;
    public int reps;
    public int sets;

    public HistoryEntity(long date, String dateStr, String workoutName, String exerciseName, double weight, int reps, int sets) {
        this.date = date;
        this.dateStr = dateStr;
        this.workoutName = workoutName;
        this.exerciseName = exerciseName;
        this.weight = weight;
        this.reps = reps;
        this.sets = sets;
    }
}