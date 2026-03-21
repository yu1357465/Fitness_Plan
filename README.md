# Fitness_Plan 项目架构说明书 (2026-01 重构版)

> **版本**: v2.0  
> **重构日期**: 2026年1月  
> **核心变更**: 从字符串名称存储迁移到动作库 ID 引用架构

---

## 1. 项目概况

### 1.1 项目类型
Android 原生健身训练记录应用，采用 MVVM-Like 架构模式，使用 Room 数据库进行本地数据持久化。

### 1.2 核心技术栈

| 技术组件 | 版本/说明 | 用途 |
|---------|----------|------|
| **Room Database** | 2.x | 本地数据库 ORM 框架 |
| **MPAndroidChart** | 3.x | 历史数据可视化图表 |
| **RecyclerView** | AndroidX | 列表展示与交互 |
| **ExecutorService** | Java 并发 | 后台线程池管理 |
| **Material Design** | AndroidX | UI 组件库 |

### 1.3 应用功能模块
- **训练记录**: 今日训练动作的增删改查、完成状态标记
- **计划管理**: 多计划多训练日的模板系统
- **历史归档**: 训练记录的时间序列存储与查询
- **数据可视化**: 单个动作的重量/次数趋势图表
- **数据备份**: CSV 格式的导入导出功能

---

## 2. 核心架构变动说明 ⭐

### 2.1 重构背景与动机

**v1.0 问题 (字符串存储模式)**:
```java
// 旧架构：直接存储动作名称字符串
public class ExerciseEntity {
    public String name;  // ❌ 问题：数据冗余、重命名困难
}
public class HistoryEntity {
    public String exerciseName;  // ❌ 问题：历史数据无法统一更新
}
```


**存在的核心问题**:
1. **数据冗余**: 每条训练记录都重复存储完整的动作名称字符串
2. **重命名噩梦**: 修改动作名称需要遍历更新所有相关记录
3. **数据碎片化**: 无法统计"有多少个不同的动作"
4. **拼写不一致**: 可能存在 "卧推" 和 "卧推 " (多空格) 等脏数据

### 2.2 重构方案：动作库分离架构

**v2.0 解决方案 (ID 引用模式)**:
```java
// 新架构：引入动作库表作为唯一真理来源
@Entity(tableName = "exercise_library")
public class ExerciseBaseEntity {
    @PrimaryKey(autoGenerate = true)
    public long baseId;        // ✅ 唯一标识符
    public String name;        // ✅ 单一真理来源
    public String defaultUnit; // ✅ 默认单位 (kg/lbs)
    public String category;    // ✅ 动作分类
    public boolean isDeleted;  // ✅ 软删除标记
}

// 训练记录：只存储 baseId 引用
@Entity(tableName = "exercise_table")
public class ExerciseEntity {
    public long baseId;  // ✅ 外键引用
    // 通过 JOIN 查询获取动作名称
}

// 历史记录：只存储 baseId 引用
@Entity(tableName = "history_table")
public class HistoryEntity {
    public long baseId;  // ✅ 外键引用
    // 通过 JOIN 查询获取动作名称
}
```


### 2.3 数据库关系图 (Mermaid ER Diagram)

```
erDiagram
    exercise_library ||--o{ exercise_table : "1:N 引用"
    exercise_library ||--o{ history_table : "1:N 引用"
    exercise_library ||--o{ template_table : "1:N 引用"
    
    exercise_library {
        long baseId PK "动作库主键"
        string name "动作名称 (唯一真理)"
        string defaultUnit "默认单位"
        string category "分类标签"
        boolean isDeleted "软删除标记"
        long createdAt "创建时间"
        long lastUsedAt "最后使用时间"
    }
    
    exercise_table {
        int id PK "记录主键"
        long baseId FK "动作库外键 ⭐"
        double weight "重量"
        int sets "组数"
        int reps "次数"
        boolean isCompleted "完成状态"
        int sortOrder "排序字段"
    }
    
    history_table {
        int id PK "历史主键"
        long date "时间戳"
        string dateStr "日期字符串"
        string workoutName "训练名称"
        long baseId FK "动作库外键 ⭐"
        double weight "重量"
        int sets "组数"
        int reps "次数"
    }
    
    template_table {
        int id PK "模板主键"
        int planId "计划ID"
        string dayName "训练日名称"
        long baseId FK "动作库外键 ⭐"
        double defaultWeight "默认重量"
        int defaultSets "默认组数"
        int defaultReps "默认次数"
        int sortOrder "排序字段"
    }
    
    plan_table {
        int planId PK "计划主键"
        string planName "计划名称"
        boolean isActive "激活状态"
    }
```


