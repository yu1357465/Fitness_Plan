package com.example.fitness_plan.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "exercise_table",
        foreignKeys = @ForeignKey(
                entity = ExerciseBaseEntity.class,
                parentColumns = "baseId",
                childColumns = "baseId",
                onDelete = ForeignKey.NO_ACTION
        ),
        indices = {@Index("baseId")}
)
public class ExerciseEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "baseId")
    public long baseId;

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

    @ColumnInfo(name = "color")
    public String color;

    // ==========================================
    //  UI 状态字段 (正确：加上 @Ignore)
    // ==========================================
    @Ignore
    public boolean isDeleteConfirmMode = false;

    @Ignore
    public boolean isExpanded = false;

    // ==========================================
    //  构造函数 (关键修正点！)
    // ==========================================

    // 1. 给这个带参构造函数加上 @Ignore
    // 告诉 Room："这个是我（开发者）手动创建对象时用的，你别碰。"
    @Ignore
    public ExerciseEntity(long baseId, double weight, int sets, int reps, boolean isCompleted) {
        this.baseId = baseId;
        this.weight = weight;
        this.sets = sets;
        this.reps = reps;
        this.isCompleted = isCompleted;
        this.sortOrder = 0;
        this.isLbs = false;
        this.color = "#FFFFFF";
    }

    // 2. 去掉这里的 @Ignore
    // 告诉 Room："请用这个空的构造函数来实例化，然后通过 public 字段把数据填进去。"
    public ExerciseEntity() {
    }
}