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

package com.amaze.filemanager.ui.fragments.mediaplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.amaze.filemanager.R;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Video player fragment with gesture controls for progress, volume, and brightness.
 * Supports common formats: MP4, MKV, AVI
 */
public class VideoPlayerFragment extends Fragment implements Player.Listener {

    private static final Logger LOG = LoggerFactory.getLogger(VideoPlayerFragment.class);

    private static final String ARG_FILE_URI = "file_uri";
    private static final String ARG_FILE_PATH = "file_path";
    private static final String ARG_FILE_NAME = "file_name";
    private static final String ARG_MIME_TYPE = "mime_type";

    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_VOLUME = 1;
    private static final int GESTURE_BRIGHTNESS = 2;
    private static final int GESTURE_SEEK = 3;

    private PlayerView playerView;
    private ExoPlayer exoPlayer;
    private ProgressBar loadingProgressBar;
    private LinearLayout controlsLayout;
    private SeekBar seekBar;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView gestureText;
    private ImageButton playPauseButton;
    private ImageButton fullscreenButton;
    private ImageButton lockButton;
    private AppCompatImageButton backButton;

    private Uri fileUri;
    private String filePath;
    private String fileName;
    private String mimeType;

    private Handler progressHandler;
    private Runnable progressRunnable;
    private Handler hideControlsHandler;
    private Runnable hideControlsRunnable;

    private GestureDetector gestureDetector;
    private ScaleGestureDetector scaleGestureDetector;
    private AudioManager audioManager;

    private int currentGesture = GESTURE_NONE;
    private float startX = 0;
    private float startY = 0;
    private int startVolume = 0;
    private int maxVolume = 0;
    private float startBrightness = 0;
    private long startSeekPosition = 0;
    private boolean isFullscreen = false;
    private boolean isLocked = false;
    private boolean isControlsVisible = true;

