package com.example.fitness_plan.data;

/**
 * 动作库辅助类
 * 用于简化动作的创建和查找逻辑
 */
public class ExerciseLibraryHelper {

    private final WorkoutDao dao;

    public ExerciseLibraryHelper(WorkoutDao dao) {
        this.dao = dao;
    }

    /**
     * 根据动作名称获取或创建动作库记录
     * 如果动作已存在，返回已有的 baseId；否则创建新动作并返回新的 baseId
     *
     * @param exerciseName 动作名称
     * @param defaultUnit 默认单位（如 "kg"）
     * @param category 训练部位（如 "胸部"）
     * @return baseId
     */
    public long getOrCreateExercise(String exerciseName, String defaultUnit, String category) {
        // 先查找是否已存在
        ExerciseBaseEntity existing = dao.getExerciseBaseByName(exerciseName);

        if (existing != null) {
            // 更新最后使用时间
            dao.updateLastUsedTime(existing.baseId, System.currentTimeMillis());
            return existing.baseId;
        }

        // 不存在则创建新动作
        ExerciseBaseEntity newExercise = new ExerciseBaseEntity(exerciseName, defaultUnit, category);
        long baseId = dao.insertExerciseBase(newExercise);
        return baseId;
    }

    /**
     * 快速创建动作（使用默认值）
     * @param exerciseName 动作名称
     * @return baseId
     */
    public long getOrCreateExercise(String exerciseName) {
        return getOrCreateExercise(exerciseName, "kg", "其他");
    }

    /**
     * 重命名动作（全局修改）
     * 这会更新动作库中的名称，所有引用该动作的地方都会自动显示新名称
     *
     * @param baseId 动作ID
     * @param newName 新名称
     */
    public void renameExercise(long baseId, String newName) {
        ExerciseBaseEntity exercise = dao.getExerciseBaseById(baseId);
        if (exercise != null) {
            exercise.name = newName;
            dao.updateExerciseBase(exercise);
        }
    }

    /**
     * 软删除动作
     * 动作会被标记为已删除，但历史记录仍然可以访问
     *
     * @param baseId 动作ID
     */
    public void deleteExercise(long baseId) {
        dao.softDeleteExercise(baseId);
    }

    /**
     * 获取动作名称
     * @param baseId 动作ID
     * @return 动作名称，如果不存在返回 "未知动作"
     */
    public String getExerciseName(long baseId) {
        ExerciseBaseEntity exercise = dao.getExerciseBaseById(baseId);
        return exercise != null ? exercise.name : "未知动作";
    }
}
