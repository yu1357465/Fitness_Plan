package com.example.fitness_plan.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "template_table")
public class TemplateEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "plan_id")
    public int planId;

    @ColumnInfo(name = "day_name")
    public String dayName;

    // 为了兼容旧代码，我们保留 dayIndex，但在数据库里它其实对应 sort_order 或逻辑顺序
    // 这里我们暂时让它不参与数据库存取，或者你可以根据逻辑映射到 sortOrder
    @Ignore
    public int dayIndex;

    @ColumnInfo(name = "exercise_name")
    public String exerciseName;

    @ColumnInfo(name = "default_sets")
    public int defaultSets;

    @ColumnInfo(name = "default_reps")
    public int defaultReps;

    @ColumnInfo(name = "default_weight")
    public double defaultWeight;

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    // 无参构造函数 (Room 必须)
    public TemplateEntity() {
    }

    // 【核心修复】全参构造函数 (为了兼容 UnifiedBackupUtils 和 MainActivity 的旧代码)
    // 签名必须匹配：int, String, int, String, int, int, int (或 double)
    @Ignore
    public TemplateEntity(int planId, String dayName, int dayIndex, String exerciseName, double defaultWeight, int defaultSets, int defaultReps) {
        this.planId = planId;
        this.dayName = dayName;
        this.dayIndex = dayIndex;
        this.exerciseName = exerciseName;
        this.defaultWeight = defaultWeight;
        this.defaultSets = defaultSets;
        this.defaultReps = defaultReps;
        this.sortOrder = 0; // 默认值
    }
}