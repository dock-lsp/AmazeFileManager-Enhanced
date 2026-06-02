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

package com.amaze.filemanager.ui.fragments.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.terminal.TerminalActivity;
import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Terminal fragment that provides a terminal emulator interface.
 * Uses libsu for shell execution.
 */
public class TerminalFragment extends Fragment {

    private static final String ARG_INITIAL_PATH = "initial_path";
    private static final String ARG_USE_ROOT = "use_root";
    private static final String ARG_FONT_SIZE = "font_size";
    private static final String ARG_THEME = "theme";

    private static final int MAX_HISTORY_SIZE = 100;
    private static final int MAX_BUFFER_LINES = 5000;

    private TextView terminalOutput;
    private TerminalEditText terminalInput;
    private ScrollView scrollView;

    private TerminalSession terminalSession;
    private String currentDirectory;
    private boolean useRoot = false;
    private int fontSize = 14;
    private int theme = TerminalActivity.THEME_DARK;

    private List<String> commandHistory = new ArrayList<>();
    private int historyPosition = -1;
    private String currentInput = "";

    private Handler mainHandler;
    private SpannableStringBuilder buffer;

    // ANSI color codes
    private static final int COLOR_BLACK = 0xFF000000;
    private static final int COLOR_RED = 0xFFCD3131;
    private static final int COLOR_GREEN = 0xFF0DBC79;
    private static final int COLOR_YELLOW = 0xFFE5E510;
    private static final int COLOR_BLUE = 0xFF2472C8;
    private static final int COLOR_MAGENTA = 0xFFBC3FBC;
    private static final int COLOR_CYAN = 0xFF11A8CD;
    private static final int COLOR_WHITE = 0xFFE5E5E5;
    private static final int COLOR_BRIGHT_BLACK = 0xFF666666;
    private static final int COLOR_BRIGHT_RED = 0xFFF14C4C;
    private static final int COLOR_BRIGHT_GREEN = 0xFF23D18B;
    private static final int COLOR_BRIGHT_YELLOW = 0xFFF5F543;
    private static final int COLOR_BRIGHT_BLUE = 0xFF3B8EEA;
    private static final int COLOR_BRIGHT_MAGENTA = 0xFFD670D6;
    private static final int COLOR_BRIGHT_CYAN = 0xFF29B8DB;
    private static final int COLOR_BRIGHT_WHITE = 0xFFFFFFFF;

    // Theme colors
    private int textColor;
    private int backgroundColor;
    private int promptColor;

