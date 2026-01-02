package com.example.fitness_plan.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "history_table")
public class HistoryEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "date")
    public long date; // 时间戳

    // 【核心修复】改回 name，对应 Adapter 和 BackupUtils 的调用
    @ColumnInfo(name = "exercise_name")
    public String name;

    @ColumnInfo(name = "weight")
    public double weight;

    @ColumnInfo(name = "sets")
    public int sets;

    @ColumnInfo(name = "reps")
    public int reps;

    @ColumnInfo(name = "is_lbs")
    public boolean isLbs;

    // 【核心修复】改回 workoutTitle，对应 Adapter 的调用
    @ColumnInfo(name = "workout_name")
    public String workoutTitle;

    // 构造函数 1：给 HistoryActivity 使用 (long date 开头)
    public HistoryEntity(long date, String name, double weight, int sets, int reps, boolean isLbs) {
        this.date = date;
        this.name = name;
        this.weight = weight;
        this.sets = sets;
        this.reps = reps;
        this.isLbs = isLbs;
    }

    // 构造函数 2：给 MainActivity/Legacy 代码使用 (String name 开头)
    // 防止出现 "String cannot be converted to long" 错误
    @Ignore
    public HistoryEntity(String name, double weight, int sets, int reps, long date, String workoutTitle) {
        this.name = name;
        this.weight = weight;
        this.sets = sets;
        this.reps = reps;
        this.date = date;
        this.workoutTitle = workoutTitle;
        this.isLbs = false; // 默认值
    }
}