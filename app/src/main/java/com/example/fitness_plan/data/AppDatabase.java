package com.example.fitness_plan.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

// 【新增】引入专门处理多任务排队的线程池工具
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用数据库 - 重构版
 *
 * 版本历史：
 * v1: 初始版本（基于字符串存储动作名称）
 * v2: 重构版本（引入动作库，基于 baseId 引用）
 * v3: 引入模块化默认计划生成器，完善初始化流程
 *
 * 重要说明：
 * - 使用 fallbackToDestructiveMigration() 允许破坏性迁移（App未上线，无需保留旧数据）
 * - 新增 ExerciseBaseEntity 动作库表
 * - 所有实体的 name 字段已替换为 baseId
 */
@Database(
        entities = {
                ExerciseBaseEntity.class,
                ExerciseEntity.class,
                HistoryEntity.class,
                PlanEntity.class,
                TemplateEntity.class
        },
        // ⭐ 【核心修改】将版本号升级为 3，强制触发手机拆掉旧数据库重建，从而执行 onCreate
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    public abstract WorkoutDao workoutDao();

    private static volatile AppDatabase INSTANCE;

    // ⭐ 【新增】建立一个专门干脏活累活的“后台施工队” (Thread Pool)
    // 以后所有不需要立刻在界面上显示的数据库操作，都可以交给它
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    /**
     * 系统占位符动作的固定 ID
     * 用于计划模板中的占位符，不会出现在用户的动作库搜索结果中
     */
    public static final long SYSTEM_PLACEHOLDER_ID = 1L;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "fitness_plan_database"
                            )
                            .fallbackToDestructiveMigration()  // 允许破坏性迁移
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    super.onCreate(db);

                                    // ⭐ 【核心修改】数据库创建的瞬间，把活交给统一的后台施工队
                                    databaseWriteExecutor.execute(() -> {

                                        // 1. 先保留你优秀的原始设计：插入固定 ID=1 的系统占位符 (使用原生 SQL)
                                        db.execSQL(
                                                "INSERT INTO exercise_library (baseId, name, defaultUnit, category, isDeleted, createdAt, lastUsedAt) " +
                                                        "VALUES (1, '[系统占位符]', 'kg', 'system', 1, " + System.currentTimeMillis() + ", 0)"
                                        );

                                        // 2. 获取数据库管家 (DAO)
                                        WorkoutDao dao = INSTANCE.workoutDao();

                                        // 3. 呼叫我们的外包生成器，搬入默认的推拉腿计划！
                                        // 这里插入的新动作会自动从 ID=2 开始，不会与占位符冲突
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