### 2.4 外键约束策略

```java
@Entity(
        foreignKeys = @ForeignKey(
                entity = ExerciseBaseEntity.class,
                parentColumns = "baseId",
                childColumns = "baseId",
                onDelete = ForeignKey.NO_ACTION  // ⭐ 关键：保留历史数据
        ),
        indices = {@Index("baseId")}  // 性能优化
)
```


**设计原则**:
- **NO_ACTION**: 删除动作库记录时不级联删除历史记录
- **软删除优先**: 使用 `isDeleted` 标记而非物理删除
- **数据完整性**: 历史记录永久保留，即使动作已被"删除"

### 2.5 系统占位符机制

为避免"点击修改动作名称"污染动作库，引入**固定 ID 系统占位符**:

```java
public abstract class AppDatabase extends RoomDatabase {
    // 系统占位符固定 ID = 1
    public static final long SYSTEM_PLACEHOLDER_ID = 1L;

    @Override
    public void onCreate(@NonNull SupportSQLiteDatabase db) {
        // 数据库初始化时预置
        db.execSQL(
                "INSERT INTO exercise_library (baseId, name, defaultUnit, category, isDeleted, createdAt, lastUsedAt) " +
                        "VALUES (1, '[系统占位符]', 'kg', 'system', 1, " + System.currentTimeMillis() + ", 0)"
        );
    }
}
```


**优势**:
- ✅ 避免重复插入占位符
- ✅ 查询时自动排除 (`baseId != 1`)
- ✅ 代码中直接引用常量，性能最优

---

## 3. 文件字典与功能解析

### 3.1 数据层 (`com.example.fitness_plan.data`)

#### 3.1.1 实体类 (Entity)

| 文件名 | 职责说明 | 重构状态 |
|-------|---------|---------|
| **ExerciseBaseEntity.java** | **[新增]** 动作库表，动作名称的唯一真理来源 | ⭐ 核心新增 |
| **ExerciseEntity.java** | 今日训练记录表，存储 `baseId` 外键 | ⭐ 重构 (移除 `name` 字段) |
| **HistoryEntity.java** | 历史训练记录表，存储 `baseId` 外键 | ⭐ 重构 (移除 `exerciseName` 字段) |
| **TemplateEntity.java** | 训练计划模板表，存储 `baseId` 外键 | ⭐ 重构 (移除 `exerciseName` 字段) |
| **PlanEntity.java** | 训练计划表，无变动 | ✅ 保持不变 |

#### 3.1.2 POJO 类 (数据传输对象)

| 文件名 | 职责说明 | 使用场景 |
|-------|---------|---------|
| **ExerciseWithDetail.java** | **[新增]** JOIN 查询结果封装 (ExerciseEntity + 动作名称) | UI 层显示 |
| **HistoryWithDetail.java** | **[新增]** JOIN 查询结果封装 (HistoryEntity + 动作名称) | 历史记录展示 |
| **TemplateWithDetail.java** | **[新增]** JOIN 查询结果封装 (TemplateEntity + 动作名称) | 计划管理展示 |

**POJO 类设计模式**:
```java
public class ExerciseWithDetail {
    @Embedded
    public ExerciseEntity exercise;  // 原始实体数据

    // JOIN 查询的额外字段
    public String exerciseName;      // 来自 exercise_library.name
    public String category;
    public String defaultUnit;

    // UI 状态字段 (不参与数据库映射)
    @Ignore
    public boolean isExpanded = false;
    @Ignore
    public boolean isDeleteConfirmMode = false;
}
```


