# 数据库架构重构指南

## 📋 重构概述

本次重构将应用从**基于字符串存储动作名称**的架构升级为**基于引用ID的动作库模型**，实现了数据库规范化。

### 核心变化

| 旧架构 | 新架构 |
|--------|--------|
| `ExerciseEntity.name = "深蹲"` | `ExerciseEntity.baseId = 123` → `ExerciseBaseEntity{baseId:123, name:"深蹲"}` |
| 每次都存储完整名称 | 通过ID引用，名称存储一次 |
| 无法区分"改错别字"和"换动作" | 全局重命名 vs 仅修改当前 |
| 相同动作不同名称无法统计 | 所有引用同一ID，统计准确 |

---

## 🗄️ 数据层变化

### 1. 新增实体：ExerciseBaseEntity（动作库）

```java
// 动作库表 - 单一真理来源
@Entity(tableName = "exercise_library")
public class ExerciseBaseEntity {
    @PrimaryKey(autoGenerate = true)
    public long baseId;          // 唯一标识

    public String name;          // 动作名称
    public String defaultUnit;   // 默认单位（kg/lb）
    public String category;      // 训练部位
    public boolean isDeleted;    // 软删除标记
    public long createdAt;       // 创建时间
    public long lastUsedAt;      // 最后使用时间
}
```

### 2. 修改后的实体

#### ExerciseEntity（今日训练）
```java
// 旧版本
public String name;  // ❌ 删除

// 新版本
public long baseId;  // ✅ 新增，引用动作库
```

#### HistoryEntity（历史记录）
```java
// 旧版本
public String exerciseName;  // ❌ 删除

// 新版本
public long baseId;          // ✅ 新增，引用动作库
```

#### TemplateEntity（训练模板）
```java
// 旧版本
public String exerciseName;  // ❌ 删除

// 新版本
public long baseId;          // ✅ 新增，引用动作库
```

### 3. 新增 POJO 类（用于 UI）

由于实体不再包含 `name` 字段，需要通过 JOIN 查询获取完整信息：

- **ExerciseWithDetail**：`ExerciseEntity` + 动作名称
- **HistoryWithDetail**：`HistoryEntity` + 动作名称
- **TemplateWithDetail**：`TemplateEntity` + 动作名称

---

## 🔧 代码适配指南

### A. Adapter 层适配

#### ❌ 旧代码（直接访问 name）
```java
public class ExerciseRecyclerAdapter extends RecyclerView.Adapter<...> {
    private List<ExerciseEntity> items;

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ExerciseEntity item = items.get(position);
        holder.tvName.setText(item.name);  // ❌ 编译错误：name 字段不存在
    }
}
```

#### ✅ 新代码（使用 WithDetail 对象）
```java
public class ExerciseRecyclerAdapter extends RecyclerView.Adapter<...> {
    private List<ExerciseWithDetail> items;  // 改用 WithDetail 类型

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ExerciseWithDetail item = items.get(position);
        holder.tvName.setText(item.exerciseName);  // ✅ 从关联查询获取名称
        holder.tvCategory.setText(item.category);  // 额外bonus：可以显示部位

        // 访问原始字段
        holder.tvWeight.setText(String.valueOf(item.getWeight()));
        holder.tvSets.setText(String.valueOf(item.getSets()));
    }
}
```

### B. Activity 层适配

#### ❌ 旧代码（直接查询 Entity）
```java
executorService.execute(() -> {
    List<ExerciseEntity> exercises = workoutDao.getAllExercises();
    runOnUiThread(() -> {
        adapter.updateData(exercises);
    });
});
```

#### ✅ 新代码（使用 JOIN 查询）
```java
executorService.execute(() -> {
    // 方式1：使用 JOIN 查询（推荐）
    List<ExerciseWithDetail> exercises = workoutDao.getAllExercisesWithDetail();
    runOnUiThread(() -> {
        adapter.updateData(exercises);
    });

    // 方式2：如果只需要 baseId，仍可使用原始查询
    // List<ExerciseEntity> exercises = workoutDao.getAllExercises();
    // 但需要额外查询名称：dao.getExerciseBaseById(exercise.baseId)
});
```

