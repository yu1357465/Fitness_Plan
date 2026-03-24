package com.example.fitness_plan.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// 【新增】引入专门处理多任务排队的线程池工具
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用数据库 - 封神版 (V2.1)
 *
 * 版本历史：
 * v1: 初始版本（基于字符串存储动作名称）
 * v2: 重构版本（引入动作库，基于 baseId 引用）
 * v3: 引入模块化默认计划生成器，完善初始化流程
 * v4: 稳定测试版本
 * v5: 【路线A升级】历史表 (HistoryEntity) 新增 planName 字段，支持快照解耦与无损导出
 */
@Database(
        entities = {
                ExerciseBaseEntity.class,
                ExerciseEntity.class,
                HistoryEntity.class,
                PlanEntity.class,
                TemplateEntity.class
        },
        version = 5, // ⭐ 核心修改：版本号升级为 5
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract WorkoutDao workoutDao();

    private static volatile AppDatabase INSTANCE;

    // 专门干脏活累活的“后台施工队” (Thread Pool)
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    /**
     * 系统占位符动作的固定 ID
     * 用于计划模板中的占位符，不会出现在用户的动作库搜索结果中
     */
    public static final long SYSTEM_PLACEHOLDER_ID = 1L;

    // ==========================================
    // ⭐ V2.1 封神版新增：数据库无损升级脚本 (Migration 4 -> 5)
    // ==========================================
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 挖掘底层逻辑 (Mechanism):
            // 直接操作底层的 SQLite 引擎，在 history_table 中强行插入新列。
            // 使用 DEFAULT '无法追溯'，确保旧数据在读取时不会因为 null 而崩溃。
            database.execSQL("ALTER TABLE history_table ADD COLUMN planName TEXT DEFAULT '无法追溯'");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "fitness_plan_database"
                            )
                            // 挂载我们的无损升级引擎！优先执行 Migration，如果跨度太大找不到脚本，才执行毁灭性重建
                            .addMigrations(MIGRATION_4_5)
                            .fallbackToDestructiveMigration()
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);

                                    databaseWriteExecutor.execute(() -> {
                                        // 1. 插入固定 ID=1 的系统占位符 (使用原生 SQL)
                                        db.execSQL(
                                                "INSERT INTO exercise_library (baseId, name, defaultUnit, category, isDeleted, createdAt, lastUsedAt) " +
                                                        "VALUES (1, '[系统占位符]', 'kg', 'system', 1, " + System.currentTimeMillis() + ", 0)"
                                        );

                                        // 2. 获取数据库管家 (DAO)
                                        WorkoutDao dao = INSTANCE.workoutDao();

                                        // 3. 呼叫外包生成器，搬入默认的推拉腿计划！
                                        DefaultPlanGenerator.populateInitialData(dao);
                                    });
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}