    /**
     * Create new instance of VideoPlayerFragment
     */
    public static VideoPlayerFragment newInstance(Uri fileUri, String filePath, String fileName, String mimeType) {
        VideoPlayerFragment fragment = new VideoPlayerFragment();
        Bundle args = new Bundle();
        if (fileUri != null) {
            args.putParcelable(ARG_FILE_URI, fileUri);
        }
        args.putString(ARG_FILE_PATH, filePath);
        args.putString(ARG_FILE_NAME, fileName);
        args.putString(ARG_MIME_TYPE, mimeType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            fileUri = getArguments().getParcelable(ARG_FILE_URI);
            filePath = getArguments().getString(ARG_FILE_PATH);
            fileName = getArguments().getString(ARG_FILE_NAME);
            mimeType = getArguments().getString(ARG_MIME_TYPE);
        }
        progressHandler = new Handler(Looper.getMainLooper());
        hideControlsHandler = new Handler(Looper.getMainLooper());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_video_player, container, false);
        initViews(view);
        setupPlayer();
        setupGestures();
        setupControls();
        return view;
    }

    private void initViews(View view) {
        playerView = view.findViewById(R.id.videoPlayerView);
        loadingProgressBar = view.findViewById(R.id.videoLoadingProgress);
        controlsLayout = view.findViewById(R.id.videoControlsLayout);
        seekBar = view.findViewById(R.id.videoSeekBar);
        currentTimeText = view.findViewById(R.id.videoCurrentTime);
        totalTimeText = view.findViewById(R.id.videoTotalTime);
        gestureText = view.findViewById(R.id.gestureText);
        playPauseButton = view.findViewById(R.id.videoPlayPauseButton);
        fullscreenButton = view.findViewById(R.id.videoFullscreenButton);
        lockButton = view.findViewById(R.id.videoLockButton);
        backButton = view.findViewById(R.id.videoBackButton);

        // Hide default controller
        playerView.setUseController(false);
    }

    private void setupPlayer() {
        Context context = requireContext();
        exoPlayer = new ExoPlayer.Builder(context).build();
        playerView.setPlayer(exoPlayer);
        exoPlayer.addListener(this);

        // Build media source
        Uri mediaUri = fileUri != null ? fileUri : Uri.parse(filePath);
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(context);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(mediaUri));

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.play();

        // Start progress updates
        startProgressUpdates();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupGestures() {
        Context context = requireContext();
        audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        maxVolume = audioManager != null ? audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!isLocked) {
                    togglePlayPause();
                }
                return true;
            }
        });

        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                // Handle pinch to zoom if needed
                return true;
            }
        });

        playerView.setOnTouchListener((v, event) -> {
            if (isLocked) {
                gestureDetector.onTouchEvent(event);
                return true;
            }

            scaleGestureDetector.onTouchEvent(event);
            handleTouchEvent(event);
            return true;
        });
    }

    private void handleTouchEvent(MotionEvent event) {
        float screenWidth = playerView.getWidth();
        float screenHeight = playerView.getHeight();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                startVolume = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 0;
                startBrightness = getCurrentBrightness();
                startSeekPosition = exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
                currentGesture = GESTURE_NONE;
                hideControlsHandler.removeCallbacks(hideControlsRunnable);
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaX = event.getX() - startX;
                float deltaY = event.getY() - startY;
                float absDeltaX = Math.abs(deltaX);
                float absDeltaY = Math.abs(deltaY);

                if (currentGesture == GESTURE_NONE) {
                    if (absDeltaX > absDeltaY && absDeltaX > 50) {
                        currentGesture = GESTURE_SEEK;
                    } else if (absDeltaY > 50) {
                        if (startX < screenWidth / 2) {
                            currentGesture = GESTURE_BRIGHTNESS;
                        } else {
                            currentGesture = GESTURE_VOLUME;
                        }
                    }
                }

                handleGestureMove(deltaX, deltaY, screenWidth, screenHeight);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                gestureText.setVisibility(View.GONE);
                if (isControlsVisible) {
                    scheduleHideControls();
                }
                currentGesture = GESTURE_NONE;
                break;
        }

        gestureDetector.onTouchEvent(event);
    }

    private void handleGestureMove(float deltaX, float deltaY, float screenWidth, float screenHeight) {
        switch (currentGesture) {
            case GESTURE_VOLUME:
                float volumePercent = -deltaY / screenHeight;
                int newVolume = (int) (startVolume + volumePercent * maxVolume);
                newVolume = Math.max(0, Math.min(newVolume, maxVolume));
                if (audioManager != null) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
                }
                showGestureText(getString(R.string.volume) + ": " + (newVolume * 100 / maxVolume) + "%");
                break;

            case GESTURE_BRIGHTNESS:
                float brightnessPercent = -deltaY / screenHeight;
                float newBrightness = Math.max(0.01f, Math.min(1f, startBrightness + brightnessPercent));
                setBrightness(newBrightness);
                showGestureText(getString(R.string.brightness) + ": " + (int)(newBrightness * 100) + "%");
                break;

            case GESTURE_SEEK:
                float seekPercent = deltaX / screenWidth;
                long seekDelta = (long) (seekPercent * 120000); // Max 2 minutes seek
                long newPosition = Math.max(0, startSeekPosition + seekDelta);
                if (exoPlayer != null) {
                    newPosition = Math.min(newPosition, exoPlayer.getDuration());
                    exoPlayer.seekTo(newPosition);
                }
                String seekText = seekDelta >= 0 ? "+" + formatTime(seekDelta) : formatTime(seekDelta);
                showGestureText(getString(R.string.seek) + ": " + seekText + "\n" + formatTime(newPosition));
                break;
        }
    }

    private float getCurrentBrightness() {
        Activity activity = getActivity();
        if (activity != null) {
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            return params.screenBrightness;
        }
        return 0.5f;
    }

    private void setBrightness(float brightness) {
        Activity activity = getActivity();
        if (activity != null) {
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.screenBrightness = brightness;
            activity.getWindow().setAttributes(params);
        }
    }

    private void showGestureText(String text) {
        gestureText.setText(text);
        gestureText.setVisibility(View.VISIBLE);
    }

    private void setupControls() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        
        lockButton.setOnClickListener(v -> toggleLock());
        
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && exoPlayer != null) {
                    long position = (long) ((progress / 100f) * exoPlayer.getDuration());
                    currentTimeText.setText(formatTime(position));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                hideControlsHandler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (exoPlayer != null) {
                    long position = (long) ((seekBar.getProgress() / 100f) * exoPlayer.getDuration());
                    exoPlayer.seekTo(position);
                }
                scheduleHideControls();
            }
        });

        scheduleHideControls();
    }

    private void togglePlayPause() {
        if (exoPlayer != null) {
            if (exoPlayer.isPlaying()) {
                exoPlayer.pause();
                playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
            } else {
                exoPlayer.play();
                playPauseButton.setImageResource(R.drawable.ic_pause_white_24dp);
            }
        }
    }

    private void toggleFullscreen() {
        Activity activity = getActivity();
        if (activity == null) return;

        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit_white_24dp);
            hideSystemUI();
        } else {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            fullscreenButton.setImageResource(R.drawable.ic_fullscreen_white_24dp);
            showSystemUI();
        }
    }

    private void toggleLock() {
        isLocked = !isLocked;
        if (isLocked) {
            lockButton.setImageResource(R.drawable.ic_lock_white_24dp);
            controlsLayout.setVisibility(View.GONE);
            backButton.setVisibility(View.GONE);
        } else {
            lockButton.setImageResource(R.drawable.ic_lock_open_white_24dp);
            controlsLayout.setVisibility(View.VISIBLE);
            backButton.setVisibility(View.VISIBLE);
            scheduleHideControls();
        }
    }

    private void toggleControls() {
        if (isLocked) return;
        
        isControlsVisible = !isControlsVisible;
        if (isControlsVisible) {
            controlsLayout.setVisibility(View.VISIBLE);
            backButton.setVisibility(View.VISIBLE);
            scheduleHideControls();
        } else {
            controlsLayout.setVisibility(View.GONE);
            backButton.setVisibility(View.GONE);
        }
    }

    private void scheduleHideControls() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
        hideControlsRunnable = () -> {
            if (isControlsVisible && !isLocked && exoPlayer != null && exoPlayer.isPlaying()) {
                toggleControls();
            }
        };
        hideControlsHandler.postDelayed(hideControlsRunnable, 3000);
    }

    private void hideSystemUI() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
            );
        }
    }

    private void showSystemUI() {
        Activity activity = getActivity();
        if (activity != null) {
            activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    private void startProgressUpdates() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (exoPlayer != null) {
                    long currentPosition = exoPlayer.getCurrentPosition();
                    long duration = exoPlayer.getDuration();
                    
                    if (duration > 0) {
                        int progress = (int) ((currentPosition * 100) / duration);
                        seekBar.setProgress(progress);
                    }
                    
                    currentTimeText.setText(formatTime(currentPosition));
                    totalTimeText.setText(formatTime(duration));
                }
                progressHandler.postDelayed(this, 1000);
            }
        };
        progressHandler.post(progressRunnable);
    }

    private String formatTime(long millis) {
        if (millis < 0) millis = 0;
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                loadingProgressBar.setVisibility(View.VISIBLE);
                break;
            case Player.STATE_READY:
                loadingProgressBar.setVisibility(View.GONE);
                if (exoPlayer.isPlaying()) {
                    playPauseButton.setImageResource(R.drawable.ic_pause_white_24dp);
                } else {
                    playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
                }
                break;
            case Player.STATE_ENDED:
                playPauseButton.setImageResource(R.drawable.ic_replay_white_24dp);
                break;
            case Player.STATE_IDLE:
                loadingProgressBar.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            playPauseButton.setImageResource(R.drawable.ic_pause_white_24dp);
            scheduleHideControls();
        } else {
            playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
        }
    }

    @Override
    public void onPlayerError(@NonNull com.google.android.exoplayer2.PlaybackException error) {
        LOG.error("Player error: " + error.getMessage(), error);
        Toast.makeText(requireContext(), R.string.error_io, Toast.LENGTH_LONG).show();
    }

    public void onBackPressed() {
        // Clean up before exiting
        releasePlayer();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (exoPlayer != null && !isLocked) {
            exoPlayer.play();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        releasePlayer();
    }

    private void releasePlayer() {
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        if (hideControlsHandler != null && hideControlsRunnable != null) {
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
        }
        if (exoPlayer != null) {
            exoPlayer.removeListener(this);
            exoPlayer.release();
            exoPlayer = null;
        }
    }
}