### C. 创建新动作

#### ❌ 旧代码
```java
ExerciseEntity exercise = new ExerciseEntity("深蹲", 20.0, 4, 12, false);
workoutDao.insert(exercise);
```

#### ✅ 新代码（使用辅助类）
```java
// 1. 先获取或创建动作库记录
ExerciseLibraryHelper helper = new ExerciseLibraryHelper(workoutDao);
long baseId = helper.getOrCreateExercise("深蹲", "kg", "腿部");

// 2. 创建训练记录
ExerciseEntity exercise = new ExerciseEntity(baseId, 20.0, 4, 12, false);
workoutDao.insert(exercise);
```

#### ✅ 新代码（手动处理）
```java
// 1. 检查动作是否存在
ExerciseBaseEntity base = workoutDao.getExerciseBaseByName("深蹲");
long baseId;

if (base == null) {
    // 不存在，创建新动作
    ExerciseBaseEntity newBase = new ExerciseBaseEntity("深蹲", "kg", "腿部");
    baseId = workoutDao.insertExerciseBase(newBase);
} else {
    baseId = base.baseId;
    // 更新最后使用时间
    workoutDao.updateLastUsedTime(baseId, System.currentTimeMillis());
}

// 2. 创建训练记录
ExerciseEntity exercise = new ExerciseEntity(baseId, 20.0, 4, 12, false);
workoutDao.insert(exercise);
```

### D. 保存历史记录

#### ❌ 旧代码
```java
HistoryEntity history = new HistoryEntity(
    timestamp, dateStr, dayName,
    "深蹲",  // ❌ 直接存储名称
    weight, reps, sets
);
workoutDao.insertHistory(history);
```

#### ✅ 新代码
```java
// 假设我们有 ExerciseWithDetail 对象
ExerciseWithDetail exercise = ...;

HistoryEntity history = new HistoryEntity(
    timestamp, dateStr, dayName,
    exercise.exercise.baseId,  // ✅ 存储 baseId
    weight, reps, sets
);
workoutDao.insertHistory(history);
```

### E. 显示历史记录

#### ❌ 旧代码
```java
List<HistoryEntity> history = workoutDao.getAllHistory();
// history.get(0).exerciseName  ❌ 字段不存在
```

#### ✅ 新代码
```java
List<HistoryWithDetail> history = workoutDao.getAllHistoryWithDetail();
String name = history.get(0).exerciseName;  // ✅ 从 JOIN 查询获取
```

### F. 查询特定动作的历史

#### ❌ 旧代码
```java
List<HistoryEntity> history = workoutDao.getHistoryByName("深蹲");
```

#### ✅ 新代码
```java
// 方式1：如果已知 baseId
long baseId = exercise.baseId;
List<HistoryWithDetail> history = workoutDao.getHistoryWithDetailByBaseId(baseId);

// 方式2：如果只有名称，先查找 baseId
ExerciseBaseEntity base = workoutDao.getExerciseBaseByName("深蹲");
if (base != null) {
    List<HistoryWithDetail> history = workoutDao.getHistoryWithDetailByBaseId(base.baseId);
}
```

### G. 重命名动作

现在可以明确区分两种场景：

#### 场景1：全局重命名（修正错别字）
```java
// 用户想把所有地方的"引体像上"改成"引体向上"
ExerciseLibraryHelper helper = new ExerciseLibraryHelper(workoutDao);
helper.renameExercise(baseId, "引体向上");
// ✅ 所有引用该动作的地方都会显示新名称
```

#### 场景2：替换动作（仅修改当前）
```java
// 用户想把今天的"深蹲"换成"保加利亚深蹲"
long newBaseId = helper.getOrCreateExercise("保加利亚深蹲");
exercise.baseId = newBaseId;  // 只修改当前记录的 baseId
workoutDao.update(exercise);
// ✅ 历史记录不受影响
```

---

## 📊 数据库查询对比

