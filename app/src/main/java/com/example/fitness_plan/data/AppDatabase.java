package com.example.fitness_plan.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

// 【关键】entities 数组里必须包含 HistoryEntity.class
// 如果之前运行过App，建议卸载重装，或者把 version 改成 2
@Database(entities = {PlanEntity.class, TemplateEntity.class, ExerciseEntity.class, HistoryEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract WorkoutDao workoutDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "fitness_plan_database")
                            .fallbackToDestructiveMigration() // 允许版本升级时清空旧数据防止崩溃
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}