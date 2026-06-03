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

package com.amaze.filemanager.asynchronous.services.mediaplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;

import com.amaze.filemanager.R;
import com.amaze.filemanager.ui.activities.mediaplayer.MediaPlayerActivity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Background audio player service with notification controls.
 * Supports common formats: MP3, AAC, FLAC
 */
public class AudioPlayerService extends Service implements Player.Listener {

    private static final Logger LOG = LoggerFactory.getLogger(AudioPlayerService.class);

    public static final String EXTRA_FILE_URI = "file_uri";
    public static final String EXTRA_FILE_PATH = "file_path";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String EXTRA_MIME_TYPE = "mime_type";

    public static final String ACTION_PLAY = "com.amaze.filemanager.action.PLAY";
    public static final String ACTION_PAUSE = "com.amaze.filemanager.action.PAUSE";
    public static final String ACTION_STOP = "com.amaze.filemanager.action.STOP";
    public static final String ACTION_PREVIOUS = "com.amaze.filemanager.action.PREVIOUS";
    public static final String ACTION_NEXT = "com.amaze.filemanager.action.NEXT";

    private static final String CHANNEL_ID = "audio_player_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final IBinder binder = new LocalBinder();
    private ExoPlayer exoPlayer;
    private MediaSessionCompat mediaSession;
    private NotificationManager notificationManager;

    private Uri fileUri;
    private String filePath;
    private String fileName;
    private String mimeType;

    /**
     * Binder class for client binding
     */
    public class LocalBinder extends Binder {
        public AudioPlayerService getService() {
            return AudioPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
        initializeMediaSession();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (action != null) {
            handleAction(action);
            return START_STICKY;
        }

        // Initialize player with new media
        fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
        filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        fileName = intent.getStringExtra(EXTRA_FILE_NAME);
        mimeType = intent.getStringExtra(EXTRA_MIME_TYPE);

        if (fileUri == null && filePath == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        initializePlayer();
        return START_STICKY;
    }

    private void handleAction(String action) {
        if (exoPlayer == null) return;

        switch (action) {
            case ACTION_PLAY:
                startPlayback();
                break;
            case ACTION_PAUSE:
                pausePlayback();
                break;
            case ACTION_STOP:
                stopPlayback();
                break;
            case ACTION_PREVIOUS:
                seekToPrevious();
                break;
            case ACTION_NEXT:
                seekToNext();
                break;
        }
    }

    private void initializePlayer() {
        if (exoPlayer != null) {
            exoPlayer.release();
        }

        exoPlayer = new ExoPlayer.Builder(this).build();
        exoPlayer.addListener(this);

        // Build media source
        Uri mediaUri = fileUri != null ? fileUri : Uri.parse(filePath);
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(mediaUri));

        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.play();

        updateMediaSessionMetadata();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    private void initializeMediaSession() {
        mediaSession = new MediaSessionCompat(this, "AudioPlayerService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                startPlayback();
            }

            @Override
            public void onPause() {
                pausePlayback();
            }

            @Override
            public void onStop() {
                stopPlayback();
            }

            @Override
            public void onSkipToPrevious() {
                seekToPrevious();
            }

            @Override
            public void onSkipToNext() {
                seekToNext();
            }

            @Override
            public void onSeekTo(long pos) {
                if (exoPlayer != null) {
                    exoPlayer.seekTo(pos);
                }
            }
        });
        mediaSession.setActive(true);
    }