#### 3.1.3 数据访问层 (DAO)

**WorkoutDao.java** - 数据库操作接口

**核心新增方法** (⭐ 重构重点):
```java
// ========== 动作库 CRUD ==========
@Insert(onConflict = OnConflictStrategy.REPLACE)
long insertExerciseBase(ExerciseBaseEntity exerciseBase);

@Query("SELECT * FROM exercise_library WHERE name = :name AND isDeleted = 0 AND baseId != 1 LIMIT 1")
ExerciseBaseEntity getExerciseBaseByName(String name);  // 查重，排除系统占位符

@Query("SELECT * FROM exercise_library WHERE baseId = :baseId")
ExerciseBaseEntity getExerciseBaseById(long baseId);

@Query("UPDATE exercise_library SET isDeleted = 1 WHERE baseId = :baseId")
void softDeleteExercise(long baseId);  // 软删除

// ========== JOIN 查询 (UI 层使用) ==========
@Query("SELECT e.*, eb.name AS exerciseName, eb.category AS category, eb.defaultUnit AS defaultUnit " +
        "FROM exercise_table e " +
        "INNER JOIN exercise_library eb ON e.baseId = eb.baseId " +
        "ORDER BY e.sort_order ASC")
List<ExerciseWithDetail> getAllExercisesWithDetail();

@Query("SELECT h.*, eb.name AS exerciseName " +
        "FROM history_table h " +
        "INNER JOIN exercise_library eb ON h.baseId = eb.baseId " +
        "ORDER BY h.date DESC")
List<HistoryWithDetail> getAllHistoryWithDetail();

// ========== 根据 baseId 查询 (替代原 name 查询) ==========
@Query("SELECT * FROM history_table WHERE baseId = :baseId ORDER BY date DESC")
List<HistoryEntity> getHistoryByBaseId(long baseId);  // 替代 getHistoryByName()

@Query("DELETE FROM template_table WHERE planId = :planId AND dayName = :dayName AND baseId = :baseId")
void deleteTemplateByBaseId(int planId, String dayName, long baseId);  // 替代 deleteTemplateByName()
```


**废弃的方法** (❌ 已移除):
```java
// ❌ 不再使用字符串名称查询
@Query("SELECT * FROM history_table WHERE exerciseName = :name ORDER BY date DESC")
List<HistoryEntity> getHistoryByName(String name);

// ❌ 智能重命名逻辑已废弃 (改为在动作库层面统一管理)
void smartRename(String oldName, String newName);
```


#### 3.1.4 辅助工具类

| 文件名 | 职责说明 | 使用场景 |
|-------|---------|---------|
| **EntityNameCache.java** | **[新增]** baseId → name 的内存缓存 | 减少重复查询，提升性能 |
| **ExerciseLibraryHelper.java** | **[新增]** 动作库操作的便捷封装 | 简化 getOrCreate 逻辑 |
| **GhostExerciseHelper.java** | **[新增]** 幽灵卡片 (baseId=-1) 的工具方法 | 处理临时占位卡片 |

**EntityNameCache 使用示例**:
```java
EntityNameCache cache = EntityNameCache.getInstance();
cache.setDao(workoutDao);

// 批量加载到缓存
cache.preloadAll(workoutDao);

// 获取名称 (先查缓存，未命中再查数据库)
String name = cache.getExerciseName(baseId);

// 特殊处理
if (baseId == -1) return "新动作";  // 幽灵卡片
        if (baseId == 1) return "点击修改动作名称";  // 系统占位符
```


#### 3.1.5 数据库管理类

**AppDatabase.java** - Room 数据库单例

```java
@Database(
        entities = {
                ExerciseBaseEntity.class,  // ⭐ 新增
                ExerciseEntity.class,
                HistoryEntity.class,
                PlanEntity.class,
                TemplateEntity.class
        },
        version = 2,  // ⭐ 版本升级 (v1 → v2)
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public static final long SYSTEM_PLACEHOLDER_ID = 1L;

    // 数据库创建回调：预置系统占位符
    .addCallback(new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            // 插入 ID=1 的系统占位符
        }
    })
}
```


