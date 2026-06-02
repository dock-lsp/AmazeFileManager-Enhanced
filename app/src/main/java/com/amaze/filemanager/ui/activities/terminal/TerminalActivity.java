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

package com.amaze.filemanager.ui.activities.terminal;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.ui.fragments.terminal.TerminalFragment;
import com.amaze.filemanager.ui.theme.AppTheme;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal emulator activity with multi-tab support.
 * Provides a full-featured terminal interface using libsu.
 */
public class TerminalActivity extends ThemedActivity {

    public static final String ARG_INITIAL_PATH = "initial_path";
    public static final String ARG_USE_ROOT = "use_root";
    public static final String ARG_SELECTED_FILES = "selected_files";

    private static final String PREFS_TERMINAL = "terminal_prefs";
    private static final String KEY_FONT_SIZE = "terminal_font_size";
    private static final String KEY_THEME = "terminal_theme";
    private static final String KEY_SHOW_KEYBOARD = "terminal_show_keyboard";

    public static final int THEME_DARK = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_BLACK = 2;

    private ViewPager viewPager;
    private TabLayout tabLayout;
    private TerminalPagerAdapter pagerAdapter;
    private FloatingActionButton fabAddTab;
    private FloatingActionButton fabKeyboard;

    private String initialPath;
    private boolean useRoot;
    private ArrayList<String> selectedFiles;

