package com.example.fitness_plan.data;

/**
 * 幽灵卡片辅助类
 * 用于处理"新动作"这个特殊的幽灵动作
 */
public class GhostExerciseHelper {

    // 幽灵动作的固定名称和ID
    public static final String GHOST_NAME = "新动作";
    public static final long GHOST_BASE_ID = -1L;  // 使用负数作为幽灵卡片的特殊ID

    /**
     * 判断是否为幽灵卡片（基于 baseId）
     */
    public static boolean isGhost(long baseId) {
        return baseId == GHOST_BASE_ID;
    }

    /**
     * 判断是否为幽灵卡片（基于 ExerciseEntity）
     */
    public static boolean isGhost(ExerciseEntity exercise) {
        return exercise != null && exercise.baseId == GHOST_BASE_ID;
    }

    /**
     * 判断是否为实体卡片（非幽灵）
     */
    public static boolean isRealExercise(ExerciseEntity exercise) {
        return exercise != null && !isGhost(exercise);
    }

    /**
     * 创建幽灵卡片
     */
    public static ExerciseEntity createGhostExercise() {
        ExerciseEntity ghost = new ExerciseEntity(GHOST_BASE_ID, 0, 0, 0, false);
        ghost.color = "#FFF9C4";  // 幽灵卡片默认黄色
        return ghost;
    }

    /**
     * 确保动作库中存在幽灵动作
     * 这个方法应该在App启动时调用一次
     */
    public static void ensureGhostExerciseExists(WorkoutDao dao) {
        ExerciseBaseEntity ghost = dao.getExerciseBaseById(GHOST_BASE_ID);
        if (ghost == null) {
            ghost = new ExerciseBaseEntity();
            ghost.baseId = GHOST_BASE_ID;
            ghost.name = GHOST_NAME;
            ghost.defaultUnit = "kg";
            ghost.category = "临时";
            ghost.isDeleted = false;
            // 注意：由于 baseId 是主键且我们手动设置，需要特殊处理
            // 如果 Room 不允许手动设置主键，可能需要调整策略
        }
    }
}
