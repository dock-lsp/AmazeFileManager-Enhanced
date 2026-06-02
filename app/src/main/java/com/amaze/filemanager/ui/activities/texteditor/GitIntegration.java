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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git integration utility for code editor
 * Provides Git status, diff, and history functionality
 */
public class GitIntegration {

    private static final String TAG = "GitIntegration";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Git file status types
     */
    public enum FileStatus {
        UNMODIFIED(' '),      // Unmodified
        MODIFIED('M'),        // Modified
        ADDED('A'),           // Added
        DELETED('D'),         // Deleted
        RENAMED('R'),         // Renamed
        COPIED('C'),          // Copied
        UNMERGED('U'),        // Updated but unmerged
        UNTRACKED('?'),       // Untracked
        IGNORED('!'),         // Ignored
        UNKNOWN('X');         // Unknown

        private final char code;

        FileStatus(char code) {
            this.code = code;
        }

        public char getCode() {
            return code;
        }

        public static FileStatus fromCode(char code) {
            for (FileStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
            return UNKNOWN;
        }

        public int getColor() {
            switch (this) {
                case MODIFIED:
                    return Color.parseColor("#FFC107"); // Amber
                case ADDED:
                    return Color.parseColor("#4CAF50"); // Green
                case DELETED:
                    return Color.parseColor("#F44336"); // Red
                case UNTRACKED:
                    return Color.parseColor("#9E9E9E"); // Gray
                case RENAMED:
                    return Color.parseColor("#2196F3"); // Blue
                case UNMERGED:
                    return Color.parseColor("#FF5722"); // Deep Orange
                default:
                    return Color.TRANSPARENT;
            }
        }
    }

    /**
     * Represents a line change in a diff
     */
    public static class LineChange {
        public enum ChangeType {
            ADDED,      // Line was added
            DELETED,    // Line was deleted
            MODIFIED,   // Line was modified
            UNCHANGED   // Line unchanged (context)
        }

        public final int oldLineNumber;
        public final int newLineNumber;
        public final ChangeType type;
        public final String content;

        public LineChange(int oldLineNumber, int newLineNumber, ChangeType type, String content) {
            this.oldLineNumber = oldLineNumber;
            this.newLineNumber = newLineNumber;
            this.type = type;
            this.content = content;
        }
    }

    /**
     * Represents a Git commit
     */
    public static class GitCommit {
        public final String hash;
        public final String shortHash;
        public final String author;
        public final String date;
        public final String message;
        public final List<String> changedFiles;

        public GitCommit(String hash, String author, String date, String message) {
            this.hash = hash;
            this.shortHash = hash.length() >= 7 ? hash.substring(0, 7) : hash;
            this.author = author;
            this.date = date;
            this.message = message;
            this.changedFiles = new ArrayList<>();
        }
    }

    /**
     * Git status information for a file
     */
    public static class GitFileStatus {
        public final FileStatus indexStatus;
        public final FileStatus workTreeStatus;
        public final String filePath;
        public final String originalPath; // For renamed files
        public final boolean isStaged;

        public GitFileStatus(FileStatus indexStatus, FileStatus workTreeStatus,
                            String filePath, String originalPath) {
            this.indexStatus = indexStatus;
            this.workTreeStatus = workTreeStatus;
            this.filePath = filePath;
            this.originalPath = originalPath;
            this.isStaged = indexStatus != FileStatus.UNMODIFIED && indexStatus != FileStatus.UNKNOWN;
        }

        public FileStatus getEffectiveStatus() {
            if (workTreeStatus != FileStatus.UNMODIFIED && workTreeStatus != FileStatus.UNKNOWN) {
                return workTreeStatus;
            }
            return indexStatus;
        }
    }

    /**
     * Callback interface for async operations
     */
    public interface GitCallback<T> {
        void onResult(T result);
        void onError(String error);
    }

