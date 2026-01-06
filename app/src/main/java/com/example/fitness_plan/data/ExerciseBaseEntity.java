package com.example.fitness_plan.data;

import androidx.room.Entity;
import androidx.room.Ignore; // 1. 记得导入这个
import androidx.room.PrimaryKey;

/**
 * 动作库表 (Exercise Library) - 单一真理来源
 * 所有动作的定义都存储在这里，其他表通过 baseId 引用
 */
@Entity(tableName = "exercise_library")
public class ExerciseBaseEntity {

    @PrimaryKey(autoGenerate = true)
    public long baseId;

    public String name;              // 动作名称
    public String defaultUnit;       // 默认单位
    public String category;          // 训练部位
    public boolean isDeleted;        // 软删除标记
    public long createdAt;           // 创建时间戳
    public long lastUsedAt;          // 最后使用时间

    // 2. 在这个构造函数上添加 @Ignore
    // 告诉 Room："这个是我写代码时创建新对象用的，你读取数据库时不要用这个。"
    @Ignore
    public ExerciseBaseEntity(String name, String defaultUnit, String category) {
        this.name = name;
        this.defaultUnit = defaultUnit;
        this.category = category;
        this.isDeleted = false;
        this.createdAt = System.currentTimeMillis();
        this.lastUsedAt = System.currentTimeMillis();
    }

    // Room 将使用这个无参构造函数来还原数据
    public ExerciseBaseEntity() {
        this.isDeleted = false;
        this.createdAt = System.currentTimeMillis();
        this.lastUsedAt = System.currentTimeMillis();
    }
}