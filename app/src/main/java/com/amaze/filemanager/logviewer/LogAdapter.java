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

package com.amaze.filemanager.logviewer;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amaze.filemanager.R;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView 适配器，用于显示日志条目
 */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    private final List<LogEntry> allEntries = new ArrayList<>();
    private final List<LogEntry> filteredEntries = new ArrayList<>();
    private LogFilter filter = new LogFilter();
    private OnLogClickListener clickListener;
    private OnLogLongClickListener longClickListener;

    // 日志级别颜色
    @ColorInt
    private int verboseColor = Color.GRAY;
    @ColorInt
    private int debugColor = Color.parseColor("#2196F3"); // Blue
    @ColorInt
    private int infoColor = Color.parseColor("#4CAF50"); // Green
    @ColorInt
    private int warnColor = Color.parseColor("#FF9800"); // Orange
    @ColorInt
    private int errorColor = Color.parseColor("#F44336"); // Red
    @ColorInt
    private int fatalColor = Color.parseColor("#B71C1C"); // Dark Red
    @ColorInt
    private int highlightColor = Color.parseColor("#FFEB3B"); // Yellow highlight
    @ColorInt
    private int textColor = Color.WHITE;
    @ColorInt
    private int secondaryTextColor = Color.LTGRAY;

    public interface OnLogClickListener {
        void onLogClick(LogEntry entry, int position);
    }

    public interface OnLogLongClickListener {
        boolean onLogLongClick(LogEntry entry, int position);
    }

    public LogAdapter() {
    }

    public void setOnLogClickListener(OnLogClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnLogLongClickListener(OnLogLongClickListener listener) {
        this.longClickListener = listener;
    }

    /**
     * 设置日志级别颜色
     */
    public void setLogLevelColors(@ColorInt int verbose, @ColorInt int debug, @ColorInt int info,
                                   @ColorInt int warn, @ColorInt int error, @ColorInt int fatal) {
        this.verboseColor = verbose;
        this.debugColor = debug;
        this.infoColor = info;
        this.warnColor = warn;
        this.errorColor = error;
        this.fatalColor = fatal;
        notifyDataSetChanged();
    }

    /**
     * 设置文本颜色
     */
    public void setTextColors(@ColorInt int primary, @ColorInt int secondary, @ColorInt int highlight) {
        this.textColor = primary;
        this.secondaryTextColor = secondary;
        this.highlightColor = highlight;
        notifyDataSetChanged();
    }

    /**
     * 添加单条日志
     */
    public void addLog(@NonNull LogEntry entry) {
        allEntries.add(entry);
        if (filter.matches(entry)) {
            filteredEntries.add(entry);
            notifyItemInserted(filteredEntries.size() - 1);
        }
    }

    /**
     * 添加多条日志
     */
    public void addLogs(@NonNull List<LogEntry> entries) {
        int oldSize = filteredEntries.size();
        for (LogEntry entry : entries) {
            allEntries.add(entry);
            if (filter.matches(entry)) {
                filteredEntries.add(entry);
            }
        }
        if (filteredEntries.size() > oldSize) {
            notifyItemRangeInserted(oldSize, filteredEntries.size() - oldSize);
        }
    }

    /**
     * 获取所有日志（未过滤）
     */
    @NonNull
    public List<LogEntry> getAllEntries() {
        return new ArrayList<>(allEntries);
    }

    /**
     * 获取过滤后的日志
     */
    @NonNull
    public List<LogEntry> getFilteredEntries() {
        return new ArrayList<>(filteredEntries);
    }

    /**
     * 获取指定位置的日志条目
     */
    public LogEntry getEntry(int position) {
        if (position >= 0 && position < filteredEntries.size()) {
            return filteredEntries.get(position);
        }
        return null;
    }

    /**
     * 清除所有日志
     */
    public void clear() {
        int oldSize = filteredEntries.size();
        allEntries.clear();
        filteredEntries.clear();
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        }
    }

    /**
     * 设置过滤器并刷新显示
     */
    public void setFilter(@NonNull LogFilter filter) {
        this.filter = filter;
        applyFilter();
    }

    /**
     * 获取当前过滤器
     */
    @NonNull
    public LogFilter getFilter() {
        return filter;
    }

    /**
     * 应用过滤器
     */
    private void applyFilter() {
        filteredEntries.clear();
        for (LogEntry entry : allEntries) {
            if (filter.matches(entry)) {
                filteredEntries.add(entry);
            }
        }
        notifyDataSetChanged();
    }

    /**
     * 获取日志级别对应的颜色
     */
    @ColorInt
    private int getLevelColor(@NonNull LogEntry.LogLevel level) {
        switch (level) {
            case VERBOSE:
                return verboseColor;
            case DEBUG:
                return debugColor;
            case INFO:
                return infoColor;
            case WARN:
                return warnColor;
            case ERROR:
                return errorColor;
            case FATAL:
            case ASSERT:
                return fatalColor;
            default:
                return textColor;
        }
    }

    /**
     * 获取日志级别对应的背景色（用于标签）
     */
    @ColorInt
    private int getLevelBackgroundColor(@NonNull LogEntry.LogLevel level) {
        int baseColor = getLevelColor(level);
        // 降低透明度，创建半透明背景
        return (baseColor & 0x00FFFFFF) | 0x33000000;
    }

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_entry, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        LogEntry entry = filteredEntries.get(position);
        holder.bind(entry);
    }

    @Override
    public int getItemCount() {
        return filteredEntries.size();
    }

    /**
     * 创建带搜索高亮的 Spannable
     */
    private SpannableStringBuilder createHighlightedText(@NonNull String text) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);

        // 如果有搜索关键词，高亮匹配部分
        if (!filter.getSearchQuery().isEmpty()) {
            List<int[]> matches = filter.findMatches(text);
            for (int[] match : matches) {
                builder.setSpan(
                    new BackgroundColorSpan(highlightColor),
                    match[0],
                    match[1],
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                builder.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    match[0],
                    match[1],
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        return builder;
    }

    class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvLevel;
        private final TextView tvTag;
        private final TextView tvMessage;
        private final TextView tvTime;
        private final View itemView;

        LogViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
            tvLevel = itemView.findViewById(R.id.tv_log_level);
            tvTag = itemView.findViewById(R.id.tv_log_tag);
            tvMessage = itemView.findViewById(R.id.tv_log_message);
            tvTime = itemView.findViewById(R.id.tv_log_time);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onLogClick(filteredEntries.get(position), position);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && longClickListener != null) {
                    return longClickListener.onLogLongClick(filteredEntries.get(position), position);
                }
                return false;
            });
        }

        void bind(@NonNull LogEntry entry) {
            // 设置日志级别
            tvLevel.setText(entry.getLevel().getShortName());
            tvLevel.setTextColor(getLevelColor(entry.getLevel()));
            tvLevel.setBackgroundColor(getLevelBackgroundColor(entry.getLevel()));

            // 设置标签
            String tag = entry.getTag();
            if (tag.length() > 20) {
                tag = tag.substring(0, 17) + "...";
            }
            tvTag.setText(tag);
            tvTag.setTextColor(secondaryTextColor);

            // 设置消息内容（带高亮）
            String message = entry.getMessage();
            if (message.isEmpty()) {
                message = entry.getDisplayText();
            }
            SpannableStringBuilder highlightedMessage = createHighlightedText(message);
            tvMessage.setText(highlightedMessage);
            tvMessage.setTextColor(textColor);

            // 设置时间
            tvTime.setText(entry.getFormattedTime());
            tvTime.setTextColor(secondaryTextColor);

            // 设置高亮背景
            if (entry.isHighlighted()) {
                itemView.setBackgroundColor((highlightColor & 0x00FFFFFF) | 0x22000000);
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }
}