---

### 3.2 UI 层 (`com.example.fitness_plan`)

#### 3.2.1 Activity

| 文件名 | 职责说明 | 核心变动 |
|-------|---------|---------|
| **MainActivity.java** | 首页训练记录界面 | ⭐ 使用 `ExerciseWithDetail` 显示数据 |
| **HistoryActivity.java** | 历史记录查询界面 | ⭐ 使用 `HistoryWithDetail` + `EntityNameCache` |
| **PlanListActivity.java** | 计划管理界面 | ⭐ 创建计划时使用 `SYSTEM_PLACEHOLDER_ID` |
| **StatsActivity.java** | 数据统计图表界面 | ⭐ 使用 `EntityNameCache` 解析名称 |
| **SettingsActivity.java** | 设置与备份界面 | 无变动 |

**MainActivity 核心逻辑变化**:
```java
// 旧代码：直接操作 ExerciseEntity.name
List<ExerciseEntity> workoutPlan = workoutDao.getAllExercises();
for (ExerciseEntity ex : workoutPlan) {
        holder.tvName.setText(ex.name);  // ❌ 字段不存在
}

// 新代码：使用 JOIN 查询获取完整信息
List<ExerciseWithDetail> workoutPlan = workoutDao.getAllExercisesWithDetail();
for (ExerciseWithDetail ex : workoutPlan) {
        holder.tvName.setText(ex.exerciseName);  // ✅ 从 JOIN 结果获取
}
```


#### 3.2.2 Adapter

**ExerciseRecyclerAdapter.java** - 训练记录列表适配器

**核心变更**:
```java
// 接口定义
public interface OnItemActionListener {
    void onUpdate(ExerciseWithDetail exercise);  // ⭐ 参数类型变更
    void onShowChart(LineChart chart, ExerciseWithDetail exercise);
    void onRename(ExerciseWithDetail exercise);
    void onDelete(ExerciseWithDetail exercise);
    // ...
}

// 构造函数
public ExerciseRecyclerAdapter(
        Context context,
        List<ExerciseWithDetail> list,  // ⭐ 泛型变更
        boolean isLbsMode,
        OnItemActionListener listener
) { }

// 绑定逻辑
private void bindItem(ItemViewHolder holder, int position) {
    ExerciseWithDetail exercise = exerciseList.get(position);

    // 判断是否为幽灵卡片
    boolean isGhost = exercise.getBaseId() == -1;

    // 显示名称
    holder.name.setText(exercise.exerciseName);  // ⭐ 从 JOIN 字段获取

    // 访问原始数据
    holder.sets.setText(String.valueOf(exercise.getSets()));
    holder.weight.setText(String.valueOf(exercise.getWeight()));
}
```


**HistoryAdapter.java** - 历史记录适配器

```java
@Override
public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
    ExerciseEntity ex = exercises.get(position);

    // 使用缓存查询名称
    EntityNameCache cache = EntityNameCache.getInstance();
    String exerciseName = cache.getExerciseName(ex.baseId);  // ⭐ 缓存优化

    holder.tvName.setText(exerciseName);
    holder.tvDetails.setText("完成: " + ex.weight + "kg × " + ex.reps + "次 (" + ex.sets + "组)");
}
```


---

### 3.3 工具类层 (`com.example.fitness_plan.utils`)

| 文件名 | 职责说明 | 核心变动 |
|-------|---------|---------|
| **CsvExportUtils.java** | CSV 导出逻辑 | ⭐ 使用 `EntityNameCache` 获取动作名称 |
| **CsvImportUtils.java** | CSV 导入逻辑 | ⭐ 导入时自动创建 `ExerciseBaseEntity` |
| **UnifiedBackupUtils.java** | 统一备份接口 | 无变动 |
| **MediaStoreUtils.java** | 媒体存储工具 | 无变动 |
| **FileUtils.java** | 文件操作工具 | 无变动 |

