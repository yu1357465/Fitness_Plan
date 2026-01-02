package com.example.fitness_plan.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "plan_table")
public class PlanEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "plan_id") // 必须加上这个，对应 SQL 里的 plan_id
    public int planId;

    @ColumnInfo(name = "plan_name") // 对应 SQL 里的 plan_name
    public String planName;

    // 【核心修复】这里必须加上 name="is_active"
    // 否则 Room 会在数据库里创建 "isActive" 列，但你的 DAO 在查 "is_active"，就会报错
    @ColumnInfo(name = "is_active")
    public boolean isActive;

    public PlanEntity(String planName, boolean isActive) {
        this.planName = planName;
        this.isActive = isActive;
    }
}