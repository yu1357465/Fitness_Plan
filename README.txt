Fitness Plan - 健身计划追踪应用 (MVP 阶段)

本文件用于记录项目文件结构、核心逻辑设计以及关键技术细节，方便未来维护与二次开发。

1. 项目目录结构 (Project File Structure)

Android 项目遵循严格的目录规范，以下是本项目涉及的核心文件及其路径：

A. 逻辑代码层 (Java Logic)

路径: app/src/main/java/com/example/fitness_plan/

MainActivity.java:

作用: 整个 App 的“大脑” (Brain)。

职责: 初始化 UI、加载硬编码数据、定义数据模型 (Exercise 类)、管理自定义适配器 (ExerciseAdapter)。

B. 资源布局层 (Resources & Layouts)

路径: app/src/main/res/layout/

activity_main.xml:

作用: 主界面布局 (Main Layout)。

内容: 包含页面标题和用于显示计划的 ListView 控件。

list_item_exercise.xml:

作用: 列表项布局 (List Item Layout)。

内容: 定义了每一行动作（名称、重量、次数、组数、完成按钮）的视觉结构。

路径: app/src/main/res/values/

strings.xml:

作用: 字符串资源池 (String Resource Pool)。

职责: 遵循“内容与表现分离”原则，统一存储 App 内所有文本，方便国际化和维护。

2. 核心技术架构 (Core Technical Architecture)

2.1 适配器模式 (Adapter Pattern)

我们使用了 ExerciseAdapter。它是连接“数据源” (DataSource) 和“视图” (View) 的桥梁 (Bridge)。

数据流: ArrayList<Exercise> -> ExerciseAdapter -> ListView。

2.2 性能优化：ViewHolder 模式

在 getView 方法中，我们使用了 ViewHolder 静态类。

目的: 避免频繁调用 findViewById() 这种高开销操作，通过“缓存” (Caching) 控件引用来提升滚动流畅度。

2.3 数据绑定：TextWatcher

为了实现“可更改内容并自动保存”的需求，我们为每个 EditText 绑定了 TextWatcher。

逻辑: 用户在界面输入数字 -> 触发 afterTextChanged 事件 -> 实时更新 ArrayList 中对应位置的对象属性。

3. 关键疑难解答 (Troubleshooting & Key Concepts)

3.1 变量捕获限制 (Variable Capture Limit)

问题: 为什么在 Lambda 内部使用 convertView 会报错？

解析: Java 规定 Lambda 只能捕获“事实上的常量” (Effectively Final)。由于 convertView 会在 if 块中被重新赋值，它就变动了。

正确写法: 创建一个 final View finalConvertView = convertView; 并在 Lambda 内部引用这个镜像。

3.2 内存泄漏预防 (Memory Leak Prevention)

写法: 将 ExerciseAdapter 声明为 static 内部类。

原理: 非静态内部类会隐式持有外部 Activity 的引用，可能导致 Activity 无法被垃圾回收器 (GC) 回收。

4. 未来开发路线 (Roadmap / Next Steps)

目前项目处于 MVP (最小可行性产品) 阶段，数据存储在内存中（重启 App 后会重置）。

持久化存储 (Data Persistence):
将硬编码的 ArrayList 替换为 SQLite 数据库 或 Room 框架，确保数据关机不丢失。

首页看板 (Dashboard):
利用 Rollup 逻辑（在 Java 中实现），展示最近一周的训练总量图表。

UI 升级:
引入 CardView 和圆角背景 (Rounded Corners)，让界面更现代化。
