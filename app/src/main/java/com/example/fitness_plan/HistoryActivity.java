package com.example.fitness_plan;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
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

    private List<Long> dateKeys = new ArrayList<>();
    private boolean isLbsMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        // 初始化数据库
        AppDatabase db = AppDatabase.getDatabase(this);
        workoutDao = db.workoutDao();

        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        isLbsMode = prefs.getBoolean("DEFAULT_IS_LBS", false);

        expandableListView = findViewById(R.id.historyExpandableListView);

        // 加载数据
        loadHistoryData();

        // 1. 绑定添加按钮
        FloatingActionButton fab = findViewById(R.id.fabAddHistory);
        fab.setOnClickListener(v -> showAddHistoryDialog());

        // 2. 【关键新增】绑定右上角的日历查找按钮
        // 替代了原来的 Menu
        View btnSearch = findViewById(R.id.btnCalendarSearch);
        btnSearch.setOnClickListener(v -> showDatePickerAndScroll());
    }

    // 原来的 onCreateOptionsMenu 和 onOptionsItemSelected 删除了，
    // 因为我们现在用 XML 里的 ImageView 直接控制，更稳定。

    // 日期跳转逻辑
    private void showDatePickerAndScroll() {
        if (dateKeys.isEmpty()) {
            Toast.makeText(this, "暂无记录", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar target = Calendar.getInstance();
            target.set(year, month, dayOfMonth, 0, 0, 0);
            target.set(Calendar.MILLISECOND, 0);
            long targetTime = target.getTimeInMillis();

            // 倒序查找最近的记录
            int targetIndex = -1;
            for (int i = 0; i < dateKeys.size(); i++) {
                // 找到第一个早于或等于选中日期的记录（因为是倒序）
                if (dateKeys.get(i) <= targetTime + 86400000L) {
                    targetIndex = i;
                    break;
                }
            }

            if (targetIndex != -1) {
                expandableListView.setSelectedGroup(targetIndex);
                Toast.makeText(this, "已跳转至附近记录", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未找到该日期之前的记录", Toast.LENGTH_SHORT).show();
            }

        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ==========================================
    //  下方代码保持不变 (添加、编辑、删除、加载数据)
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
        etTitle.setHint("训练标题 (如: 经典三分化 - 推力日)");
        layout.addView(etTitle);

        final Button btnDate = new Button(this);
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

                        HistoryEntity newItem = new HistoryEntity(calendar.getTimeInMillis(), name, dbWeight, s, r, isLbsMode);
                        newItem.workoutTitle = finalTitle;
                        workoutDao.insertHistory(newItem);
                        loadHistoryData();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateDateButton(Button btn, Calendar cal) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        btn.setText("日期: " + sdf.format(cal.getTime()));
    }

    private void loadHistoryData() {
        executorService.execute(() -> {
            List<HistoryEntity> rawList = workoutDao.getAllHistory();

            Map<Long, List<HistoryEntity>> groupedData = new LinkedHashMap<>();
            dateKeys = new ArrayList<>();

            for (HistoryEntity item : rawList) {
                long dayTimestamp = getStartOfDay(item.date);
                if (!groupedData.containsKey(dayTimestamp)) {
                    groupedData.put(dayTimestamp, new ArrayList<>());
                    dateKeys.add(dayTimestamp);
                }
                groupedData.get(dayTimestamp).add(item);
            }

            Collections.sort(dateKeys, (t1, t2) -> Long.compare(t2, t1));

            runOnUiThread(() -> {
                HistoryExpandableAdapter adapter = new HistoryExpandableAdapter(
                        this,
                        dateKeys,
                        groupedData,
                        isLbsMode,
                        new HistoryExpandableAdapter.OnHistoryActionListener() {
                            @Override
                            public void onEditHistory(HistoryEntity history) { showEditHistoryDialog(history); }
                            @Override
                            public void onDeleteHistory(HistoryEntity history) { showDeleteDialog(history); }
                        }
                );
                expandableListView.setAdapter(adapter);
                if (!dateKeys.isEmpty()) expandableListView.expandGroup(0);
            });
        });
    }

    private long getStartOfDay(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(time);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private void showEditHistoryDialog(HistoryEntity historyItem) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet_history_edit, null);
        sheet.setContentView(view);
        if (sheet.getWindow() != null) sheet.getWindow().setDimAmount(0.3f);

        EditText etName = view.findViewById(R.id.etEditName);
        EditText etWeight = view.findViewById(R.id.etEditWeight);
        EditText etSets = view.findViewById(R.id.etEditSets);
        EditText etReps = view.findViewById(R.id.etEditReps);
        EditText etPlan = view.findViewById(R.id.etEditPlanName);
        EditText etDay = view.findViewById(R.id.etEditDayTitle);
        Button btnSave = view.findViewById(R.id.btnSaveHistory);

        etName.setText(historyItem.name);
        double displayWeight = isLbsMode ? (historyItem.weight * 2.20462) : historyItem.weight;
        etWeight.setText(String.format(Locale.getDefault(), "%.1f", displayWeight));
        etSets.setText(String.valueOf(historyItem.sets));
        etReps.setText(String.valueOf(historyItem.reps));

        String fullTitle = historyItem.workoutTitle;
        if (fullTitle != null && fullTitle.contains(" - ")) {
            String[] parts = fullTitle.split(" - ", 2);
            etPlan.setText(parts[0]);
            etDay.setText(parts.length > 1 ? parts[1] : "");
        } else {
            etPlan.setText("");
            etDay.setText(fullTitle == null ? "自由训练" : fullTitle);
        }

        btnSave.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String newPlan = etPlan.getText().toString().trim();
            String newDay = etDay.getText().toString().trim();

            if (newName.isEmpty()) return;

            try {
                historyItem.name = newName;
                double inputW = Double.parseDouble(etWeight.getText().toString());
                historyItem.weight = isLbsMode ? (inputW / 2.20462) : inputW;
                historyItem.sets = Integer.parseInt(etSets.getText().toString());
                historyItem.reps = Integer.parseInt(etReps.getText().toString());
                if (!newPlan.isEmpty()) historyItem.workoutTitle = newPlan + " - " + newDay;
                else historyItem.workoutTitle = newDay;

                executorService.execute(() -> {
                    workoutDao.updateHistory(historyItem);
                    loadHistoryData();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "修改已保存", Toast.LENGTH_SHORT).show();
                        sheet.dismiss();
                    });
                });
            } catch (NumberFormatException ignored) {}
        });
        sheet.show();
    }

    private void showDeleteDialog(HistoryEntity history) {
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
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