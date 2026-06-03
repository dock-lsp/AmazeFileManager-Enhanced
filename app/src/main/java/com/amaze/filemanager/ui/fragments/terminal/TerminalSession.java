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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.topjohnwu.superuser.Shell;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a terminal session using libsu for shell execution.
 * Handles input/output streams and process lifecycle.
 */
public class TerminalSession {

    private static final String TAG = "TerminalSession";

    private final String initialDirectory;
    private boolean useRoot;
    private final Callback callback;

    private Shell shell;
    private Shell.Task currentTask;
    private ExecutorService executor;
    private Handler mainHandler;

    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private String currentDirectory;

    // Buffer for collecting output
    private StringBuilder outputBuffer;
    private static final int BUFFER_FLUSH_SIZE = 1024;
    private static final int BUFFER_FLUSH_DELAY_MS = 50;

    public interface Callback {
        void onOutput(String output);
        void onError(String error);
        void onPrompt(String prompt);
        void onExit(int code);
    }

    public TerminalSession(String initialDirectory, boolean useRoot, Callback callback) {
        this.initialDirectory = initialDirectory;
        this.currentDirectory = initialDirectory;
        this.useRoot = useRoot;
        this.callback = callback;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.outputBuffer = new StringBuilder();
    }

    /**
     * Start the terminal session
     */
    public void start() {
        if (isRunning.get()) {
            return;
        }

        executor.execute(() -> {
            try {
                isRunning.set(true);

                // Create shell with appropriate settings
                Shell.Builder builder = Shell.Builder.create();
                if (useRoot) {
                    builder.setFlags(Shell.FLAG_REDIRECT_STDERR);
                }

                shell = builder.build();

                // Change to initial directory
                if (initialDirectory != null && !initialDirectory.isEmpty()) {
                    executeCommand("cd " + escapePath(initialDirectory));
                }

                // Setup environment
                setupEnvironment();

                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onPrompt(currentDirectory + " $ ");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to start terminal session", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError("Failed to start shell: " + e.getMessage());
                    }
                });
                isRunning.set(false);
            }
        });
    }

    /**
     * Send input to the shell
     */
    public void sendInput(String input) {
        if (!isRunning.get() || shell == null) {
            return;
        }

        executor.execute(() -> {
            try {
                // Handle special characters
                if (input.equals("\u0004")) { // Ctrl+D (EOF)
                    destroy();
                    return;
                }

                // Execute the command
                Shell.Result result = shell.newJob().add(input).exec();

                // Get output
                String stdout = String.join("\n", result.getOut());
                String stderr = String.join("\n", result.getErr());

                mainHandler.post(() -> {
                    if (callback != null) {
                        if (!stdout.isEmpty()) {
                            callback.onOutput(stdout);
                        }
                        if (!stderr.isEmpty()) {
                            callback.onError(stderr);
                        }
                        callback.onPrompt(currentDirectory + " $ ");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error sending input", e);
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError("Error: " + e.getMessage());
                    }
                });
            }
        });
    }

    /**
     * Send a signal to the current process
     */
    public void sendSignal(int signal) {
        if (!isRunning.get()) {
            return;
        }

        // For libsu, we can use the shell's interrupt capability
        executor.execute(() -> {
            try {
                // Send interrupt signal using kill command
                shell.newJob().add("kill -" + signal + " $(pidof sh 2>/dev/null || echo 0) 2>/dev/null || true").exec();
            } catch (Exception e) {
                Log.e(TAG, "Error sending signal", e);
            }
        });
    }

    /**
     * Execute a command and return the result
     */
    private String executeCommand(String command) {
        if (shell == null) {
            return "";
        }

        try {
            Shell.Result result = shell.newJob().add(command).exec();
            return String.join("\n", result.getOut());
        } catch (Exception e) {
            Log.e(TAG, "Error executing command: " + command, e);
            return "";
        }
    }

    /**
     * Setup shell environment
     */
    private void setupEnvironment() {
        // Set common environment variables
        executeCommand("export TERM=xterm-256color");
        executeCommand("export HOME=" + (System.getenv("HOME") != null ? System.getenv("HOME") : "/data/data/com.amaze.filemanager"));
        executeCommand("export PATH=$PATH:/system/bin:/system/xbin:/vendor/bin");
        executeCommand("export PS1='\\w \\$ '");
    }

    /**
     * Escape special characters in file paths
     */
    private String escapePath(String path) {
        return path.replace("'", "'\\''");
    }

    /**
     * Set the working directory
     */
    public void setWorkingDirectory(String directory) {
        this.currentDirectory = directory;
        if (isRunning.get() && shell != null) {
            executor.execute(() -> executeCommand("cd " + escapePath(directory)));
        }
    }

    /**
     * Set root mode
     */
    public void setUseRoot(boolean useRoot) {
        this.useRoot = useRoot;
        // Restart session with new root setting
        destroy();
        start();
    }

    /**
     * Check if session is running
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Get current working directory
     */
    public String getCurrentDirectory() {
        return currentDirectory;
    }

    /**
     * Destroy the session and cleanup resources
     */
    public void destroy() {
        isRunning.set(false);

        executor.execute(() -> {
            try {
                if (shell != null) {
                    shell.close();
                    shell = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error closing shell", e);
            }
        });

        executor.shutdown();
    }

    /**
     * Alternative implementation using process-based shell for more control
     * This can be used if libsu doesn't meet requirements
     */
    public static class ProcessBasedSession {
        private Process process;
        private OutputStreamWriter stdin;
        private BufferedReader stdout;
        private BufferedReader stderr;
        private Thread stdoutThread;
        private Thread stderrThread;
        private Callback callback;
        private AtomicBoolean running = new AtomicBoolean(false);

        public ProcessBasedSession(String directory, boolean useRoot, Callback callback) {
            this.callback = callback;
        }

        public void start() {
            executor.execute(() -> {
                try {
                    ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
                    pb.directory(new java.io.File(initialDirectory));
                    pb.environment().put("TERM", "xterm-256color");
                    pb.environment().put("HOME", System.getenv("HOME") != null ? System.getenv("HOME") : "/data/data/com.amaze.filemanager");
                    pb.environment().put("PATH", System.getenv("PATH") + ":/system/bin:/system/xbin:/vendor/bin");

                    process = pb.start();
                    running.set(true);

                    stdin = new OutputStreamWriter(process.getOutputStream());
                    stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                    // Start output reader threads
                    stdoutThread = new Thread(() -> readOutput(stdout, false));
                    stderrThread = new Thread(() -> readOutput(stderr, true));
                    stdoutThread.start();
                    stderrThread.start();

                    // Wait for process
                    int exitCode = process.waitFor();
                    running.set(false);

                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onExit(exitCode);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Process session error", e);
                    running.set(false);
                }
            });
        }

        private void readOutput(BufferedReader reader, boolean isError) {
            try {
                char[] buffer = new char[1024];
                int read;
                while (running.get() && (read = reader.read(buffer)) != -1) {
                    final String output = new String(buffer, 0, read);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            if (isError) {
                                callback.onError(output);
                            } else {
                                callback.onOutput(output);
                            }
                        }
                    });
                }
            } catch (IOException e) {
                if (running.get()) {
                    Log.e(TAG, "Error reading output", e);
                }
            }
        }

        public void sendInput(String input) {
            if (running.get() && stdin != null) {
                try {
                    stdin.write(input);
                    stdin.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Error writing input", e);
                }
            }
        }

        public void sendSignal(int signal) {
            if (process != null) {
                // On Android, we can use destroy() for SIGTERM
                if (signal == 15) {
                    process.destroy();
                }
            }
        }

        public void destroy() {
            running.set(false);
            if (process != null) {
                process.destroy();
            }
            try {
                if (stdoutThread != null) {
                    stdoutThread.interrupt();
                }
                if (stderrThread != null) {
                    stderrThread.interrupt();
                }
                if (stdin != null) {
                    stdin.close();
                }
                if (stdout != null) {
                    stdout.close();
                }
                if (stderr != null) {
                    stderr.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams", e);
            }
        }
    }
}
