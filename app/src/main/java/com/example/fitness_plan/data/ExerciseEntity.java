package com.example.fitness_plan.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "exercise_table")
public class ExerciseEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "name")
    public String name;

    @ColumnInfo(name = "weight")
    public double weight;

    @ColumnInfo(name = "sets")
    public int sets;

    @ColumnInfo(name = "reps")
    public int reps;

    @ColumnInfo(name = "is_completed")
    public boolean isCompleted;

    @ColumnInfo(name = "sort_order")
    public int sortOrder;

    @ColumnInfo(name = "is_lbs")
    public boolean isLbs;

    // 【新增】颜色字段 (存 Hex 颜色码，如 "#FFFFFF")
    @ColumnInfo(name = "color")
    public String color;

    // ==========================================
    //  内存标记字段 (不存数据库)
    // ==========================================

    @Ignore
    public boolean isDeleteConfirmMode = false;

    @Ignore
    public boolean isExpanded = false;

    public ExerciseEntity(String name, double weight, int sets, int reps, boolean isCompleted) {
        this.name = name;
        this.weight = weight;
        this.sets = sets;
        this.reps = reps;
        this.isCompleted = isCompleted;
        this.sortOrder = 0;
        this.isLbs = false;
        this.color = "#FFFFFF"; // 默认白色
    }

    @Ignore
    public ExerciseEntity() {
    }
}