    private void updateMediaSessionMetadata() {
        if (mediaSession == null) return;

        android.support.v4.media.MediaMetadataCompat.Builder metadataBuilder =
                new android.support.v4.media.MediaMetadataCompat.Builder()
                        .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE,
                                fileName != null ? fileName : getString(R.string.unknown))
                        .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST,
                                getString(R.string.unknown));

        mediaSession.setMetadata(metadataBuilder.build());
    }

    private void updatePlaybackState() {
        if (mediaSession == null || exoPlayer == null) return;

        int state = exoPlayer.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .setState(state, exoPlayer.getCurrentPosition(), 1.0f);

        mediaSession.setPlaybackState(stateBuilder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.amaze_audio_player),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.channel_description_normal));
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        boolean isPlaying = exoPlayer != null && exoPlayer.isPlaying();

        // Create intents for notification actions
        Intent contentIntent = new Intent(this, MediaPlayerActivity.class);
        contentIntent.putExtra(MediaPlayerActivity.EXTRA_FILE_URI, fileUri);
        contentIntent.putExtra(MediaPlayerActivity.EXTRA_FILE_PATH, filePath);
        contentIntent.putExtra(MediaPlayerActivity.EXTRA_FILE_NAME, fileName);
        contentIntent.putExtra(MediaPlayerActivity.EXTRA_MIME_TYPE, mimeType);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Play/Pause action
        Intent playPauseIntent = new Intent(this, AudioPlayerService.class);
        playPauseIntent.setAction(isPlaying ? ACTION_PAUSE : ACTION_PLAY);
        PendingIntent playPausePendingIntent = PendingIntent.getService(
                this, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Stop action
        Intent stopIntent = new Intent(this, AudioPlayerService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Previous action
        Intent previousIntent = new Intent(this, AudioPlayerService.class);
        previousIntent.setAction(ACTION_PREVIOUS);
        PendingIntent previousPendingIntent = PendingIntent.getService(
                this, 3, previousIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Next action
        Intent nextIntent = new Intent(this, AudioPlayerService.class);
        nextIntent.setAction(ACTION_NEXT);
        PendingIntent nextPendingIntent = PendingIntent.getService(
                this, 4, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(fileName != null ? fileName : getString(R.string.unknown))
                .setContentText(getString(R.string.amaze_audio_player))
                .setSmallIcon(R.drawable.ic_library_music_white_24dp)
                .setContentIntent(contentPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .addAction(R.drawable.ic_skip_previous_white_24dp, getString(R.string.media_previous), previousPendingIntent)
                .addAction(isPlaying ? R.drawable.ic_pause_white_24dp : R.drawable.ic_play_arrow_white_24dp,
                        isPlaying ? getString(R.string.media_pause) : getString(R.string.media_play), playPausePendingIntent)
                .addAction(R.drawable.ic_skip_next_white_24dp, getString(R.string.media_next), nextPendingIntent)
                .addAction(R.drawable.ic_close_white_24dp, getString(R.string.media_stop), stopPendingIntent);

        return builder.build();
    }

    private void updateNotification() {
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releasePlayer();
        if (mediaSession != null) {
            mediaSession.release();
        }
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            exoPlayer.removeListener(this);
            exoPlayer.release();
            exoPlayer = null;
        }
        stopForeground(true);
    }

    // Public methods for fragment to control playback

    public void startPlayback() {
        if (exoPlayer != null) {
            exoPlayer.play();
        }
    }

    public void pausePlayback() {
        if (exoPlayer != null) {
            exoPlayer.pause();
        }
    }

    public void stopPlayback() {
        stopForeground(true);
        stopSelf();
    }

    public void seekToPrevious() {
        if (exoPlayer != null) {
            exoPlayer.seekTo(0);
        }
    }

    public void seekToNext() {
        if (exoPlayer != null) {
            // For single file, restart from beginning
            exoPlayer.seekTo(0);
        }
    }

    public Player getPlayer() {
        return exoPlayer;
    }

    // Player.Listener callbacks

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        updatePlaybackState();
        updateNotification();
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, buildNotification());
        } else {
            stopForeground(false);
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        updatePlaybackState();
    }

    @Override
    public void onPlayerError(com.google.android.exoplayer2.PlaybackException error) {
        LOG.error("Audio service player error: " + error.getMessage(), error);
        stopPlayback();
    }
}
