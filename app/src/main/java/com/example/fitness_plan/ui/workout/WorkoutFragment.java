package com.example.fitness_plan.ui.workout;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness_plan.ExerciseRecyclerAdapter;
import com.example.fitness_plan.PlanListActivity;
import com.example.fitness_plan.R;
import com.example.fitness_plan.SettingsActivity;
import com.example.fitness_plan.StatsActivity;
import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.EntityNameCache;
import com.example.fitness_plan.data.ExerciseBaseEntity;
import com.example.fitness_plan.data.ExerciseEntity;
import com.example.fitness_plan.data.ExerciseWithDetail;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.PlanEntity;
import com.example.fitness_plan.data.TemplateEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkoutFragment extends Fragment {

    private WorkoutDao workoutDao;
    private AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private TextView tvPageTitle, tvActivePlanName;
    private RecyclerView mainRecyclerView;
    private Button btnFinishWorkout;
    private ImageView ivDayArrow, ivPlanArrow;

    private ExerciseRecyclerAdapter adapter;
    private List<ExerciseWithDetail> workoutPlan = new ArrayList<>();
    private EntityNameCache nameCache;

    private boolean isLbsMode = false;
    private boolean lastIsLbs = false;
    private boolean defaultAddToPlan = true;
    private String currentPlanName = "自由训练";

    private volatile boolean isArchiving = false;
    private ItemTouchHelper itemTouchHelper;

    public WorkoutFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_workout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = AppDatabase.getDatabase(requireContext());
        workoutDao = db.workoutDao();

        nameCache = EntityNameCache.getInstance();
        nameCache.setDao(workoutDao);

        tvPageTitle = view.findViewById(R.id.tvPageTitle);
        tvActivePlanName = view.findViewById(R.id.tvActivePlanName);
        mainRecyclerView = view.findViewById(R.id.mainRecyclerView);
        btnFinishWorkout = view.findViewById(R.id.btnFinishWorkout);
        ivDayArrow = view.findViewById(R.id.ivDayArrow);
        ivPlanArrow = view.findViewById(R.id.ivPlanArrow);

        mainRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        if (mainRecyclerView.getItemAnimator() instanceof androidx.recyclerview.widget.SimpleItemAnimator) {
            ((androidx.recyclerview.widget.SimpleItemAnimator) mainRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);
        }

        SharedPreferences prefs = requireContext().getSharedPreferences("fitness_prefs", Context.MODE_PRIVATE);
        lastIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        isLbsMode = lastIsLbs;
        defaultAddToPlan = prefs.getBoolean("DEFAULT_ADD_TO_PLAN", true);

        loadDataFromDatabase();
        setupClickListeners(view);
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));

        // ⭐ 终极重构：将右下角按钮直接连通硬核数据分析中心 (StatsActivity)
        view.findViewById(R.id.btnStats).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), StatsActivity.class)));

        view.findViewById(R.id.planSelectionContainer).setOnClickListener(v -> {
            toggleArrow(ivPlanArrow);
            showPlanMenu(v);
        });
        view.findViewById(R.id.daySelectionContainer).setOnClickListener(v -> {
            toggleArrow(ivDayArrow);
            showDayMenu(v);
        });

        // 注意：这里已经删除了对 fabAdd 的引用，因为我们在 XML 中删掉了它

        btnFinishWorkout.setOnClickListener(v -> finishAndSwitchCycle());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() == null) return;

        SharedPreferences prefs = requireContext().getSharedPreferences("fitness_prefs", Context.MODE_PRIVATE);
        boolean currentIsLbs = prefs.getBoolean("DEFAULT_IS_LBS", false);
        defaultAddToPlan = prefs.getBoolean("DEFAULT_ADD_TO_PLAN", true);

        if (currentIsLbs != lastIsLbs) {
            lastIsLbs = currentIsLbs;
            isLbsMode = currentIsLbs;
            loadDataFromDatabase();
        } else {
            loadDataFromDatabase();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        clearGhostCard();
    }

    public void loadDataFromDatabase() {
        executorService.execute(() -> {
            if (!isAdded()) return;

            PlanEntity activePlan = workoutDao.getActivePlan();
            SharedPreferences prefs = requireContext().getSharedPreferences("fitness_prefs", Context.MODE_PRIVATE);
            int savedIndex = prefs.getInt("PLAN_INDEX", 0);

            String finalDayTitle = "自由训练";
            String finalPlanName = "暂无计划";

            if (activePlan != null) {
                finalPlanName = activePlan.planName;
                List<String> days = workoutDao.getPlanDays(activePlan.planId);
                if (!days.isEmpty()) {
                    if (savedIndex >= days.size()) savedIndex = 0;
                    finalDayTitle = days.get(savedIndex);
                }
            }

            String titleForUI = finalDayTitle;
            String planNameForUI = finalPlanName;
            currentPlanName = finalDayTitle;

            workoutPlan = workoutDao.getAllExercisesWithDetail();

            Iterator<ExerciseWithDetail> iterator = workoutPlan.iterator();
            while (iterator.hasNext()) {
                ExerciseWithDetail ex = iterator.next();
                if (ex.getBaseId() == -1) {
                    workoutDao.delete(ex.exercise);
                    iterator.remove();
                }
            }

            Collections.sort(workoutPlan, (o1, o2) -> Integer.compare(o1.getSortOrder(), o2.getSortOrder()));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (!isAdded()) return;

                    if (tvPageTitle != null) tvPageTitle.setText(titleForUI);
                    if (tvActivePlanName != null) tvActivePlanName.setText(planNameForUI);

                    adapter = new ExerciseRecyclerAdapter(requireContext(), workoutPlan, isLbsMode, new ExerciseRecyclerAdapter.OnItemActionListener() {
                        @Override
                        public void onUpdate(ExerciseWithDetail exercise) {
                            if(exercise.getBaseId() != -1) {
                                executorService.execute(() -> workoutDao.update(exercise.exercise));
                            }
                        }
                        @Override
                        public void onShowChart(LineChart chart, ExerciseWithDetail exercise) {
                            loadChartData(chart, exercise.getBaseId());
                        }
                        @Override
                        public void onItemLongClick(ExerciseWithDetail exercise) {}
                        @Override
                        public void onStartDrag(RecyclerView.ViewHolder holder) {
                            clearGhostCard();
                            if (itemTouchHelper != null) itemTouchHelper.startDrag(holder);
                        }
                        @Override
                        public void onOrderChanged() {
                            executorService.execute(() -> {
                                for (int i = 0; i < workoutPlan.size(); i++) {
                                    ExerciseWithDetail e = workoutPlan.get(i);
                                    e.exercise.sortOrder = i;
                                    workoutDao.update(e.exercise);
                                }
                            });
                        }
                        @Override
                        public void onRename(ExerciseWithDetail exercise) {
                            showRenameDialog(exercise);
                        }
                        @Override
                        public void onDelete(ExerciseWithDetail exercise) {
                            handleSmartDelete(exercise);
                        }
                        @Override
                        public void onTogglePin(ExerciseWithDetail exercise) {
                            handleTogglePin(exercise);
                        }
                        @Override
                        public void onAddEmptyCard() {
                            // ⭐⭐ 核心修改：点击底部绿色加号时，直接触发添加弹窗，绕开导致崩溃的幽灵卡片(-1)机制
                            showAddDialog();
                        }
                        @Override
                        public void onCompletionChanged() {
                            updateFinishButtonState();
                        }
                    });

                    mainRecyclerView.setAdapter(adapter);
                    setupItemTouchHelper();
                    updateFinishButtonState();
                });
            }
        });
    }

