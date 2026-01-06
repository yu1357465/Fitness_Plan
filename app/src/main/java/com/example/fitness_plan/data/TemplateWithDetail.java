package com.example.fitness_plan.data;

import androidx.room.Embedded;

/**
 * 训练模板 + 动作详情的关联查询结果
 * 用于 UI 层显示模板时需要动作名称
 */
public class TemplateWithDetail {

    @Embedded
    public TemplateEntity template;  // 包含所有 TemplateEntity 字段

    public String exerciseName;      // 从 ExerciseBaseEntity JOIN 来的动作名称
    public String category;          // 训练部位

    // 便捷访问方法
    public int getId() {
        return template != null ? template.id : 0;
    }

    public int getPlanId() {
        return template != null ? template.planId : 0;
    }

    public String getDayName() {
        return template != null ? template.dayName : "";
    }

    public long getBaseId() {
        return template != null ? template.baseId : 0;
    }

    public int getDefaultSets() {
        return template != null ? template.defaultSets : 0;
    }

    public int getDefaultReps() {
        return template != null ? template.defaultReps : 0;
    }

    public double getDefaultWeight() {
        return template != null ? template.defaultWeight : 0;
    }

    public int getSortOrder() {
        return template != null ? template.sortOrder : 0;
    }
}
