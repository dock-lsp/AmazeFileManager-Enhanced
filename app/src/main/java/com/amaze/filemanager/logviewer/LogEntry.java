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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志条目数据类，用于存储单条日志信息
 */
public class LogEntry {

    /**
     * 日志级别枚举
     */
    public enum LogLevel {
        VERBOSE("V", 0),
        DEBUG("D", 1),
        INFO("I", 2),
        WARN("W", 3),
        ERROR("E", 4),
        FATAL("F", 5),
        ASSERT("A", 6),
        UNKNOWN("?", -1);

        private final String shortName;
        private final int priority;

        LogLevel(String shortName, int priority) {
            this.shortName = shortName;
            this.priority = priority;
        }

        public String getShortName() {
            return shortName;
        }

        public int getPriority() {
            return priority;
        }

        public static LogLevel fromShortName(String shortName) {
            if (shortName == null || shortName.isEmpty()) {
                return UNKNOWN;
            }
            String upper = shortName.toUpperCase();
            for (LogLevel level : values()) {
                if (level.shortName.equals(upper)) {
                    return level;
                }
            }
            return UNKNOWN;
        }

        /**
         * 检查当前日志级别是否满足最低级别要求
         */
        public boolean meetsLevel(LogLevel minimumLevel) {
            if (minimumLevel == null || this == UNKNOWN) {
                return true;
            }
            return this.priority >= minimumLevel.priority;
        }
    }

    // 日志解析正则表达式
    private static final Pattern LOG_PATTERN = Pattern.compile(
        "^(\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+" +  // 时间戳
        "(\\w+)\\s+" +  // PID
        "(\\w+)\\s+" +  // TID
        "([VDIWEF])\\s+" +  // 日志级别
        "([^:]+):\\s*" +  // 标签
        "(.*)$"  // 消息内容
    );

    // 简化版正则（用于解析不同格式的日志）
    private static final Pattern SIMPLE_LOG_PATTERN = Pattern.compile(
        "^.*([VDIWEF])\\s*([^:]+):\\s*(.*)$"
    );

    private final long timestamp;
    private final String rawLog;
    private final LogLevel level;
    private final String tag;
    private final String message;
    private final String pid;
    private final String tid;
    private boolean isHighlighted;

    public LogEntry(@NonNull String rawLog) {
        this.rawLog = rawLog;
        this.timestamp = System.currentTimeMillis();
        this.isHighlighted = false;

        LogEntry parsed = parseLog(rawLog);
        this.level = parsed.level;
        this.tag = parsed.tag;
        this.message = parsed.message;
        this.pid = parsed.pid;
        this.tid = parsed.tid;
    }

    public LogEntry(long timestamp, LogLevel level, String tag, String message) {
        this.timestamp = timestamp;
        this.level = level;
        this.tag = tag;
        this.message = message;
        this.pid = "";
        this.tid = "";
        this.rawLog = formatRawLog();
        this.isHighlighted = false;
    }

    private LogEntry() {
        this.timestamp = System.currentTimeMillis();
        this.level = LogLevel.UNKNOWN;
        this.tag = "";
        this.message = "";
        this.pid = "";
        this.tid = "";
        this.rawLog = "";
        this.isHighlighted = false;
    }

    private String formatRawLog() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());
        String timeStr = sdf.format(new Date(timestamp));
        return String.format("%s %s %s: %s", timeStr, level.getShortName(), tag, message);
    }

    /**
     * 解析日志字符串
     */
    private static LogEntry parseLog(String rawLog) {
        if (rawLog == null || rawLog.isEmpty()) {
            return new LogEntry();
        }

        Matcher matcher = LOG_PATTERN.matcher(rawLog);
        if (matcher.matches()) {
            LogEntry entry = new LogEntry();
            entry.level = LogLevel.fromShortName(matcher.group(4));
            entry.tag = matcher.group(5);
            entry.message = matcher.group(6);
            entry.pid = matcher.group(2);
            entry.tid = matcher.group(3);
            return entry;
        }

        // 尝试简化版解析
        Matcher simpleMatcher = SIMPLE_LOG_PATTERN.matcher(rawLog);
        if (simpleMatcher.find()) {
            LogEntry entry = new LogEntry();
            entry.level = LogLevel.fromShortName(simpleMatcher.group(1));
            entry.tag = simpleMatcher.group(2);
            entry.message = simpleMatcher.group(3);
            entry.pid = "";
            entry.tid = "";
            return entry;
        }

        // 无法解析，将整行作为消息
        LogEntry entry = new LogEntry();
        entry.level = LogLevel.UNKNOWN;
        entry.tag = "Unknown";
        entry.message = rawLog;
        entry.pid = "";
        entry.tid = "";
        return entry;
    }

    // Getters
    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public String getRawLog() {
        return rawLog;
    }

    @NonNull
    public LogLevel getLevel() {
        return level;
    }

    @NonNull
    public String getTag() {
        return tag != null ? tag : "";
    }

    @NonNull
    public String getMessage() {
        return message != null ? message : "";
    }

    @NonNull
    public String getPid() {
        return pid != null ? pid : "";
    }

    @NonNull
    public String getTid() {
        return tid != null ? tid : "";
    }

    public boolean isHighlighted() {
        return isHighlighted;
    }

    public void setHighlighted(boolean highlighted) {
        isHighlighted = highlighted;
    }

    /**
     * 获取格式化的时间字符串
     */
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    /**
     * 获取完整的日志内容（用于显示）
     */
    public String getDisplayText() {
        if (rawLog != null && !rawLog.isEmpty()) {
            return rawLog;
        }
        return formatRawLog();
    }

    @Override
    @NonNull
    public String toString() {
        return "LogEntry{" +
                "level=" + level +
                ", tag='" + tag + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
