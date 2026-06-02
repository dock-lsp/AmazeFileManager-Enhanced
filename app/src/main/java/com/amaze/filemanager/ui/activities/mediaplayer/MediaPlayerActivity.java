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

package com.amaze.filemanager.ui.activities.mediaplayer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.ui.fragments.mediaplayer.AudioPlayerFragment;
import com.amaze.filemanager.ui.fragments.mediaplayer.VideoPlayerFragment;
import com.amaze.filemanager.ui.icons.MimeTypes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Media player activity for playing audio and video files using ExoPlayer.
 * Supports common formats: MP4, MKV, AVI, MP3, AAC, FLAC
 */
public class MediaPlayerActivity extends ThemedActivity {

    private static final Logger LOG = LoggerFactory.getLogger(MediaPlayerActivity.class);

    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_MIME_TYPE = "mime_type";
    public static final String EXTRA_IS_VIDEO = "is_video";

    private Toolbar toolbar;
    private String fileName;
    private boolean isVideo;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
        }

        initStatusBarResources(findViewById(R.id.mediaPlayerRootView));

        // Get intent extras
        Intent intent = getIntent();
        Uri fileUri = intent.getData();
        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        String mimeType = intent.getStringExtra(EXTRA_MIME_TYPE);

        if (fileUri == null && filePath == null) {
            Toast.makeText(this, R.string.no_file_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Determine if it's video or audio
        if (mimeType == null) {
            mimeType = MimeTypes.getMimeType(filePath != null ? filePath : fileUri.toString(), false);
        }
        isVideo = isVideoFile(mimeType);

        if (fileName == null) {
            fileName = getFileNameFromUri(fileUri != null ? fileUri : Uri.parse(filePath));
        }

        if (actionBar != null) {
            actionBar.setTitle(fileName);
        }

        // Load appropriate fragment
        loadPlayerFragment(fileUri, filePath, mimeType, savedInstanceState);
    }

    /**
     * Load the appropriate player fragment based on media type
     */
    private void loadPlayerFragment(Uri fileUri, String filePath, String mimeType, Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // Fragment will be restored automatically
            return;
        }

        Fragment fragment;
        if (isVideo) {
            fragment = VideoPlayerFragment.newInstance(fileUri, filePath, fileName, mimeType);
        } else {
            fragment = AudioPlayerFragment.newInstance(fileUri, filePath, fileName, mimeType);
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.mediaPlayerContainer, fragment);
        transaction.commit();
    }

    /**
     * Check if the mime type represents a video file
     */
    private boolean isVideoFile(String mimeType) {
        if (mimeType == null) return false;
        return mimeType.startsWith("video/");
    }

    /**
     * Extract file name from URI
     */
    private String getFileNameFromUri(Uri uri) {
        if (uri == null) return getString(R.string.unknown);
        String path = uri.getPath();
        if (path == null) return getString(R.string.unknown);
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < path.length() - 1 
            ? path.substring(lastSlash + 1) 
            : path;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // Let fragment handle any cleanup if needed
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.mediaPlayerContainer);
        if (currentFragment instanceof VideoPlayerFragment) {
            ((VideoPlayerFragment) currentFragment).onBackPressed();
        } else if (currentFragment instanceof AudioPlayerFragment) {
            ((AudioPlayerFragment) currentFragment).onBackPressed();
        }
        super.onBackPressed();
    }

    /**
     * Check if currently playing video
     */
    public boolean isPlayingVideo() {
        return isVideo;
    }

    /**
     * Get current file name
     */
    public String getFileName() {
        return fileName;
    }
}
