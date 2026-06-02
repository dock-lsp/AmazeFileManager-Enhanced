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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.fragment.app.Fragment;

import com.amaze.filemanager.R;
import com.amaze.filemanager.asynchronous.services.mediaplayer.AudioPlayerService;
import com.google.android.exoplayer2.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio player fragment with background playback support.
 * Supports common formats: MP3, AAC, FLAC
 */
public class AudioPlayerFragment extends Fragment implements Player.Listener {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlayerFragment.class);

    private static final String ARG_FILE_URI = "file_uri";
    private static final String ARG_FILE_PATH = "file_path";
    private static final String ARG_FILE_NAME = "file_name";
    private static final String ARG_MIME_TYPE = "mime_type";

    private TextView titleText;
    private TextView artistText;
    private TextView currentTimeText;
    private TextView totalTimeText;
    private SeekBar seekBar;
    private ImageButton playPauseButton;
    private ImageButton previousButton;
    private ImageButton nextButton;
    private ImageButton repeatButton;
    private ImageButton shuffleButton;
    private AppCompatImageButton backButton;

    private Uri fileUri;
    private String filePath;
    private String fileName;
    private String mimeType;

    private AudioPlayerService audioService;
    private boolean serviceBound = false;
    private Handler progressHandler;
    private Runnable progressRunnable;

    private int repeatMode = 0; // 0: off, 1: repeat one, 2: repeat all
    private boolean isShuffled = false;

    /**
     * Service connection to bind to AudioPlayerService
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AudioPlayerService.LocalBinder binder = (AudioPlayerService.LocalBinder) service;
            audioService = binder.getService();
            serviceBound = true;
            setupPlayer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            audioService = null;
            serviceBound = false;
        }
    };

    /**
     * Create new instance of AudioPlayerFragment
     */
    public static AudioPlayerFragment newInstance(Uri fileUri, String filePath, String fileName, String mimeType) {
        AudioPlayerFragment fragment = new AudioPlayerFragment();
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
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio_player, container, false);
        initViews(view);
        setupControls();
        bindToService();
        return view;
    }

    private void initViews(View view) {
        titleText = view.findViewById(R.id.audioTitleText);
        artistText = view.findViewById(R.id.audioArtistText);
        currentTimeText = view.findViewById(R.id.audioCurrentTime);
        totalTimeText = view.findViewById(R.id.audioTotalTime);
        seekBar = view.findViewById(R.id.audioSeekBar);
        playPauseButton = view.findViewById(R.id.audioPlayPauseButton);
        previousButton = view.findViewById(R.id.audioPreviousButton);
        nextButton = view.findViewById(R.id.audioNextButton);
        repeatButton = view.findViewById(R.id.audioRepeatButton);
        shuffleButton = view.findViewById(R.id.audioShuffleButton);
        backButton = view.findViewById(R.id.audioBackButton);

        // Set initial title
        titleText.setText(fileName != null ? fileName : getString(R.string.unknown));
        artistText.setText(getString(R.string.unknown));
    }

    private void bindToService() {
        Context context = requireContext();
        Intent intent = new Intent(context, AudioPlayerService.class);
        intent.putExtra(AudioPlayerService.EXTRA_FILE_URI, fileUri);
        intent.putExtra(AudioPlayerService.EXTRA_FILE_PATH, filePath);
        intent.putExtra(AudioPlayerService.EXTRA_FILE_NAME, fileName);
        intent.putExtra(AudioPlayerService.EXTRA_MIME_TYPE, mimeType);
        
        context.startService(intent);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void setupPlayer() {
        if (!serviceBound || audioService == null) return;

        Player player = audioService.getPlayer();
        if (player != null) {
            player.addListener(this);
            updatePlayPauseButton(player.isPlaying());
            startProgressUpdates();
        }
    }

    private void setupControls() {
        playPauseButton.setOnClickListener(v -> togglePlayPause());
        
        previousButton.setOnClickListener(v -> seekToPrevious());
        
        nextButton.setOnClickListener(v -> seekToNext());
        
        repeatButton.setOnClickListener(v -> toggleRepeatMode());
        
        shuffleButton.setOnClickListener(v -> toggleShuffle());
        
        backButton.setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().onBackPressed();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && serviceBound && audioService != null) {
                    Player player = audioService.getPlayer();
                    if (player != null) {
                        long position = (long) ((progress / 100f) * player.getDuration());
                        currentTimeText.setText(formatTime(position));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Pause progress updates while seeking
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (serviceBound && audioService != null) {
                    Player player = audioService.getPlayer();
                    if (player != null) {
                        long position = (long) ((seekBar.getProgress() / 100f) * player.getDuration());
                        player.seekTo(position);
                    }
                }
            }
        });
    }

    private void togglePlayPause() {
        if (!serviceBound || audioService == null) return;

        Player player = audioService.getPlayer();
        if (player != null) {
            if (player.isPlaying()) {
                audioService.pausePlayback();
            } else {
                audioService.startPlayback();
            }
        }
    }

    private void seekToPrevious() {
        if (!serviceBound || audioService == null) return;
        audioService.seekToPrevious();
    }

    private void seekToNext() {
        if (!serviceBound || audioService == null) return;
        audioService.seekToNext();
    }

    private void toggleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3;
        if (!serviceBound || audioService == null) return;

        Player player = audioService.getPlayer();
        if (player != null) {
            switch (repeatMode) {
                case 0:
                    player.setRepeatMode(Player.REPEAT_MODE_OFF);
                    repeatButton.setImageResource(R.drawable.ic_repeat_white_24dp);
                    break;
                case 1:
                    player.setRepeatMode(Player.REPEAT_MODE_ONE);
                    repeatButton.setImageResource(R.drawable.ic_repeat_one_white_24dp);
                    break;
                case 2:
                    player.setRepeatMode(Player.REPEAT_MODE_ALL);
                    repeatButton.setImageResource(R.drawable.ic_repeat_white_24dp);
                    break;
            }
        }
    }

    private void toggleShuffle() {
        isShuffled = !isShuffled;
        if (!serviceBound || audioService == null) return;

        Player player = audioService.getPlayer();
        if (player != null) {
            player.setShuffleModeEnabled(isShuffled);
            shuffleButton.setAlpha(isShuffled ? 1.0f : 0.5f);
        }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) {
            playPauseButton.setImageResource(R.drawable.ic_pause_white_24dp);
        } else {
            playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
        }
    }

    private void startProgressUpdates() {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (serviceBound && audioService != null) {
                    Player player = audioService.getPlayer();
                    if (player != null) {
                        long currentPosition = player.getCurrentPosition();
                        long duration = player.getDuration();
                        
                        if (duration > 0) {
                            int progress = (int) ((currentPosition * 100) / duration);
                            seekBar.setProgress(progress);
                        }
                        
                        currentTimeText.setText(formatTime(currentPosition));
                        totalTimeText.setText(formatTime(duration));
                    }
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
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlayPauseButton(isPlaying);
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (playbackState == Player.STATE_ENDED) {
            updatePlayPauseButton(false);
        }
    }

    @Override
    public void onPlayerError(@NonNull com.google.android.exoplayer2.PlaybackException error) {
        LOG.error("Audio player error: " + error.getMessage(), error);
        Toast.makeText(requireContext(), R.string.error_io, Toast.LENGTH_LONG).show();
    }

    public void onBackPressed() {
        // Service continues running in background
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        if (progressHandler != null && progressRunnable != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }
        
        if (serviceBound) {
            if (audioService != null && audioService.getPlayer() != null) {
                audioService.getPlayer().removeListener(this);
            }
            requireContext().unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (serviceBound && audioService != null) {
            Player player = audioService.getPlayer();
            if (player != null) {
                updatePlayPauseButton(player.isPlaying());
            }
        }
    }
}
