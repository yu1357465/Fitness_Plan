package com.example.fitness_plan.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface WorkoutDao {

    // ==========================================
    //  ExerciseBase 表 (动作库 - 新增)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertExerciseBase(ExerciseBaseEntity exerciseBase);

    @Delete
    void deleteExerciseBase(ExerciseBaseEntity exerciseBase);

    // 获取所有未删除的动作（排除系统占位符）
    @Query("SELECT * FROM exercise_library WHERE isDeleted = 0 AND baseId != 1 ORDER BY lastUsedAt DESC")
    List<ExerciseBaseEntity> getAllExerciseBases();

    // 根据名称查找动作（用于避免重复，排除系统占位符）
    @Query("SELECT * FROM exercise_library WHERE name = :name AND isDeleted = 0 AND baseId != 1 LIMIT 1")
    ExerciseBaseEntity getExerciseBaseByName(String name);

    // 根据 baseId 查找动作
    @Query("SELECT * FROM exercise_library WHERE baseId = :baseId")
    ExerciseBaseEntity getExerciseBaseById(long baseId);

    // 更新最后使用时间
    @Query("UPDATE exercise_library SET lastUsedAt = :timestamp WHERE baseId = :baseId")
    void updateLastUsedTime(long baseId, long timestamp);

    // ==========================================
    //  Exercise 表 (当前训练动作 - 首页)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ExerciseEntity exercise);

    @Delete
    void delete(ExerciseEntity exercise);

    @Update
    void update(ExerciseEntity exercise);

    // 【重构】返回原始 Entity（后续会用 JOIN 查询替代）
    @Query("SELECT * FROM exercise_table ORDER BY sort_order ASC")
    List<ExerciseEntity> getAllExercises();

    // 【新增】JOIN 查询：获取今日训练 + 动作详情
    // 修改：使用字符串拼接代替文本块
    @Query("SELECT e.*, eb.name AS exerciseName, eb.category AS category, eb.defaultUnit AS defaultUnit " +
            "FROM exercise_table e " +
            "INNER JOIN exercise_library eb ON e.baseId = eb.baseId " +
            "ORDER BY e.sort_order ASC")
    List<ExerciseWithDetail> getAllExercisesWithDetail();

    // 清空当前首页动作
    @Query("DELETE FROM exercise_table")
    void clearCurrentPlan();

    @Query("DELETE FROM exercise_table")
    void clearAllExercises();

    // ==========================================
    //  Plan 表 (计划管理)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertPlan(PlanEntity plan);

    @Delete
    void deletePlan(PlanEntity plan);

    @Update
    void updatePlan(PlanEntity plan);

    @Query("SELECT * FROM plan_table WHERE is_active = 1 LIMIT 1")
    PlanEntity getActivePlan();

    @Query("UPDATE plan_table SET is_active = 0")
    void deactivateAllPlans();

    @Query("UPDATE plan_table SET is_active = 1 WHERE plan_id = :planId")
    void activatePlan(int planId);

    @Query("SELECT * FROM plan_table")
    List<PlanEntity> getAllPlans();

    // ==========================================
    //  Template 表 (模板管理)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTemplate(TemplateEntity template);

    @Update
    void updateTemplate(TemplateEntity template);

    @Query("SELECT DISTINCT day_name FROM template_table WHERE plan_id = :planId ORDER BY sort_order ASC")
    List<String> getPlanDays(int planId);

    @Query("SELECT DISTINCT day_name FROM template_table WHERE plan_id = :planId ORDER BY sort_order ASC")
    List<String> getDayNamesByPlanId(int planId);

    // 【重构】返回原始 Entity
    @Query("SELECT * FROM template_table WHERE plan_id = :planId AND day_name = :dayName ORDER BY sort_order ASC")
    List<TemplateEntity> getTemplatesByPlanAndDay(int planId, String dayName);

    // 【新增】JOIN 查询：获取模板 + 动作详情
    // 修改：使用字符串拼接代替文本块
    @Query("SELECT t.*, eb.name AS exerciseName, eb.category AS category " +
            "FROM template_table t " +
            "INNER JOIN exercise_library eb ON t.baseId = eb.baseId " +
            "WHERE t.plan_id = :planId AND t.day_name = :dayName " +
            "ORDER BY t.sort_order ASC")
    List<TemplateWithDetail> getTemplatesWithDetailByPlanAndDay(int planId, String dayName);

    // 【重构】现在通过 baseId 删除
    @Query("DELETE FROM template_table WHERE plan_id = :planId AND day_name = :dayName AND baseId = :baseId")
    void deleteTemplateByBaseId(int planId, String dayName, long baseId);

    @Query("DELETE FROM template_table WHERE plan_id = :planId")
    void deleteTemplatesByPlanId(int planId);

    @Query("DELETE FROM template_table WHERE plan_id = :planId AND day_name = :dayName")
    void deleteTemplatesByPlanAndDay(int planId, String dayName);

    @Query("SELECT * FROM template_table")
    List<TemplateEntity> getAllTemplates();

    @Query("UPDATE template_table SET day_name = :newName WHERE plan_id = :planId AND day_name = :oldName")
    void updateDayName(int planId, String oldName, String newName);

    // ==========================================
    //  History 表 (历史记录)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertHistory(HistoryEntity history);

    @Delete
    void deleteHistory(HistoryEntity history);

    @Update
    void updateHistory(HistoryEntity history);

    // 【重构】返回原始 Entity
    @Query("SELECT * FROM history_table ORDER BY date DESC")
    List<HistoryEntity> getAllHistory();

    // 【新增】JOIN 查询：获取历史记录 + 动作详情
    // 修改：使用字符串拼接代替文本块
    @Query("SELECT h.*, eb.name AS exerciseName, eb.category AS category " +
            "FROM history_table h " +
            "INNER JOIN exercise_library eb ON h.baseId = eb.baseId " +
            "ORDER BY h.date DESC")
    List<HistoryWithDetail> getAllHistoryWithDetail();

    // 【重构】通过 baseId 查询历史记录
    @Query("SELECT * FROM history_table WHERE baseId = :baseId ORDER BY date DESC")
    List<HistoryEntity> getHistoryByBaseId(long baseId);

    // 【新增】JOIN 查询版本
    // 修改：使用字符串拼接代替文本块
    @Query("SELECT h.*, eb.name AS exerciseName, eb.category AS category " +
            "FROM history_table h " +
            "INNER JOIN exercise_library eb ON h.baseId = eb.baseId " +
            "WHERE h.baseId = :baseId " +
            "ORDER BY h.date DESC")
    List<HistoryWithDetail> getHistoryWithDetailByBaseId(long baseId);

    @Query("DELETE FROM history_table WHERE date >= :start AND date < :end")
    void deleteHistoryByRange(long start, long end);

    @Query("DELETE FROM history_table")
    void deleteAllHistory();

    // 1. 查询所有未删除的动作 (排除系统占位符 ID=1)
    @Query("SELECT * FROM exercise_library WHERE isDeleted = 0 AND baseId != 1 ORDER BY name ASC")
    List<ExerciseBaseEntity> getAllActiveExerciseBases();

    // 2. 软删除
    @Query("UPDATE exercise_library SET isDeleted = 1 WHERE baseId = :baseId")
    void softDeleteExercise(long baseId);

    // 3. 更新动作信息
    @Update
    void updateExerciseBase(ExerciseBaseEntity exercise);

    // ==========================================
    // V2.1 P2 级核心：第二大脑记忆雷达
    // ==========================================
    @androidx.room.Query("SELECT * FROM history_table WHERE baseId = :baseId ORDER BY date DESC LIMIT 1")
    HistoryEntity getLatestHistoryByBaseId(long baseId);

    // ==========================================
    //  V2.1 封神版新增：核心结算原子操作 (P1 级防呆)
    // ==========================================

    /**
     * 事务说明：确保“转存历史”和“清空首页”必须同时成功。
     * 如果期间发生断电或崩溃，数据将自动回滚，杜绝“数据幽灵”。
     */
    @androidx.room.Transaction
    default void finishWorkoutAtomic(List<HistoryEntity> histories) {
        // 1. 将结算的动作列表逐个写入历史表
        for (HistoryEntity history : histories) {
            insertHistory(history);
        }
        // 2. 彻底清空今日的训练台
        clearCurrentPlan();
    }
}