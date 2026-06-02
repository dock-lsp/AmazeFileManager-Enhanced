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

package com.amaze.filemanager.ui.activities.texteditor;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code folding manager for text editor
 * Supports folding code blocks like functions, classes, if/for/while statements
 */
public class CodeFoldingManager {

    private static final String TAG = "CodeFoldingManager";
    private static final String ELLIPSIS = " … ";

    /**
     * Represents a foldable region in the code
     */
    public static class FoldRegion {
        public final int startLine;
        public int endLine;
        public final int startPosition;
        public int endPosition;
        public final String type;
        public final String preview;
        public boolean isFolded;
        public int nestingLevel;

        public FoldRegion(int startLine, int startPosition, String type, String preview) {
            this.startLine = startLine;
            this.startPosition = startPosition;
            this.type = type;
            this.preview = preview;
            this.endLine = -1;
            this.endPosition = -1;
            this.isFolded = false;
            this.nestingLevel = 0;
        }

        public int getLineCount() {
            return endLine - startLine + 1;
        }

        public boolean containsLine(int line) {
            return line >= startLine && line <= endLine;
        }
    }

    /**
     * Interface for fold state change callbacks
     */
    public interface OnFoldStateChangeListener {
        void onRegionFolded(FoldRegion region);
        void onRegionUnfolded(FoldRegion region);
    }

    private final List<FoldRegion> foldRegions;
    private final Map<Integer, FoldRegion> lineToRegionMap;
    private OnFoldStateChangeListener listener;
    private SyntaxHighlighter.Language currentLanguage;

    // Patterns for different languages
    private final Map<SyntaxHighlighter.Language, LanguagePatterns> languagePatterns;

    public CodeFoldingManager() {
        this.foldRegions = new ArrayList<>();
        this.lineToRegionMap = new HashMap<>();
        this.languagePatterns = new HashMap<>();
        initializeLanguagePatterns();
    }

    /**
     * Language-specific patterns for code folding
     */
    private static class LanguagePatterns {
        final Pattern blockStartPattern;
        final Pattern blockEndPattern;
        final Pattern[] foldablePatterns;
        final char[] openBrackets;
        final char[] closeBrackets;

        LanguagePatterns(Pattern blockStart, Pattern blockEnd, Pattern[] foldable,
                        char[] openBrackets, char[] closeBrackets) {
            this.blockStartPattern = blockStart;
            this.blockEndPattern = blockEnd;
            this.foldablePatterns = foldable;
            this.openBrackets = openBrackets;
            this.closeBrackets = closeBrackets;
        }
    }

