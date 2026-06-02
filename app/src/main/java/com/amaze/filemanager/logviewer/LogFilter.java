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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 日志过滤器类，支持多种过滤条件
 */
public class LogFilter {

    private LogEntry.LogLevel minLevel = LogEntry.LogLevel.VERBOSE;
    private String searchQuery = "";
    private String tagFilter = "";
    private boolean caseSensitive = false;
    private boolean useRegex = false;
    private boolean showOnlyHighlighted = false;
    private Pattern searchPattern;
    private boolean patternValid = true;

    public LogFilter() {
        updateSearchPattern();
    }

    /**
     * 设置最小日志级别
     */
    public void setMinLevel(@NonNull LogEntry.LogLevel level) {
        this.minLevel = level;
    }

    @NonNull
    public LogEntry.LogLevel getMinLevel() {
        return minLevel;
    }

    /**
     * 设置搜索关键词
     */
    public void setSearchQuery(@Nullable String query) {
        this.searchQuery = query != null ? query : "";
        updateSearchPattern();
    }

    @NonNull
    public String getSearchQuery() {
        return searchQuery;
    }

    /**
     * 设置标签过滤
     */
    public void setTagFilter(@Nullable String tag) {
        this.tagFilter = tag != null ? tag : "";
    }

    @NonNull
    public String getTagFilter() {
        return tagFilter;
    }

    /**
     * 设置是否区分大小写
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        updateSearchPattern();
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    /**
     * 设置是否使用正则表达式
     */
    public void setUseRegex(boolean useRegex) {
        this.useRegex = useRegex;
        updateSearchPattern();
    }

    public boolean isUseRegex() {
        return useRegex;
    }

    /**
     * 设置是否只显示高亮项
     */
    public void setShowOnlyHighlighted(boolean showOnlyHighlighted) {
        this.showOnlyHighlighted = showOnlyHighlighted;
    }

    public boolean isShowOnlyHighlighted() {
        return showOnlyHighlighted;
    }

    /**
     * 检查搜索模式是否有效（仅在正则模式下）
     */
    public boolean isPatternValid() {
        return patternValid;
    }

    /**
     * 更新搜索正则表达式
     */
    private void updateSearchPattern() {
        if (searchQuery.isEmpty()) {
            searchPattern = null;
            patternValid = true;
            return;
        }

        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
            if (useRegex) {
                searchPattern = Pattern.compile(searchQuery, flags);
            } else {
                // 转义特殊字符，进行普通文本搜索
                String escaped = Pattern.quote(searchQuery);
                searchPattern = Pattern.compile(escaped, flags);
            }
            patternValid = true;
        } catch (PatternSyntaxException e) {
            searchPattern = null;
            patternValid = false;
        }
    }

    /**
     * 检查日志条目是否匹配过滤条件
     */
    public boolean matches(@NonNull LogEntry entry) {
        // 检查日志级别
        if (!entry.getLevel().meetsLevel(minLevel)) {
            return false;
        }

        // 检查标签过滤
        if (!tagFilter.isEmpty()) {
            String entryTag = caseSensitive ? entry.getTag() : entry.getTag().toLowerCase();
            String filterTag = caseSensitive ? tagFilter : tagFilter.toLowerCase();
            if (!entryTag.contains(filterTag)) {
                return false;
            }
        }

        // 检查是否只显示高亮项
        if (showOnlyHighlighted && !entry.isHighlighted()) {
            return false;
        }

        // 检查搜索关键词
        if (searchPattern != null && patternValid) {
            String textToSearch = entry.getDisplayText();
            if (!caseSensitive) {
                textToSearch = textToSearch.toLowerCase();
            }
            return searchPattern.matcher(textToSearch).find();
        }

        return true;
    }

    /**
     * 过滤日志列表
     */
    @NonNull
    public List<LogEntry> filter(@NonNull List<LogEntry> entries) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : entries) {
            if (matches(entry)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 在文本中查找搜索关键词的位置（用于高亮显示）
     * @return 匹配位置的起始索引数组
     */
    @NonNull
    public List<int[]> findMatches(@NonNull String text) {
        List<int[]> matches = new ArrayList<>();
        if (searchPattern == null || !patternValid || searchQuery.isEmpty()) {
            return matches;
        }

        java.util.regex.Matcher matcher = searchPattern.matcher(text);
        while (matcher.find()) {
            matches.add(new int[]{matcher.start(), matcher.end()});
        }
        return matches;
    }

    /**
     * 检查是否有活动的过滤条件
     */
    public boolean hasActiveFilters() {
        return minLevel != LogEntry.LogLevel.VERBOSE
                || !searchQuery.isEmpty()
                || !tagFilter.isEmpty()
                || showOnlyHighlighted;
    }

    /**
     * 重置所有过滤条件
     */
    public void reset() {
        minLevel = LogEntry.LogLevel.VERBOSE;
        searchQuery = "";
        tagFilter = "";
        caseSensitive = false;
        useRegex = false;
        showOnlyHighlighted = false;
        updateSearchPattern();
    }

    @Override
    @NonNull
    public String toString() {
        return "LogFilter{" +
                "minLevel=" + minLevel +
                ", searchQuery='" + searchQuery + '\'' +
                ", tagFilter='" + tagFilter + '\'' +
                ", caseSensitive=" + caseSensitive +
                ", useRegex=" + useRegex +
                ", showOnlyHighlighted=" + showOnlyHighlighted +
                '}';
    }
}
