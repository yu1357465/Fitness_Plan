package com.example.fitness_plan;

import com.example.fitness_plan.data.ExerciseBaseEntity;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MockDataGenerator {

    public static void injectData(WorkoutDao dao, Runnable onComplete) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            // 1. 创建 6 大维度的底层动作 (确保分类绝对正确)
            String[][] exercises = {
                    {"杠铃平板卧推", "Chest"},
                    {"高位下拉", "Back"},
                    {"杠铃深蹲", "Legs&Glutes"},
                    {"哑铃推举", "Shoulders"},
                    {"哑铃弯举", "Arms"},
                    {"悬垂举腿", "Core"}
            };

            long[] baseIds = new long[6];
            for (int i = 0; i < 6; i++) {
                ExerciseBaseEntity base = dao.getExerciseBaseByName(exercises[i][0]);
                if (base == null) {
                    baseIds[i] = dao.insertExerciseBase(new ExerciseBaseEntity(exercises[i][0], "kg", exercises[i][1]));
                } else {
                    baseIds[i] = base.baseId;
                }
            }

            // 2. 注入历史记录：制造明显的偏科和能量系统差异
            long now = System.currentTimeMillis();
            long dayMs = 24L * 60 * 60 * 1000L;

            // [改动后的代码]：在日期和训练日主题之间，统一插入 "模拟计划" 这个 planName
            dao.insertHistory(new HistoryEntity(now - 3 * dayMs, "03-18", "模拟计划", "推力日", baseIds[0], 100.0, 5, 5));
            dao.insertHistory(new HistoryEntity(now - 2 * dayMs, "03-19", "模拟计划", "拉力日", baseIds[1], 70.0, 10, 4));
            dao.insertHistory(new HistoryEntity(now - dayMs, "03-20", "模拟计划", "腿部日", baseIds[2], 60.0, 15, 4));
            dao.insertHistory(new HistoryEntity(now, "03-21", "模拟计划", "推力日", baseIds[3], 45.0, 8, 4));
            dao.insertHistory(new HistoryEntity(now, "03-21", "模拟计划", "拉力日", baseIds[4], 15.0, 20, 3));
            dao.insertHistory(new HistoryEntity(now, "03-21", "模拟计划", "核心日", baseIds[5], 0.0, 25, 4));

            // 通知主线程注入完毕
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
}