/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ui.activities;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.amaze.filemanager.R;
import com.amaze.filemanager.logviewer.LogAdapter;
import com.amaze.filemanager.logviewer.LogEntry;
import com.amaze.filemanager.logviewer.LogFilter;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.ui.theme.AppTheme;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志查看器 Activity
 */
public class LogViewerActivity extends ThemedActivity {

    private static final String TAG = "LogViewerActivity";
    private static final int REQUEST_CODE_OPEN_LOG_FILE = 1001;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 1002;
    private static final int DEFAULT_BUFFER_SIZE = 5000;
    private static final int MAX_BUFFER_SIZE = 50000;

    private RecyclerView recyclerView;
    private LogAdapter logAdapter;
    private LinearLayoutManager layoutManager;
    private EditText etSearch;
    private ImageButton btnClearSearch;
    private Spinner spinnerLogLevel;
    private TextView tvLogCount;
    private TextView tvStatus;
    private FloatingActionButton fabScrollToBottom;
    private FloatingActionButton fabPause;
    private View searchContainer;
    private View emptyView;

    private ExecutorService logReaderExecutor;
    private Handler mainHandler;
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private int bufferSize = DEFAULT_BUFFER_SIZE;

    private LogFilter currentFilter = new LogFilter();
    private Process logcatProcess;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_viewer);

        initViews();
        setupToolbar();
        setupRecyclerView();
        setupListeners();
        setupLogLevelSpinner();

        mainHandler = new Handler(Looper.getMainLooper());
        logReaderExecutor = Executors.newSingleThreadExecutor();

        // 检查并请求存储权限（用于导出日志）
        checkStoragePermission();

        // 开始读取日志
        startLogCapture();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view_logs);
        etSearch = findViewById(R.id.et_search);
        btnClearSearch = findViewById(R.id.btn_clear_search);
        spinnerLogLevel = findViewById(R.id.spinner_log_level);
        tvLogCount = findViewById(R.id.tv_log_count);
        tvStatus = findViewById(R.id.tv_status);
        fabScrollToBottom = findViewById(R.id.fab_scroll_to_bottom);
        fabPause = findViewById(R.id.fab_pause);
        searchContainer = findViewById(R.id.search_container);
        emptyView = findViewById(R.id.empty_view);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.log_viewer_title);
        }
    }

    private void setupRecyclerView() {
        logAdapter = new LogAdapter();
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(logAdapter);

        // 设置颜色主题
        updateAdapterColors();

        // 设置点击监听器
        logAdapter.setOnLogClickListener((entry, position) -> {
            showLogDetailDialog(entry);
        });

        logAdapter.setOnLogLongClickListener((entry, position) -> {
            showLogOptionsDialog(entry);
            return true;
        });

        // 监听滚动状态，控制自动滚动
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // 检查是否在底部
                int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
                int totalItemCount = logAdapter.getItemCount();
                if (lastVisiblePosition < totalItemCount - 1) {
                    fabScrollToBottom.show();
                } else {
                    fabScrollToBottom.hide();
                }
            }
        });
    }

    private void updateAdapterColors() {
        AppTheme theme = getAppTheme();
        boolean isDark = theme != AppTheme.LIGHT;

        int textColor = isDark ? Color.WHITE : Color.BLACK;
        int secondaryColor = isDark ? Color.LTGRAY : Color.DKGRAY;

        logAdapter.setTextColors(textColor, secondaryColor, Color.parseColor("#FFEB3B"));
    }

    private void setupListeners() {
        // 搜索框监听
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentFilter.setSearchQuery(s.toString());
                applyFilter();
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // 清除搜索按钮
        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            currentFilter.setSearchQuery("");
            applyFilter();
        });

        // 滚动到底部按钮
        fabScrollToBottom.setOnClickListener(v -> {
            scrollToBottom();
        });

        // 暂停/继续按钮
        fabPause.setOnClickListener(v -> {
            togglePause();
        });
    }

    private void setupLogLevelSpinner() {
        String[] levels = {
            getString(R.string.log_level_verbose),
            getString(R.string.log_level_debug),
            getString(R.string.log_level_info),
            getString(R.string.log_level_warn),
            getString(R.string.log_level_error)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, levels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLogLevel.setAdapter(adapter);

        spinnerLogLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LogEntry.LogLevel level;
                switch (position) {
                    case 0: level = LogEntry.LogLevel.VERBOSE; break;
                    case 1: level = LogEntry.LogLevel.DEBUG; break;
                    case 2: level = LogEntry.LogLevel.INFO; break;
                    case 3: level = LogEntry.LogLevel.WARN; break;
                    case 4: level = LogEntry.LogLevel.ERROR; break;
                    default: level = LogEntry.LogLevel.VERBOSE;
                }
                currentFilter.setMinLevel(level);
                applyFilter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyFilter() {
        logAdapter.setFilter(currentFilter);
        updateLogCount();
        updateEmptyView();
    }

    private void updateLogCount() {
        int filteredCount = logAdapter.getItemCount();
        int totalCount = logAdapter.getAllEntries().size();
        tvLogCount.setText(getString(R.string.log_count_format, filteredCount, totalCount));
    }

    private void updateEmptyView() {
        if (logAdapter.getItemCount() == 0) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void scrollToBottom() {
        int position = logAdapter.getItemCount() - 1;
        if (position >= 0) {
            recyclerView.smoothScrollToPosition(position);
        }
        fabScrollToBottom.hide();
    }

    private void togglePause() {
        boolean paused = isPaused.getAndSet(!isPaused.get());
        if (paused) {
            // 恢复
            fabPause.setImageResource(R.drawable.ic_pause_white_24dp);
            tvStatus.setText(R.string.log_status_running);
            tvStatus.setTextColor(Color.GREEN);
        } else {
            // 暂停
            fabPause.setImageResource(R.drawable.ic_play_arrow_white_24dp);
            tvStatus.setText(R.string.log_status_paused);
            tvStatus.setTextColor(Color.YELLOW);
        }
    }

    private void startLogCapture() {
        if (isRunning.get()) {
            return;
        }
        isRunning.set(true);
        tvStatus.setText(R.string.log_status_running);
        tvStatus.setTextColor(Color.GREEN);

        logReaderExecutor.execute(() -> {
            try {
                // 使用 logcat 命令读取日志
                String[] cmd = {"logcat", "-v", "threadtime"};
                logcatProcess = Runtime.getRuntime().exec(cmd);
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream()));

                String line;
                while (isRunning.get() && (line = reader.readLine()) != null) {
                    if (!isPaused.get()) {
                        final String logLine = line;
                        mainHandler.post(() -> {
                            addLogEntry(logLine);
                        });
                    }
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    tvStatus.setText(R.string.log_status_error);
                    tvStatus.setTextColor(Color.RED);
                    Toast.makeText(this, R.string.log_read_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void stopLogCapture() {
        isRunning.set(false);
        if (logcatProcess != null) {
            logcatProcess.destroy();
            logcatProcess = null;
        }
    }

    private void addLogEntry(String logLine) {
        LogEntry entry = new LogEntry(logLine);
        logAdapter.addLog(entry);
        updateLogCount();
        updateEmptyView();

        // 限制缓冲区大小
        List<LogEntry> allEntries = logAdapter.getAllEntries();
        if (allEntries.size() > bufferSize) {
            // 移除最旧的日志
            int removeCount = allEntries.size() - bufferSize;
            for (int i = 0; i < removeCount; i++) {
                allEntries.remove(0);
            }
            applyFilter();
        }

        // 自动滚动到底部（如果已经在底部附近）
        int lastVisiblePosition = layoutManager.findLastVisibleItemPosition();
        int totalItemCount = logAdapter.getItemCount();
        if (lastVisiblePosition >= totalItemCount - 3) {
            recyclerView.scrollToPosition(totalItemCount - 1);
        }
    }

    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_viewer_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_clear) {
            clearLogs();
            return true;
        } else if (id == R.id.action_export) {
            exportLogs();
            return true;
        } else if (id == R.id.action_open_file) {
            openLogFile();
            return true;
        } else if (id == R.id.action_buffer_size) {
            showBufferSizeDialog();
            return true;
        } else if (id == R.id.action_search) {
            toggleSearch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleSearch() {
        if (searchContainer.getVisibility() == View.VISIBLE) {
            searchContainer.setVisibility(View.GONE);
        } else {
            searchContainer.setVisibility(View.VISIBLE);
            etSearch.requestFocus();
        }
    }

    private void clearLogs() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.confirm_clear_logs)
            .setMessage(R.string.confirm_clear_logs_message)
            .setPositiveButton(R.string.yes, (dialog, which) -> {
                logAdapter.clear();
                updateLogCount();
                updateEmptyView();
                Toast.makeText(this, R.string.logs_cleared, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    private void exportLogs() {
        if (logAdapter.getAllEntries().isEmpty()) {
            Toast.makeText(this, R.string.no_logs_to_export, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File exportDir = new File(Environment.getExternalStorageDirectory(), "AmazeLogs");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
            File exportFile = new File(exportDir, "log_" + timestamp + ".txt");

            FileWriter writer = new FileWriter(exportFile);
            for (LogEntry entry : logAdapter.getAllEntries()) {
                writer.write(entry.getDisplayText());
                writer.write("\n");
            }
            writer.close();

            Toast.makeText(this, getString(R.string.log_exported, exportFile.getAbsolutePath()), 
                Toast.LENGTH_LONG).show();

            // 分享文件
            shareLogFile(exportFile);

        } catch (IOException e) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareLogFile(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName(), file);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_log)));
    }

    private void openLogFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        startActivityForResult(intent, REQUEST_CODE_OPEN_LOG_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_LOG_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                loadLogFile(uri);
            }
        }
    }

    private void loadLogFile(Uri uri) {
        stopLogCapture();
        logAdapter.clear();
        updateLogCount();

        logReaderExecutor.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    List<LogEntry> entries = new ArrayList<>();
                    while ((line = reader.readLine()) != null) {
                        entries.add(new LogEntry(line));
                        if (entries.size() >= 100) {
                            final List<LogEntry> batch = new ArrayList<>(entries);
                            mainHandler.post(() -> logAdapter.addLogs(batch));
                            entries.clear();
                        }
                    }
                    if (!entries.isEmpty()) {
                        final List<LogEntry> batch = new ArrayList<>(entries);
                        mainHandler.post(() -> logAdapter.addLogs(batch));
                    }
                    reader.close();
                    inputStream.close();

                    mainHandler.post(() -> {
                        updateLogCount();
                        updateEmptyView();
                        Toast.makeText(this, R.string.log_file_loaded, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (IOException e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, R.string.log_file_load_error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showBufferSizeDialog() {
        String[] sizes = {"1000", "5000", "10000", "20000", "50000"};
        int currentIndex = 0;
        for (int i = 0; i < sizes.length; i++) {
            if (Integer.parseInt(sizes[i]) == bufferSize) {
                currentIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.buffer_size_title)
            .setSingleChoiceItems(sizes, currentIndex, (dialog, which) -> {
                bufferSize = Integer.parseInt(sizes[which]);
                dialog.dismiss();
                Toast.makeText(this, getString(R.string.buffer_size_set, bufferSize), 
                    Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private void showLogDetailDialog(LogEntry entry) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.log_detail)
            .setMessage(entry.getDisplayText())
            .setPositiveButton(R.string.copy, (dialog, which) -> {
                copyToClipboard(entry.getDisplayText());
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    private void showLogOptionsDialog(LogEntry entry) {
        String[] options = {
            getString(R.string.copy),
            getString(R.string.copy_tag),
            getString(R.string.highlight_log),
            getString(R.string.filter_by_tag)
        };

        new AlertDialog.Builder(this)
            .setTitle(R.string.log_options)
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0:
                        copyToClipboard(entry.getDisplayText());
                        break;
                    case 1:
                        copyToClipboard(entry.getTag());
                        break;
                    case 2:
                        entry.setHighlighted(!entry.isHighlighted());
                        logAdapter.notifyDataSetChanged();
                        break;
                    case 3:
                        currentFilter.setTagFilter(entry.getTag());
                        applyFilter();
                        break;
                }
            })
            .show();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("log", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLogCapture();
        if (logReaderExecutor != null) {
            logReaderExecutor.shutdown();
        }
    }
}