    /**
     * Check if a file is in a Git repository
     */
    public static boolean isInGitRepository(@NonNull File file) {
        File current = file.isDirectory() ? file : file.getParentFile();
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                return true;
            }
            current = current.getParentFile();
        }
        return false;
    }

    /**
     * Find the Git repository root for a file
     */
    @Nullable
    public static File findGitRoot(@NonNull File file) {
        File current = file.isDirectory() ? file : file.getParentFile();
        while (current != null) {
            File gitDir = new File(current, ".git");
            if (gitDir.exists() && gitDir.isDirectory()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    /**
     * Get Git status for a file asynchronously
     */
    public static void getFileStatus(@NonNull File file, @NonNull GitCallback<GitFileStatus> callback) {
        executor.execute(() -> {
            try {
                GitFileStatus status = getFileStatusSync(file);
                mainHandler.post(() -> callback.onResult(status));
            } catch (Exception e) {
                Log.e(TAG, "Error getting file status", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Get Git status for a file synchronously
     */
    @Nullable
    public static GitFileStatus getFileStatusSync(@NonNull File file) {
        File gitRoot = findGitRoot(file);
        if (gitRoot == null) {
            return null;
        }

        try {
            // Get relative path
            String relativePath = getRelativePath(gitRoot, file);

            // Run git status --porcelain
            ProcessBuilder pb = new ProcessBuilder(
                "git", "status", "--porcelain", "-u", relativePath
            );
            pb.directory(gitRoot);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            GitFileStatus result = null;

            while ((line = reader.readLine()) != null) {
                if (line.length() >= 3) {
                    char indexStatus = line.charAt(0);
                    char workTreeStatus = line.charAt(1);
                    String path = line.substring(3).trim();

                    // Handle renamed files (R status)
                    String originalPath = null;
                    if (indexStatus == 'R' || workTreeStatus == 'R') {
                        int arrowIndex = path.indexOf(" -> ");
                        if (arrowIndex != -1) {
                            originalPath = path.substring(0, arrowIndex);
                            path = path.substring(arrowIndex + 4);
                        }
                    }

                    if (path.equals(relativePath) || path.endsWith(relativePath)) {
                        result = new GitFileStatus(
                            FileStatus.fromCode(indexStatus),
                            FileStatus.fromCode(workTreeStatus),
                            path,
                            originalPath
                        );
                        break;
                    }
                }
            }

            process.waitFor();

            // If no status found, file is unmodified
            if (result == null) {
                result = new GitFileStatus(
                    FileStatus.UNMODIFIED,
                    FileStatus.UNMODIFIED,
                    relativePath,
                    null
                );
            }

            return result;

        } catch (Exception e) {
            Log.e(TAG, "Error getting git status", e);
            return null;
        }
    }

    /**
     * Get diff for a file asynchronously
     */
    public static void getFileDiff(@NonNull File file, @NonNull GitCallback<List<LineChange>> callback) {
        executor.execute(() -> {
            try {
                List<LineChange> diff = getFileDiffSync(file);
                mainHandler.post(() -> callback.onResult(diff));
            } catch (Exception e) {
                Log.e(TAG, "Error getting file diff", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Get diff for a file synchronously
     */
    @NonNull
    public static List<LineChange> getFileDiffSync(@NonNull File file) {
        List<LineChange> changes = new ArrayList<>();
        File gitRoot = findGitRoot(file);
        if (gitRoot == null) {
            return changes;
        }

        try {
            String relativePath = getRelativePath(gitRoot, file);

            // Run git diff
            ProcessBuilder pb = new ProcessBuilder(
                "git", "diff", "-U3", "--no-color", relativePath
            );
            pb.directory(gitRoot);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int oldLine = 0;
            int newLine = 0;
            boolean inHunk = false;

            Pattern hunkPattern = Pattern.compile("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@");

            while ((line = reader.readLine()) != null) {
                // Skip diff headers
                if (line.startsWith("diff ") || line.startsWith("index ") ||
                    line.startsWith("--- ") || line.startsWith("+++ ")) {
                    continue;
                }

                // Parse hunk header
                Matcher matcher = hunkPattern.matcher(line);
                if (matcher.find()) {
                    oldLine = Integer.parseInt(matcher.group(1));
                    newLine = Integer.parseInt(matcher.group(3));
                    inHunk = true;
                    continue;
                }

                if (!inHunk || line.length() == 0) {
                    continue;
                }

                char marker = line.charAt(0);
                String content = line.length() > 1 ? line.substring(1) : "";

                switch (marker) {
                    case ' ':
                        // Unchanged line
                        changes.add(new LineChange(oldLine, newLine, LineChange.ChangeType.UNCHANGED, content));
                        oldLine++;
                        newLine++;
                        break;
                    case '-':
                        // Deleted line
                        changes.add(new LineChange(oldLine, -1, LineChange.ChangeType.DELETED, content));
                        oldLine++;
                        break;
                    case '+':
                        // Added line
                        changes.add(new LineChange(-1, newLine, LineChange.ChangeType.ADDED, content));
                        newLine++;
                        break;
                }
            }

            process.waitFor();

        } catch (Exception e) {
            Log.e(TAG, "Error getting git diff", e);
        }

        return changes;
    }

    /**
     * Get commit history for a file asynchronously
     */
    public static void getFileHistory(@NonNull File file, int maxCommits,
                                     @NonNull GitCallback<List<GitCommit>> callback) {
        executor.execute(() -> {
            try {
                List<GitCommit> history = getFileHistorySync(file, maxCommits);
                mainHandler.post(() -> callback.onResult(history));
            } catch (Exception e) {
                Log.e(TAG, "Error getting file history", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Get commit history for a file synchronously
     */
    @NonNull
    public static List<GitCommit> getFileHistorySync(@NonNull File file, int maxCommits) {
        List<GitCommit> commits = new ArrayList<>();
        File gitRoot = findGitRoot(file);
        if (gitRoot == null) {
            return commits;
        }

        try {
            String relativePath = getRelativePath(gitRoot, file);

            // Run git log
            ProcessBuilder pb = new ProcessBuilder(
                "git", "log", "--pretty=format:%H|%an|%ad|%s",
                "--date=short", "-n", String.valueOf(maxCommits),
                "--", relativePath
            );
            pb.directory(gitRoot);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 4) {
                    GitCommit commit = new GitCommit(parts[0], parts[1], parts[2], parts[3]);
                    commits.add(commit);
                }
            }

            process.waitFor();

            // Get changed files for each commit
            for (GitCommit commit : commits) {
                getCommitFiles(gitRoot, commit);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error getting file history", e);
        }

        return commits;
    }

    /**
     * Get files changed in a commit
     */
    private static void getCommitFiles(File gitRoot, GitCommit commit) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "git", "show", "--name-only", "--pretty=format:", commit.hash
            );
            pb.directory(gitRoot);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    commit.changedFiles.add(line.trim());
                }
            }

            process.waitFor();

        } catch (Exception e) {
            Log.e(TAG, "Error getting commit files", e);
        }
    }

    /**
     * Get blame information for a file asynchronously
     */
    public static void getFileBlame(@NonNull File file, @NonNull GitCallback<List<BlameLine>> callback) {
        executor.execute(() -> {
            try {
                List<BlameLine> blame = getFileBlameSync(file);
                mainHandler.post(() -> callback.onResult(blame));
            } catch (Exception e) {
                Log.e(TAG, "Error getting file blame", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Represents a blame line
     */
    public static class BlameLine {
        public final String commitHash;
        public final String author;
        public final String date;
        public final int lineNumber;
        public final String content;

        public BlameLine(String commitHash, String author, String date,
                        int lineNumber, String content) {
            this.commitHash = commitHash;
            this.author = author;
            this.date = date;
            this.lineNumber = lineNumber;
            this.content = content;
        }
    }

    /**
     * Get blame information for a file synchronously
     */
    @NonNull
    public static List<BlameLine> getFileBlameSync(@NonNull File file) {
        List<BlameLine> blameLines = new ArrayList<>();
        File gitRoot = findGitRoot(file);
        if (gitRoot == null) {
            return blameLines;
        }

        try {
            String relativePath = getRelativePath(gitRoot, file);

            ProcessBuilder pb = new ProcessBuilder(
                "git", "blame", "--porcelain", relativePath
            );
            pb.directory(gitRoot);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String currentHash = "";
            String currentAuthor = "";
            String currentDate = "";
            int currentLine = 0;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("\t")) {
                    // Line content
                    String content = line.substring(1);
                    blameLines.add(new BlameLine(
                        currentHash, currentAuthor, currentDate, currentLine, content
                    ));
                } else if (line.startsWith("author ")) {
                    currentAuthor = line.substring(7);
                } else if (line.startsWith("author-time ")) {
                    long timestamp = Long.parseLong(line.substring(12));
                    currentDate = new java.text.SimpleDateFormat("yyyy-MM-dd")
                        .format(new java.util.Date(timestamp * 1000));
                } else if (Character.isDigit(line.charAt(0))) {
                    // Header line: hash original-line line-number
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        currentHash = parts[0];
                        currentLine = Integer.parseInt(parts[2]);
                    }
                }
            }

            process.waitFor();

        } catch (Exception e) {
            Log.e(TAG, "Error getting file blame", e);
        }

        return blameLines;
    }

    /**
     * Get the current branch name
     */
    public static void getCurrentBranch(@NonNull File file, @NonNull GitCallback<String> callback) {
        executor.execute(() -> {
            try {
                File gitRoot = findGitRoot(file);
                if (gitRoot == null) {
                    mainHandler.post(() -> callback.onResult(null));
                    return;
                }

                ProcessBuilder pb = new ProcessBuilder(
                    "git", "rev-parse", "--abbrev-ref", "HEAD"
                );
                pb.directory(gitRoot);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String branch = reader.readLine();
                process.waitFor();

                final String result = branch != null ? branch.trim() : null;
                mainHandler.post(() -> callback.onResult(result));

            } catch (Exception e) {
                Log.e(TAG, "Error getting current branch", e);
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    /**
     * Check if Git is available on the system
     */
    public static boolean isGitAvailable() {
        try {
            Process process = Runtime.getRuntime().exec("git --version");
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get relative path from git root to file
     */
    @NonNull
    private static String getRelativePath(@NonNull File root, @NonNull File file) {
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        if (filePath.startsWith(rootPath)) {
            String relative = filePath.substring(rootPath.length());
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            return relative;
        }

        return file.getName();
    }

    /**
     * Cleanup resources
     */
    public static void shutdown() {
        executor.shutdown();
    }
}