    private void initializeLanguagePatterns() {
        // Java/Kotlin patterns
        Pattern javaBlockStart = Pattern.compile("^\\s*(public|private|protected|static|final|abstract|class|interface|enum|if|else|for|while|do|switch|try|catch|finally|synchronized|void|\\w+)\\s*[^{;]*\\{", Pattern.MULTILINE);
        Pattern javaBlockEnd = Pattern.compile("\\}");
        Pattern[] javaFoldable = {
            Pattern.compile("^\\s*(public|private|protected)?\\s*(static)?\\s*\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{"),
            Pattern.compile("^\\s*(public|private|protected)?\\s*class\\s+\\w+"),
            Pattern.compile("^\\s*(if|else if|else|for|while|do|switch)\\s*\\("),
            Pattern.compile("^\\s*try\\s*\\{|^\\s*catch\\s*\\(|^\\s*finally\\s*\\{")
        };

        LanguagePatterns javaPatterns = new LanguagePatterns(
            javaBlockStart, javaBlockEnd, javaFoldable,
            new char[]{'{', '[', '('}, new char[]{'}', ']', ')'}
        );

        languagePatterns.put(SyntaxHighlighter.Language.JAVA, javaPatterns);
        languagePatterns.put(SyntaxHighlighter.Language.KOTLIN, javaPatterns);

        // C/C++ patterns
        Pattern cBlockStart = Pattern.compile("^\\s*(if|else|for|while|do|switch|class|struct|union|enum|namespace|try|catch|void|int|char|float|double|bool|\\w+)\\s*[^{;]*\\{", Pattern.MULTILINE);
        Pattern cBlockEnd = Pattern.compile("\\}");
        Pattern[] cFoldable = {
            Pattern.compile("^\\s*\\w+\\s+\\w+\\s*\\([^)]*\\)\\s*\\{"),
            Pattern.compile("^\\s*(class|struct|union|enum|namespace)\\s+\\w+"),
            Pattern.compile("^\\s*(if|else|for|while|do|switch)\\s*\\("),
            Pattern.compile("^\\s*#\\s*(if|ifdef|ifndef|elif|else|endif)")
        };

        LanguagePatterns cPatterns = new LanguagePatterns(
            cBlockStart, cBlockEnd, cFoldable,
            new char[]{'{', '[', '('}, new char[]{'}', ']', ')'}
        );

        languagePatterns.put(SyntaxHighlighter.Language.C, cPatterns);
        languagePatterns.put(SyntaxHighlighter.Language.CPP, cPatterns);

        // Python patterns (uses indentation)
        Pattern pythonBlockStart = Pattern.compile("^\\s*(def|class|if|elif|else|for|while|try|except|finally|with)\\s*[:\\(]", Pattern.MULTILINE);
        Pattern pythonBlockEnd = Pattern.compile("^\\S");  // Non-indented line
        Pattern[] pythonFoldable = {
            Pattern.compile("^\\s*def\\s+\\w+\\s*\\("),
            Pattern.compile("^\\s*class\\s+\\w+"),
            Pattern.compile("^\\s*(if|elif|else|for|while|try|except|finally|with)\\s*:")
        };

        LanguagePatterns pythonPatterns = new LanguagePatterns(
            pythonBlockStart, pythonBlockEnd, pythonFoldable,
            new char[]{'(', '[', '{'}, new char[]{')', ']', '}'}
        );

        languagePatterns.put(SyntaxHighlighter.Language.PYTHON, pythonPatterns);

        // JavaScript patterns
        Pattern jsBlockStart = Pattern.compile("^\\s*(function|class|if|else|for|while|do|switch|try|catch|finally|\\w+)\\s*[^{]*\\{", Pattern.MULTILINE);
        Pattern jsBlockEnd = Pattern.compile("\\}");
        Pattern[] jsFoldable = {
            Pattern.compile("^\\s*(function|async\\s+function)\\s*\\w*\\s*\\("),
            Pattern.compile("^\\s*class\\s+\\w+"),
            Pattern.compile("^\\s*(if|else|for|while|do|switch)\\s*\\("),
            Pattern.compile("^\\s*\\w+\\s*\\([^)]*\\)\\s*=>\\s*\\{")
        };

        LanguagePatterns jsPatterns = new LanguagePatterns(
            jsBlockStart, jsBlockEnd, jsFoldable,
            new char[]{'{', '[', '('}, new char[]{'}', ']', ')'}
        );

        languagePatterns.put(SyntaxHighlighter.Language.JAVASCRIPT, jsPatterns);

        // XML/HTML patterns
        Pattern xmlBlockStart = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)[^>]*>");
        Pattern xmlBlockEnd = Pattern.compile("</([a-zA-Z][a-zA-Z0-9]*)>");
        Pattern[] xmlFoldable = {
            Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)[^>]*>.*</\\1>", Pattern.DOTALL)
        };

        LanguagePatterns xmlPatterns = new LanguagePatterns(
            xmlBlockStart, xmlBlockEnd, xmlFoldable,
            new char[]{'<', '[', '('}, new char[]{'>', ']', ')'}
        );

        languagePatterns.put(SyntaxHighlighter.Language.XML, xmlPatterns);
        languagePatterns.put(SyntaxHighlighter.Language.HTML, xmlPatterns);

        // JSON patterns
        Pattern jsonBlockStart = Pattern.compile("[\\[{]");
        Pattern jsonBlockEnd = Pattern.compile("[\\]}]");
        Pattern[] jsonFoldable = {
            Pattern.compile("\"[^\"]+\"\\s*:\\s*\\{", Pattern.MULTILINE),
            Pattern.compile("\"[^\"]+\"\\s*:\\s*\\[", Pattern.MULTILINE)
        };

        LanguagePatterns jsonPatterns = new LanguagePatterns(
            jsonBlockStart, jsonBlockEnd, jsonFoldable,
            new char[]{'{', '['}, new char[]{'}', ']'}
        );

        languagePatterns.put(SyntaxHighlighter.Language.JSON, jsonPatterns);

        // CSS patterns
        Pattern cssBlockStart = Pattern.compile("[.#]?[a-zA-Z][a-zA-Z0-9_-]*\\s*\\{");
        Pattern cssBlockEnd = Pattern.compile("\\}");
        Pattern[] cssFoldable = {
            Pattern.compile("[.#]?[a-zA-Z][a-zA-Z0-9_-]*\\s*\\{"),
            Pattern.compile("@media\\s+[^\\{]+\\{")
        };

        LanguagePatterns cssPatterns = new LanguagePatterns(
            cssBlockStart, cssBlockEnd, cssFoldable,
            new char[]{'{'}, new char[]{'}'}
        );

        languagePatterns.put(SyntaxHighlighter.Language.CSS, cssPatterns);
    }

    /**
     * Set the current language for code folding
     */
    public void setLanguage(SyntaxHighlighter.Language language) {
        this.currentLanguage = language;
        analyzeCode(null); // Clear existing regions
    }

    /**
     * Analyze code and identify foldable regions
     */
    public void analyzeCode(String text) {
        foldRegions.clear();
        lineToRegionMap.clear();

        if (text == null || text.isEmpty() || currentLanguage == null) {
            return;
        }

        LanguagePatterns patterns = languagePatterns.get(currentLanguage);
        if (patterns == null) {
            return;
        }

        String[] lines = text.split("\n", -1);

        // Use bracket matching for most languages
        if (currentLanguage != SyntaxHighlighter.Language.PYTHON) {
            analyzeWithBrackets(lines, patterns);
        } else {
            analyzeWithIndentation(lines, patterns);
        }

        // Build line to region map
        for (FoldRegion region : foldRegions) {
            for (int i = region.startLine; i <= region.endLine; i++) {
                if (!lineToRegionMap.containsKey(i)) {
                    lineToRegionMap.put(i, region);
                }
            }
        }
    }

    /**
     * Analyze code using bracket matching
     */
    private void analyzeWithBrackets(String[] lines, LanguagePatterns patterns) {
        Stack<FoldRegion> stack = new Stack<>();
        int position = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineStart = position;

            // Check for foldable patterns
            for (Pattern pattern : patterns.foldablePatterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    // Check if this line starts a new block
                    int bracketIndex = findOpenBracket(line, patterns.openBrackets);
                    if (bracketIndex != -1) {
                        String preview = line.trim();
                        if (preview.length() > 40) {
                            preview = preview.substring(0, 40) + "…";
                        }

                        FoldRegion region = new FoldRegion(i, lineStart + bracketIndex,
                            getRegionType(line, patterns), preview);
                        region.nestingLevel = stack.size();
                        stack.push(region);
                    }
                    break;
                }
            }

            // Check for closing brackets
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (isCloseBracket(c, patterns.closeBrackets)) {
                    if (!stack.isEmpty()) {
                        FoldRegion region = stack.pop();
                        region.endLine = i;
                        region.endPosition = lineStart + j;

                        // Only add regions with content
                        if (region.endLine > region.startLine) {
                            foldRegions.add(region);
                        }
                    }
                }
            }

            position += line.length() + 1; // +1 for newline
        }

        // Close any remaining open regions
        while (!stack.isEmpty()) {
            FoldRegion region = stack.pop();
            region.endLine = lines.length - 1;
            region.endPosition = position - 1;
            if (region.endLine > region.startLine) {
                foldRegions.add(region);
            }
        }
    }

    /**
     * Analyze Python code using indentation
     */
    private void analyzeWithIndentation(String[] lines, LanguagePatterns patterns) {
        Stack<FoldRegion> stack = new Stack<>();
        int position = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineStart = position;
            int currentIndent = getIndentLevel(line);

            // Check for foldable patterns
            for (Pattern pattern : patterns.foldablePatterns) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String preview = line.trim();
                    if (preview.length() > 40) {
                        preview = preview.substring(0, 40) + "…";
                    }

                    FoldRegion region = new FoldRegion(i, lineStart,
                        getRegionType(line, patterns), preview);
                    region.nestingLevel = stack.size();
                    stack.push(region);
                    break;
                }
            }

            // Check if we should close any regions (dedent)
            while (!stack.isEmpty()) {
                FoldRegion region = stack.peek();
                String regionLine = lines[region.startLine];
                int regionIndent = getIndentLevel(regionLine);

                if (currentIndent <= regionIndent && !line.trim().isEmpty()) {
                    region = stack.pop();
                    region.endLine = i - 1;
                    region.endPosition = lineStart - 1;
                    if (region.endLine > region.startLine) {
                        foldRegions.add(region);
                    }
                } else {
                    break;
                }
            }

            position += line.length() + 1;
        }

        // Close any remaining open regions
        while (!stack.isEmpty()) {
            FoldRegion region = stack.pop();
            region.endLine = lines.length - 1;
            region.endPosition = position - 1;
            if (region.endLine > region.startLine) {
                foldRegions.add(region);
            }
        }
    }

    /**
     * Get the indentation level of a line
     */
    private int getIndentLevel(String line) {
        int indent = 0;
        for (char c : line.toCharArray()) {
            if (c == ' ') {
                indent++;
            } else if (c == '\t') {
                indent += 4;
            } else {
                break;
            }
        }
        return indent;
    }

    /**
     * Find the index of an opening bracket in a line
     */
    private int findOpenBracket(String line, char[] openBrackets) {
        for (int i = 0; i < line.length(); i++) {
            for (char bracket : openBrackets) {
                if (line.charAt(i) == bracket) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Check if character is a closing bracket
     */
    private boolean isCloseBracket(char c, char[] closeBrackets) {
        for (char bracket : closeBrackets) {
            if (c == bracket) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determine the type of a fold region
     */
    private String getRegionType(String line, LanguagePatterns patterns) {
        String trimmed = line.trim().toLowerCase();

        if (trimmed.startsWith("class ") || trimmed.startsWith("class\t")) {
            return "class";
        } else if (trimmed.startsWith("def ") || trimmed.startsWith("def\t") ||
                   trimmed.contains(" function") || trimmed.matches("^\\w+\\s*\\(")) {
            return "function";
        } else if (trimmed.startsWith("if ") || trimmed.startsWith("if\t")) {
            return "if";
        } else if (trimmed.startsWith("else if") || trimmed.startsWith("elif ")) {
            return "else-if";
        } else if (trimmed.startsWith("else")) {
            return "else";
        } else if (trimmed.startsWith("for ") || trimmed.startsWith("for\t")) {
            return "for";
        } else if (trimmed.startsWith("while ") || trimmed.startsWith("while\t")) {
            return "while";
        } else if (trimmed.startsWith("do ") || trimmed.startsWith("do\t")) {
            return "do-while";
        } else if (trimmed.startsWith("switch ") || trimmed.startsWith("switch\t")) {
            return "switch";
        } else if (trimmed.startsWith("try") || trimmed.startsWith("try ")) {
            return "try";
        } else if (trimmed.startsWith("catch") || trimmed.startsWith("except")) {
            return "catch";
        } else if (trimmed.startsWith("finally")) {
            return "finally";
        } else if (trimmed.startsWith("interface ") || trimmed.startsWith("interface\t")) {
            return "interface";
        } else if (trimmed.startsWith("enum ") || trimmed.startsWith("enum\t")) {
            return "enum";
        } else if (trimmed.startsWith("struct ") || trimmed.startsWith("struct\t")) {
            return "struct";
        } else if (trimmed.startsWith("namespace ") || trimmed.startsWith("namespace\t")) {
            return "namespace";
        } else if (trimmed.startsWith("#")) {
            return "preprocessor";
        } else if (trimmed.startsWith("<")) {
            return "element";
        } else if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return "block";
        }

        return "region";
    }

    /**
     * Get all fold regions
     */
    public List<FoldRegion> getFoldRegions() {
        return new ArrayList<>(foldRegions);
    }

    /**
     * Get fold region for a specific line
     */
    @Nullable
    public FoldRegion getRegionForLine(int line) {
        return lineToRegionMap.get(line);
    }

    /**
     * Fold a specific region
     */
    public void foldRegion(FoldRegion region) {
        if (!region.isFolded) {
            region.isFolded = true;
            if (listener != null) {
                listener.onRegionFolded(region);
            }
        }
    }

    /**
     * Unfold a specific region
     */
    public void unfoldRegion(FoldRegion region) {
        if (region.isFolded) {
            region.isFolded = false;
            if (listener != null) {
                listener.onRegionUnfolded(region);
            }
        }
    }

    /**
     * Toggle fold state of a region
     */
    public void toggleFold(FoldRegion region) {
        if (region.isFolded) {
            unfoldRegion(region);
        } else {
            foldRegion(region);
        }
    }

    /**
     * Fold all regions
     */
    public void foldAll() {
        for (FoldRegion region : foldRegions) {
            foldRegion(region);
        }
    }

    /**
     * Unfold all regions
     */
    public void unfoldAll() {
        for (FoldRegion region : foldRegions) {
            unfoldRegion(region);
        }
    }

    /**
     * Fold regions by type
     */
    public void foldByType(String type) {
        for (FoldRegion region : foldRegions) {
            if (region.type.equals(type)) {
                foldRegion(region);
            }
        }
    }

    /**
     * Collapse all regions to a specific level
     */
    public void collapseToLevel(int level) {
        for (FoldRegion region : foldRegions) {
            if (region.nestingLevel >= level) {
                foldRegion(region);
            } else {
                unfoldRegion(region);
            }
        }
    }

    /**
     * Check if a line is in a folded region
     */
    public boolean isLineFolded(int line) {
        for (FoldRegion region : foldRegions) {
            if (region.isFolded && region.containsLine(line) && line > region.startLine) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the visible line number accounting for folded regions
     */
    public int getVisibleLineNumber(int actualLine) {
        int visibleLine = 0;
        for (int i = 0; i < actualLine; i++) {
            if (!isLineFolded(i)) {
                visibleLine++;
            }
        }
        return visibleLine;
    }

    /**
     * Get the actual line number from a visible line number
     */
    public int getActualLineNumber(int visibleLine) {
        int actualLine = 0;
        int currentVisible = 0;

        while (currentVisible < visibleLine) {
            actualLine++;
            if (!isLineFolded(actualLine)) {
                currentVisible++;
            }
        }

        return actualLine;
    }

    /**
     * Get folded text representation
     */
    public CharSequence getFoldedText(FoldRegion region) {
        if (!region.isFolded) {
            return null;
        }

        return region.preview + ELLIPSIS + "}";
    }

    /**
     * Set fold state change listener
     */
    public void setOnFoldStateChangeListener(OnFoldStateChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Get the number of foldable regions
     */
    public int getRegionCount() {
        return foldRegions.size();
    }

    /**
     * Get the number of folded regions
     */
    public int getFoldedCount() {
        int count = 0;
        for (FoldRegion region : foldRegions) {
            if (region.isFolded) {
                count++;
            }
        }
        return count;
    }

    /**
     * Check if code folding is available for current language
     */
    public boolean isFoldingAvailable() {
        return currentLanguage != null && languagePatterns.containsKey(currentLanguage);
    }

    /**
     * Clear all fold regions
     */
    public void clear() {
        foldRegions.clear();
        lineToRegionMap.clear();
    }
}