**CsvImportUtils 核心逻辑**:
```java
public static List<HistoryEntity> importHistoryFromCSV(
        Context context,
        WorkoutDao workoutDao,  // ⭐ 新增参数
        Uri uri
) {
    for (String line : csvLines) {
        String exerciseName = tokens[2].trim();

        // ⭐ 自动创建或查找动作库记录
        ExerciseBaseEntity base = workoutDao.getExerciseBaseByName(exerciseName);
        if (base == null) {
            base = new ExerciseBaseEntity(exerciseName, "kg", "其他");
            long baseId = workoutDao.insertExerciseBase(base);
            base.baseId = baseId;
        }

        // 使用 baseId 创建历史记录
        HistoryEntity history = new HistoryEntity(
                date, dateStr, workoutName,
                base.baseId,  // ⭐ 使用 ID 而非字符串
                weight, reps, sets
        );
        importedList.add(history);
    }
}
```


---

## 4. 数据流向图

### 4.1 新建动作流程

```
sequenceDiagram
    participant User as 用户
    participant UI as MainActivity
    participant Adapter as ExerciseRecyclerAdapter
    participant DAO as WorkoutDao
    participant LibTable as exercise_library
    participant ExTable as exercise_table

    User->>UI: 点击添加动作
    UI->>UI: showAddDialog("哑铃弯举")
    
    UI->>DAO: getExerciseBaseByName("哑铃弯举")
    DAO->>LibTable: SELECT * WHERE name = '哑铃弯举'
    
    alt 动作不存在
        LibTable-->>DAO: null
        DAO->>LibTable: INSERT INTO exercise_library
        LibTable-->>DAO: return baseId=123
    else 动作已存在
        LibTable-->>DAO: return baseId=123
    end
    
    UI->>DAO: insert(new ExerciseEntity(baseId=123, weight=20, ...))
    DAO->>ExTable: INSERT INTO exercise_table
    
    UI->>DAO: getAllExercisesWithDetail()
    DAO->>ExTable: SELECT e.*, eb.name AS exerciseName FROM exercise_table e JOIN exercise_library eb
    ExTable-->>DAO: List<ExerciseWithDetail>
    
    DAO-->>UI: 返回 JOIN 结果
    UI->>Adapter: setData(exerciseList)
    Adapter->>User: 显示"哑铃弯举 20kg 4×12"
```


### 4.2 训练记录归档流程

```
flowchart TD
    A[用户点击完成打卡] --> B{检查未完成动作}
    B -->|有未完成| C[显示弹窗选择]
    B -->|全部完成| D[直接归档]
    
    C --> E[选项1: 全部标记完成]
    C --> F[选项2: 仅归档已完成]
    
    E --> G[更新 exercise_table.isCompleted = true]
    F --> H[过滤已完成记录]
    
    G --> I[创建 HistoryEntity]
    H --> I
    
    I --> J[存储 baseId 外键]
    J --> K[INSERT INTO history_table]
    
    K --> L[清空 exercise_table]
    L --> M[加载下一天模板]
    
    M --> N[从 template_table 读取 baseId]
    N --> O[创建新的 ExerciseEntity]
    O --> P[刷新 UI]
    
    style I fill:#90EE90
    style J fill:#90EE90
    style K fill:#90EE90
```


### 4.3 历史查询与展示流程

```
graph LR
    A[HistoryActivity] --> B[getAllHistory]
    B --> C[读取 history_table]
    
    C --> D{使用缓存?}
    D -->|是| E[EntityNameCache.getExerciseName]
    D -->|否| F[JOIN 查询 exercise_library]
    
    E --> G[baseId → name 映射]
    F --> G
    
    G --> H[HistoryAdapter]
    H --> I[按日期分组显示]
    
    style E fill:#FFD700
    style G fill:#FFD700
```


### 4.4 数据备份流程

