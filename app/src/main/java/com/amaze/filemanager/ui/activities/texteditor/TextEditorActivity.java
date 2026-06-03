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

import static com.amaze.filemanager.filesystem.EditableFileAbstraction.Scheme.CONTENT;
import static com.amaze.filemanager.filesystem.EditableFileAbstraction.Scheme.FILE;
import static com.amaze.filemanager.ui.fragments.preferencefragments.PreferencesConstants.PREFERENCE_TEXTEDITOR_NEWSTACK;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.afollestad.materialdialogs.MaterialDialog;
import com.amaze.filemanager.R;
import com.amaze.filemanager.asynchronous.asynctasks.SearchTextTask;
import com.amaze.filemanager.asynchronous.asynctasks.TaskKt;
import com.amaze.filemanager.asynchronous.asynctasks.texteditor.read.ReadTextFileTask;
import com.amaze.filemanager.asynchronous.asynctasks.texteditor.write.WriteTextFileTask;
import com.amaze.filemanager.fileoperations.filesystem.OpenMode;
import com.amaze.filemanager.filesystem.EditableFileAbstraction;
import com.amaze.filemanager.filesystem.HybridFileParcelable;
import com.amaze.filemanager.filesystem.files.FileUtils;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.ui.dialogs.GeneralDialogCreation;
import com.amaze.filemanager.ui.theme.AppTheme;
import com.amaze.filemanager.utils.OnAsyncTaskFinished;
import com.amaze.filemanager.utils.OnProgressUpdate;
import com.amaze.filemanager.utils.Utils;
import com.google.android.material.snackbar.Snackbar;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;

/**
 * Enhanced Text Editor Activity with syntax highlighting, code folding, Git integration,
 * line numbers, auto-indent, bracket matching, and enhanced search/replace.
 */
