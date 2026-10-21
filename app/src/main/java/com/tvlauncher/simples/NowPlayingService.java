package com.tvlauncher.simples;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * O Android só deixa um app ler a sessão de mídia (Spotify etc.) se ele tiver um
 * NotificationListenerService com acesso liberado. Além disso, este serviço fica vigiando
 * os apps de vídeo e grava o que você assistiu e onde parou ("Continue assistindo").
 */
public class NowPlayingService extends NotificationListenerService {

    static final String[] SKIP = {"com.spotify", "com.google.android.music", "com.google.android.apps.youtube.music",
            "com.amazon.mp3", "deezer", "br.com.claro.musica", "com.tvlauncher.simples"};

    final Handler h = new Handler(Looper.getMainLooper());
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    final Map<MediaSession.Token, Tracker> trackers = new HashMap<>();
    MediaSessionManager msm;
    ComponentName me;
    boolean connected = false;

    class Tracker extends MediaController.Callback {
        final MediaController c;

        Tracker(MediaController c) {
            this.c = c;
        }

        @Override
        public void onMetadataChanged(MediaMetadata m) {
            capture(c);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            capture(c);
        }

        @Override
        public void onSessionDestroyed() {
            drop(c);
        }
    }

    final MediaSessionManager.OnActiveSessionsChangedListener sl = this::sync;

    final Runnable periodic = new Runnable() {
        @Override
        public void run() {
            for (Tracker t : new ArrayList<>(trackers.values())) {
                PlaybackState s = t.c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) capture(t.c);
            }
            if (connected) h.postDelayed(this, 20000);
        }
    };

    @Override
    public void onListenerConnected() {
        connected = true;
        try {
            msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            me = new ComponentName(this, NowPlayingService.class);
            msm.addOnActiveSessionsChangedListener(sl, me, h);
            sync(msm.getActiveSessions(me));
        } catch (Exception ignored) {
        }
        h.removeCallbacks(periodic);
        h.postDelayed(periodic, 20000);
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        cleanup();
    }

    @Override
    public void onDestroy() {
        connected = false;
        cleanup();
        ex.shutdown();
        super.onDestroy();
    }

    void cleanup() {
        h.removeCallbacks(periodic);
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sl);
        } catch (Exception ignored) {
        }
        for (Tracker t : trackers.values()) {
            try {
                t.c.unregisterCallback(t);
            } catch (Exception ignored) {
            }
        }
        trackers.clear();
    }

    void sync(List<MediaController> list) {
        List<MediaController> now = list == null ? new ArrayList<MediaController>() : list;
        List<MediaSession.Token> toks = new ArrayList<>();
        for (MediaController c : now) toks.add(c.getSessionToken());
        for (MediaSession.Token k : new ArrayList<>(trackers.keySet())) {
            if (!toks.contains(k)) drop(trackers.get(k).c);
        }
        for (MediaController c : now) {
            if (trackers.containsKey(c.getSessionToken())) continue;
            Tracker t = new Tracker(c);
            trackers.put(c.getSessionToken(), t);
            try {
                c.registerCallback(t, h);
            } catch (Exception ignored) {
            }
            capture(c);
        }
    }

    void drop(MediaController c) {
        Tracker t = trackers.remove(c.getSessionToken());
        if (t != null) {
            try {
                c.unregisterCallback(t);
            } catch (Exception ignored) {
            }
        }
    }

    static String str(MediaMetadata m, String... keys) {
        for (String k : keys) {
            String s = m.getString(k);
            if (s != null && !s.trim().isEmpty()) return s.trim();
        }
        return null;
    }

    void capture(MediaController c) {
        try {
            String pkg = c.getPackageName();
            for (String s : SKIP) if (pkg.contains(s)) return;
            MediaMetadata m = c.getMetadata();
            PlaybackState st = c.getPlaybackState();
            if (m == null || st == null || st.getState() == PlaybackState.STATE_NONE) return;
            if (m.getString(MediaMetadata.METADATA_KEY_ALBUM) != null) return; // música
            String title = str(m, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            if (title == null) return;

            final WatchStore.Item it = new WatchStore.Item();
            it.pkg = pkg;
            it.title = title;
            String sub = str(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            it.sub = sub == null ? "" : sub;
            it.key = pkg + "|" + title;
            it.dur = Math.max(0, m.getLong(MediaMetadata.METADATA_KEY_DURATION));
            long p = Math.max(0, st.getPosition());
            if (st.getState() == PlaybackState.STATE_PLAYING) {
                p += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime())
                        * st.getPlaybackSpeed());
            }
            if (it.dur > 0) p = Math.min(p, it.dur);
            it.pos = p;

            Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            final Bitmap art = b;
            final String uri = art != null ? null : str(m, MediaMetadata.METADATA_KEY_ART_URI,
                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
            final Context ctx = getApplicationContext();
            ex.execute(() -> {
                try {
                    Bitmap a = art;
                    if (a == null && uri != null && !WatchStore.hasArt(ctx, it.key)) a = fetch(uri);
                    WatchStore.save(ctx, it, a);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    Bitmap fetch(String u) {
        try {
            Uri uri = Uri.parse(u);
            InputStream in;
            String sc = uri.getScheme();
            if ("http".equals(sc) || "https".equals(sc)) {
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                in = c.getInputStream();
            } else {
                in = getContentResolver().openInputStream(uri);
            }
            if (in == null) return null;
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }
}