    private SharedPreferences terminalPrefs;
    private int currentFontSize = 14;
    private int currentTheme = THEME_DARK;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);

        terminalPrefs = getSharedPreferences(PREFS_TERMINAL, Context.MODE_PRIVATE);
        currentFontSize = terminalPrefs.getInt(KEY_FONT_SIZE, 14);
        currentTheme = terminalPrefs.getInt(KEY_THEME, THEME_DARK);

        parseIntent();
        initToolbar();
        initViews();
        setupViewPager();
    }

    private void parseIntent() {
        Intent intent = getIntent();
        initialPath = intent.getStringExtra(ARG_INITIAL_PATH);
        useRoot = intent.getBooleanExtra(ARG_USE_ROOT, false);
        selectedFiles = intent.getStringArrayListExtra(ARG_SELECTED_FILES);

        if (initialPath == null) {
            initialPath = System.getenv("HOME");
            if (initialPath == null) {
                initialPath = "/data/data/" + getPackageName();
            }
        }
    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.terminal);
        }
    }

    private void initViews() {
        viewPager = findViewById(R.id.terminal_viewpager);
        tabLayout = findViewById(R.id.terminal_tabs);
        fabAddTab = findViewById(R.id.fab_add_tab);
        fabKeyboard = findViewById(R.id.fab_keyboard);

        fabAddTab.setOnClickListener(v -> addNewTab());
        fabKeyboard.setOnClickListener(v -> toggleKeyboard());

        // Apply theme colors
        applyTerminalTheme();
    }

    private void setupViewPager() {
        pagerAdapter = new TerminalPagerAdapter(getSupportFragmentManager());
        viewPager.setAdapter(pagerAdapter);
        tabLayout.setupWithViewPager(viewPager);

        // Add initial tab
        addNewTab();
    }

    /**
     * Adds a new terminal tab
     */
    public void addNewTab() {
        String tabTitle = getString(R.string.terminal_tab_title, pagerAdapter.getCount() + 1);
        TerminalFragment fragment = TerminalFragment.newInstance(initialPath, useRoot, currentFontSize, currentTheme);
        pagerAdapter.addFragment(fragment, tabTitle);
        pagerAdapter.notifyDataSetChanged();
        viewPager.setCurrentItem(pagerAdapter.getCount() - 1);
    }

    /**
     * Closes a terminal tab
     */
    public void closeTab(int position) {
        if (pagerAdapter.getCount() <= 1) {
            // Don't close the last tab, just clear it
            TerminalFragment fragment = pagerAdapter.getFragment(position);
            if (fragment != null) {
                fragment.clearTerminal();
            }
            return;
        }

        pagerAdapter.removeFragment(position);
        pagerAdapter.notifyDataSetChanged();
    }

    private void toggleKeyboard() {
        TerminalFragment currentFragment = pagerAdapter.getFragment(viewPager.getCurrentItem());
        if (currentFragment != null) {
            currentFragment.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(currentFragment.getView(), InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.terminal_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_font_increase) {
            changeFontSize(1);
            return true;
        } else if (id == R.id.action_font_decrease) {
            changeFontSize(-1);
            return true;
        } else if (id == R.id.action_theme_dark) {
            setTerminalTheme(THEME_DARK);
            return true;
        } else if (id == R.id.action_theme_light) {
            setTerminalTheme(THEME_LIGHT);
            return true;
        } else if (id == R.id.action_theme_black) {
            setTerminalTheme(THEME_BLACK);
            return true;
        } else if (id == R.id.action_toggle_root) {
            toggleRootMode();
            return true;
        } else if (id == R.id.action_clear) {
            clearCurrentTerminal();
            return true;
        } else if (id == R.id.action_close_tab) {
            closeTab(viewPager.getCurrentItem());
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void changeFontSize(int delta) {
        currentFontSize = Math.max(8, Math.min(24, currentFontSize + delta));
        terminalPrefs.edit().putInt(KEY_FONT_SIZE, currentFontSize).apply();

        for (int i = 0; i < pagerAdapter.getCount(); i++) {
            TerminalFragment fragment = pagerAdapter.getFragment(i);
            if (fragment != null) {
                fragment.setFontSize(currentFontSize);
            }
        }

        Toast.makeText(this, getString(R.string.terminal_font_size_changed, currentFontSize), Toast.LENGTH_SHORT).show();
    }

    private void setTerminalTheme(int theme) {
        currentTheme = theme;
        terminalPrefs.edit().putInt(KEY_THEME, currentTheme).apply();
        applyTerminalTheme();

        for (int i = 0; i < pagerAdapter.getCount(); i++) {
            TerminalFragment fragment = pagerAdapter.getFragment(i);
            if (fragment != null) {
                fragment.setTerminalTheme(theme);
            }
        }
    }

    private void applyTerminalTheme() {
        switch (currentTheme) {
            case THEME_LIGHT:
                viewPager.setBackgroundColor(getResources().getColor(android.R.color.white));
                break;
            case THEME_BLACK:
                viewPager.setBackgroundColor(getResources().getColor(android.R.color.black));
                break;
            case THEME_DARK:
            default:
                viewPager.setBackgroundColor(getResources().getColor(R.color.holo_dark_background));
                break;
        }
    }

    private void toggleRootMode() {
        useRoot = !useRoot;
        String message = useRoot ? R.string.terminal_root_enabled : R.string.terminal_root_disabled;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

        // Apply to current fragment
        TerminalFragment currentFragment = pagerAdapter.getFragment(viewPager.getCurrentItem());
        if (currentFragment != null) {
            currentFragment.setUseRoot(useRoot);
        }
    }

    private void clearCurrentTerminal() {
        TerminalFragment currentFragment = pagerAdapter.getFragment(viewPager.getCurrentItem());
        if (currentFragment != null) {
            currentFragment.clearTerminal();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        TerminalFragment currentFragment = pagerAdapter.getFragment(viewPager.getCurrentItem());
        if (currentFragment != null) {
            if (currentFragment.handleKeyEvent(keyCode, event)) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        TerminalFragment currentFragment = pagerAdapter.getFragment(viewPager.getCurrentItem());
        if (currentFragment != null && currentFragment.isSessionActive()) {
            // Show confirmation dialog
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.terminal_close_title)
                    .setMessage(R.string.terminal_close_message)
                    .setPositiveButton(R.string.yes, (dialog, which) -> finish())
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up all terminal sessions
        for (int i = 0; i < pagerAdapter.getCount(); i++) {
            TerminalFragment fragment = pagerAdapter.getFragment(i);
            if (fragment != null) {
                fragment.cleanup();
            }
        }
    }

    /**
     * Gets the current terminal theme
     */
    public int getCurrentTerminalTheme() {
        return currentTheme;
    }

    /**
     * Gets the current font size
     */
    public int getCurrentFontSize() {
        return currentFontSize;
    }

    /**
     * Adapter for managing terminal tabs
     */
    private static class TerminalPagerAdapter extends FragmentPagerAdapter {
        private final List<TerminalFragment> fragments = new ArrayList<>();
        private final List<String> titles = new ArrayList<>();

        public TerminalPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        public void addFragment(TerminalFragment fragment, String title) {
            fragments.add(fragment);
            titles.add(title);
        }

        public void removeFragment(int position) {
            if (position >= 0 && position < fragments.size()) {
                TerminalFragment fragment = fragments.get(position);
                fragment.cleanup();
                fragments.remove(position);
                titles.remove(position);
            }
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            return fragments.get(position);
        }

        @Override
        public int getCount() {
            return fragments.size();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return titles.get(position);
        }

        public TerminalFragment getFragment(int position) {
            if (position >= 0 && position < fragments.size()) {
                return fragments.get(position);
            }
            return null;
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }
    }
}
