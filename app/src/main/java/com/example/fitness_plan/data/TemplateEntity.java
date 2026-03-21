package com.example.fitness_plan.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 训练计划模板实体 - 重构版
 * 使用 baseId 引用动作库，不再直接存储动作名称
 */
@Entity(
    tableName = "template_table",
    foreignKeys = @ForeignKey(
        entity = ExerciseBaseEntity.class,
        parentColumns = "baseId",
        childColumns = "baseId",
        onDelete = ForeignKey.NO_ACTION  // 保留模板，即使动作被删除
    ),
    indices = {@Index("baseId"), @Index("plan_id")}  // 提升查询性能
)
public class TemplateEntity {

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "plan_id")
    public int planId;

    @ColumnInfo(name = "day_name")
    public String dayName;

    @Ignore
    public int dayIndex;  // 兼容旧代码

    @ColumnInfo(name = "baseId")
    public long baseId;  // 【重构】引用动作库的ID

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

    // 【重构】新构造函数
    @Ignore
    public TemplateEntity(int planId, String dayName, int dayIndex, long baseId, double defaultWeight, int defaultSets, int defaultReps) {
        this.planId = planId;
        this.dayName = dayName;
        this.dayIndex = dayIndex;
        this.baseId = baseId;
        this.defaultWeight = defaultWeight;
        this.defaultSets = defaultSets;
        this.defaultReps = defaultReps;
        this.sortOrder = 0;
    }
}