### 旧查询（基于字符串）
```sql
-- 查询"深蹲"的历史记录
SELECT * FROM history_table WHERE exerciseName = '深蹲';

-- 问题：用户输入"杠铃深蹲"就查不到了
```

### 新查询（基于ID）
```sql
-- 查询 baseId=123 的历史记录（包括名称）
SELECT h.*, eb.name AS exerciseName
FROM history_table h
INNER JOIN exercise_library eb ON h.baseId = eb.baseId
WHERE h.baseId = 123;

-- ✅ 即使动作改名，也能查到所有记录
```

---

## 🚀 迁移步骤

### 1. 数据库迁移（自动）
由于使用了 `fallbackToDestructiveMigration()`，首次运行会自动清空旧数据并创建新结构。

**重要**：App 未上线可以直接这样做，已上线需要编写 Migration 脚本。

### 2. 卸载重装（推荐）
```bash
# 开发环境建议直接卸载重装，确保数据库结构完全更新
adb uninstall com.example.fitness_plan
```

### 3. 预填充常用动作（可选）
```java
// 在 App 首次启动时，可以预填充一些常用动作
ExerciseLibraryHelper helper = new ExerciseLibraryHelper(workoutDao);
helper.getOrCreateExercise("深蹲", "kg", "腿部");
helper.getOrCreateExercise("卧推", "kg", "胸部");
helper.getOrCreateExercise("硬拉", "kg", "背部");
// ... 更多动作
```

---

## 🎯 关键优势

### 1. 解决改名歧义
- **旧架构**：用户修改"深蹲"为"杠铃深蹲"，无法区分是改错别字还是换动作
- **新架构**：全局重命名 vs 替换动作，意图明确

### 2. 数据统一性
- **旧架构**：A计划叫"深蹲"，B计划叫"杠铃深蹲"，图表无法合并统计
- **新架构**：都引用同一个 baseId，自动合并统计

### 3. 存储效率
- **旧架构**：每条历史记录都存储完整名称（几百条记录 = 几百次"深蹲"字符串）
- **新架构**：每条记录只存8字节的 baseId，名称只存一次

### 4. 软删除
- **旧架构**：删除动作后，历史记录中的名称还在，但无法知道这是什么动作
- **新架构**：软删除标记，历史记录仍可查询到完整信息

---

## 🛠️ 常见问题

### Q1: 为什么要用 JOIN 查询，不能直接访问 name 吗？
**A**: 这是规范化的代价。虽然增加了查询复杂度，但换来了数据一致性。Room 的 JOIN 查询性能很好，加上索引后几乎无感。

### Q2: 能否在 Entity 中保留一个 @Ignore 的 name 字段方便访问？
**A**: 不推荐。这会导致数据不同步，增加维护成本。应使用 WithDetail POJO 类。

### Q3: 如果用户创建两个同名动作怎么办？
**A**: 使用 `getExerciseBaseByName()` 可以避免重复。如果确实需要同名但不同动作（如"深蹲（自重）"和"深蹲（杠铃）"），应在名称中区分。

### Q4: 性能会受影响吗？
**A**: 极小。JOIN 查询在有索引的情况下非常快。我们已在外键列上创建了索引：
```java
@Entity(..., indices = {@Index("baseId")})
```

---

## ✅ 检查清单

在提交代码前，请确保：

- [ ] 所有 Adapter 都改用 WithDetail 类型
- [ ] 所有创建动作的地方都先获取 baseId
- [ ] 所有查询历史记录的地方都用 JOIN 查询
- [ ] 删除了所有对 `item.name` 的直接访问
- [ ] 测试了重命名功能（全局 vs 局部）
- [ ] 测试了图表统计（确保同一动作的数据能合并）

---

## 📞 技术支持

如有疑问，请参考：
- `ExerciseLibraryHelper.java` - 辅助类，简化动作库操作
- `WorkoutDao.java` - 所有 JOIN 查询方法
- `ExerciseWithDetail.java` - UI 层使用的 POJO 类

重构完成后，数据架构将更加健壮和可扩展！ 🎉