```
sequenceDiagram
    participant User as 用户
    participant Settings as SettingsActivity
    participant ExportUtil as CsvExportUtils
    participant Cache as EntityNameCache
    participant DAO as WorkoutDao
    participant MediaStore as MediaStoreUtils

    User->>Settings: 点击导出数据
    Settings->>DAO: getAllHistory()
    DAO-->>Settings: List<HistoryEntity>
    
    Settings->>Cache: getInstance().setDao(dao)
    Cache->>DAO: getAllExerciseBases()
    DAO-->>Cache: 预加载到缓存
    
    Settings->>ExportUtil: generateCsvContent(historyList)
    
    loop 遍历每条历史记录
        ExportUtil->>Cache: getExerciseName(baseId)
        Cache-->>ExportUtil: "哑铃弯举"
        ExportUtil->>ExportUtil: 拼接 CSV 行
    end
    
    ExportUtil-->>Settings: csvContent (String)
    Settings->>MediaStore: saveToDownloads(csvContent)
    MediaStore-->>Settings: 文件路径
    
    Settings->>User: Toast "导出成功"
```


---

## 5. 交互逻辑更新

### 5.1 UI 层如何获取动作名称

#### 方案 A: JOIN 查询 (推荐用于列表展示)

**适用场景**: MainActivity、PlanListActivity 等需要一次性加载多条记录的界面

```java
// 1. DAO 层定义 JOIN 查询
@Query("SELECT e.*, eb.name AS exerciseName, eb.category, eb.defaultUnit " +
        "FROM exercise_table e " +
        "INNER JOIN exercise_library eb ON e.baseId = eb.baseId " +
        "ORDER BY e.sort_order ASC")
List<ExerciseWithDetail> getAllExercisesWithDetail();

// 2. Activity 层调用
executorService.execute(() -> {
List<ExerciseWithDetail> workoutPlan = workoutDao.getAllExercisesWithDetail();

runOnUiThread(() -> {
adapter = new ExerciseRecyclerAdapter(this, workoutPlan, isLbsMode, listener);
        recyclerView.setAdapter(adapter);
    });
            });

// 3. Adapter 层直接使用
private void bindItem(ItemViewHolder holder, int position) {
    ExerciseWithDetail exercise = exerciseList.get(position);
    holder.tvName.setText(exercise.exerciseName);  // 直接访问 JOIN 字段
    holder.tvWeight.setText(String.valueOf(exercise.getWeight()));
}
```


**优势**:
- ✅ 一次查询获取所有信息，性能最优
- ✅ 数据库层面完成 JOIN，逻辑清晰
- ✅ 适合批量数据展示

#### 方案 B: 缓存查询 (推荐用于单条记录或异步加载)

**适用场景**: HistoryAdapter、StatsActivity 等需要逐条解析的场景

```java
// 1. 初始化缓存
EntityNameCache cache = EntityNameCache.getInstance();
cache.setDao(workoutDao);
cache.preloadAll(workoutDao);  // 预加载所有动作到内存

// 2. Adapter 中使用
@Override
public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    ExerciseEntity ex = exercises.get(position);

    // 从缓存获取名称 (先查内存，未命中再查数据库)
    String exerciseName = cache.getExerciseName(ex.baseId);

    holder.tvName.setText(exerciseName);
}
```


**优势**:
- ✅ 避免重复查询数据库
- ✅ 内存开销可控 (通常 < 100KB)
- ✅ 支持惰性加载

**特殊值处理**:
```java
public String getExerciseName(long baseId) {
    if (baseId == -1) return "新动作";  // 幽灵卡片
    if (baseId == 1) return "点击修改动作名称";  // 系统占位符

    // 查缓存 → 查数据库 → 返回"未知动作"
}
```


### 5.2 重命名逻辑演变

#### v1.0 旧逻辑 (字符串模式)

```java
// ❌ 需要遍历更新所有相关表
void renameExercise(String oldName, String newName) {
    // 更新今日训练
    workoutDao.updateExerciseName(oldName, newName);

    // 更新历史记录 (可能影响数千条数据)
    workoutDao.updateHistoryName(oldName, newName);

    // 更新计划模板
    workoutDao.updateTemplateName(oldName, newName);
}
```


**问题**:
- 性能差：需要扫描全表
- 风险高：可能遗漏某张表
- 原子性差：无法保证事务一致性

