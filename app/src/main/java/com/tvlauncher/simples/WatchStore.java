package com.tvlauncher.simples;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Histórico de "Continue assistindo": guardado em prefs "watch" + miniaturas em files/watch. */
final class WatchStore {
    static final int MAX = 15;

    static class Item {
        String key, pkg, title, sub;
        long pos, dur, time;
    }

    private WatchStore() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    static File artFile(Context c, String key) {
        File d = new File(c.getFilesDir(), "watch");
        if (!d.exists()) d.mkdirs();
        return new File(d, Integer.toHexString(key.hashCode()) + ".jpg");
    }

    static synchronized String stamp(Context c) {
        return prefs(c).getString("items", "");
    }

    static synchronized List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.key = o.getString("key");
                it.pkg = o.getString("pkg");
                it.title = o.getString("title");
                it.sub = o.optString("sub", "");
                it.pos = o.optLong("pos");
                it.dur = o.optLong("dur");
                it.time = o.optLong("time");
                out.add(it);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void write(Context c, List<Item> l) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : l) {
                JSONObject o = new JSONObject();
                o.put("key", it.key);
                o.put("pkg", it.pkg);
                o.put("title", it.title);
                o.put("sub", it.sub == null ? "" : it.sub);
                o.put("pos", it.pos);
                o.put("dur", it.dur);
                o.put("time", it.time);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("items", a.toString()).apply();
    }

    /** Salva/atualiza um item e o põe no começo. Terminou (>=95%)? Remove da lista. */
    static synchronized void save(Context c, Item n, Bitmap art) {
        List<Item> l = load(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).key.equals(n.key)) {
                l.remove(i);
                break;
            }
        }
        if (n.dur > 0 && n.pos >= n.dur * 95L / 100L) {
            artFile(c, n.key).delete();
            write(c, l);
            return;
        }
        if (art != null) {
            try {
                Bitmap s = art;
                if (art.getWidth() > 480) {
                    int h = Math.max(1, art.getHeight() * 480 / art.getWidth());
                    s = Bitmap.createScaledBitmap(art, 480, h, true);
                }
                FileOutputStream o = new FileOutputStream(artFile(c, n.key));
                s.compress(Bitmap.CompressFormat.JPEG, 85, o);
                o.close();
            } catch (Exception ignored) {
            }
        }
        n.time = System.currentTimeMillis();
        l.add(0, n);
        while (l.size() > MAX) {
            Item d = l.remove(l.size() - 1);
            artFile(c, d.key).delete();
        }
        write(c, l);
    }

    static synchronized boolean hasArt(Context c, String key) {
        return artFile(c, key).exists();
    }

    static synchronized void remove(Context c, String key) {
        List<Item> l = load(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).key.equals(key)) {
                l.remove(i);
                break;
            }
        }
        artFile(c, key).delete();
        write(c, l);
    }

    static synchronized void clear(Context c) {
        for (Item it : load(c)) artFile(c, it.key).delete();
        write(c, new ArrayList<Item>());
    }
}