    public static TerminalFragment newInstance(String initialPath, boolean useRoot, int fontSize, int theme) {
        TerminalFragment fragment = new TerminalFragment();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_PATH, initialPath);
        args.putBoolean(ARG_USE_ROOT, useRoot);
        args.putInt(ARG_FONT_SIZE, fontSize);
        args.putInt(ARG_THEME, theme);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentDirectory = getArguments().getString(ARG_INITIAL_PATH, "/");
            useRoot = getArguments().getBoolean(ARG_USE_ROOT, false);
            fontSize = getArguments().getInt(ARG_FONT_SIZE, 14);
            theme = getArguments().getInt(ARG_THEME, TerminalActivity.THEME_DARK);
        }
        mainHandler = new Handler(Looper.getMainLooper());
        buffer = new SpannableStringBuilder();
        applyThemeColors();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        initViews(view);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initializeSession();
    }

    private void initViews(View view) {
        scrollView = view.findViewById(R.id.terminal_scroll);
        terminalOutput = view.findViewById(R.id.terminal_output);
        terminalInput = view.findViewById(R.id.terminal_input);

        // Set font
        Typeface monospaceTypeface = Typeface.MONOSPACE;
        terminalOutput.setTypeface(monospaceTypeface);
        terminalInput.setTypeface(monospaceTypeface);

        // Set font size
        terminalOutput.setTextSize(fontSize);
        terminalInput.setTextSize(fontSize);

        // Apply theme
        applyThemeToViews();

        // Setup input handling
        terminalInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                return handleKeyEvent(keyCode, event);
            }
            return false;
        });

        terminalInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                currentInput = s.toString();
            }
        });

        // Focus on input
        terminalInput.requestFocus();
    }

    private void applyThemeColors() {
        switch (theme) {
            case TerminalActivity.THEME_LIGHT:
                textColor = Color.BLACK;
                backgroundColor = Color.WHITE;
                promptColor = COLOR_BLUE;
                break;
            case TerminalActivity.THEME_BLACK:
                textColor = COLOR_WHITE;
                backgroundColor = Color.BLACK;
                promptColor = COLOR_GREEN;
                break;
            case TerminalActivity.THEME_DARK:
            default:
                textColor = COLOR_WHITE;
                backgroundColor = 0xFF1E1E1E;
                promptColor = COLOR_GREEN;
                break;
        }
    }

    private void applyThemeToViews() {
        terminalOutput.setTextColor(textColor);
        terminalOutput.setBackgroundColor(backgroundColor);
        terminalInput.setTextColor(textColor);
        terminalInput.setBackgroundColor(backgroundColor);
        scrollView.setBackgroundColor(backgroundColor);
    }

    private void initializeSession() {
        terminalSession = new TerminalSession(currentDirectory, useRoot, new TerminalSession.Callback() {
            @Override
            public void onOutput(String output) {
                mainHandler.post(() -> appendOutput(output));
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> appendError(error));
            }

            @Override
            public void onPrompt(String prompt) {
                mainHandler.post(() -> showPrompt(prompt));
            }

            @Override
            public void onExit(int code) {
                mainHandler.post(() -> {
                    appendOutput("\nProcess exited with code " + code + "\n");
                    showPrompt(currentDirectory + " $ ");
                });
            }
        });

        terminalSession.start();
        showPrompt(currentDirectory + " $ ");
    }

    private void appendOutput(String text) {
        // Process ANSI escape codes
        SpannableStringBuilder processed = processAnsiCodes(text);
        buffer.append(processed);

        // Trim buffer if too large
        trimBuffer();

        terminalOutput.setText(buffer);
        scrollToBottom();
    }

    private void appendError(String text) {
        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        spannable.setSpan(new ForegroundColorSpan(COLOR_RED), 0, text.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        buffer.append(spannable);
        buffer.append("\n");

        trimBuffer();
        terminalOutput.setText(buffer);
        scrollToBottom();
    }

    private void showPrompt(String prompt) {
        SpannableStringBuilder spannable = new SpannableStringBuilder(prompt);
        spannable.setSpan(new ForegroundColorSpan(promptColor), 0, prompt.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        buffer.append(spannable);

        trimBuffer();
        terminalOutput.setText(buffer);
        scrollToBottom();

        // Clear input
        terminalInput.setText("");
        currentInput = "";
    }

    private void trimBuffer() {
        if (buffer.length() > MAX_BUFFER_LINES * 100) {
            int start = buffer.toString().indexOf('\n', buffer.length() - MAX_BUFFER_LINES * 100);
            if (start > 0) {
                buffer.delete(0, start + 1);
            }
        }
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * Process ANSI escape codes for colors
     */
    private SpannableStringBuilder processAnsiCodes(String text) {
        SpannableStringBuilder result = new SpannableStringBuilder();
        int currentColor = textColor;
        int currentBg = backgroundColor;
        boolean bold = false;

        // Simple ANSI parser
        Pattern pattern = Pattern.compile("\u001B\\[([0-9;]*)m");
        Matcher matcher = pattern.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            // Append text before this code
            String before = text.substring(lastEnd, matcher.start());
            if (!before.isEmpty()) {
                SpannableStringBuilder span = new SpannableStringBuilder(before);
                span.setSpan(new ForegroundColorSpan(currentColor), 0, before.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (currentBg != backgroundColor) {
                    span.setSpan(new BackgroundColorSpan(currentBg), 0, before.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                result.append(span);
            }

            // Parse the code
            String code = matcher.group(1);
            String[] parts = code.split(";");

            for (String part : parts) {
                int num = part.isEmpty() ? 0 : Integer.parseInt(part);
                switch (num) {
                    case 0: // Reset
                        currentColor = textColor;
                        currentBg = backgroundColor;
                        bold = false;
                        break;
                    case 1: // Bold
                        bold = true;
                        break;
                    case 30: currentColor = COLOR_BLACK; break;
                    case 31: currentColor = bold ? COLOR_BRIGHT_RED : COLOR_RED; break;
                    case 32: currentColor = bold ? COLOR_BRIGHT_GREEN : COLOR_GREEN; break;
                    case 33: currentColor = bold ? COLOR_BRIGHT_YELLOW : COLOR_YELLOW; break;
                    case 34: currentColor = bold ? COLOR_BRIGHT_BLUE : COLOR_BLUE; break;
                    case 35: currentColor = bold ? COLOR_BRIGHT_MAGENTA : COLOR_MAGENTA; break;
                    case 36: currentColor = bold ? COLOR_BRIGHT_CYAN : COLOR_CYAN; break;
                    case 37: currentColor = bold ? COLOR_BRIGHT_WHITE : COLOR_WHITE; break;
                    case 40: currentBg = COLOR_BLACK; break;
                    case 41: currentBg = COLOR_RED; break;
                    case 42: currentBg = COLOR_GREEN; break;
                    case 43: currentBg = COLOR_YELLOW; break;
                    case 44: currentBg = COLOR_BLUE; break;
                    case 45: currentBg = COLOR_MAGENTA; break;
                    case 46: currentBg = COLOR_CYAN; break;
                    case 47: currentBg = COLOR_WHITE; break;
                }
            }

            lastEnd = matcher.end();
        }

        // Append remaining text
        String remaining = text.substring(lastEnd);
        if (!remaining.isEmpty()) {
            SpannableStringBuilder span = new SpannableStringBuilder(remaining);
            span.setSpan(new ForegroundColorSpan(currentColor), 0, remaining.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (currentBg != backgroundColor) {
                span.setSpan(new BackgroundColorSpan(currentBg), 0, remaining.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            result.append(span);
        }

        return result;
    }

    /**
     * Handle key events for terminal shortcuts
     */
    public boolean handleKeyEvent(int keyCode, KeyEvent event) {
        // Ctrl+C - Send interrupt
        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_C) {
            if (terminalSession != null) {
                terminalSession.sendSignal(2); // SIGINT
            }
            return true;
        }

        // Ctrl+D - Send EOF
        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_D) {
            if (terminalSession != null) {
                terminalSession.sendInput("\u0004");
            }
            return true;
        }

        // Ctrl+L - Clear screen
        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_L) {
            clearTerminal();
            return true;
        }

        // Ctrl+V - Paste
        if (event.isCtrlPressed() && keyCode == KeyEvent.KEYCODE_V) {
            pasteFromClipboard();
            return true;
        }

        // Up arrow - Previous command
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            showPreviousCommand();
            return true;
        }

        // Down arrow - Next command
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            showNextCommand();
            return true;
        }

        // Tab - Auto-complete (simplified)
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            // Could implement tab completion here
            terminalInput.append("    ");
            return true;
        }

        // Enter - Execute command
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            executeCommand();
            return true;
        }

        return false;
    }

    private void executeCommand() {
        String command = terminalInput.getText().toString().trim();
        if (command.isEmpty()) {
            showPrompt(currentDirectory + " $ ");
            return;
        }

        // Add to history
        addToHistory(command);

        // Echo the command
        appendOutput(currentDirectory + " $ " + command + "\n");

        // Handle built-in commands
        if (handleBuiltInCommand(command)) {
            return;
        }

        // Send to session
        if (terminalSession != null) {
            terminalSession.sendInput(command + "\n");
        }
    }

    private boolean handleBuiltInCommand(String command) {
        String[] parts = command.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "clear":
            case "cls":
                clearTerminal();
                return true;

            case "exit":
                if (getActivity() != null) {
                    getActivity().finish();
                }
                return true;

            case "cd":
                if (parts.length > 1) {
                    changeDirectory(parts[1]);
                } else {
                    changeDirectory(System.getenv("HOME"));
                }
                showPrompt(currentDirectory + " $ ");
                return true;

            case "pwd":
                appendOutput(currentDirectory + "\n");
                showPrompt(currentDirectory + " $ ");
                return true;

            case "help":
                showHelp();
                showPrompt(currentDirectory + " $ ");
                return true;

            case "copy":
            case "cp":
                // Copy selected files if available
                if (getActivity() instanceof TerminalActivity) {
                    TerminalActivity activity = (TerminalActivity) getActivity();
                    // Handle file operations
                }
                return false; // Let shell handle it

            default:
                return false;
        }
    }

    private void changeDirectory(String path) {
        if (path == null || path.isEmpty()) {
            path = System.getenv("HOME");
        }

        if (path.startsWith("/")) {
            currentDirectory = path;
        } else {
            currentDirectory = new File(currentDirectory, path).getAbsolutePath();
        }

        // Update session working directory
        if (terminalSession != null) {
            terminalSession.setWorkingDirectory(currentDirectory);
        }
    }

    private void showHelp() {
        String help = "\nTerminal Help:\n" +
                "  clear/cls  - Clear screen\n" +
                "  cd <dir>   - Change directory\n" +
                "  pwd        - Print working directory\n" +
                "  exit       - Close terminal\n" +
                "  help       - Show this help\n\n" +
                "Shortcuts:\n" +
                "  Ctrl+C     - Interrupt\n" +
                "  Ctrl+D     - Send EOF\n" +
                "  Ctrl+L     - Clear screen\n" +
                "  Ctrl+V     - Paste\n" +
                "  Up/Down    - Command history\n" +
                "  Tab        - Indent\n\n";
        appendOutput(help);
    }

    private void addToHistory(String command) {
        if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
            commandHistory.add(command);
            if (commandHistory.size() > MAX_HISTORY_SIZE) {
                commandHistory.remove(0);
            }
        }
        historyPosition = commandHistory.size();
    }

    private void showPreviousCommand() {
        if (historyPosition > 0) {
            historyPosition--;
            terminalInput.setText(commandHistory.get(historyPosition));
            terminalInput.setSelection(terminalInput.getText().length());
        }
    }

    private void showNextCommand() {
        if (historyPosition < commandHistory.size() - 1) {
            historyPosition++;
            terminalInput.setText(commandHistory.get(historyPosition));
            terminalInput.setSelection(terminalInput.getText().length());
        } else if (historyPosition == commandHistory.size() - 1) {
            historyPosition++;
            terminalInput.setText("");
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clip = clipboard.getPrimaryClip();
            if (clip != null && clip.getItemCount() > 0) {
                CharSequence paste = clip.getItemAt(0).getText();
                if (paste != null) {
                    terminalInput.append(paste.toString().replace("\n", " "));
                }
            }
        }
    }

    /**
     * Clear the terminal screen
     */
    public void clearTerminal() {
        buffer.clear();
        terminalOutput.setText("");
        showPrompt(currentDirectory + " $ ");
    }

    /**
     * Set font size
     */
    public void setFontSize(int size) {
        this.fontSize = size;
        if (terminalOutput != null) {
            terminalOutput.setTextSize(size);
        }
        if (terminalInput != null) {
            terminalInput.setTextSize(size);
        }
    }

    /**
     * Set terminal theme
     */
    public void setTerminalTheme(int theme) {
        this.theme = theme;
        applyThemeColors();
        applyThemeToViews();
    }

    /**
     * Set root mode
     */
    public void setUseRoot(boolean useRoot) {
        this.useRoot = useRoot;
        if (terminalSession != null) {
            terminalSession.setUseRoot(useRoot);
        }
    }

    /**
     * Check if session is active
     */
    public boolean isSessionActive() {
        return terminalSession != null && terminalSession.isRunning();
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        if (terminalSession != null) {
            terminalSession.destroy();
            terminalSession = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    /**
     * Custom EditText for terminal input
     */
    public static class TerminalEditText extends androidx.appcompat.widget.AppCompatEditText {

        public TerminalEditText(Context context) {
            super(context);
        }

        public TerminalEditText(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            return new BaseInputConnection(this, true) {
                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    // Handle text input
                    return super.commitText(text, newCursorPosition);
                }

                @Override
                public boolean sendKeyEvent(KeyEvent event) {
                    return super.sendKeyEvent(event);
                }
            };
        }
    }
}
