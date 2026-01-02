package com.example.fitness_plan;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {

    private WorkoutDao workoutDao;
    private ExpandableListView expandableListView;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    // 全局单位状态
    private boolean isLbsMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 1. 获取数据库
        AppDatabase db = AppDatabase.getDatabase(this);
        workoutDao = db.workoutDao();

        // 2. 读取全局单位设置
        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        isLbsMode = prefs.getBoolean("DEFAULT_IS_LBS", false);

        expandableListView = findViewById(R.id.historyExpandableListView);

        // 加载数据
        loadHistoryData();

        // 添加按钮
        FloatingActionButton fab = findViewById(R.id.fabAddHistory);
        fab.setOnClickListener(v -> showAddHistoryDialog());
    }

    // ==========================================
    //  添加历史记录对话框
    // ==========================================
    private void showAddHistoryDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText etName = new EditText(this);
        etName.setHint("动作名称 (必填)");
        layout.addView(etName);

        String unitHint = isLbsMode ? "重量 (lbs)" : "重量 (kg)";
        final EditText etWeight = new EditText(this);
        etWeight.setHint(unitHint);
        etWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etWeight);

        final EditText etSets = new EditText(this);
        etSets.setHint("组数");
        etSets.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etSets);

        final EditText etReps = new EditText(this);
        etReps.setHint("次数");
        etReps.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(etReps);

        final EditText etTitle = new EditText(this);
        etTitle.setHint("训练标题 (如: 推力日)");
        layout.addView(etTitle);

        // 日期选择
        final android.widget.Button btnDate = new android.widget.Button(this);
        final Calendar calendar = Calendar.getInstance();
        updateDateButton(btnDate, calendar);
        btnDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                updateDateButton(btnDate, calendar);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(btnDate);

        new AlertDialog.Builder(this)
                .setTitle("补录历史记录")
                .setView(layout)
                .setPositiveButton("添加", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String wStr = etWeight.getText().toString().trim();
                    String sStr = etSets.getText().toString().trim();
                    String rStr = etReps.getText().toString().trim();
                    String titleInput = etTitle.getText().toString().trim();

                    if (name.isEmpty() || wStr.isEmpty()) {
                        Toast.makeText(this, "名称和重量不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    executorService.execute(() -> {
                        double inputWeight = Double.parseDouble(wStr);
                        double dbWeight = isLbsMode ? (inputWeight / 2.20462) : inputWeight;
                        int s = sStr.isEmpty() ? 0 : Integer.parseInt(sStr);
                        int r = rStr.isEmpty() ? 0 : Integer.parseInt(rStr);
                        String finalTitle = titleInput.isEmpty() ? "手动补录" : titleInput;

                        // 使用构造函数 1
                        HistoryEntity newItem = new HistoryEntity(
                                calendar.getTimeInMillis(),
                                name,
                                dbWeight,
                                s,
                                r,
                                isLbsMode
                        );
                        // 【修正】使用 workoutTitle
                        newItem.workoutTitle = finalTitle;

                        workoutDao.insertHistory(newItem);
                        loadHistoryData();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDateButton(android.widget.Button btn, Calendar cal) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        btn.setText("日期: " + sdf.format(cal.getTime()));
    }

    // ==========================================
    //  加载数据 (含日期分组逻辑)
    // ==========================================
    private void loadHistoryData() {
        executorService.execute(() -> {
            List<HistoryEntity> rawList = workoutDao.getAllHistory();

            // 使用 Map 进行分组：Key 是当天 00:00 的时间戳
            Map<Long, List<HistoryEntity>> groupedData = new LinkedHashMap<>();
            List<Long> dateKeys = new ArrayList<>();

            for (HistoryEntity item : rawList) {
                long dayTimestamp = getStartOfDay(item.date);

                if (!groupedData.containsKey(dayTimestamp)) {
                    groupedData.put(dayTimestamp, new ArrayList<>());
                    dateKeys.add(dayTimestamp);
                }
                groupedData.get(dayTimestamp).add(item);
            }

            runOnUiThread(() -> {
                HistoryExpandableAdapter adapter = new HistoryExpandableAdapter(
                        this,
                        dateKeys,
                        groupedData,
                        isLbsMode,
                        new HistoryExpandableAdapter.OnHistoryActionListener() {
                            @Override
                            public void onEditHistory(HistoryEntity history) {
                                showEditDialog(history);
                            }
                            @Override
                            public void onDeleteHistory(HistoryEntity history) {
                                showDeleteDialog(history);
                            }
                        }
                );
                expandableListView.setAdapter(adapter);
                // 默认展开第一个组
                if (!dateKeys.isEmpty()) expandableListView.expandGroup(0);
            });
        });
    }

    // 辅助：获取某天开始的时间戳
    private long getStartOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // ==========================================
    //  编辑对话框
    // ==========================================
    private void showEditDialog(HistoryEntity history) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // 1. 标题
        TextView labelTitle = new TextView(this);
        labelTitle.setText("训练日标题");
        layout.addView(labelTitle);
        final EditText etTitle = new EditText(this);
        // 【修正】使用 workoutTitle
        etTitle.setText(history.workoutTitle == null ? "自由训练" : history.workoutTitle);
        layout.addView(etTitle);

        // 2. 重量
        TextView labelWeight = new TextView(this);
        labelWeight.setText(isLbsMode ? "重量 (lbs)" : "重量 (kg)");
        layout.addView(labelWeight);
        final EditText etWeight = new EditText(this);
        etWeight.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        double displayWeight = isLbsMode ? (history.weight * 2.20462) : history.weight;
        String wStr = (displayWeight % 1 == 0) ? String.valueOf((int) displayWeight) : String.format(Locale.getDefault(), "%.1f", displayWeight);
        etWeight.setText(wStr);
        layout.addView(etWeight);

        // 3. 组数
        TextView labelSets = new TextView(this);
        labelSets.setText("组数");
        layout.addView(labelSets);
        final EditText etSets = new EditText(this);
        etSets.setInputType(InputType.TYPE_CLASS_NUMBER);
        etSets.setText(String.valueOf(history.sets));
        layout.addView(etSets);

        // 4. 次数
        TextView labelReps = new TextView(this);
        labelReps.setText("次数");
        layout.addView(labelReps);
        final EditText etReps = new EditText(this);
        etReps.setInputType(InputType.TYPE_CLASS_NUMBER);
        etReps.setText(String.valueOf(history.reps));
        layout.addView(etReps);

        new AlertDialog.Builder(this)
                // 【修正】使用 name
                .setTitle("修改: " + history.name)
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        String tInput = etTitle.getText().toString().trim();
                        String wInput = etWeight.getText().toString();
                        String sInput = etSets.getText().toString();
                        String rInput = etReps.getText().toString();

                        if (wInput.isEmpty() || sInput.isEmpty() || rInput.isEmpty()) return;

                        double inputW = Double.parseDouble(wInput);
                        int s = Integer.parseInt(sInput);
                        int r = Integer.parseInt(rInput);

                        // 【修正】使用 workoutTitle
                        history.workoutTitle = tInput.isEmpty() ? "自由训练" : tInput;
                        history.weight = isLbsMode ? (inputW / 2.20462) : inputW;
                        history.sets = s;
                        history.reps = r;

                        executorService.execute(() -> {
                            workoutDao.updateHistory(history);
                            loadHistoryData();
                        });
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "无效输入", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteDialog(HistoryEntity history) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                // 【修正】使用 name
                .setMessage("确定删除 " + history.name + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    executorService.execute(() -> {
                        workoutDao.deleteHistory(history);
                        loadHistoryData();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }
}