#### v2.0 新逻辑 (ID 引用模式)

```java
// ✅ 只需更新一张表
void renameExercise(long baseId, String newName) {
    // 只更新动作库
    ExerciseBaseEntity base = workoutDao.getExerciseBaseById(baseId);
    base.name = newName;
    workoutDao.updateExerciseBase(base);

    // 所有引用该 baseId 的记录自动生效！
    // 无需手动更新 exercise_table、history_table、template_table
}
```


**优势**:
- ✅ O(1) 复杂度，只更新一条记录
- ✅ 原子性保证
- ✅ 所有引用自动生效

### 5.3 动作删除逻辑

#### 软删除策略

```java
// 用户在 UI 上"删除动作"
void deleteExercise(long baseId) {
    // 1. 软删除动作库记录
    workoutDao.softDeleteExercise(baseId);
    // UPDATE exercise_library SET isDeleted = 1 WHERE baseId = ?

    // 2. 历史记录不受影响 (外键约束为 NO_ACTION)
    // SELECT * FROM history_table WHERE baseId = ? 仍然可以查询

    // 3. UI 层查询时自动过滤
    // SELECT * FROM exercise_library WHERE isDeleted = 0 AND baseId != 1
}
```


**设计原则**:
- **历史数据永久保留**: 即使动作被删除，历史记录仍可查询
- **动作库隐藏**: `isDeleted = 1` 的动作不出现在搜索结果中
- **可恢复**: 支持将 `isDeleted` 改回 0 来恢复动作

### 5.4 图表绘制逻辑

```java
// MainActivity 中点击动作展开图表
@Override
public void onShowChart(LineChart chart, ExerciseWithDetail exercise) {
    loadChartData(chart, exercise.getBaseId());  // ⭐ 传递 baseId
}

private void loadChartData(LineChart chart, long baseId) {
    executorService.execute(() -> {
        // 根据 baseId 查询历史记录
        List<HistoryEntity> historyList = workoutDao.getHistoryByBaseId(baseId);

        // 提取最近 10 次训练数据
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < historyList.size(); i++) {
            HistoryEntity h = historyList.get(i);
            entries.add(new Entry(i, (float) h.weight));
        }

        runOnUiThread(() -> setupChart(chart, entries));
    });
}
```


---

## 6. 核心设计模式总结

### 6.1 数据库规范化 (3NF)

| 范式 | 说明 | 实现 |
|------|------|------|
| **1NF** | 原子性 | 每个字段不可再分 |
| **2NF** | 消除部分依赖 | baseId 完全决定动作信息 |
| **3NF** | 消除传递依赖 | 动作名称只存储在 exercise_library |

### 6.2 单一职责原则 (SRP)

```
exercise_library  → 负责动作的"定义"
exercise_table    → 负责当前训练的"状态"
history_table     → 负责过往训练的"存档"
template_table    → 负责计划的"模板"
```


### 6.3 外键引用策略

```sql
-- 保留历史数据完整性
FOREIGN KEY (baseId) REFERENCES exercise_library(baseId) ON DELETE NO_ACTION

-- 索引优化查询性能
CREATE INDEX idx_exercise_baseId ON exercise_table(baseId);
CREATE INDEX idx_history_baseId ON history_table(baseId);
```


### 6.4 缓存优化策略

```java
// 三级缓存架构
1. 内存缓存 (EntityNameCache)     → 最快，容量小
2. Room 数据库缓存                 → 较快，持久化
3. SQLite 原生查询                → 兜底方案
```


---

## 7. 迁移指南

### 7.1 从 v1.0 升级到 v2.0

**数据库版本升级**:
```java
@Database(
        entities = { ... },
version = 2,  // v1 → v2
exportSchema = false
        )
        .fallbackToDestructiveMigration()  // 允许破坏性迁移（仅限未上线应用）
```


