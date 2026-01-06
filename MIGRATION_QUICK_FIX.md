# 数据库重构 - 快速修复指南

由于改动量巨大（48个编译错误），建议采用**渐进式修复**策略。

## 🚨 核心问题

所有 `exercise.name` 和 `exercise.exerciseName` 的访问都需要改为通过 `baseId` 查询。

## 💡 推荐方案：使用辅助查询方法

### 方案A：最小改动（推荐用于快速编译通过）

在每个需要 name 的地方，临时添加一个查询：

```java
// 旧代码
String name = exercise.name;

// 新代码（临时方案）
ExerciseBaseEntity base = workoutDao.getExerciseBaseById(exercise.baseId);
String name = base != null ? base.name : "未知动作";
```

### 方案B：使用 JOIN 查询（推荐用于正式使用）

修改数据加载逻辑，直接使用 `WithDetail` 类型：

```java
// 旧代码
List<ExerciseEntity> workoutPlan = workoutDao.getAllExercises();

// 新代码
List<ExerciseWithDetail> workoutPlan = workoutDao.getAllExercisesWithDetail();
```

## 📝 具体修复步骤

### 1. MainActivity 的关键修改

#### a) 修改成员变量类型

```java
// 旧：private List<ExerciseEntity> workoutPlan = new ArrayList<>();
// 新：
private List<ExerciseWithDetail> workoutPlan = new ArrayList<>();
```

#### b) 修改 Adapter 的数据类型

```java
// Adapter 构造函数改为接收 List<ExerciseWithDetail>
adapter = new ExerciseRecyclerAdapter(this, workoutPlan, isLbsMode, listener);
```

#### c) 幽灵卡片判断

```java
// 旧：if (ex.name.equals("新动作"))
// 新：if (ex.exerciseName != null && ex.exerciseName.equals("新动作"))
// 或使用辅助类：if (GhostExerciseHelper.isGhost(ex.exercise.baseId))
```

#### d) 创建幽灵卡片

```java
// 旧：new ExerciseEntity("新动作", 0, 0, 0, false)
// 新：创建幽灵动作需要先有 baseId
ExerciseLibraryHelper helper = new ExerciseLibraryHelper(workoutDao);
long ghostId = helper.getOrCreateExercise("新动作", "kg", "临时");
ExerciseEntity newExercise = new ExerciseEntity(ghostId, 0, 0, 0, false);
```

#### e) 创建普通动作

```java
// 旧：new ExerciseEntity(name, 20.0, 4, 12, false)
// 新：
ExerciseLibraryHelper helper = new ExerciseLibraryHelper(workoutDao);
long baseId = helper.getOrCreateExercise(name);
ExerciseEntity exercise = new ExerciseEntity(baseId, 20.0, 4, 12, false);
```

#### f) 保存历史记录

```java
// 旧：new HistoryEntity(now, dateStr, currentDayName, ex.name, ...)
// 新：new HistoryEntity(now, dateStr, currentDayName, ex.exercise.baseId, ...)
```

#### g) 从模板创建动作

```java
// 旧：new ExerciseEntity(t.exerciseName, ...)
// 新：new ExerciseEntity(t.baseId, ...)
```

#### h) 图表加载

```java
// 旧：loadChartData(chart, exercise.name)
// 新：loadChartData(chart, exercise.exercise.baseId)

// 同时修改 loadChartData 方法签名
private void loadChartData(LineChart chart, long baseId) {
    executorService.execute(() -> {
        List<HistoryEntity> historyList = workoutDao.getHistoryByBaseId(baseId);
        // ...
    });
}
```

### 2. ExerciseRecyclerAdapter 修改

```java
// 修改数据类型
private List<ExerciseWithDetail> items;

public ExerciseRecyclerAdapter(Context context, List<ExerciseWithDetail> items, ...) {
    this.items = items;
}

// 在 onBindViewHolder 中
ExerciseWithDetail item = items.get(position);
holder.name.setText(item.exerciseName);  // ✅ 从关联查询获取
holder.tvWeight.setText(String.valueOf(item.getWeight()));

// 判断幽灵卡片
boolean isGhost = item.exerciseName != null && item.exerciseName.equals("新动作");
```

### 3. HistoryActivity 修改

```java
// 使用 JOIN 查询
List<HistoryWithDetail> history = workoutDao.getAllHistoryWithDetail();

// 分组时
for (HistoryWithDetail record : history) {
    String exerciseName = record.exerciseName;  // ✅
    // ...
}
```

### 4. PlanListActivity 修改

创建模板时需要先获取 baseId：

```java
// 旧：new TemplateEntity(planId, dayName, 0, "点击修改动作名称", ...)
// 新：
ExerciseLibraryHelper helper = new ExerciseLibraryHelper(workoutDao);
long baseId = helper.getOrCreateExercise("点击修改动作名称");
TemplateEntity template = new TemplateEntity(planId, dayName, 0, baseId, 20.0, 3, 12);
```

### 5. CSV 导入导出修改

#### 导出

```java
// 使用 WithDetail 查询
List<HistoryWithDetail> history = workoutDao.getAllHistoryWithDetail();
for (HistoryWithDetail item : history) {
    sb.append(escapeCsv(item.exerciseName));  // ✅
}
```

#### 导入

```java
// 导入时需要获取或创建 baseId
ExerciseLibraryHelper helper = new ExerciseLibraryHelper(workoutDao);
long baseId = helper.getOrCreateExercise(name);
HistoryEntity history = new HistoryEntity(date, dateStr, workoutName, baseId, ...);
```

## 🔧 WorkoutDao 补充方法

添加一个兼容旧代码的方法（如果还没有）：

```java
// 通过名称查找 baseId（用于兼容）
@Query("SELECT baseId FROM exercise_library WHERE name = :name AND isDeleted = 0 LIMIT 1")
Long getBaseIdByName(String name);
```

## ⚡ 快速编译通过的捷径

如果你想快速让代码编译通过，可以暂时在 `ExerciseEntity` 中添加一个 `@Ignore` 字段：

```java
@Entity(tableName = "exercise_table", ...)
public class ExerciseEntity {
    // ... 现有字段

    // 【临时兼容字段】用于快速编译，不存储到数据库
    @Ignore
    public transient String name;

    // 【临时方法】在查询后手动填充 name
    public void fillNameFrom(ExerciseBaseEntity base) {
        this.name = base != null ? base.name : "未知动作";
    }
}
```

然后在 `loadDataFromDatabase()` 中填充：

```java
List<ExerciseEntity> exercises = workoutDao.getAllExercises();
for (ExerciseEntity ex : exercises) {
    ExerciseBaseEntity base = workoutDao.getExerciseBaseById(ex.baseId);
    ex.fillNameFrom(base);  // 临时填充 name 字段
}
```

**注意**：这只是临时方案，最终还是要改用 `WithDetail` 查询。

## ✅ 验证清单

修改完成后，确保：

- [ ] 代码能编译通过
- [ ] App 能启动（数据库会重建）
- [ ] 能创建新动作
- [ ] 能看到动作名称
- [ ] 能保存历史记录
- [ ] 能查看历史图表
- [ ] 能修改动作名称

## 🆘 如果实在太复杂

如果觉得修改太复杂，可以考虑：

1. **回退重构**：恢复到旧的基于字符串的架构
2. **分支开发**：在新分支逐步迁移，保留主分支可用
3. **请求协助**：将具体的文件内容发给我，我帮你修改

---

**重要提示**：由于改动巨大，建议：
1. 先备份当前代码
2. 创建新的 Git 分支
3. 逐个文件修改测试
4. 不要一次性修改所有文件
