# Fitness_Plan 项目架构说明书 (2026-01 V2.1 封神版)

**版本:** v2.1
**重构日期:** 2026年1月
**核心变更:** 从字符串名称存储迁移到动作库 ID 引用架构，重构数据分析核心，升级交互 UI。

## 1. 项目概况

### 1.1 项目类型
Android 原生健身训练记录应用，采用 MVVM-Like 架构模式，使用 Room 数据库进行本地数据持久化，注重硬核数据分析与极致交互体验。

### 1.2 核心技术栈
| 技术组件 | 版本/说明 | 用途 |
| :--- | :--- | :--- |
| **Room Database** | 2.x | 本地数据库 ORM 框架，管理数据源。 |
| **MPAndroidChart** | 3.x | 历史数据可视化图表 (折线趋势图、多重模式雷达图)。 |
| **RecyclerView** | AndroidX | 核心列表展示与复杂拖拽/编辑交互。 |
| **ExecutorService** | Java 并发 | 后台线程池管理，确保 UI 线程纯洁性。 |
| **Material Design** | AndroidX | UI 组件库，实现扁平化商业级视觉。 |

### 1.3 应用功能模块
* **训练记录:** 今日训练动作的增删改查、防呆联想输入、完成状态标记。
* **计划管理:** 多计划多训练日的模板系统，支持动态切换。
* **历史归档:** 训练记录的时间序列存储与基于分类的查询。
* **数据可视化:** 单动作重量趋势图、六大肌群“多重人格”能力雷达图。
* **数据备份:** CSV 格式的导出与智能解析导入功能。

---

## 2. 核心架构变动说明 (V1.0 -> V2.0 升级) ⭐

### 2.1 重构背景与动机
**v1.0 旧架构 (字符串存储模式) 的致命问题：**
* **数据冗余:** 每条训练记录都重复存储完整的动作名称字符串。
* **重命名噩梦:** 修改一个动作名称需要遍历并更新数百条历史记录和计划模板。
* **数据碎片化:** 拼写不一致（如“卧推”和“平板卧推”）导致数据图表断层。

### 2.2 重构方案：动作库分离架构 (Single Source of Truth)
**v2.0 解决方案 (ID 引用模式)：**
引入 **`exercise_library`** 作为整个系统的唯一真理来源。主页和历史页只存储外键 `baseId`，通过 JOIN 或者内存缓存来显示名称和肌群分类。

```java
// 核心思想：动作库表作为唯一真理来源
@Entity(tableName = "exercise_library")
public class ExerciseBaseEntity {
    @PrimaryKey(autoGenerate = true)
    public long baseId;        // ✅ 唯一标识符
    public String name;        // ✅ 动作名称
    public String category;    // ✅ 动作分类 (如 Chest, Back)
    public boolean isDeleted;  // ✅ 软删除标记
}

// 训练记录/历史记录：只存储 baseId 引用
@Entity(tableName = "exercise_table")
public class ExerciseEntity {
    public long baseId;  // ✅ 外键引用，不再存储 String name
}
```

### 2.3 外键约束策略
在 Room 实体中使用 `@ForeignKey` 建立关联，并严格遵守以下设计原则：
* **NO_ACTION:** 删除动作库记录时不级联删除历史记录。
* **软删除优先:** 使用 `isDeleted` 标记而非物理删除，保证历史数据永远可回溯。
* **防呆保护:** UI 层面新建动作时，一旦命中已有动作，强制锁死分类修改权限，防止污染主数据。

---

## 3. V2.1 史诗级能力升级 (The "God-Tier" Update)

### 3.1 “多重人格”数据漏斗雷达引擎
告别单一雷达图造成的“干扰效应”，引入物理级的多重滤镜：
* **底层重构:** 抛弃 `animateXY` 导致的边距坍塌 Bug，采用图表数据内存复用 (In-place Update) 技术，结合 `clear()` 与瞬间重绘 (`invalidate`)。
* **动态算法:**
    * **综合模式:** 全量数据展示。
    * **力量模式:** 仅截取 1~6RM 数据，展示极限力量。
    * **耐力模式:** 仅截取 10RM+ 数据，并在底层将 1RM 算法从 Brzycki 无缝切换为 Epley 公式，防止分母归零引发的图表爆炸。

### 3.2 极致的 UI 与交互逻辑优化
* **智能联想与防呆:** 采用 `AutoCompleteTextView` 重构主页新建/编辑弹窗。用户输入时提供历史动作联想；选中老动作即刻**锁死肌群选择器**，并转化为彩色胶囊徽章，剥夺篡改主数据的权限。
* **彩色肌群徽章:** 列表卡片全面扁平化 (去除 Elevation)，根据 `category` 字段动态渲染不同颜色的胶囊徽章（如胸部蓝、背部绿、核心黄）。
* **丝滑切换:** 顶部控制台重构为类似 iOS Segmented Control 的扁平圆角开关组，切换瞬间极速响应。

---

## 4. 文件字典与功能解析

### 4.1 核心 POJO 类 (数据传输对象)
为了解决跨表显示的问题，引入了 `WithDetail` 后缀的 POJO 类：

| 文件名 | 职责说明 | 使用场景 |
| :--- | :--- | :--- |
| **`ExerciseWithDetail.java`** | JOIN 封装 (`ExerciseEntity` + 库属性) | UI 主页列表展示 |
| **`HistoryWithDetail.java`** | JOIN 封装 (`HistoryEntity` + 库属性) | 历史记录展示 |

```java
public class ExerciseWithDetail {
    @Embedded
    public ExerciseEntity exercise;  // 原始实体数据
    
    // JOIN 查询出的扩展字段
    public String exerciseName;      
    public String category;
}
```

### 4.2 缓存与工具类层
* **`EntityNameCache.java`**: 内存级缓存 (`baseId` -> `name`)。减少 RecyclerView 滑动时重复查询数据库，提升渲染性能。
* **`CsvImportUtils.java`**: CSV 导入时智能解析。如果发现新动作，会自动向 `exercise_library` 中隐式创建。

---

## 5. 迁移与排错指南 (Troubleshooting)

### 5.1 从 v1.0 升级到 v2.0 的常见错误
| 错误信息 | 原因 | 解决方案 |
| :--- | :--- | :--- |
| `cannot find symbol: name` | 仍在访问已被移除的 `ExerciseEntity.name` | 替换为访问 `ExerciseWithDetail.exerciseName` |
| `CURSOR_MISMATCH` | POJO 字段与查询列数不匹配 | 为不参与映射的 UI 状态字段添加 `@Ignore` 注解 |
| 雷达图随着切换不断缩小 | MPAndroidChart 的 `calculateOffsets()` 缓存累加 | 在 `setData` 之前强制调用 `radarChart.clear()` |
| `Lambda referenced local variable must be final` | Lambda 内部修改了外部变量 | 在 `execute` 外部声明一个 `final` 的“替身快照”变量 |

### 5.2 性能优化建议
1.  **UI 线程纯洁性:** 所有加减乘除和数据过滤必须在 `executorService` 中完成，只能将定格的快照结果传递给 `runOnUiThread` 进行图表赋值。
2.  **避免 N+1 查询:** 列表展示必须使用 DAO 层的 `INNER JOIN` 语句，绝不能在 Adapter 的循环里根据 ID 去反查数据库。

---

*文档维护: 2026年1月*
*架构版本: v2.1*