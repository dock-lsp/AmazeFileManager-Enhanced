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

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax highlighter for code editor supporting multiple languages
 */
public class SyntaxHighlighter {

    private static final String TAG = "SyntaxHighlighter";

    // Language types
    public enum Language {
        JAVA, KOTLIN, PYTHON, JAVASCRIPT, HTML, CSS, XML, JSON, C, CPP, MARKDOWN, UNKNOWN
    }

    // Color scheme
    public static class ColorScheme {
        public int keywordColor = Color.parseColor("#CC7832");      // Orange
        public int stringColor = Color.parseColor("#6A8759");       // Green
        public int commentColor = Color.parseColor("#808080");      // Gray
        public int numberColor = Color.parseColor("#6897BB");       // Blue
        public int functionColor = Color.parseColor("#FFC66D");     // Yellow
        public int classColor = Color.parseColor("#A9B7C6");        // Light gray
        public int operatorColor = Color.parseColor("#A9B7C6");     // Light gray
        public int tagColor = Color.parseColor("#E8BF6A");          // Gold
        public int attributeColor = Color.parseColor("#BABABA");    // Light gray
        public int bracketMatchColor = Color.parseColor("#3B514D"); // Dark green

        public ColorScheme() {}

        public ColorScheme(int keyword, int string, int comment, int number,
                          int function, int classColor, int operator, int tag, int attribute) {
            this.keywordColor = keyword;
            this.stringColor = string;
            this.commentColor = comment;
            this.numberColor = number;
            this.functionColor = function;
            this.classColor = classColor;
            this.operatorColor = operator;
            this.tagColor = tag;
            this.attributeColor = attribute;
        }
    }

    private ColorScheme colorScheme;
    private Language currentLanguage;
    private final Map<Language, List<Pattern>> patternsMap;
    private final Map<Language, Map<Pattern, Integer>> colorMap;

    // Bracket pairs for matching
    private static final Map<Character, Character> BRACKET_PAIRS = new HashMap<>();
    static {
        BRACKET_PAIRS.put('(', ')');
        BRACKET_PAIRS.put('[', ']');
        BRACKET_PAIRS.put('{', '}');
        BRACKET_PAIRS.put('<', '>');
    }

    public SyntaxHighlighter() {
        this.colorScheme = new ColorScheme();
        this.patternsMap = new HashMap<>();
        this.colorMap = new HashMap<>();
        initializePatterns();
    }

    public SyntaxHighlighter(ColorScheme scheme) {
        this.colorScheme = scheme;
        this.patternsMap = new HashMap<>();
        this.colorMap = new HashMap<>();
        initializePatterns();
    }

    /**
     * Initialize regex patterns for all supported languages
     */
    private void initializePatterns() {
        // Java patterns
        List<Pattern> javaPatterns = new ArrayList<>();
        Map<Pattern, Integer> javaColors = new HashMap<>();

        String javaKeywords = "\\b(abstract|assert|boolean|break|byte|case|catch|char|class|const|" +
                "continue|default|do|double|else|enum|extends|final|finally|float|for|goto|if|" +
                "implements|import|instanceof|int|interface|long|native|new|package|private|" +
                "protected|public|return|short|static|strictfp|super|switch|synchronized|this|" +
                "throw|throws|transient|try|void|volatile|while|true|false|null)\\b";
        Pattern javaKeywordPattern = Pattern.compile(javaKeywords);
        javaPatterns.add(javaKeywordPattern);
        javaColors.put(javaKeywordPattern, colorScheme.keywordColor);

        Pattern javaStringPattern = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'");
        javaPatterns.add(javaStringPattern);
        javaColors.put(javaStringPattern, colorScheme.stringColor);

        Pattern javaCommentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/");
        javaPatterns.add(javaCommentPattern);
        javaColors.put(javaCommentPattern, colorScheme.commentColor);

        Pattern javaNumberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdDlL]?\\b");
        javaPatterns.add(javaNumberPattern);
        javaColors.put(javaNumberPattern, colorScheme.numberColor);

        Pattern javaClassPattern = Pattern.compile("\\b[A-Z][a-zA-Z0-9_]*\\b");
        javaPatterns.add(javaClassPattern);
        javaColors.put(javaClassPattern, colorScheme.classColor);

