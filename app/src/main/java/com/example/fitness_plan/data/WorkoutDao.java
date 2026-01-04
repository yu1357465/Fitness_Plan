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
    //  Exercise 表 (当前训练动作 - 首页)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ExerciseEntity exercise);

    @Delete
    void delete(ExerciseEntity exercise);

    @Update
    void update(ExerciseEntity exercise);

    // 保留了你的 sort_order 排序，这很好
    @Query("SELECT * FROM exercise_table ORDER BY sort_order ASC")
    List<ExerciseEntity> getAllExercises();

    // 清空当前首页动作
    @Query("DELETE FROM exercise_table")
    void clearCurrentPlan();

    // 备用清空方法 (防止方法名混淆，保留着没错)
    @Query("DELETE FROM exercise_table")
    void clearAllExercises();

    // ==========================================
    //  Plan 表 (计划管理)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertPlan(PlanEntity plan);

    @Delete
    void deletePlan(PlanEntity plan);

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

    @Query("SELECT DISTINCT day_name FROM template_table WHERE plan_id = :planId ORDER BY sort_order ASC")
    List<String> getPlanDays(int planId);

    // 兼容 PlanListActivity 的方法名
    @Query("SELECT DISTINCT day_name FROM template_table WHERE plan_id = :planId ORDER BY sort_order ASC")
    List<String> getDayNamesByPlanId(int planId);

    @Query("SELECT * FROM template_table WHERE plan_id = :planId AND day_name = :dayName ORDER BY sort_order ASC")
    List<TemplateEntity> getTemplatesByPlanAndDay(int planId, String dayName);

    @Query("DELETE FROM template_table WHERE plan_id = :planId AND day_name = :dayName AND exercise_name = :exName")
    void deleteTemplateByName(int planId, String dayName, String exName);

    @Query("DELETE FROM template_table WHERE plan_id = :planId")
    void deleteTemplatesByPlanId(int planId);

    @Query("DELETE FROM template_table WHERE plan_id = :planId AND day_name = :dayName")
    void deleteTemplatesByPlanAndDay(int planId, String dayName);

    @Query("SELECT * FROM template_table")
    List<TemplateEntity> getAllTemplates();

    @Query("UPDATE template_table SET day_name = :newName WHERE plan_id = :planId AND day_name = :oldName")
    void updateDayName(int planId, String oldName, String newName);

    // 高级功能：修改动作名时同步更新模板，很有价值
    @Query("UPDATE template_table SET exercise_name = :newName WHERE exercise_name = :oldName")
    void smartRename(String oldName, String newName);

    // 【核心修复】添加这个缺失的方法！
    @Update
    void updatePlan(PlanEntity plan);

    // ==========================================
    //  History 表 (历史记录) - 【核心修复区域】
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertHistory(HistoryEntity history);

    @Delete
    void deleteHistory(HistoryEntity history);

    @Update
    void updateHistory(HistoryEntity history);

    @Query("SELECT * FROM history_table ORDER BY date DESC")
    List<HistoryEntity> getAllHistory();

    @Query("SELECT * FROM history_table WHERE exerciseName = :name ORDER BY date DESC")
    List<HistoryEntity> getHistoryByName(String name);

    // 【之前你已经加了这个】用于删除整天
    @Query("DELETE FROM history_table WHERE date >= :start AND date < :end")
    void deleteHistoryByRange(long start, long end);

    // 【👉 本次新增/修复 👈】用于 SettingsActivity 的"清空所有历史"
    // 之前报错就是因为缺这个！
    @Query("DELETE FROM history_table")
    void deleteAllHistory();
}