public class TextEditorActivity extends ThemedActivity
    implements TextWatcher, View.OnClickListener {

  public AppCompatEditText mainTextView;
  public AppCompatEditText searchEditText;
  private TextView lineNumberView;
  private TextView gitStatusView;
  private View gitStatusIndicator;
  private HorizontalScrollView horizontalScrollView;
  private LinearLayout editorContainer;
  
  private Typeface inputTypefaceDefault;
  private Typeface inputTypefaceMono;
  private androidx.appcompat.widget.Toolbar toolbar;
  ScrollView scrollView;

  private SearchTextTask searchTextTask;
  private static final String KEY_MODIFIED_TEXT = "modified";
  private static final String KEY_INDEX = "index";
  private static final String KEY_ORIGINAL_TEXT = "original";
  private static final String KEY_MONOFONT = "monofont";

  private ConstraintLayout searchViewLayout;
  private ConstraintLayout replaceViewLayout;
  private AppCompatEditText replaceEditText;
  private AppCompatImageButton replaceButton;
  private AppCompatImageButton replaceAllButton;
  private CheckBox regexCheckBox;
  private CheckBox caseSensitiveCheckBox;
  
  public AppCompatImageButton upButton;
  public AppCompatImageButton downButton;

  private Snackbar loadingSnackbar;

  private TextEditorActivityViewModel viewModel;
  
  // Enhanced features
  private SyntaxHighlighter syntaxHighlighter;
  private CodeFoldingManager codeFoldingManager;
  private GitIntegration.GitFileStatus gitFileStatus;
  private Handler highlightHandler;
  private Runnable highlightRunnable;
  private static final long HIGHLIGHT_DELAY_MS = 300;
  
  // Bracket matching
  private int lastBracketMatchStart = -1;
  private int lastBracketMatchEnd = -1;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.search);
    toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    viewModel = new ViewModelProvider(this).get(TextEditorActivityViewModel.class);
    highlightHandler = new Handler(Looper.getMainLooper());

    searchViewLayout = findViewById(R.id.textEditorSearchBar);
    replaceViewLayout = findViewById(R.id.textEditorReplaceBar);
    
    searchViewLayout.setBackgroundColor(getPrimary());
    if (replaceViewLayout != null) {
      replaceViewLayout.setBackgroundColor(getPrimary());
    }

    searchEditText = searchViewLayout.findViewById(R.id.textEditorSearchBox);
    upButton = searchViewLayout.findViewById(R.id.textEditorSearchPrevButton);
    downButton = searchViewLayout.findViewById(R.id.textEditorSearchNextButton);
    
    // Replace UI
    if (replaceViewLayout != null) {
      replaceEditText = replaceViewLayout.findViewById(R.id.textEditorReplaceBox);
      replaceButton = replaceViewLayout.findViewById(R.id.textEditorReplaceButton);
      replaceAllButton = replaceViewLayout.findViewById(R.id.textEditorReplaceAllButton);
      regexCheckBox = replaceViewLayout.findViewById(R.id.regexCheckBox);
      caseSensitiveCheckBox = replaceViewLayout.findViewById(R.id.caseSensitiveCheckBox);
      
      replaceButton.setOnClickListener(v -> performReplace());
      replaceAllButton.setOnClickListener(v -> performReplaceAll());
    }

    searchEditText.addTextChangedListener(this);

    upButton.setOnClickListener(this);
    downButton.setOnClickListener(this);

    if (getSupportActionBar() != null) {
      boolean useNewStack = getBoolean(PREFERENCE_TEXTEDITOR_NEWSTACK);
      getSupportActionBar().setDisplayHomeAsUpEnabled(!useNewStack);
    }
    
    mainTextView = findViewById(R.id.textEditorMainEditText);
    scrollView = findViewById(R.id.textEditorScrollView);
    lineNumberView = findViewById(R.id.lineNumberView);
    gitStatusView = findViewById(R.id.gitStatusView);
    gitStatusIndicator = findViewById(R.id.gitStatusIndicator);
    horizontalScrollView = findViewById(R.id.horizontalScrollView);
    editorContainer = findViewById(R.id.editorContainer);

    final Uri uri = getIntent().getData();
    if (uri != null) {
      viewModel.setFile(new EditableFileAbstraction(this, uri));
    } else {
      Toast.makeText(this, R.string.no_file_error, Toast.LENGTH_LONG).show();
      finish();
      return;
    }

    ActionBar actionBar = getSupportActionBar();

    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(!getBoolean(PREFERENCE_TEXTEDITOR_NEWSTACK));
      actionBar.setTitle(viewModel.getFile().name);
    }

    mainTextView.addTextChangedListener(this);
    
    // Setup enhanced features
    setupSyntaxHighlighter();
    setupCodeFolding();
    setupLineNumbers();
    setupBracketMatching();
    setupGitIntegration();

    if (getAppTheme().equals(AppTheme.DARK)) {
      mainTextView.setBackgroundColor(Utils.getColor(this, R.color.holo_dark_action_mode));
      mainTextView.setTextColor(Utils.getColor(this, R.color.primary_white));
      if (lineNumberView != null) {
        lineNumberView.setBackgroundColor(Utils.getColor(this, R.color.holo_dark_action_mode));
        lineNumberView.setTextColor(Utils.getColor(this, R.color.primary_grey_500));
      }
    } else if (getAppTheme().equals(AppTheme.BLACK)) {
      mainTextView.setBackgroundColor(Utils.getColor(this, android.R.color.black));
      mainTextView.setTextColor(Utils.getColor(this, R.color.primary_white));
      if (lineNumberView != null) {
        lineNumberView.setBackgroundColor(Utils.getColor(this, android.R.color.black));
        lineNumberView.setTextColor(Utils.getColor(this, R.color.primary_grey_500));
      }
    } else {
      mainTextView.setTextColor(Utils.getColor(this, R.color.primary_grey_900));
      if (lineNumberView != null) {
        lineNumberView.setBackgroundColor(Utils.getColor(this, R.color.primary_grey_100));
        lineNumberView.setTextColor(Utils.getColor(this, R.color.primary_grey_600));
      }
    }

    if (mainTextView.getTypeface() == null) {
      mainTextView.setTypeface(Typeface.DEFAULT);
    }

    inputTypefaceDefault = mainTextView.getTypeface();
    inputTypefaceMono = Typeface.MONOSPACE;

    if (savedInstanceState != null) {
      viewModel.setOriginal(savedInstanceState.getString(KEY_ORIGINAL_TEXT));
      int index = savedInstanceState.getInt(KEY_INDEX);
      mainTextView.setText(savedInstanceState.getString(KEY_MODIFIED_TEXT));
      mainTextView.setScrollY(index);
      if (savedInstanceState.getBoolean(KEY_MONOFONT)) {
        mainTextView.setTypeface(inputTypefaceMono);
      }
    } else {
      load(this);
    }
    initStatusBarResources(findViewById(R.id.textEditorRootView));
  }

  /**
   * Setup syntax highlighter based on file extension
   */
  private void setupSyntaxHighlighter() {
    syntaxHighlighter = new SyntaxHighlighter();
    
    EditableFileAbstraction file = viewModel.getFile();
    if (file != null && file.name != null) {
      syntaxHighlighter.setLanguage(file.name);
    }
    
    // Apply initial highlighting after a delay
    highlightRunnable = () -> {
      if (mainTextView.getText() != null && syntaxHighlighter.isHighlightingAvailable()) {
        syntaxHighlighter.applyHighlightingToEditable(mainTextView.getText());
      }
    };
  }

  /**
   * Setup code folding manager
   */
  private void setupCodeFolding() {
    codeFoldingManager = new CodeFoldingManager();
    EditableFileAbstraction file = viewModel.getFile();
    if (file != null && file.name != null) {
      codeFoldingManager.setLanguage(SyntaxHighlighter.detectLanguage(file.name));
    }
  }

  /**
   * Setup line number display
   */
  private void setupLineNumbers() {
    if (lineNumberView == null) return;
    
    lineNumberView.setTypeface(Typeface.MONOSPACE);
    lineNumberView.setTextSize(12);
    
    scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
      updateLineNumbers();
    });
  }

  /**
   * Update line number display
   */
  private void updateLineNumbers() {
    if (lineNumberView == null || mainTextView.getText() == null) return;
    
    String text = mainTextView.getText().toString();
    int lineCount = countLines(text);
    
    StringBuilder lineNumbers = new StringBuilder();
    for (int i = 1; i <= lineCount; i++) {
      lineNumbers.append(i).append("\n");
    }
    
    lineNumberView.setText(lineNumbers.toString());
  }

  /**
   * Count lines in text
   */
  private int countLines(String text) {
    if (text == null || text.isEmpty()) return 1;
    int count = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') count++;
    }
    return count;
  }

  /**
   * Setup bracket matching
   */
  private void setupBracketMatching() {
    mainTextView.setOnClickListener(v -> {
      int cursorPosition = mainTextView.getSelectionStart();
      if (cursorPosition >= 0 && mainTextView.getText() != null) {
        highlightMatchingBracket(cursorPosition);
      }
    });
  }

  /**
   * Highlight matching bracket
   */
  private void highlightMatchingBracket(int cursorPosition) {
    if (syntaxHighlighter == null || mainTextView.getText() == null) return;
    
    // Remove previous bracket highlights
    if (lastBracketMatchStart >= 0) {
      BackgroundColorSpan[] spans = mainTextView.getText().getSpans(
          lastBracketMatchStart, lastBracketMatchStart + 1, BackgroundColorSpan.class);
      for (BackgroundColorSpan span : spans) {
        mainTextView.getText().removeSpan(span);
      }
    }
    if (lastBracketMatchEnd >= 0) {
      BackgroundColorSpan[] spans = mainTextView.getText().getSpans(
          lastBracketMatchEnd, lastBracketMatchEnd + 1, BackgroundColorSpan.class);
      for (BackgroundColorSpan span : spans) {
        mainTextView.getText().removeSpan(span);
      }
    }
    
    // Find and highlight matching bracket
    int matchPos = syntaxHighlighter.highlightMatchingBracket(mainTextView.getText(), cursorPosition);
    
    if (matchPos >= 0) {
      lastBracketMatchStart = cursorPosition;
      lastBracketMatchEnd = matchPos;
    }
  }

  /**
   * Setup Git integration
   */
  private void setupGitIntegration() {
    EditableFileAbstraction file = viewModel.getFile();
    if (file == null || file.scheme != FILE) return;
    
    File javaFile = file.hybridFileParcelable.getFile();
    if (javaFile == null) return;
    
    // Check if file is in Git repository
    if (GitIntegration.isInGitRepository(javaFile)) {
      // Load Git status
      GitIntegration.getFileStatus(javaFile, new GitIntegration.GitCallback<GitIntegration.GitFileStatus>() {
        @Override
        public void onResult(GitIntegration.GitFileStatus status) {
          gitFileStatus = status;
          updateGitStatusUI();
        }
        
        @Override
        public void onError(String error) {
          // Git not available or error
          if (gitStatusView != null) {
            gitStatusView.setVisibility(View.GONE);
          }
          if (gitStatusIndicator != null) {
            gitStatusIndicator.setVisibility(View.GONE);
          }
        }
      });
      
      // Load current branch
      GitIntegration.getCurrentBranch(javaFile, new GitIntegration.GitCallback<String>() {
        @Override
        public void onResult(String branch) {
          if (branch != null && gitStatusView != null) {
            String currentText = gitStatusView.getText().toString();
            gitStatusView.setText(branch + " | " + currentText);
          }
        }
        
        @Override
        public void onError(String error) {
          // Ignore
        }
      });
    } else {
      if (gitStatusView != null) {
        gitStatusView.setVisibility(View.GONE);
      }
      if (gitStatusIndicator != null) {
        gitStatusIndicator.setVisibility(View.GONE);
      }
    }
  }

  /**
   * Update Git status UI
   */
  private void updateGitStatusUI() {
    if (gitFileStatus == null || gitStatusView == null) return;
    
    GitIntegration.FileStatus status = gitFileStatus.getEffectiveStatus();
    String statusText;
    
    switch (status) {
      case MODIFIED:
        statusText = getString(R.string.git_modified);
        break;
      case ADDED:
        statusText = getString(R.string.git_added);
        break;
      case DELETED:
        statusText = getString(R.string.git_deleted);
        break;
      case UNTRACKED:
        statusText = getString(R.string.git_untracked);
        break;
      case RENAMED:
        statusText = getString(R.string.git_renamed);
        break;
      case UNMODIFIED:
        statusText = getString(R.string.git_unmodified);
        break;
      default:
        statusText = status.name();
    }
    
    gitStatusView.setText(statusText);
    gitStatusView.setTextColor(status.getColor());
    
    if (gitStatusIndicator != null) {
      gitStatusIndicator.setBackgroundColor(status.getColor());
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    final TextEditorActivityViewModel viewModel =
        new ViewModelProvider(this).get(TextEditorActivityViewModel.class);

    outState.putString(
        KEY_MODIFIED_TEXT, mainTextView.getText() != null ? mainTextView.getText().toString() : "");
    outState.putInt(KEY_INDEX, mainTextView.getScrollY());
    outState.putString(KEY_ORIGINAL_TEXT, viewModel.getOriginal());
    outState.putBoolean(KEY_MONOFONT, inputTypefaceMono.equals(mainTextView.getTypeface()));
  }

  private void checkUnsavedChanges() {
    final TextEditorActivityViewModel viewModel =
        new ViewModelProvider(this).get(TextEditorActivityViewModel.class);

    if (viewModel.getOriginal() != null
        && mainTextView.isShown()
        && mainTextView.getText() != null
        && !viewModel.getOriginal().equals(mainTextView.getText().toString())) {
      new MaterialDialog.Builder(this)
          .title(R.string.unsaved_changes)
          .content(R.string.unsaved_changes_description)
          .positiveText(R.string.yes)
          .negativeText(R.string.no)
          .positiveColor(getAccent())
          .negativeColor(getAccent())
          .onPositive(
              (dialog, which) -> {
                saveFile(this, mainTextView.getText().toString());
                finish();
              })
          .onNegative((dialog, which) -> finish())
          .build()
          .show();
    } else {
      finish();
    }
  }

  /**
   * Method initiates a worker thread which writes the {@link #mainTextView} bytes to the defined
   * file/uri 's output stream
   *
   * @param activity a reference to the current activity
   * @param editTextString the edit text string
   */
  private static void saveFile(final TextEditorActivity activity, final String editTextString) {
    final WeakReference<TextEditorActivity> textEditorActivityWR = new WeakReference<>(activity);
    final WeakReference<Context> appContextWR =
        new WeakReference<>(activity.getApplicationContext());

    TaskKt.fromTask(
        new WriteTextFileTask(activity, editTextString, textEditorActivityWR, appContextWR));
  }

  /**
   * Initiates loading of file/uri by getting an input stream associated with it on a worker thread
   */
  private static void load(final TextEditorActivity activity) {
    activity.dismissLoadingSnackbar();

    activity.loadingSnackbar =
        Snackbar.make(activity.scrollView, R.string.loading, Snackbar.LENGTH_SHORT);
    activity.loadingSnackbar.show();

    final WeakReference<TextEditorActivity> textEditorActivityWR = new WeakReference<>(activity);
    final WeakReference<Context> appContextWR =
        new WeakReference<>(activity.getApplicationContext());

    TaskKt.fromTask(new ReadTextFileTask(activity, textEditorActivityWR, appContextWR));
  }

  public void setReadOnly() {
    mainTextView.setInputType(EditorInfo.TYPE_NULL);
    mainTextView.setSingleLine(false);
    mainTextView.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
  }

  public void dismissLoadingSnackbar() {
    if (loadingSnackbar != null) {
      loadingSnackbar.dismiss();
      loadingSnackbar = null;
    }
  }

  @Override
  public void onBackPressed() {
    checkUnsavedChanges();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.text, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    final TextEditorActivityViewModel viewModel =
        new ViewModelProvider(this).get(TextEditorActivityViewModel.class);

    menu.findItem(R.id.save).setVisible(viewModel.getModified());
    menu.findItem(R.id.monofont).setChecked(inputTypefaceMono.equals(mainTextView.getTypeface()));
    
    // Update syntax highlighting toggle
    MenuItem highlightItem = menu.findItem(R.id.syntax_highlight);
    if (highlightItem != null) {
      highlightItem.setChecked(syntaxHighlighter != null && 
          syntaxHighlighter.isHighlightingAvailable());
      highlightItem.setEnabled(syntaxHighlighter != null && 
          syntaxHighlighter.getCurrentLanguage() != SyntaxHighlighter.Language.UNKNOWN);
    }
    
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    final TextEditorActivityViewModel viewModel =
        new ViewModelProvider(this).get(TextEditorActivityViewModel.class);
    final EditableFileAbstraction editableFileAbstraction = viewModel.getFile();

    if (item.getItemId() == android.R.id.home) {
      checkUnsavedChanges();
    } else if (item.getItemId() == R.id.save) {
      // Make sure EditText is visible before saving!
      if (mainTextView.getText() != null) {
        saveFile(this, mainTextView.getText().toString());
      }
    } else if (item.getItemId() == R.id.details) {
      if (editableFileAbstraction.scheme.equals(FILE)
          && editableFileAbstraction.hybridFileParcelable.getFile() != null
          && editableFileAbstraction.hybridFileParcelable.getFile().exists()) {
        GeneralDialogCreation.showPropertiesDialogWithoutPermissions(
            editableFileAbstraction.hybridFileParcelable, this, getAppTheme());
      } else if (editableFileAbstraction.scheme.equals(CONTENT)) {
        if (getApplicationContext()
            .getPackageName()
            .equals(editableFileAbstraction.uri.getAuthority())) {
          File file = FileUtils.fromContentUri(editableFileAbstraction.uri);
          HybridFileParcelable p = new HybridFileParcelable(file.getAbsolutePath());
          if (isRootExplorer()) p.setMode(OpenMode.ROOT);
          GeneralDialogCreation.showPropertiesDialogWithoutPermissions(p, this, getAppTheme());
        }
      } else {
        Toast.makeText(this, R.string.no_obtainable_info, Toast.LENGTH_SHORT).show();
      }
    } else if (item.getItemId() == R.id.openwith) {
      if (editableFileAbstraction != null && editableFileAbstraction.scheme.equals(FILE)) {
        File currentFile = editableFileAbstraction.hybridFileParcelable.getFile();
        if (currentFile != null && currentFile.exists()) {
          boolean useNewStack = getBoolean(PREFERENCE_TEXTEDITOR_NEWSTACK);
          FileUtils.openWith(currentFile, this, useNewStack);
        } else {
          Toast.makeText(this, R.string.not_allowed, Toast.LENGTH_SHORT).show();
        }
      } else {
        Toast.makeText(this, R.string.reopen_from_source, Toast.LENGTH_SHORT).show();
      }
    } else if (item.getItemId() == R.id.find) {
      if (searchViewLayout.isShown()) {
        hideSearchView();
      } else {
        revealSearchView();
      }
    } else if (item.getItemId() == R.id.replace) {
      if (replaceViewLayout != null) {
        if (replaceViewLayout.isShown()) {
          hideReplaceView();
        } else {
          revealReplaceView();
        }
      }
    } else if (item.getItemId() == R.id.monofont) {
      item.setChecked(!item.isChecked());
      mainTextView.setTypeface(item.isChecked() ? inputTypefaceMono : inputTypefaceDefault);
    } else if (item.getItemId() == R.id.syntax_highlight) {
      toggleSyntaxHighlighting();
    } else if (item.getItemId() == R.id.fold_all) {
      if (codeFoldingManager != null) {
        codeFoldingManager.foldAll();
      }
    } else if (item.getItemId() == R.id.unfold_all) {
      if (codeFoldingManager != null) {
        codeFoldingManager.unfoldAll();
      }
    } else if (item.getItemId() == R.id.git_history) {
      showGitHistory();
    } else if (item.getItemId() == R.id.git_diff) {
      showGitDiff();
    } else {
      return false;
    }
    return super.onOptionsItemSelected(item);
  }

  /**
   * Toggle syntax highlighting
   */
  private void toggleSyntaxHighlighting() {
    if (syntaxHighlighter == null) return;
    
    if (syntaxHighlighter.isHighlightingAvailable()) {
      // Disable highlighting
      syntaxHighlighter.setLanguage(SyntaxHighlighter.Language.UNKNOWN);
      if (mainTextView.getText() != null) {
        // Remove all color spans
        ForegroundColorSpan[] spans = mainTextView.getText().getSpans(
            0, mainTextView.getText().length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan span : spans) {
          mainTextView.getText().removeSpan(span);
        }
      }
    } else {
      // Enable highlighting
      EditableFileAbstraction file = viewModel.getFile();
      if (file != null && file.name != null) {
        syntaxHighlighter.setLanguage(file.name);
        if (mainTextView.getText() != null) {
          syntaxHighlighter.applyHighlightingToEditable(mainTextView.getText());
        }
      }
    }
    invalidateOptionsMenu();
  }

  /**
   * Show Git history dialog
   */
  private void showGitHistory() {
    EditableFileAbstraction file = viewModel.getFile();
    if (file == null || file.scheme != FILE) return;
    
    File javaFile = file.hybridFileParcelable.getFile();
    if (javaFile == null) return;
    
    GitIntegration.getFileHistory(javaFile, 20, new GitIntegration.GitCallback<List<GitIntegration.GitCommit>>() {
      @Override
      public void onResult(List<GitIntegration.GitCommit> commits) {
        showHistoryDialog(commits);
      }
      
      @Override
      public void onError(String error) {
        Toast.makeText(TextEditorActivity.this, 
            getString(R.string.git_history_error, error), Toast.LENGTH_SHORT).show();
      }
    });
  }

  /**
   * Show history dialog
   */
  private void showHistoryDialog(List<GitIntegration.GitCommit> commits) {
    if (commits == null || commits.isEmpty()) {
      Toast.makeText(this, R.string.git_no_history, Toast.LENGTH_SHORT).show();
      return;
    }
    
    String[] items = new String[commits.size()];
    for (int i = 0; i < commits.size(); i++) {
      GitIntegration.GitCommit commit = commits.get(i);
      items[i] = commit.shortHash + " - " + commit.message + "\n" +
                 commit.author + " - " + commit.date;
    }
    
    new MaterialDialog.Builder(this)
        .title(R.string.git_history_title)
        .items(items)
        .positiveText(R.string.close)
        .build()
        .show();
  }

  /**
   * Show Git diff dialog
   */
  private void showGitDiff() {
    EditableFileAbstraction file = viewModel.getFile();
    if (file == null || file.scheme != FILE) return;
    
    File javaFile = file.hybridFileParcelable.getFile();
    if (javaFile == null) return;
    
    GitIntegration.getFileDiff(javaFile, new GitIntegration.GitCallback<List<GitIntegration.LineChange>>() {
      @Override
      public void onResult(List<GitIntegration.LineChange> changes) {
        showDiffDialog(changes);
      }
      
      @Override
      public void onError(String error) {
        Toast.makeText(TextEditorActivity.this, 
            getString(R.string.git_diff_error, error), Toast.LENGTH_SHORT).show();
      }
    });
  }

  /**
   * Show diff dialog
   */
  private void showDiffDialog(List<GitIntegration.LineChange> changes) {
    if (changes == null || changes.isEmpty()) {
      Toast.makeText(this, R.string.git_no_changes, Toast.LENGTH_SHORT).show();
      return;
    }
    
    StringBuilder diffText = new StringBuilder();
    for (GitIntegration.LineChange change : changes) {
      switch (change.type) {
        case ADDED:
          diffText.append("+ ").append(change.content).append("\n");
          break;
        case DELETED:
          diffText.append("- ").append(change.content).append("\n");
          break;
        case UNCHANGED:
          diffText.append("  ").append(change.content).append("\n");
          break;
      }
    }
    
    new MaterialDialog.Builder(this)
        .title(R.string.git_diff_title)
        .content(diffText.toString())
        .positiveText(R.string.close)
        .build()
        .show();
  }

  /**
   * Perform replace operation
   */
  private void performReplace() {
    if (mainTextView.getText() == null || searchEditText.getText() == null || 
        replaceEditText == null || replaceEditText.getText() == null) {
      return;
    }
    
    String searchText = searchEditText.getText().toString();
    String replaceText = replaceEditText.getText().toString();
    String content = mainTextView.getText().toString();
    
    if (searchText.isEmpty()) return;
    
    int cursorPosition = mainTextView.getSelectionStart();
    boolean useRegex = regexCheckBox != null && regexCheckBox.isChecked();
    boolean caseSensitive = caseSensitiveCheckBox == null || caseSensitiveCheckBox.isChecked();
    
    try {
      String result;
      if (useRegex) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern pattern = Pattern.compile(searchText, flags);
        result = pattern.matcher(content).replaceFirst(replaceText);
      } else {
        if (caseSensitive) {
          result = content.replaceFirst(Pattern.quote(searchText), replaceText);
        } else {
          result = content.replaceFirst("(?i)" + Pattern.quote(searchText), replaceText);
        }
      }
      
      mainTextView.setText(result);
      mainTextView.setSelection(Math.min(cursorPosition, result.length()));
      
    } catch (PatternSyntaxException e) {
      Toast.makeText(this, R.string.invalid_regex, Toast.LENGTH_SHORT).show();
    }
  }

  /**
   * Perform replace all operation
   */
  private void performReplaceAll() {
    if (mainTextView.getText() == null || searchEditText.getText() == null || 
        replaceEditText == null || replaceEditText.getText() == null) {
      return;
    }
    
    String searchText = searchEditText.getText().toString();
    String replaceText = replaceEditText.getText().toString();
    String content = mainTextView.getText().toString();
    
    if (searchText.isEmpty()) return;
    
    boolean useRegex = regexCheckBox != null && regexCheckBox.isChecked();
    boolean caseSensitive = caseSensitiveCheckBox == null || caseSensitiveCheckBox.isChecked();
    
    try {
      String result;
      if (useRegex) {
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        Pattern pattern = Pattern.compile(searchText, flags);
        result = pattern.matcher(content).replaceAll(replaceText);
      } else {
        if (caseSensitive) {
          result = content.replace(searchText, replaceText);
        } else {
          result = content.replaceAll("(?i)" + Pattern.quote(searchText), replaceText);
        }
      }
      
      int replacements = countOccurrences(content, searchText, useRegex, caseSensitive);
      mainTextView.setText(result);
      
      Toast.makeText(this, 
          getString(R.string.replaced_count, replacements), Toast.LENGTH_SHORT).show();
      
    } catch (PatternSyntaxException e) {
      Toast.makeText(this, R.string.invalid_regex, Toast.LENGTH_SHORT).show();
    }
  }

  /**
   * Count occurrences of search text
   */
  private int countOccurrences(String text, String search, boolean useRegex, boolean caseSensitive) {
    if (useRegex) {
      int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
      Pattern pattern = Pattern.compile(search, flags);
      Matcher matcher = pattern.matcher(text);
      int count = 0;
      while (matcher.find()) count++;
      return count;
    } else {
      if (caseSensitive) {
        return text.split(Pattern.quote(search), -1).length - 1;
      } else {
        return text.split("(?i)" + Pattern.quote(search), -1).length - 1;
      }
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    final TextEditorActivityViewModel viewModel =
        new ViewModelProvider(this).get(TextEditorActivityViewModel.class);
    final File cacheFile = viewModel.getCacheFile();

    if (cacheFile != null && cacheFile.exists()) {
      cacheFile.delete();
    }
    
    if (highlightHandler != null && highlightRunnable != null) {
      highlightHandler.removeCallbacks(highlightRunnable);
    }
  }

  @Override
  public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
    // condition to check if callback is called in search editText
    if (searchEditText.getText() != null
        && charSequence.hashCode() == searchEditText.getText().hashCode()) {
      final TextEditorActivityViewModel viewModel =
          new ViewModelProvider(this).get(TextEditorActivityViewModel.class);

      // clearing before adding new values
      if (searchTextTask != null) {
        searchTextTask.cancel(true);
        searchTextTask = null; // dereference the task for GC
      }

      cleanSpans(viewModel);
    }
  }

  @Override
  public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
    if (mainTextView.getText() != null
        && charSequence.hashCode() == mainTextView.getText().hashCode()) {
      final TextEditorActivityViewModel viewModel =
          new ViewModelProvider(this).get(TextEditorActivityViewModel.class);
      final Timer oldTimer = viewModel.getTimer();
      viewModel.setTimer(null);

      if (oldTimer != null) {
        oldTimer.cancel();
        oldTimer.purge();
      }

      final WeakReference<TextEditorActivity> textEditorActivityWR = new WeakReference<>(this);

      Timer newTimer = new Timer();
      newTimer.schedule(
          new TimerTask() {
            boolean modified;

            @Override
            public void run() {
              final TextEditorActivity textEditorActivity = textEditorActivityWR.get();
              if (textEditorActivity == null) {
                return;
              }

              final TextEditorActivityViewModel viewModel =
                  new ViewModelProvider(textEditorActivity).get(TextEditorActivityViewModel.class);

              modified =
                  textEditorActivity.mainTextView.getText() != null
                      && !textEditorActivity
                          .mainTextView
                          .getText()
                          .toString()
                          .equals(viewModel.getOriginal());
              if (viewModel.getModified() != modified) {
                viewModel.setModified(modified);
                invalidateOptionsMenu();
              }
            }
          },
          250);

      viewModel.setTimer(newTimer);
      
      // Schedule syntax highlighting
      if (highlightHandler != null && highlightRunnable != null) {
        highlightHandler.removeCallbacks(highlightRunnable);
        highlightHandler.postDelayed(highlightRunnable, HIGHLIGHT_DELAY_MS);
      }
      
      // Update line numbers
      updateLineNumbers();
    }
  }

  @Override
  public void afterTextChanged(Editable editable) {
    // searchBox callback block
    if (searchEditText.getText() != null
        && editable.hashCode() == searchEditText.getText().hashCode()) {
      final WeakReference<TextEditorActivity> textEditorActivityWR = new WeakReference<>(this);

      final OnProgressUpdate<SearchResultIndex> onProgressUpdate =
          index -> {
            final TextEditorActivity textEditorActivity = textEditorActivityWR.get();
            if (textEditorActivity == null) {
              return;
            }
            textEditorActivity.colorSearchResult(index, getPrimary());
          };

      final OnAsyncTaskFinished<List<SearchResultIndex>> onAsyncTaskFinished =
          data -> {
            final TextEditorActivity textEditorActivity = textEditorActivityWR.get();

            if (textEditorActivity == null) {
              return;
            }

            final TextEditorActivityViewModel viewModel =
                new ViewModelProvider(textEditorActivity).get(TextEditorActivityViewModel.class);
            viewModel.setSearchResultIndices(data);

            for (SearchResultIndex searchResultIndex : data) {
              textEditorActivity.colorSearchResult(searchResultIndex, getPrimary());
            }

            if (data.size() != 0) {
              textEditorActivity.upButton.setEnabled(true);
              textEditorActivity.downButton.setEnabled(true);

              // downButton
              textEditorActivity.onClick(textEditorActivity.downButton);
            } else {
              textEditorActivity.upButton.setEnabled(false);
              textEditorActivity.downButton.setEnabled(false);
            }
          };

      if (mainTextView.getText() != null) {
        searchTextTask =
            new SearchTextTask(
                mainTextView.getText().toString(),
                editable.toString(),
                onProgressUpdate,
                onAsyncTaskFinished);
        searchTextTask.execute();
      }
    }
  }

  private void revealSearchView() {

    searchViewLayout.setVisibility(View.VISIBLE);

    Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_in_top);

    animation.setAnimationListener(
        new Animation.AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {

            searchEditText.requestFocus();

            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });

    searchViewLayout.startAnimation(animation);
  }

  private void hideSearchView() {

    Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_out_top);

    animation.setAnimationListener(
        new Animation.AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {

            searchViewLayout.setVisibility(View.GONE);

            cleanSpans(viewModel);
            searchEditText.setText("");

            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(
                    searchEditText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });

    searchViewLayout.startAnimation(animation);
  }

  private void revealReplaceView() {
    if (replaceViewLayout == null) return;
    
    replaceViewLayout.setVisibility(View.VISIBLE);
    Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_in_top);
    animation.setAnimationListener(
        new Animation.AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {
            if (replaceEditText != null) {
              replaceEditText.requestFocus();
              ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                  .showSoftInput(replaceEditText, InputMethodManager.SHOW_IMPLICIT);
            }
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
    replaceViewLayout.startAnimation(animation);
  }

  private void hideReplaceView() {
    if (replaceViewLayout == null) return;
    
    Animation animation = AnimationUtils.loadAnimation(this, R.anim.fade_out_top);
    animation.setAnimationListener(
        new Animation.AnimationListener() {
          @Override
          public void onAnimationStart(Animation animation) {}

          @Override
          public void onAnimationEnd(Animation animation) {
            replaceViewLayout.setVisibility(View.GONE);
            if (replaceEditText != null) {
              ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                  .hideSoftInputFromWindow(
                      replaceEditText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
            }
          }

          @Override
          public void onAnimationRepeat(Animation animation) {}
        });
    replaceViewLayout.startAnimation(animation);
  }

  @Override
  public void onClick(View v) {
    final TextEditorActivityViewModel viewModel =
        new ViewModelProvider(this).get(TextEditorActivityViewModel.class);

    if (v.getId() == R.id.textEditorSearchPrevButton) {
      // upButton
      if (viewModel.getCurrent() > 0) {
        unhighlightCurrentSearchResult(viewModel);

        // highlighting previous element in list
        viewModel.setCurrent(viewModel.getCurrent() - 1);

        highlightCurrentSearchResult(viewModel);
      }
    } else if (v.getId() == R.id.textEditorSearchNextButton) {
      // downButton
      if (viewModel.getCurrent() < viewModel.getSearchResultIndices().size() - 1) {
        unhighlightCurrentSearchResult(viewModel);

        viewModel.setCurrent(viewModel.getCurrent() + 1);

        highlightCurrentSearchResult(viewModel);
      }
    } else {
      throw new IllegalStateException();
    }
  }

  private void unhighlightCurrentSearchResult(final TextEditorActivityViewModel viewModel) {
    if (viewModel.getCurrent() == -1) {
      return;
    }

    SearchResultIndex resultIndex = viewModel.getSearchResultIndices().get(viewModel.getCurrent());
    colorSearchResult(resultIndex, getPrimary());
  }

  private void highlightCurrentSearchResult(final TextEditorActivityViewModel viewModel) {
    SearchResultIndex keyValueNew = viewModel.getSearchResultIndices().get(viewModel.getCurrent());
    colorSearchResult(keyValueNew, getAccent());

    // scrolling to the highlighted element
    if (getSupportActionBar() != null) {
      scrollView.scrollTo(
          0,
          (Integer) keyValueNew.getLineNumber()
              + mainTextView.getLineHeight()
              + Math.round(mainTextView.getLineSpacingExtra())
              - getSupportActionBar().getHeight());
    }
  }

  private void colorSearchResult(SearchResultIndex resultIndex, @ColorInt int color) {
    if (mainTextView.getText() != null) {
      mainTextView
          .getText()
          .setSpan(
              new BackgroundColorSpan(color),
              (Integer) resultIndex.getStartCharNumber(),
              (Integer) resultIndex.getEndCharNumber(),
              Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    }
  }

  private void cleanSpans(TextEditorActivityViewModel viewModel) {
    // resetting current highlight and line number
    viewModel.setSearchResultIndices(Collections.emptyList());
    viewModel.setCurrent(-1);
    viewModel.setLine(0);

    // clearing textView spans
    if (mainTextView.getText() != null) {
      BackgroundColorSpan[] colorSpans =
          mainTextView.getText().getSpans(0, mainTextView.length(), BackgroundColorSpan.class);
      for (BackgroundColorSpan colorSpan : colorSpans) {
        mainTextView.getText().removeSpan(colorSpan);
      }
    }
  }
}