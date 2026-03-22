package com.example.fitness_plan.data;

/**
 * 默认计划生成器
 * 作用：完全解耦的初始数据源。
 * 未来演进方向：这里的逻辑将被替换为“读取本地 JSON 文件”或“读取用户分享的链接”。
 */
public class DefaultPlanGenerator {

    public static void populateInitialData(WorkoutDao dao) {
        // --- A. 注入核心动作库 (相当于解析导入文件里的动作列表) ---
        long benchId = dao.insertExerciseBase(new ExerciseBaseEntity("杠铃平板卧推", "kg", "Chest"));
        long pressId = dao.insertExerciseBase(new ExerciseBaseEntity("哑铃推举", "kg", "Shoulders"));
        long pullId = dao.insertExerciseBase(new ExerciseBaseEntity("高位下拉", "kg", "Back"));
        long rowId = dao.insertExerciseBase(new ExerciseBaseEntity("杠铃划船", "kg", "Back"));
        long squatId = dao.insertExerciseBase(new ExerciseBaseEntity("杠铃深蹲", "kg", "Legs&Glutes"));
        long deadliftId = dao.insertExerciseBase(new ExerciseBaseEntity("传统硬拉", "kg", "Legs&Glutes"));

        // --- B. 创建默认计划 ---
        // 修复 1：调用现有的带有 (String, boolean) 的构造器
        PlanEntity defaultPlan = new PlanEntity("经典推拉腿 (PPL)", true);
        long planIdLong = dao.insertPlan(defaultPlan);
        int planId = (int) planIdLong;

        // --- C. 绑定每天的训练模板 ---
        // 修复 2：使用辅助方法实例化，避开构造器参数不匹配的问题
        dao.insertTemplate(createTemplate(planId, "推力日", benchId, 0, 4, 12, 40.0));
        dao.insertTemplate(createTemplate(planId, "推力日", pressId, 1, 4, 12, 15.0));

        dao.insertTemplate(createTemplate(planId, "拉力日", pullId, 0, 4, 12, 35.0));
        dao.insertTemplate(createTemplate(planId, "拉力日", rowId, 1, 4, 12, 40.0));

        dao.insertTemplate(createTemplate(planId, "腿部日", squatId, 0, 5, 5, 60.0));
        dao.insertTemplate(createTemplate(planId, "腿部日", deadliftId, 1, 5, 5, 70.0));

        // --- D. 生成第一天的今日卡片 ---
        ExerciseEntity benchCard = new ExerciseEntity(benchId, 40.0, 4, 12, false);
        benchCard.sortOrder = 0;
        benchCard.color = "#FFFFFF";
        dao.insert(benchCard);

        ExerciseEntity pressCard = new ExerciseEntity(pressId, 15.0, 4, 12, false);
        pressCard.sortOrder = 1;
        pressCard.color = "#FFFFFF";
        dao.insert(pressCard);
    }

    /**
     * 辅助方法：优雅地组装 TemplateEntity，规避构造器匹配错误
     */
    private static TemplateEntity createTemplate(int planId, String dayName, long baseId, int sortOrder, int sets, int reps, double weight) {
        TemplateEntity t = new TemplateEntity();
        t.planId = planId;
        t.dayName = dayName;
        t.baseId = baseId;
        t.sortOrder = sortOrder;
        t.defaultSets = sets;
        t.defaultReps = reps;
        t.defaultWeight = weight;
        return t;
    }
}