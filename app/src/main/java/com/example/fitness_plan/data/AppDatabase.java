package com.example.fitness_plan.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * =================================================================================
 * 数据库持有者 (Database Holder)
 * =================================================================================
 * 作用：
 * 这是 Room 数据库的入口点。它负责创建数据库实例，并提供对 DAO 的访问权限。
 *
 * * @Database 注解：
 * 1. entities = {ExerciseEntity.class}: 告诉 Room 这个数据库里有哪些表。
 * 如果有多个表，写法是 {TableA.class, TableB.class}。
 * 2. version = 1: 数据库版本号。
 * 【重要】以后如果您修改了 ExerciseEntity (比如增加了一列)，
 * 必须把这里的 version 改成 2，否则 APP 启动时会崩溃。
 * 3. exportSchema = false: 是否把数据库结构导出成文本文件。这里暂时不需要。
 */
@Database(entities = {ExerciseEntity.class, HistoryEntity.class, TemplateEntity.class, PlanEntity.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    // 抽象方法：Room 会自动生成代码来实现它，返回我们可以使用的 WorkoutDao
    public abstract WorkoutDao workoutDao();

    // ==========================================
    // 单例模式实现 (Singleton Implementation)
    // ==========================================

    /**
     * volatile 关键字：
     * 英语解析: 'Volatile' (易变的/不稳定的)。
     * 作用：确保内存可见性 (Memory Visibility)。
     * 当一个线程创建了数据库实例，其他线程能立刻看到这个变化。
     * 如果不加 volatile，其他线程可能会读取到半初始化的对象，导致崩溃。
     */
    private static volatile AppDatabase INSTANCE;

    /**
     * 获取数据库实例的方法
     * @param context 上下文，用于访问文件系统
     * @return 唯一的数据库实例
     */
    public static AppDatabase getDatabase(final Context context) {
        // 第一次检查：如果不为 null，直接返回，避免进入耗时的同步块
        if (INSTANCE == null) {
            // synchronized 关键字：
            // 英语解析: 'Synchronized' (同步)。
            // 作用：加锁。保证同一时间只有一个线程能执行大括号里的代码。
            // 防止两个线程同时发现 INSTANCE 为 null，然后分别创建了两个数据库实例。
            synchronized (AppDatabase.class) {
                // 第二次检查：双重保险
                if (INSTANCE == null) {
                    // 创建数据库
                    // getApplicationContext():
                    // 【关键】必须使用 Application 的上下文，而不是 Activity 的上下文。
                    // 这样即使 Activity 关闭了，数据库连接也不会导致 Activity 内存泄漏。
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "fitness_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}