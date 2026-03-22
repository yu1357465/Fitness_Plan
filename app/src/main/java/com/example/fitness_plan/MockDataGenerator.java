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

            // 胸部 (Chest): 绝对力量型选手 (5次大重量)
            dao.insertHistory(new HistoryEntity(now - 3 * dayMs, "03-18", "推力日", baseIds[0], 100.0, 5, 5));
            // 背部 (Back): 均衡型增肌 (10次中等重量)
            dao.insertHistory(new HistoryEntity(now - 2 * dayMs, "03-19", "拉力日", baseIds[1], 70.0, 10, 4));
            // 腿部 (Legs): 新手水平，偏向耐力 (15次轻重量)
            dao.insertHistory(new HistoryEntity(now - dayMs, "03-20", "腿部日", baseIds[2], 60.0, 15, 4));
            // 肩部 (Shoulders): 标准增肌
            dao.insertHistory(new HistoryEntity(now, "03-21", "推力日", baseIds[3], 45.0, 8, 4));
            // 手臂 (Arms): 极高频耐力训练 (20次)
            dao.insertHistory(new HistoryEntity(now, "03-21", "拉力日", baseIds[4], 15.0, 20, 3));
            // 核心 (Core): 极高频耐力训练 (25次)
            dao.insertHistory(new HistoryEntity(now, "03-21", "核心日", baseIds[5], 0.0, 25, 4));

            // 通知主线程注入完毕
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }
}