        patternsMap.put(Language.JAVA, javaPatterns);
        colorMap.put(Language.JAVA, javaColors);

        // Kotlin patterns
        List<Pattern> kotlinPatterns = new ArrayList<>();
        Map<Pattern, Integer> kotlinColors = new HashMap<>();

        String kotlinKeywords = "\\b(abstract|actual|annotation|as|as\\?|break|by|catch|class|companion|" +
                "const|constructor|continue|crossinline|data|delegate|do|dynamic|else|enum|" +
                "expect|external|false|field|file|final|finally|for|fun|get|if|import|in|!in|" +
                "inline|inner|interface|internal|is|!is|lateinit|noinline|null|object|open|" +
                "operator|out|override|package|param|private|property|protected|public|receiver|" +
                "reified|return|sealed|set|setparam|super|suspend|tailrec|this|throw|true|try|" +
                "typealias|typeof|val|var|vararg|when|where|while)\\b";
        Pattern kotlinKeywordPattern = Pattern.compile(kotlinKeywords);
        kotlinPatterns.add(kotlinKeywordPattern);
        kotlinColors.put(kotlinKeywordPattern, colorScheme.keywordColor);

        Pattern kotlinStringPattern = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\"(\\\\.|[^\"\\\\])*\"|'[^']*'");
        kotlinPatterns.add(kotlinStringPattern);
        kotlinColors.put(kotlinStringPattern, colorScheme.stringColor);

        Pattern kotlinCommentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/");
        kotlinPatterns.add(kotlinCommentPattern);
        kotlinColors.put(kotlinCommentPattern, colorScheme.commentColor);

        Pattern kotlinNumberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFdD]?\\b");
        kotlinPatterns.add(kotlinNumberPattern);
        kotlinColors.put(kotlinNumberPattern, colorScheme.numberColor);

        patternsMap.put(Language.KOTLIN, kotlinPatterns);
        colorMap.put(Language.KOTLIN, kotlinColors);

        // Python patterns
        List<Pattern> pythonPatterns = new ArrayList<>();
        Map<Pattern, Integer> pythonColors = new HashMap<>();

        String pythonKeywords = "\\b(and|as|assert|break|class|continue|def|del|elif|else|except|" +
                "exec|finally|for|from|global|if|import|in|is|lambda|not|or|pass|print|" +
                "raise|return|try|while|with|yield|True|False|None)\\b";
        Pattern pythonKeywordPattern = Pattern.compile(pythonKeywords);
        pythonPatterns.add(pythonKeywordPattern);
        pythonColors.put(pythonKeywordPattern, colorScheme.keywordColor);

        Pattern pythonStringPattern = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(\\\\.|[^\"\\\\])*\"|'[^'\\\\]*'");
        pythonPatterns.add(pythonStringPattern);
        pythonColors.put(pythonStringPattern, colorScheme.stringColor);

        Pattern pythonCommentPattern = Pattern.compile("#.*");
        pythonPatterns.add(pythonCommentPattern);
        pythonColors.put(pythonCommentPattern, colorScheme.commentColor);

        Pattern pythonNumberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[jJ]?\\b");
        pythonPatterns.add(pythonNumberPattern);
        pythonColors.put(pythonNumberPattern, colorScheme.numberColor);

        Pattern pythonFunctionPattern = Pattern.compile("\\bdef\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
        pythonPatterns.add(pythonFunctionPattern);
        pythonColors.put(pythonFunctionPattern, colorScheme.functionColor);

        patternsMap.put(Language.PYTHON, pythonPatterns);
        colorMap.put(Language.PYTHON, pythonColors);

        // JavaScript patterns
        List<Pattern> jsPatterns = new ArrayList<>();
        Map<Pattern, Integer> jsColors = new HashMap<>();

        String jsKeywords = "\\b(break|case|catch|class|const|continue|debugger|default|delete|do|" +
                "else|export|extends|false|finally|for|function|if|import|in|instanceof|new|" +
                "null|return|super|switch|this|throw|true|try|typeof|var|void|while|with|" +
                "let|static|yield|await|async|of)\\b";
        Pattern jsKeywordPattern = Pattern.compile(jsKeywords);
        jsPatterns.add(jsKeywordPattern);
        jsColors.put(jsKeywordPattern, colorScheme.keywordColor);

        Pattern jsStringPattern = Pattern.compile("`(\\\\.|[^`\\\\])*`|\"(\\\\.|[^\"\\\\])*\"|'[^'\\\\]*'");
        jsPatterns.add(jsStringPattern);
        jsColors.put(jsStringPattern, colorScheme.stringColor);

        Pattern jsCommentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/");
        jsPatterns.add(jsCommentPattern);
        jsColors.put(jsCommentPattern, colorScheme.commentColor);

        Pattern jsNumberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b");
        jsPatterns.add(jsNumberPattern);
        jsColors.put(jsNumberPattern, colorScheme.numberColor);

        Pattern jsFunctionPattern = Pattern.compile("\\bfunction\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
        jsPatterns.add(jsFunctionPattern);
        jsColors.put(jsFunctionPattern, colorScheme.functionColor);

        patternsMap.put(Language.JAVASCRIPT, jsPatterns);
        colorMap.put(Language.JAVASCRIPT, jsColors);

        // HTML patterns
        List<Pattern> htmlPatterns = new ArrayList<>();
        Map<Pattern, Integer> htmlColors = new HashMap<>();

        Pattern htmlTagPattern = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9]*");
        htmlPatterns.add(htmlTagPattern);
        htmlColors.put(htmlTagPattern, colorScheme.tagColor);

        Pattern htmlAttrPattern = Pattern.compile("\\s[a-zA-Z-]+=");
        htmlPatterns.add(htmlAttrPattern);
        htmlColors.put(htmlAttrPattern, colorScheme.attributeColor);

        Pattern htmlStringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'");
        htmlPatterns.add(htmlStringPattern);
        htmlColors.put(htmlStringPattern, colorScheme.stringColor);

        Pattern htmlCommentPattern = Pattern.compile("<!--[\\s\\S]*?-->");
        htmlPatterns.add(htmlCommentPattern);
        htmlColors.put(htmlCommentPattern, colorScheme.commentColor);

        patternsMap.put(Language.HTML, htmlPatterns);
        colorMap.put(Language.HTML, htmlColors);

        // CSS patterns
        List<Pattern> cssPatterns = new ArrayList<>();
        Map<Pattern, Integer> cssColors = new HashMap<>();

        String cssKeywords = "\\b(@media|@import|@charset|@font-face|@keyframes|@supports)\\b";
        Pattern cssKeywordPattern = Pattern.compile(cssKeywords);
        cssPatterns.add(cssKeywordPattern);
        cssColors.put(cssKeywordPattern, colorScheme.keywordColor);

        Pattern cssSelectorPattern = Pattern.compile("[.#][a-zA-Z][a-zA-Z0-9_-]*");
        cssPatterns.add(cssSelectorPattern);
        cssColors.put(cssSelectorPattern, colorScheme.classColor);

        Pattern cssPropertyPattern = Pattern.compile("\\b[a-z-]+(?=\\s*:)");
        cssPatterns.add(cssPropertyPattern);
        cssColors.put(cssPropertyPattern, colorScheme.attributeColor);

        Pattern cssStringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'");
        cssPatterns.add(cssStringPattern);
        cssColors.put(cssStringPattern, colorScheme.stringColor);

        Pattern cssCommentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/");
        cssPatterns.add(cssCommentPattern);
        cssColors.put(cssCommentPattern, colorScheme.commentColor);

        Pattern cssNumberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|pt|pc|in|cm|mm|ex|ch|vmin|vmax)?\\b");
        cssPatterns.add(cssNumberPattern);
        cssColors.put(cssNumberPattern, colorScheme.numberColor);

        Pattern cssColorPattern = Pattern.compile("#[a-fA-F0-9]{3,8}\\b");
        cssPatterns.add(cssColorPattern);
        cssColors.put(cssColorPattern, colorScheme.numberColor);

        patternsMap.put(Language.CSS, cssPatterns);
        colorMap.put(Language.CSS, cssColors);

        // XML patterns
        List<Pattern> xmlPatterns = new ArrayList<>();
        Map<Pattern, Integer> xmlColors = new HashMap<>();

        Pattern xmlTagPattern = Pattern.compile("</?[a-zA-Z][a-zA-Z0-9:._-]*");
        xmlPatterns.add(xmlTagPattern);
        xmlColors.put(xmlTagPattern, colorScheme.tagColor);

        Pattern xmlAttrPattern = Pattern.compile("\\s[a-zA-Z_:][a-zA-Z0-9:._-]*=");
        xmlPatterns.add(xmlAttrPattern);
        xmlColors.put(xmlAttrPattern, colorScheme.attributeColor);

        Pattern xmlStringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'");
        xmlPatterns.add(xmlStringPattern);
        xmlColors.put(xmlStringPattern, colorScheme.stringColor);

        Pattern xmlCommentPattern = Pattern.compile("<!--[\\s\\S]*?-->");
        xmlPatterns.add(xmlCommentPattern);
        xmlColors.put(xmlCommentPattern, colorScheme.commentColor);

        Pattern xmlPrologPattern = Pattern.compile("<\\?xml[^?]*\\?>");
        xmlPatterns.add(xmlPrologPattern);
        xmlColors.put(xmlPrologPattern, colorScheme.keywordColor);

        patternsMap.put(Language.XML, xmlPatterns);
        colorMap.put(Language.XML, xmlColors);

        // JSON patterns
        List<Pattern> jsonPatterns = new ArrayList<>();
        Map<Pattern, Integer> jsonColors = new HashMap<>();

        Pattern jsonKeyPattern = Pattern.compile("\"[^\"]+\"(?=\\s*:)");
        jsonPatterns.add(jsonKeyPattern);
        jsonColors.put(jsonKeyPattern, colorScheme.attributeColor);

        Pattern jsonStringPattern = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"");
        jsonPatterns.add(jsonStringPattern);
        jsonColors.put(jsonStringPattern, colorScheme.stringColor);

        Pattern jsonNumberPattern = Pattern.compile("\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b");
        jsonPatterns.add(jsonNumberPattern);
        jsonColors.put(jsonNumberPattern, colorScheme.numberColor);

        Pattern jsonBoolPattern = Pattern.compile("\\b(true|false|null)\\b");
        jsonPatterns.add(jsonBoolPattern);
        jsonColors.put(jsonBoolPattern, colorScheme.keywordColor);

        patternsMap.put(Language.JSON, jsonPatterns);
        colorMap.put(Language.JSON, jsonColors);

        // C/C++ patterns
        List<Pattern> cPatterns = new ArrayList<>();
        Map<Pattern, Integer> cColors = new HashMap<>();

        String cKeywords = "\\b(auto|break|case|char|const|continue|default|do|double|else|enum|" +
                "extern|float|for|goto|if|inline|int|long|register|restrict|return|short|signed|" +
                "sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while|" +
                "_Alignas|_Alignof|_Atomic|_Bool|_Complex|_Generic|_Imaginary|_Noreturn|" +
                "_Static_assert|_Thread_local|class|public|private|protected|virtual|template|" +
                "typename|namespace|using|new|delete|try|catch|throw|const_cast|dynamic_cast|" +
                "reinterpret_cast|static_cast|explicit|mutable|friend|operator|this|bool|" +
                "wchar_t|nullptr|override|final|noexcept|constexpr|decltype|thread_local|static_assert)\\b";
        Pattern cKeywordPattern = Pattern.compile(cKeywords);
        cPatterns.add(cKeywordPattern);
        cColors.put(cKeywordPattern, colorScheme.keywordColor);

        Pattern cStringPattern = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'[^'\\\\]*'");
        cPatterns.add(cStringPattern);
        cColors.put(cStringPattern, colorScheme.stringColor);

        Pattern cCommentPattern = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/");
        cPatterns.add(cCommentPattern);
        cColors.put(cCommentPattern, colorScheme.commentColor);

        Pattern cNumberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?([eE][+-]?\\d+)?[uUlLfF]?\\b|\\b0[xX][a-fA-F0-9]+\\b");
        cPatterns.add(cNumberPattern);
        cColors.put(cNumberPattern, colorScheme.numberColor);

        Pattern cPreprocPattern = Pattern.compile("#[\\s]*[a-zA-Z]+");
        cPatterns.add(cPreprocPattern);
        cColors.put(cPreprocPattern, colorScheme.functionColor);

        patternsMap.put(Language.C, cPatterns);
        colorMap.put(Language.C, cColors);
        patternsMap.put(Language.CPP, cPatterns);
        colorMap.put(Language.CPP, cColors);

        // Markdown patterns
        List<Pattern> mdPatterns = new ArrayList<>();
        Map<Pattern, Integer> mdColors = new HashMap<>();

        Pattern mdHeaderPattern = Pattern.compile("^#{1,6}\\s.*$", Pattern.MULTILINE);
        mdPatterns.add(mdHeaderPattern);
        mdColors.put(mdHeaderPattern, colorScheme.keywordColor);

        Pattern mdBoldPattern = Pattern.compile("\\*\\*[^*]+\\*\\*|__[^_]+__");
        mdPatterns.add(mdBoldPattern);
        mdColors.put(mdBoldPattern, colorScheme.classColor);

        Pattern mdItalicPattern = Pattern.compile("\\*[^*]+\\*|_[^_]+_");
        mdPatterns.add(mdItalicPattern);
        mdColors.put(mdItalicPattern, colorScheme.attributeColor);

        Pattern mdCodePattern = Pattern.compile("`[^`]+`|```[\\s\\S]*?```");
        mdPatterns.add(mdCodePattern);
        mdColors.put(mdCodePattern, colorScheme.stringColor);

        Pattern mdLinkPattern = Pattern.compile("\\[[^\\]]+\\]\\([^)]+\\)");
        mdPatterns.add(mdLinkPattern);
        mdColors.put(mdLinkPattern, colorScheme.numberColor);

        Pattern mdListPattern = Pattern.compile("^[\\s]*[-*+]\\s", Pattern.MULTILINE);
        mdPatterns.add(mdListPattern);
        mdColors.put(mdListPattern, colorScheme.tagColor);

        patternsMap.put(Language.MARKDOWN, mdPatterns);
        colorMap.put(Language.MARKDOWN, mdColors);
    }

    /**
     * Detect language from file extension
     */
    public static Language detectLanguage(String filename) {
        if (filename == null) return Language.UNKNOWN;

        String lower = filename.toLowerCase();
        if (lower.endsWith(".java")) return Language.JAVA;
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return Language.KOTLIN;
        if (lower.endsWith(".py")) return Language.PYTHON;
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return Language.JAVASCRIPT;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Language.HTML;
        if (lower.endsWith(".css")) return Language.CSS;
        if (lower.endsWith(".xml")) return Language.XML;
        if (lower.endsWith(".json")) return Language.JSON;
        if (lower.endsWith(".c")) return Language.C;
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx") ||
            lower.endsWith(".h") || lower.endsWith(".hpp")) return Language.CPP;
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return Language.MARKDOWN;

        return Language.UNKNOWN;
    }

    /**
     * Set the current language for highlighting
     */
    public void setLanguage(Language language) {
        this.currentLanguage = language;
    }

    /**
     * Set the current language from filename
     */
    public void setLanguage(String filename) {
        this.currentLanguage = detectLanguage(filename);
    }

    /**
     * Get current language
     */
    public Language getCurrentLanguage() {
        return currentLanguage;
    }

    /**
     * Apply syntax highlighting to text
     */
    public Spannable applyHighlighting(String text) {
        if (text == null || currentLanguage == null || currentLanguage == Language.UNKNOWN) {
            return new SpannableStringBuilder(text);
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(text);

        List<Pattern> patterns = patternsMap.get(currentLanguage);
        Map<Pattern, Integer> colors = colorMap.get(currentLanguage);

        if (patterns == null || colors == null) {
            return spannable;
        }

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            Integer color = colors.get(pattern);

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                // Avoid overlapping spans by checking if this region already has a span
                ForegroundColorSpan[] existingSpans = spannable.getSpans(start, end, ForegroundColorSpan.class);
                if (existingSpans.length == 0) {
                    spannable.setSpan(new ForegroundColorSpan(color), start, end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        return spannable;
    }

    /**
     * Apply highlighting to an Editable (for use with EditText)
     */
    public void applyHighlightingToEditable(Editable editable) {
        if (editable == null || currentLanguage == null || currentLanguage == Language.UNKNOWN) {
            return;
        }

        // Remove existing foreground color spans
        ForegroundColorSpan[] existingSpans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : existingSpans) {
            editable.removeSpan(span);
        }

        String text = editable.toString();
        List<Pattern> patterns = patternsMap.get(currentLanguage);
        Map<Pattern, Integer> colors = colorMap.get(currentLanguage);

        if (patterns == null || colors == null) {
            return;
        }

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(text);
            Integer color = colors.get(pattern);

            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();

                editable.setSpan(new ForegroundColorSpan(color), start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    /**
     * Highlight matching brackets at the given position
     * @return the position of the matching bracket, or -1 if not found
     */
    public int highlightMatchingBracket(Editable editable, int cursorPosition) {
        if (editable == null || cursorPosition < 0 || cursorPosition >= editable.length()) {
            return -1;
        }

        // Remove existing bracket highlights
        BackgroundColorSpan[] existingSpans = editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : existingSpans) {
            editable.removeSpan(span);
        }

        char currentChar = editable.charAt(cursorPosition);
        char searchChar;
        boolean forward;

        if (BRACKET_PAIRS.containsKey(currentChar)) {
            searchChar = BRACKET_PAIRS.get(currentChar);
            forward = true;
        } else if (BRACKET_PAIRS.containsValue(currentChar)) {
            // Find the opening bracket
            searchChar = ' ';
            for (Map.Entry<Character, Character> entry : BRACKET_PAIRS.entrySet()) {
                if (entry.getValue() == currentChar) {
                    searchChar = entry.getKey();
                    break;
                }
            }
            forward = false;
        } else {
            return -1;
        }

        int matchPos = findMatchingBracket(editable.toString(), cursorPosition, searchChar, forward);

        if (matchPos != -1) {
            // Highlight both brackets
            editable.setSpan(new BackgroundColorSpan(colorScheme.bracketMatchColor),
                    cursorPosition, cursorPosition + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            editable.setSpan(new BackgroundColorSpan(colorScheme.bracketMatchColor),
                    matchPos, matchPos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return matchPos;
    }

    /**
     * Find the position of a matching bracket
     */
    private int findMatchingBracket(String text, int startPos, char searchChar, boolean forward) {
        int depth = 1;
        int pos = startPos + (forward ? 1 : -1);
        char startChar = text.charAt(startPos);

        while (pos >= 0 && pos < text.length()) {
            char c = text.charAt(pos);

            // Skip strings and comments (simplified)
            if (c == '"' || c == '\'') {
                pos = skipString(text, pos, c);
                if (pos == -1) break;
                continue;
            }

            if (forward) {
                if (c == startChar) depth++;
                else if (c == searchChar) depth--;
            } else {
                if (c == searchChar) depth++;
                else if (c == startChar) depth--;
            }

            if (depth == 0) {
                return pos;
            }

            pos += forward ? 1 : -1;
        }

        return -1;
    }

    /**
     * Skip over a string literal
     */
    private int skipString(String text, int startPos, char quoteChar) {
        int pos = startPos + 1;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == '\\') {
                pos += 2; // Skip escaped character
            } else if (c == quoteChar) {
                return pos + 1;
            } else {
                pos++;
            }
        }
        return -1;
    }

    /**
     * Auto-indent the given text
     */
    public String autoIndent(String text, int cursorLine) {
        if (text == null || cursorLine < 0) return text;

        String[] lines = text.split("\n");
        if (cursorLine >= lines.length) return text;

        String currentLine = lines[cursorLine];
        StringBuilder indent = new StringBuilder();

        // Count leading spaces/tabs of current line
        int i = 0;
        while (i < currentLine.length() && (currentLine.charAt(i) == ' ' || currentLine.charAt(i) == '\t')) {
            indent.append(currentLine.charAt(i));
            i++;
        }

        // Check if line ends with opening bracket
        String trimmed = currentLine.trim();
        if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")) {
            // Add one more level of indentation
            indent.append("    "); // 4 spaces
        }

        return indent.toString();
    }

    /**
     * Get the color scheme
     */
    public ColorScheme getColorScheme() {
        return colorScheme;
    }

    /**
     * Set a custom color scheme
     */
    public void setColorScheme(ColorScheme scheme) {
        this.colorScheme = scheme;
        // Re-initialize patterns with new colors
        patternsMap.clear();
        colorMap.clear();
        initializePatterns();
    }

    /**
     * Check if syntax highlighting is available for the current language
     */
    public boolean isHighlightingAvailable() {
        return currentLanguage != null && currentLanguage != Language.UNKNOWN &&
               patternsMap.containsKey(currentLanguage);
    }
}