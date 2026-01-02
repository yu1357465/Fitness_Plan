package com.example.fitness_plan;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.fitness_plan.data.AppDatabase;
import com.example.fitness_plan.data.HistoryEntity;
import com.example.fitness_plan.data.WorkoutDao;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;

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

        AppDatabase db = AppDatabase.getDatabase(this);
        workoutDao = db.workoutDao();

        SharedPreferences prefs = getSharedPreferences("fitness_prefs", MODE_PRIVATE);
        isLbsMode = prefs.getBoolean("DEFAULT_IS_LBS", false);

        expandableListView = findViewById(R.id.historyExpandableListView);

        loadHistoryData();

        FloatingActionButton fab = findViewById(R.id.fabAddHistory);
        fab.setOnClickListener(v -> showAddHistoryDialog());

        View btnSearch = findViewById(R.id.btnCalendarSearch);
        btnSearch.setOnClickListener(v -> showDatePickerAndScroll());
    }

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

            int targetIndex = -1;
            for (int i = 0; i < dateKeys.size(); i++) {
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
    //  【重构】添加历史记录 (使用 BottomSheet + 新风格)
    // ==========================================
    private void showAddHistoryDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet_history_add, null);
        sheet.setContentView(view);
        if (sheet.getWindow() != null) sheet.getWindow().setDimAmount(0.0f);

        // 绑定控件
        EditText etPlan = view.findViewById(R.id.etAddPlanName);
        EditText etDay = view.findViewById(R.id.etAddDayTitle);
        EditText etName = view.findViewById(R.id.etAddName);
        EditText etWeight = view.findViewById(R.id.etAddWeight);
        EditText etSets = view.findViewById(R.id.etAddSets);
        EditText etReps = view.findViewById(R.id.etAddReps);
        Button btnDate = view.findViewById(R.id.btnAddDate);
        Button btnAdd = view.findViewById(R.id.btnAddConfirm);
        TextInputLayout layoutWeight = view.findViewById(R.id.layoutAddWeight);

        // 设置单位提示
        layoutWeight.setHint(isLbsMode ? "重量 (lbs)" : "重量 (kg)");

        // 默认日期为今天
        final Calendar calendar = Calendar.getInstance();
        updateDateButton(btnDate, calendar);

        btnDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (d, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth);
                updateDateButton(btnDate, calendar);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnAdd.setOnClickListener(v -> {
            String plan = etPlan.getText().toString().trim();
            String day = etDay.getText().toString().trim();
            String name = etName.getText().toString().trim();
            String wStr = etWeight.getText().toString().trim();
            String sStr = etSets.getText().toString().trim();
            String rStr = etReps.getText().toString().trim();

            if (name.isEmpty() || wStr.isEmpty()) {
                Toast.makeText(this, "动作名称和重量必填", Toast.LENGTH_SHORT).show();
                return;
            }

            executorService.execute(() -> {
                try {
                    double inputWeight = Double.parseDouble(wStr);
                    double dbWeight = isLbsMode ? (inputWeight / 2.20462) : inputWeight;
                    int s = sStr.isEmpty() ? 0 : Integer.parseInt(sStr);
                    int r = rStr.isEmpty() ? 0 : Integer.parseInt(rStr);

                    String finalTitle;
                    if (!plan.isEmpty()) {
                        finalTitle = plan + " - " + (day.isEmpty() ? "自由训练" : day);
                    } else {
                        finalTitle = day.isEmpty() ? "手动补录" : day;
                    }

                    HistoryEntity newItem = new HistoryEntity(calendar.getTimeInMillis(), name, dbWeight, s, r, isLbsMode);
                    newItem.workoutTitle = finalTitle;
                    workoutDao.insertHistory(newItem);

                    loadHistoryData();
                    runOnUiThread(() -> sheet.dismiss());
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "输入格式错误", Toast.LENGTH_SHORT).show());
                }
            });
        });
        sheet.show();
    }

    private void updateDateButton(Button btn, Calendar cal) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        btn.setText("日期: " + sdf.format(cal.getTime()));
    }

    // ==========================================
    //  【更新】编辑对话框 (匹配新的 UI 和顺序)
    // ==========================================
    private void showEditHistoryDialog(HistoryEntity historyItem) {
        BottomSheetDialog sheet = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View view = getLayoutInflater().inflate(R.layout.dialog_bottom_sheet_history_edit, null);
        sheet.setContentView(view);
        if (sheet.getWindow() != null) sheet.getWindow().setDimAmount(0.0f);

        // 绑定控件 (顺序已变)
        EditText etPlan = view.findViewById(R.id.etEditPlanName); // 1
        EditText etDay = view.findViewById(R.id.etEditDayTitle);   // 2
        EditText etName = view.findViewById(R.id.etEditName);      // 3
        EditText etWeight = view.findViewById(R.id.etEditWeight);  // 4
        EditText etSets = view.findViewById(R.id.etEditSets);
        EditText etReps = view.findViewById(R.id.etEditReps);
        Button btnSave = view.findViewById(R.id.btnSaveHistory);

        // 填充数据
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
            String newPlan = etPlan.getText().toString().trim();
            String newDay = etDay.getText().toString().trim();
            String newName = etName.getText().toString().trim();

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