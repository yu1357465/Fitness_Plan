package com.example.fitness_plan.data;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 历史记录实体 - 重构版
 * 使用 baseId 引用动作库，不再直接存储动作名称
 */
@Entity(
    tableName = "history_table",
    foreignKeys = @ForeignKey(
        entity = ExerciseBaseEntity.class,
        parentColumns = "baseId",
        childColumns = "baseId",
        onDelete = ForeignKey.NO_ACTION  // 保留历史记录，即使动作被删除
    ),
    indices = {@Index("baseId"), @Index("date")}  // 提升查询性能
)
public class HistoryEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public long date;           // 时间戳
    public String dateStr;      // 显示用日期，如 "2023年10月27日"
    public String workoutName;  // 计划名，如 "推力日"
    // 【V2.1 路线A新增】记录产生此记录时的计划快照
    public String planName;

    public long baseId;         // 【重构】引用动作库的ID
    public double weight;
    public int reps;
    public int sets;

    // 【重构】新构造函数
    public HistoryEntity(long date, String dateStr, String planName, String workoutName, long baseId, double weight, int reps, int sets) {
        this.date = date;
        this.dateStr = dateStr;
        this.planName = planName;
        this.workoutName = workoutName;
        this.baseId = baseId;
        this.weight = weight;
        this.reps = reps;
        this.sets = sets;
    }
}
