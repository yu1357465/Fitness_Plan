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
    //  Exercise 表 (当前训练动作)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ExerciseEntity exercise);

    @Delete
    void delete(ExerciseEntity exercise);

    @Update
    void update(ExerciseEntity exercise);

    // 注意：使用数据库列名 sort_order
    @Query("SELECT * FROM exercise_table ORDER BY sort_order ASC")
    List<ExerciseEntity> getAllExercises();

    @Query("DELETE FROM exercise_table")
    void clearCurrentPlan();

    @Query("DELETE FROM exercise_table")
    void clearAllExercises();

    // ==========================================
    //  Plan 表 (计划)
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
    //  Template 表 (模板)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTemplate(TemplateEntity template);

    @Query("SELECT DISTINCT day_name FROM template_table WHERE plan_id = :planId ORDER BY sort_order ASC")
    List<String> getPlanDays(int planId);

    // 兼容旧代码的方法名
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

    @Query("UPDATE template_table SET exercise_name = :newName WHERE exercise_name = :oldName")
    void smartRename(String oldName, String newName);

    // ==========================================
    //  History 表 (历史记录) - 【核心修复区域】
    // ==========================================

    // 之前报错就是因为缺了这几个方法：

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertHistory(HistoryEntity history);

    @Delete
    void deleteHistory(HistoryEntity history);

    @Update
    void updateHistory(HistoryEntity history);

    @Query("SELECT * FROM history_table ORDER BY date DESC")
    List<HistoryEntity> getAllHistory();

    // exercise_name 是数据库列名，name 是传入参数
    @Query("SELECT * FROM history_table WHERE exercise_name = :name ORDER BY date DESC")
    List<HistoryEntity> getHistoryByName(String name);
}