// ---------------- 第一段结束 ----------------

    // =========================================================
    //  逻辑 C: UI 状态更新 (按钮变色、文字排版)
    // =========================================================
    private void updateFinishButtonState() {
        if (workoutPlan == null || workoutPlan.isEmpty()) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    btnFinishWorkout.setText("今日无训练");
                    btnFinishWorkout.setBackgroundColor(Color.parseColor("#B0BEC5"));
                    btnFinishWorkout.setEnabled(false);
                });
            }
            return;
        }

        int uncompletedCount = 0;
        for (ExerciseWithDetail ex : workoutPlan) {
            if (ex.getBaseId() != -1 && !ex.isCompleted()) {
                uncompletedCount++;
            }
        }

        int finalUncompletedCount = uncompletedCount;

        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                btnFinishWorkout.setEnabled(true);

                if (finalUncompletedCount == 0) {
                    // 🟢 全部完成
                    btnFinishWorkout.setText("完成打卡");
                    btnFinishWorkout.setBackgroundColor(Color.parseColor("#4DB6AC"));
                } else {
                    // 🟠 还有未完成
                    String countStr = String.valueOf(finalUncompletedCount);
                    String prefix = "还剩 ";
                    String suffix = " 个动作结束";
                    String fullText = prefix + countStr + suffix;

                    android.text.SpannableString spannable = new android.text.SpannableString(fullText);
                    int start = prefix.length();
                    int end = start + countStr.length();

                    // 使用自定义的 CenterScaleSpan
                    spannable.setSpan(new CenterScaleSpan(1.8f), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    btnFinishWorkout.setText(spannable);
                    btnFinishWorkout.setBackgroundColor(Color.parseColor("#FB8C00"));
                }
            });
        }
    }

    // =========================================================
    //  逻辑 D: 幽灵卡片清理逻辑 (保留用于清理历史遗留脏数据)
    // =========================================================
    private ExerciseWithDetail clearGhostCardSync() {
        if (adapter == null || workoutPlan == null) return null;
        ExerciseWithDetail ghostFound = null;
        for (int i = workoutPlan.size() - 1; i >= 0; i--) {
            ExerciseWithDetail ex = workoutPlan.get(i);
            if (ex.getBaseId() == -1) {
                ghostFound = ex;
                adapter.removeItem(ex);
                break;
            }
        }
        return ghostFound;
    }

    private void clearGhostCard() {
        ExerciseWithDetail ghost = clearGhostCardSync();
        if (ghost != null) {
            executorService.execute(() -> workoutDao.delete(ghost.exercise));
        }
    }

    // ==========================================
    //  ⭐ 核心修改：编辑现有动作卡片 (带联想，锁死数据源)
    // ==========================================
    private void showRenameDialog(ExerciseWithDetail exercise) {
        if (getContext() == null) return;

        executorService.execute(() -> {
            ExerciseBaseEntity base = workoutDao.getExerciseBaseById(exercise.getBaseId());
            List<ExerciseBaseEntity> allBases = workoutDao.getAllExerciseBases();
            List<String> existNames = new ArrayList<>();
            for (ExerciseBaseEntity b : allBases) {
                existNames.add(b.name);
            }

            if (base != null) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
                        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                        layout.setPadding(50, 40, 50, 0);

                        // 升级 1：带联想的输入框
                        final android.widget.AutoCompleteTextView etInput = new android.widget.AutoCompleteTextView(requireContext());
                        etInput.setHint("输入新动作名称");
                        etInput.setText(base.name);
                        etInput.setSingleLine(true); // ⭐ 限制单行
                        android.widget.ArrayAdapter<String> autoAdapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, existNames);
                        etInput.setAdapter(autoAdapter);
                        etInput.setThreshold(1);
                        layout.addView(etInput);

                        // 升级 2：显示彩色徽章
                        final TextView tvCategoryBadge = new TextView(requireContext());

                        // ⭐ 核心修复：限制宽度，居中对齐
                        android.widget.LinearLayout.LayoutParams badgeParams = new android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        badgeParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                        badgeParams.topMargin = 30;
                        badgeParams.bottomMargin = 10;
                        tvCategoryBadge.setLayoutParams(badgeParams);

                        tvCategoryBadge.setPadding(40, 15, 40, 15); // 精致胶囊
                        tvCategoryBadge.setTextSize(12f);
                        tvCategoryBadge.setTextColor(Color.WHITE);
                        tvCategoryBadge.setText("当前肌群： " + translateCategoryToChinese(base.category));
                        layout.addView(tvCategoryBadge);

                        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                        shape.setCornerRadius(16f * requireContext().getResources().getDisplayMetrics().density);
                        shape.setColor(getCategoryColor(base.category));
                        tvCategoryBadge.setBackground(shape);

                        final Context ctx = requireContext();

                        // 联想选中逻辑：更新显示的徽章
                        etInput.setOnItemClickListener((parent, v, pos, id) -> {
                            String selectedName = parent.getItemAtPosition(pos).toString();
                            executorService.execute(() -> {
                                ExerciseBaseEntity newBase = workoutDao.getExerciseBaseByName(selectedName);
                                if (newBase != null) {
                                    if(getActivity()!=null){
                                        getActivity().runOnUiThread(() -> {
                                            tvCategoryBadge.setText("目标肌群： " + translateCategoryToChinese(newBase.category));
                                            shape.setColor(getCategoryColor(newBase.category));
                                            tvCategoryBadge.setBackground(shape);
                                        });
                                    }
                                }
                            });
                        });

                        new AlertDialog.Builder(requireContext())
                                .setTitle("修改动作 (或替换为已有)")
                                .setView(layout)
                                .setPositiveButton("保存", (dialog, which) -> {
                                    String newName = etInput.getText().toString().trim();
                                    if (!newName.isEmpty()) {
                                        executorService.execute(() -> {
                                            long oldBaseId = exercise.getBaseId();
                                            boolean isPermanent = exercise.getColor() != null && exercise.getColor().equalsIgnoreCase("#FFFFFF");

                                            ExerciseBaseEntity targetBase = workoutDao.getExerciseBaseByName(newName);
                                            if (targetBase == null) {
                                                // 依然是全新的名字，只能默认给个 Other，因为编辑界面我们不显示长长的 Spinner
                                                targetBase = new ExerciseBaseEntity(newName, "kg", "Other");
                                                long baseId = workoutDao.insertExerciseBase(targetBase);
                                                targetBase.baseId = baseId;
                                            }

                                            // 替换当前卡片的 baseId
                                            exercise.exercise.baseId = targetBase.baseId;
                                            workoutDao.update(exercise.exercise);
                                            nameCache.updateCache(targetBase.baseId, newName);

                                            // 处理永久计划模板的替换逻辑 (保持不变)
                                            if (isPermanent) {
                                                PlanEntity activePlan = workoutDao.getActivePlan();
                                                if (activePlan != null && currentPlanName != null) {
                                                    workoutDao.deleteTemplateByBaseId(activePlan.planId, currentPlanName, oldBaseId);
                                                    TemplateEntity template = new TemplateEntity();
                                                    template.planId = activePlan.planId;
                                                    template.dayName = currentPlanName;
                                                    template.baseId = targetBase.baseId;
                                                    template.defaultSets = exercise.getSets();
                                                    template.defaultReps = exercise.getReps();
                                                    template.defaultWeight = exercise.getWeight();
                                                    template.sortOrder = exercise.getSortOrder();
                                                    workoutDao.insertTemplate(template);
                                                }
                                            }
                                            loadDataFromDatabase();
                                        });
                                    }
                                })
                                .setNeutralButton("取消", null)
                                .show();
                    });
                }
            }
        });
    }

    private void deleteTempCard(ExerciseWithDetail exercise) {
        if (adapter != null) adapter.removeItem(exercise);

        executorService.execute(() -> {
            workoutDao.delete(exercise.exercise);
            // 清理可能存在的模板残留
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan != null && currentPlanName != null && exercise.getBaseId() != -1) {
                workoutDao.deleteTemplateByBaseId(activePlan.planId, currentPlanName, exercise.getBaseId());
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "已取消添加", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // ==========================================
    //  ⭐ 核心插入：新建动作弹窗 (带自动联想 + 锁死肌群修改权)
    // ==========================================
    private void showAddDialog() {
        if (getContext() == null) return;

        executorService.execute(() -> {
            // ⭐ 异步读取所有动作名字，用于联想
            List<ExerciseBaseEntity> allBases = workoutDao.getAllExerciseBases();
            List<String> existNames = new ArrayList<>();
            for (ExerciseBaseEntity b : allBases) {
                existNames.add(b.name);
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
                    layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                    layout.setPadding(50, 40, 50, 0);

                    // 升级 1：AutoCompleteTextView 联想框 (增加单行限制，恢复美观)
                    final android.widget.AutoCompleteTextView input = new android.widget.AutoCompleteTextView(requireContext());
                    input.setHint("输入动作名称 (如: 杠铃划船)");
                    input.setSingleLine(true); // ⭐ 限制单行，防变形
                    android.widget.ArrayAdapter<String> autoAdapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, existNames);
                    input.setAdapter(autoAdapter);
                    input.setThreshold(1);
                    layout.addView(input);

                    // 升级 2：用于显示已存在肌群的 Badge
                    final TextView tvCategoryBadge = new TextView(requireContext());

                    // ⭐ 核心修复：限制宽度，居中对齐，告别“狗皮膏药”
                    android.widget.LinearLayout.LayoutParams badgeParams = new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    badgeParams.gravity = android.view.Gravity.CENTER_HORIZONTAL; // 居中
                    badgeParams.topMargin = 30; // 往下挪一点，和输入框拉开距离
                    tvCategoryBadge.setLayoutParams(badgeParams);

                    // ⭐ 打磨成精致的胶囊形状 (左右留白大，上下留白小)
                    tvCategoryBadge.setPadding(40, 15, 40, 15);
                    tvCategoryBadge.setTextSize(12f);
                    tvCategoryBadge.setTextColor(Color.WHITE);
                    tvCategoryBadge.setVisibility(View.GONE);
                    layout.addView(tvCategoryBadge);

                    // 升级 3：肌群选择器提示文本 (初始显示)
                    final TextView tvLabel = new TextView(requireContext());
                    tvLabel.setText("目标肌群 (如果是库中已有动作，将忽略此项):");
                    tvLabel.setPadding(0, 30, 0, 10);
                    tvLabel.setTextColor(Color.parseColor("#757575"));
                    tvLabel.setVisibility(View.VISIBLE);
                    layout.addView(tvLabel);

                    // 升级 4：Spinner (初始显示)
                    final android.widget.Spinner spinner = new android.widget.Spinner(requireContext());
                    String[] muscleGroups = {"胸部 (Chest)", "背部 (Back)", "腿臀 (Legs&Glutes)", "肩部 (Shoulders)", "手臂 (Arms)", "核心 (Core)", "其他 (Other)"};
                    android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, muscleGroups);
                    spinner.setAdapter(spinnerAdapter);
                    layout.addView(spinner);

                    final Context ctx = requireContext();

                    // ⭐ ⭐ 神级逻辑：监听联想框选择事件，一旦选中已有动作，锁死权限！
                    input.setOnItemClickListener((parent, v, pos, id) -> {
                        String selectedName = parent.getItemAtPosition(pos).toString();
                        executorService.execute(() -> {
                            ExerciseBaseEntity base = workoutDao.getExerciseBaseByName(selectedName);
                            if (base != null) {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        tvLabel.setVisibility(View.GONE);
                                        spinner.setVisibility(View.GONE);
                                        tvCategoryBadge.setVisibility(View.VISIBLE);
                                        tvCategoryBadge.setText("目标肌群： " + translateCategoryToChinese(base.category));

                                        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
                                        shape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                                        shape.setCornerRadius(16f * ctx.getResources().getDisplayMetrics().density);
                                        shape.setColor(getCategoryColor(base.category));
                                        tvCategoryBadge.setBackground(shape);
                                    });
                                }
                            }
                        });
                    });

                    // 监听输入框内容改变，如果删除了联想词，恢复 Spinner
                    input.addTextChangedListener(new android.text.TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        @Override public void afterTextChanged(android.text.Editable s) {
                            if (tvCategoryBadge.getVisibility() == View.VISIBLE) {
                                tvCategoryBadge.setVisibility(View.GONE);
                                tvLabel.setVisibility(View.VISIBLE);
                                spinner.setVisibility(View.VISIBLE);
                            }
                        }
                    });

                    input.setOnFocusChangeListener((v, hasFocus) -> {
                        if (hasFocus) {
                            input.post(() -> {
                                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                            });
                        }
                    });

                    new AlertDialog.Builder(requireContext())
                            .setTitle("添加今日动作")
                            .setView(layout)
                            .setPositiveButton("添加", (dialog, which) -> {
                                String name = input.getText().toString().trim();

                                String tempCategory = "Other";
                                if (spinner.getVisibility() == View.VISIBLE) {
                                    String selectedText = spinner.getSelectedItem().toString();
                                    tempCategory = selectedText.contains("(") ?
                                            selectedText.substring(selectedText.indexOf("(") + 1, selectedText.indexOf(")")) : "Other";
                                }

                                // ⭐ 核心修复：在跨入 Lambda 结界前，给修改过的 tempCategory 拍一张 final 快照！
                                final String finalDbCategory = tempCategory;

                                if (!name.isEmpty()) {
                                    executorService.execute(() -> {
                                        ExerciseBaseEntity base = workoutDao.getExerciseBaseByName(name);
                                        if (base == null) {
                                            // ⭐ 在 Lambda 里面，使用定格后的 final 替身
                                            base = new ExerciseBaseEntity(name, "kg", finalDbCategory);
                                            long baseId = workoutDao.insertExerciseBase(base);
                                            base.baseId = baseId;
                                        }

                                        int sortOrder = workoutDao.getAllExercises().size();
                                        // ==========================================
                                        // ⭐ 核心注入 1：召唤第二大脑，打捞最后一次的记忆
                                        // ==========================================
                                        HistoryEntity lastRecord = workoutDao.getLatestHistoryByBaseId(base.baseId);

                                        double initWeight = (lastRecord != null) ? lastRecord.weight : 20.0;
                                        int initSets = (lastRecord != null) ? lastRecord.sets : 4;
                                        int initReps = (lastRecord != null) ? lastRecord.reps : 12;

                                        ExerciseEntity exercise = new ExerciseEntity(base.baseId, initWeight, initSets, initReps, false);
                                        exercise.sortOrder = sortOrder;

                                        if (defaultAddToPlan) {
                                            exercise.color = "#FFFFFF";
                                            PlanEntity activePlan = workoutDao.getActivePlan();
                                            if (activePlan != null && currentPlanName != null) {
                                                TemplateEntity template = new TemplateEntity();
                                                template.planId = activePlan.planId;
                                                template.dayName = currentPlanName;
                                                template.baseId = base.baseId;
                                                template.defaultSets = 4;
                                                template.defaultReps = 12;
                                                template.sortOrder = sortOrder;
                                                workoutDao.insertTemplate(template);
                                            }
                                        } else {
                                            exercise.color = "#FFF9C4";
                                        }

                                        workoutDao.insert(exercise);
                                        loadDataFromDatabase();
                                    });
                                }
                            })
                            .setNegativeButton("取消", null)
                            .show();
                    input.requestFocus();
                });
            }
        });
    }

    // =========================================================
    //  逻辑 F: 归档与完成 (Finish Workout)
    // =========================================================
    private void finishAndSwitchCycle() {
        if (isArchiving) {
            Toast.makeText(requireContext(), "正在归档中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }
        clearGhostCard();

        executorService.execute(() -> {
            List<ExerciseWithDetail> uncompletedExercises = new ArrayList<>();
            for (ExerciseWithDetail ex : workoutPlan) {
                if (ex.getBaseId() != -1 && !ex.isCompleted()) {
                    uncompletedExercises.add(ex);
                }
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> showFinishDialog(uncompletedExercises));
            }
        });
    }

    private void showFinishDialog(List<ExerciseWithDetail> uncompletedList) {
        if (getContext() == null) return;
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext(), com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_bottom_sheet_finish, null);
        sheet.setContentView(view);

        if (sheet.getWindow() != null) sheet.getWindow().setDimAmount(0.0f);

        TextView tvTitle = view.findViewById(R.id.tvFinishTitle);
        TextView tvSubtitle = view.findViewById(R.id.tvFinishSubtitle);
        Button btnPrimary = view.findViewById(R.id.btnPrimaryAction);
        Button btnSecondary = view.findViewById(R.id.btnSecondaryAction);

        int count = uncompletedList.size();

        if (count == 0) {
            tvTitle.setText("🎉 训练完成！");
            tvTitle.setTextColor(Color.parseColor("#4DB6AC"));
            tvSubtitle.setText("所有动作均已搞定。\n准备好存档并进入下一个循环了吗？");

            btnPrimary.setText("确定存档");
            btnPrimary.setOnClickListener(v -> {
                if (isArchiving) return;
                btnPrimary.setEnabled(false);
                btnSecondary.setEnabled(false);
                sheet.setCancelable(false);
                performFinishWorkout(false);
                sheet.dismiss();
            });

            btnSecondary.setText("取消");
            btnSecondary.setOnClickListener(v -> sheet.dismiss());
        } else {
            tvTitle.setText("⚠️ 还有 " + count + " 个动作没做完");
            tvTitle.setTextColor(Color.parseColor("#FFAB91"));
            tvSubtitle.setText("你想怎么处理剩下的动作？");

            btnPrimary.setText("全部标记完成并存档");
            btnPrimary.setOnClickListener(v -> {
                if (isArchiving) return;
                btnPrimary.setEnabled(false);
                btnSecondary.setEnabled(false);
                sheet.setCancelable(false);
                performFinishWorkout(true);
                sheet.dismiss();
            });

            btnSecondary.setText("跳过这些，仅存档已完成");
            btnSecondary.setTextColor(Color.parseColor("#666666"));
            btnSecondary.setOnClickListener(v -> {
                if (isArchiving) return;
                btnPrimary.setEnabled(false);
                btnSecondary.setEnabled(false);
                sheet.setCancelable(false);
                performFinishWorkout(false);
                sheet.dismiss();
            });
        }
        sheet.show();
    }

    private void performFinishWorkout(boolean autoMarkAll) {
        if (isArchiving) return;
        isArchiving = true;

        final List<ExerciseWithDetail> snapshotPlan = new ArrayList<>();
        if (workoutPlan != null) {
            for (ExerciseWithDetail ex : workoutPlan) {
                if (ex.getBaseId() != -1) {
                    snapshotPlan.add(ex);
                }
            }
        }

        executorService.execute(() -> {
            try {
                if (snapshotPlan.isEmpty()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "没有数据可归档", Toast.LENGTH_SHORT).show();
                            isArchiving = false;
                        });
                    }
                    return;
                }

                long now = System.currentTimeMillis();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
                String dateStr = sdf.format(new Date(now));
                PlanEntity activePlan = workoutDao.getActivePlan();
                String actualPlanName = (activePlan != null) ? activePlan.planName : "自由训练";
                // 原来的 currentPlanName 其实存的是训练日主题
                String currentDayName = (currentPlanName != null) ? currentPlanName : "临时训练";

                int archivedCount = 0;

                if (autoMarkAll) {
                    for (ExerciseWithDetail ex : snapshotPlan) {
                        if (!ex.isCompleted()) {
                            ex.exercise.isCompleted = true;
                            workoutDao.update(ex.exercise);
                        }
                    }
                }

                // ==========================================
                // ⚠️ 核心修改区：将分散写入改为内存收集 (In-memory buffering)
                // ==========================================
                List<HistoryEntity> historiesToSave = new ArrayList<>(); // 新建一个内存篮子

                for (ExerciseWithDetail ex : snapshotPlan) {
                    if (ex.isCompleted()) {
                        HistoryEntity history = new HistoryEntity(
                                now, dateStr,
                                actualPlanName,  // ⭐ 第 3 个参数：填入刚刚查出来的真正的“经典推拉腿(PPL)”
                                currentDayName,  // ⭐ 第 4 个参数：依然是“推力日”
                                ex.getBaseId(), ex.getWeight(), ex.getReps(), ex.getSets()
                        );
                        // [新代码]：先全部装进内存里的篮子
                        historiesToSave.add(history);
                        archivedCount++;
                    }
                }

                if (archivedCount == 0) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "没有已完成的动作，取消归档", Toast.LENGTH_SHORT).show();
                            isArchiving = false;
                        });
                    }
                    return;
                }

                final int finalCount = archivedCount;
                // [新代码]：将装满数据的篮子一次性交给 Dao 层的原子结界 (Atomic Transaction)
                workoutDao.finishWorkoutAtomic(historiesToSave);

                activePlan = workoutDao.getActivePlan();
                if (activePlan != null) {
                    List<String> days = workoutDao.getDayNamesByPlanId(activePlan.planId);
                    if (days != null && !days.isEmpty()) {
                        SharedPreferences prefs = requireContext().getSharedPreferences("fitness_prefs", Context.MODE_PRIVATE);
                        int currentIndex = prefs.getInt("PLAN_INDEX", 0);
                        int nextIndex = (currentIndex + 1) % days.size();
                        prefs.edit().putInt("PLAN_INDEX", nextIndex).apply();

                        String nextDayName = days.get(nextIndex);
                        List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, nextDayName);

                        for (TemplateEntity t : templates) {
                            HistoryEntity lastRecord = workoutDao.getLatestHistoryByBaseId(t.baseId);

                            double initWeight = (lastRecord != null) ? lastRecord.weight : t.defaultWeight;
                            int initSets = (lastRecord != null) ? lastRecord.sets : t.defaultSets;
                            int initReps = (lastRecord != null) ? lastRecord.reps : t.defaultReps;

                            ExerciseEntity e = new ExerciseEntity(t.baseId, initWeight, initSets, initReps, false);
                            e.sortOrder = t.sortOrder;
                            e.color = "#FFFFFF"; // 如果是在 performFinishWorkout 里，可能没有这一行，如果有就保留
                            workoutDao.insert(e);
                        }
                    }
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "✅ 已归档 " + finalCount + " 个动作", Toast.LENGTH_SHORT).show();
                        loadDataFromDatabase();
                        isArchiving = false;
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "归档失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        isArchiving = false;
                    });
                }
            }
        });
    }

    // =========================================================
    //  逻辑 G: 计划与菜单控制
    // =========================================================
    private void toggleArrow(ImageView arrowView) {
        if (arrowView == null) return;
        Object tag = arrowView.getTag();
        boolean isUp = tag != null && tag.equals("up");

        if (isUp) {
            arrowView.setImageResource(R.drawable.ic_chevron_down);
            arrowView.setTag("down");
        } else {
            arrowView.setImageResource(R.drawable.ic_chevron_up);
            arrowView.setTag("up");
        }
    }

    private void resetArrow(ImageView arrowView) {
        if (arrowView == null) return;
        arrowView.setImageResource(R.drawable.ic_chevron_down);
        arrowView.setTag("down");
    }

    private void showPlanMenu(View anchor) {
        executorService.execute(() -> {
            List<PlanEntity> allPlans = workoutDao.getAllPlans();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    PopupMenu popup = new PopupMenu(requireContext(), anchor);
                    Menu menu = popup.getMenu();
                    for (PlanEntity plan : allPlans) {
                        MenuItem item = menu.add(1, plan.planId, 0, plan.planName);
                        if (plan.isActive) { item.setCheckable(true); item.setChecked(true); }
                    }
                    MenuItem manageItem = menu.add(2, 9999, 1, "⚙️ 管理所有计划...");
                    popup.setOnMenuItemClickListener(item -> {
                        int id = item.getItemId();
                        if (id == 9999) { startActivity(new Intent(requireContext(), PlanListActivity.class)); }
                        else {
                            boolean isCurrent = false;
                            for (PlanEntity p : allPlans) { if (p.planId == id && p.isActive) { isCurrent = true; break; } }
                            if (isCurrent) return true;
                            performSwitchPlan(id);
                        }
                        return true;
                    });
                    popup.setOnDismissListener(m -> resetArrow(ivPlanArrow));
                    popup.show();
                });
            }
        });
    }

    private void showDayMenu(View anchor) {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan == null) {
                if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "当前没有激活的计划", Toast.LENGTH_SHORT).show());
                return;
            }
            List<String> days = workoutDao.getPlanDays(activePlan.planId);
            if (days.isEmpty()) return;
            SharedPreferences prefs = requireContext().getSharedPreferences("fitness_prefs", Context.MODE_PRIVATE);
            int currentIndex = prefs.getInt("PLAN_INDEX", 0);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    PopupMenu popup = new PopupMenu(requireContext(), anchor);
                    Menu menu = popup.getMenu();
                    for (int i = 0; i < days.size(); i++) {
                        MenuItem item = menu.add(0, i, i, days.get(i));
                        if (i == currentIndex) { item.setCheckable(true); item.setChecked(true); }
                    }
                    popup.setOnMenuItemClickListener(item -> {
                        int index = item.getItemId();
                        if (index == currentIndex) return true;
                        String selectedDay = days.get(index);
                        prefs.edit().putInt("PLAN_INDEX", index).apply();
                        switchDayAndReload(activePlan, selectedDay);
                        return true;
                    });
                    popup.setOnDismissListener(m -> resetArrow(ivDayArrow));
                    popup.show();
                });
            }
        });
    }

    private void switchDayAndReload(PlanEntity plan, String dayName) {
        executorService.execute(() -> {
            workoutDao.clearAllExercises();
            List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(plan.planId, dayName);
            for (TemplateEntity t : templates) {
                HistoryEntity lastRecord = workoutDao.getLatestHistoryByBaseId(t.baseId);

                double initWeight = (lastRecord != null) ? lastRecord.weight : t.defaultWeight;
                int initSets = (lastRecord != null) ? lastRecord.sets : t.defaultSets;
                int initReps = (lastRecord != null) ? lastRecord.reps : t.defaultReps;

                ExerciseEntity e = new ExerciseEntity(t.baseId, initWeight, initSets, initReps, false);
                e.sortOrder = t.sortOrder;
                e.color = "#FFFFFF"; // 如果是在 performFinishWorkout 里，可能没有这一行，如果有就保留
                workoutDao.insert(e);
            }
            loadDataFromDatabase();
        });
    }

    private void performSwitchPlan(int planId) {
        executorService.execute(() -> {
            workoutDao.deactivateAllPlans();
            workoutDao.activatePlan(planId);
            SharedPreferences prefs = requireContext().getSharedPreferences("fitness_prefs", Context.MODE_PRIVATE);
            prefs.edit().putInt("PLAN_INDEX", 0).apply();
            PlanEntity newPlan = workoutDao.getActivePlan();
            if (newPlan != null) {
                List<String> days = workoutDao.getPlanDays(newPlan.planId);
                if (!days.isEmpty()) {
                    workoutDao.clearAllExercises();
                    List<TemplateEntity> templates = workoutDao.getTemplatesByPlanAndDay(newPlan.planId, days.get(0));
                    for (TemplateEntity t : templates) {
                        HistoryEntity lastRecord = workoutDao.getLatestHistoryByBaseId(t.baseId);

                        double initWeight = (lastRecord != null) ? lastRecord.weight : t.defaultWeight;
                        int initSets = (lastRecord != null) ? lastRecord.sets : t.defaultSets;
                        int initReps = (lastRecord != null) ? lastRecord.reps : t.defaultReps;

                        ExerciseEntity e = new ExerciseEntity(t.baseId, initWeight, initSets, initReps, false);
                        e.sortOrder = t.sortOrder;
                        e.color = "#FFFFFF"; // 如果是在 performFinishWorkout 里，可能没有这一行，如果有就保留
                        workoutDao.insert(e);
                    }
                }
            }
            loadDataFromDatabase();
            if (getActivity() != null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "已切换计划", Toast.LENGTH_SHORT).show());
        });
    }

    private void handleSmartDelete(ExerciseWithDetail exercise) {
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            boolean isInTemplate = false;
            if (activePlan != null && currentPlanName != null) {
                List<TemplateEntity> temps = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, currentPlanName);
                for (TemplateEntity t : temps) { if (t.baseId == exercise.getBaseId()) { isInTemplate = true; break; } }
            }
            boolean finalIsInTemplate = isInTemplate;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (finalIsInTemplate) {
                        new AlertDialog.Builder(requireContext()).setTitle("删除计划动作").setMessage("该动作属于当前计划模板。").setPositiveButton("仅本次跳过", (d, w) -> performDelete(exercise, false)).setNegativeButton("永久移除", (d, w) -> performDelete(exercise, true)).setNeutralButton("取消", null).show();
                    } else { performDelete(exercise, false); }
                });
            }
        });
    }

    private void performDelete(ExerciseWithDetail exercise, boolean deleteFromTemplate) {
        int pos = workoutPlan.indexOf(exercise);
        if (pos != -1 && adapter != null) adapter.deleteItem(pos);
        executorService.execute(() -> {
            workoutDao.delete(exercise.exercise);
            if (deleteFromTemplate) {
                PlanEntity activePlan = workoutDao.getActivePlan();
                if (activePlan != null && currentPlanName != null) { workoutDao.deleteTemplateByBaseId(activePlan.planId, currentPlanName, exercise.getBaseId()); }
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> com.google.android.material.snackbar.Snackbar.make(mainRecyclerView, "已删除 " + exercise.exerciseName, 4000).setAction("撤销", v -> { if (adapter != null) adapter.restoreItem(exercise, pos); executorService.execute(() -> workoutDao.insert(exercise.exercise)); }).setAnchorView(btnFinishWorkout).show());
            }
        });
    }

    private void handleTogglePin(ExerciseWithDetail exercise) {
        if (exercise.getBaseId() == -1) return;
        executorService.execute(() -> {
            PlanEntity activePlan = workoutDao.getActivePlan();
            if (activePlan == null || currentPlanName == null) {
                if(getActivity()!=null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "当前无激活计划，无法固定", Toast.LENGTH_SHORT).show());
                return;
            }
            boolean isPermanent = exercise.getColor() != null && exercise.getColor().equalsIgnoreCase("#FFFFFF");
            if (isPermanent) {
                exercise.exercise.color = "#FFF9C4";
                workoutDao.update(exercise.exercise);
                workoutDao.deleteTemplateByBaseId(activePlan.planId, currentPlanName, exercise.getBaseId());
                if(getActivity()!=null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "已从计划中移除", Toast.LENGTH_SHORT).show());
            } else {
                exercise.exercise.color = "#FFFFFF";
                workoutDao.update(exercise.exercise);
                List<TemplateEntity> existing = workoutDao.getTemplatesByPlanAndDay(activePlan.planId, currentPlanName);
                boolean exists = false;
                for (TemplateEntity t : existing) { if (t.baseId == exercise.getBaseId()) { exists = true; break; } }
                if (!exists) {
                    TemplateEntity template = new TemplateEntity();
                    template.planId = activePlan.planId;
                    template.dayName = currentPlanName;
                    template.baseId = exercise.getBaseId();
                    template.defaultSets = exercise.getSets();
                    template.defaultReps = exercise.getReps();
                    template.defaultWeight = exercise.getWeight();
                    template.sortOrder = existing.size();
                    workoutDao.insertTemplate(template);
                }
                if(getActivity()!=null) getActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "已加入计划模板", Toast.LENGTH_SHORT).show());
            }
            loadDataFromDatabase();
        });
    }

    // =========================================================
    //  逻辑 H: 拖拽与图表
    // =========================================================
    private void setupItemTouchHelper() {
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public boolean isLongPressDragEnabled() { return false; }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder instanceof ExerciseRecyclerAdapter.ItemViewHolder) {
                    ExerciseRecyclerAdapter.ItemViewHolder holder = (ExerciseRecyclerAdapter.ItemViewHolder) viewHolder;
                    clearGhostCard();
                    holder.cardNormal.setStrokeColor(Color.parseColor("#4DB6AC"));
                    holder.cardNormal.setStrokeWidth(6);
                    holder.itemView.setScaleX(1.02f);
                    holder.itemView.setScaleY(1.02f);
                    holder.itemView.setAlpha(0.9f);
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (viewHolder instanceof ExerciseRecyclerAdapter.ItemViewHolder) {
                    ExerciseRecyclerAdapter.ItemViewHolder holder = (ExerciseRecyclerAdapter.ItemViewHolder) viewHolder;
                    holder.itemView.setScaleX(1.0f);
                    holder.itemView.setScaleY(1.0f);
                    holder.itemView.setAlpha(1.0f);
                    int position = holder.getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && adapter != null) {
                        adapter.notifyItemChanged(position);
                    }
                }
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                if (adapter != null) {
                    adapter.onItemMove(source.getAdapterPosition(), target.getAdapterPosition());
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        itemTouchHelper.attachToRecyclerView(mainRecyclerView);
    }

    private void loadChartData(LineChart chart, long baseId) {
        executorService.execute(() -> {
            List<HistoryEntity> historyList = workoutDao.getHistoryByBaseId(baseId);
            Collections.sort(historyList, (h1, h2) -> Long.compare(h1.date, h2.date));
            int start = Math.max(0, historyList.size() - 10);
            List<HistoryEntity> recentList = historyList.subList(start, historyList.size());

            if (getActivity() == null) return;
            if (recentList.isEmpty()) {
                getActivity().runOnUiThread(() -> {
                    chart.setNoDataText("暂无历史数据");
                    chart.invalidate();
                });
                return;
            }
            List<Entry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd", Locale.getDefault());
            for (int i = 0; i < recentList.size(); i++) {
                HistoryEntity h = recentList.get(i);
                entries.add(new Entry(i, (float) h.weight));
                labels.add(sdf.format(new Date(h.date)));
            }
            getActivity().runOnUiThread(() -> setupChart(chart, entries, labels));
        });
    }

    private void setupChart(LineChart chart, List<Entry> entries, List<String> labels) {
        LineDataSet dataSet = new LineDataSet(entries, "重量趋势");
        dataSet.setColor(Color.parseColor("#4DB6AC"));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(Color.parseColor("#4DB6AC"));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(true);
        dataSet.setValueTextSize(10f);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);
        chart.setDescription(null);
        chart.getLegend().setEnabled(false);
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setDrawGridLines(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.animateX(500);
        chart.invalidate();
    }

    // =========================================================
    //  Inner Class: 字体特效
    // =========================================================
    public static class CenterScaleSpan extends android.text.style.ReplacementSpan {
        private final float scale;

        public CenterScaleSpan(float scale) { this.scale = scale; }

        @Override
        public int getSize(@NonNull android.graphics.Paint paint, CharSequence text, int start, int end, @Nullable android.graphics.Paint.FontMetricsInt fm) {
            android.graphics.Paint newPaint = new android.graphics.Paint(paint);
            newPaint.setTextSize(paint.getTextSize() * scale);
            return (int) newPaint.measureText(text, start, end);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull android.graphics.Paint paint) {
            android.graphics.Paint newPaint = new android.graphics.Paint(paint);
            newPaint.setTextSize(paint.getTextSize() * scale);
            android.graphics.Paint.FontMetricsInt originalFm = paint.getFontMetricsInt();
            android.graphics.Paint.FontMetricsInt newFm = newPaint.getFontMetricsInt();
            float originalCenter = (originalFm.descent + originalFm.ascent) / 2f;
            float newCenter = (newFm.descent + newFm.ascent) / 2f;
            float dy = originalCenter - newCenter;
            canvas.drawText(text, start, end, x, y + dy, newPaint);
        }
    }

    // ⭐ 在类末尾，加入这两个用于显示徽章的翻译辅助方法：
    private String translateCategoryToChinese(String rawCategory) {
        if (rawCategory == null) return "其他";
        switch (rawCategory) {
            case "Chest": return "胸部";
            case "Back": return "背部";
            case "Legs&Glutes": return "腿臀";
            case "Shoulders": return "肩部";
            case "Arms": return "手臂";
            case "Core": return "核心";
            default: return "其他";
        }
    }

    private int getCategoryColor(String rawCategory) {
        if (rawCategory == null) return Color.parseColor("#9E9E9E");
        switch (rawCategory) {
            case "Chest": return Color.parseColor("#1E88E5");
            case "Back": return Color.parseColor("#43A047");
            case "Legs&Glutes": return Color.parseColor("#F4511E");
            case "Shoulders": return Color.parseColor("#8E24AA");
            case "Arms": return Color.parseColor("#E53935");
            case "Core": return Color.parseColor("#FDD835");
            default: return Color.parseColor("#9E9E9E");
        }
    }
}