**代码迁移检查清单**:
- [ ] 所有 `exercise.name` 改为 `exercise.exerciseName` (JOIN 字段)
- [ ] 所有 `List<ExerciseEntity>` 改为 `List<ExerciseWithDetail>` (UI 层)
- [ ] 所有 `new ExerciseEntity(name, ...)` 改为 `new ExerciseEntity(baseId, ...)`
- [ ] 所有 `getHistoryByName()` 改为 `getHistoryByBaseId()`
- [ ] 移除 `smartRename()` 调用
- [ ] Adapter 接口参数类型更新

### 7.2 常见问题排查

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `cannot find symbol: name` | 仍在访问已删除的字段 | 使用 `exerciseName` 或 `getExerciseName()` |
| `incompatible types: String cannot be converted to long` | 构造函数参数错误 | 传递 `baseId` 而非 `name` |
| `CURSOR_MISMATCH` | POJO 字段与查询不匹配 | 添加 `@Ignore` 注解到 UI 状态字段 |
| `FOREIGN KEY constraint failed` | 尝试插入不存在的 baseId | 先创建 ExerciseBaseEntity |

---

## 8. 性能优化建议

### 8.1 查询优化

```java
// ✅ 推荐：一次 JOIN 查询
List<ExerciseWithDetail> exercises = workoutDao.getAllExercisesWithDetail();

// ❌ 避免：循环查询
for (ExerciseEntity ex : exercises) {
String name = workoutDao.getExerciseBaseById(ex.baseId).name;  // N+1 问题
}
```


### 8.2 索引优化

```sql
-- 自动创建的索引
CREATE INDEX idx_exercise_baseId ON exercise_table(baseId);
CREATE INDEX idx_history_baseId ON history_table(baseId);
CREATE INDEX idx_template_baseId ON template_table(baseId);

-- 优化 JOIN 性能
EXPLAIN QUERY PLAN
SELECT e.*, eb.name FROM exercise_table e JOIN exercise_library eb ON e.baseId = eb.baseId;
```


### 8.3 缓存预热

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // 后台预加载缓存
    executorService.execute(() -> {
        EntityNameCache.getInstance().preloadAll(workoutDao);
    });
}
```


---

## 9. 未来扩展方向

### 9.1 动作库增强

- [ ] 动作分类管理 (胸、背、腿等)
- [ ] 自定义动作单位 (kg、lbs、次数、时间)
- [ ] 动作教学视频链接
- [ ] 个人记录 (PR) 自动计算

### 9.2 数据分析

- [ ] 基于 baseId 的训练频率统计
- [ ] 肌群训练平衡度分析
- [ ] 训练量自动调整建议

### 9.3 云同步支持

- [ ] 基于 baseId 的跨设备数据同步
- [ ] 冲突解决策略 (exercise_library 合并)

---

## 附录：快速参考

### A. 关键常量

```java
AppDatabase.SYSTEM_PLACEHOLDER_ID = 1L   // 系统占位符固定 ID
GHOST_CARD_BASE_ID = -1L                  // 幽灵卡片标识
```


### B. 核心 SQL 语句

```sql
-- 获取今日训练 (带动作名称)
SELECT e.*, eb.name AS exerciseName
FROM exercise_table e
         INNER JOIN exercise_library eb ON e.baseId = eb.baseId
ORDER BY e.sort_order;

-- 查询动作历史记录
SELECT * FROM history_table WHERE baseId = ? ORDER BY date DESC;

-- 软删除动作
UPDATE exercise_library SET isDeleted = 1 WHERE baseId = ?;
```


### C. 代码迁移速查表

| v1.0 (旧代码) | v2.0 (新代码) |
|--------------|--------------|
| `exercise.name` | `exercise.exerciseName` (ExerciseWithDetail) |
| `new ExerciseEntity("深蹲", ...)` | `new ExerciseEntity(baseId, ...)` |
| `getHistoryByName("深蹲")` | `getHistoryByBaseId(baseId)` |
| `smartRename(oldName, newName)` | `updateExerciseBase(baseId, newName)` |
| `List<ExerciseEntity>` | `List<ExerciseWithDetail>` (UI 层) |

---

**文档维护**: 2026年1月  
**架构版本**: v2.0  
**下次审查**: 2026年6月