package com.zingofast.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * Native side of window.ZingoDesktop on Android. Every call is asynchronous:
 * page -> ZingoAndroid.call(id, method, argsJson); answer -> window.__zfCb(id, ok, resultJson).
 * Files: projects live in the app folder (Android/data/com.zingofast.app/files/Projects) or wherever the user
 * picks with the system file picker; only those locations can be read or written.
 */
public class Bridge {
    private static final int MAX = 50 * 1024 * 1024;
    private static final Pattern DATA_NAME = Pattern.compile("^[a-z]{2,16}$");
    private static final Pattern NET_OK = Pattern.compile("^(https://\\S+|http://(127\\.0\\.0\\.1|localhost)(:\\d+)?(/\\S*)?)$", Pattern.CASE_INSENSITIVE);

    private final MainActivity act;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Map<Integer, HttpURLConnection> aiConns = new ConcurrentHashMap<>();
    private final Set<String> aborted = ConcurrentHashMap.newKeySet();
    private final Set<String> allowed = new HashSet<>();
    private final SharedPreferences prefs;
    private int pendId = 0, pendReq = 0;
    private String pendName = "";
    private byte[] pendBytes = null;
    private volatile boolean ready = false;
    private volatile String openLater = null;

    Bridge(MainActivity a) {
        act = a;
        prefs = a.getSharedPreferences("zingo", Context.MODE_PRIVATE);
    }

    void shutdown() {
        for (HttpURLConnection c : aiConns.values()) try { c.disconnect(); } catch (Exception ignored) { }
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------ dispatch

    @JavascriptInterface
    public void call(final int id, final String method, final String argsJson) {
        pool.execute(() -> {
            try {
                JSONArray a = new JSONArray(argsJson == null || argsJson.isEmpty() ? "[]" : argsJson);
                Object r = run(id, method, a);
                if (r != PENDING) ok(id, r);
            } catch (Throwable e) {
                fail(id, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        });
    }

    private static final Object PENDING = new Object();

    private Object run(int id, String m, JSONArray a) throws Exception {
        switch (m) {
            case "ready": {
                ready = true;
                String p = openLater;
                openLater = null;
                if (p != null) act.runJs("window.__zfOpen&&window.__zfOpen(" + JSONObject.quote(p) + ")");
                return true;
            }
            case "closeNow": act.runOnUiThread(act::finish); return true;
            case "openProject": return picker(id, MainActivity.REQ_OPEN, openIntent(), "", null);
            case "pickSavePath": {
                String name = cleanName(a.optString(0, "Voltra")).replaceAll("(?i)\\.(voltra|zingo)$", "") + ".voltra";
                return picker(id, MainActivity.REQ_SAVE_AS, createIntent(name, "application/octet-stream"), name, null);
            }
            case "exportFile": {
                String name = cleanName(a.optString(0, "export"));
                byte[] b = Base64.decode(a.optString(1, ""), Base64.DEFAULT);
                if (b.length > MAX) throw new Exception("too large");
                return picker(id, MainActivity.REQ_EXPORT, createIntent(name, mimeOf(name)), name, b);
            }
            case "readProject": return readProject(a.optString(0, ""));
            case "writeProject": return writeProject(a.optString(0, ""), a.optString(1, ""));
            case "createProject": return createProject(a.optString(0, "Voltra"));
            case "renameProject": return renameProject(a.optString(0, ""), a.optString(1, ""));
            case "recent": return recent();
            case "openExternal": act.runOnUiThread(() -> act.openExternal(a.optString(0, ""))); return true;
            case "dataRead": return dataRead(a.optString(0, ""));
            case "dataWrite": return dataWrite(a.optString(0, ""), a.optString(1, ""));
            case "secretGet": return SecretBox.get(act, a.optString(0, ""));
            case "secretSet": return SecretBox.set(act, a.optString(0, ""), a.optString(1, ""));
            case "net": return net(a.optJSONObject(0));
            case "aiStream": aiStream(a.optJSONObject(0)); return true;
            case "aiAbort": {
                int k = a.optInt(0);
                aborted.add(String.valueOf(k));
                HttpURLConnection c = aiConns.remove(k);
                if (c != null) try { c.disconnect(); } catch (Exception ignored) { }
                return true;
            }
            default: throw new Exception("unknown method " + m);
        }
    }

    private void ok(int id, Object r) {
        String s = r == null ? "null" : (r instanceof String ? JSONObject.quote((String) r) : r.toString());
        act.runJs("window.__zfCb&&window.__zfCb(" + id + ",true," + JSONObject.quote(s) + ")");
    }

    private void fail(int id, String msg) {
        act.runJs("window.__zfCb&&window.__zfCb(" + id + ",false," + JSONObject.quote(JSONObject.quote(msg == null ? "error" : msg)) + ")");
    }

    // ------------------------------------------------------------------ system file picker

    private Intent openIntent() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/octet-stream", "application/json", "text/plain", "*/*"});
        return i;
    }

    private Intent createIntent(String name, String mime) {
        return new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE, name);
    }

    private synchronized Object picker(int id, int req, Intent i, String name, byte[] bytes) throws Exception {
        if (pendId != 0) throw new Exception("another file window is open");
        pendId = id; pendReq = req; pendName = name; pendBytes = bytes;
        act.startPicker(i, req);
        return PENDING;
    }

    /** called on the UI thread with the picked document (or null when cancelled) */
    void onPicked(int req, Uri uri) {
        final int id;
        final String name;
        final byte[] bytes;
        synchronized (this) {
            if (pendId == 0 || req != pendReq) return;
            id = pendId; name = pendName; bytes = pendBytes;
            pendId = 0; pendReq = 0; pendName = ""; pendBytes = null;
        }
        if (uri == null) { ok(id, null); return; }
        try {
            act.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) { /* not every provider grants persistent access */ }
        allowed.add(uri.toString());
        pool.execute(() -> {
            try {
                if (req == MainActivity.REQ_OPEN) {
                    JSONObject o = readUri(uri);
                    addRecent(uri.toString(), o.getString("name"));
                    ok(id, o);
                } else if (req == MainActivity.REQ_SAVE_AS) {
                    String n = displayName(uri, name);
                    ok(id, new JSONObject().put("path", uri.toString()).put("name", n.replaceAll("(?i)\\.(voltra|zingo|json)$", "")));
                } else {
                    try (OutputStream os = act.getContentResolver().openOutputStream(uri, "wt")) {
                        if (os == null) throw new Exception("cannot write");
                        os.write(bytes);
                    }
                    ok(id, new JSONObject().put("path", uri.toString()).put("name", displayName(uri, name)));
                }
            } catch (Exception e) { fail(id, e.getMessage()); }
        });
    }

    void openFromIntent(Uri uri) {
        String n = displayName(uri, "");
        if (!n.isEmpty() && !n.toLowerCase().matches(".*\\.(voltra|zingo|json)$")) return;
        allowed.add(uri.toString());
        if (ready) act.runJs("window.__zfOpen&&window.__zfOpen(" + JSONObject.quote(uri.toString()) + ")");
        else openLater = uri.toString();
    }

    // ------------------------------------------------------------------ projects

    private File projectsDir() {
        File base = act.getExternalFilesDir(null);
        File d = new File(base != null ? base : act.getFilesDir(), "Projects");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private boolean ownFile(String p) {
        try {
            String c = new File(p).getCanonicalPath();
            return c.startsWith(projectsDir().getCanonicalPath() + File.separator);
        } catch (Exception e) { return false; }
    }

    private boolean mayUse(String p) {
        if (p.startsWith("content://")) {
            if (allowed.contains(p) || inRecent(p)) return true;
            for (android.content.UriPermission up : act.getContentResolver().getPersistedUriPermissions())
                if (up.getUri().toString().equals(p)) return true;
            return false;
        }
        return ownFile(p);
    }

    private Object readProject(String p) throws Exception {
        if (!mayUse(p)) return null;
        try {
            JSONObject o = p.startsWith("content://") ? readUri(Uri.parse(p)) : readFile(new File(p));
            addRecent(p, o.getString("name"));
            return o;
        } catch (Exception e) { dropRecent(p); return null; }
    }

    private JSONObject readUri(Uri u) throws Exception {
        ContentResolver cr = act.getContentResolver();
        try (InputStream in = cr.openInputStream(u)) {
            if (in == null) throw new Exception("cannot read");
            String name = displayName(u, "Voltra").replaceAll("(?i)\\.(voltra|zingo|json)$", "");
            return new JSONObject().put("path", u.toString()).put("name", name).put("text", new String(readAll(in), StandardCharsets.UTF_8));
        }
    }

    private JSONObject readFile(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            return new JSONObject().put("path", f.getAbsolutePath()).put("name", f.getName().replaceAll("(?i)\\.(voltra|zingo|json)$", "")).put("text", new String(readAll(in), StandardCharsets.UTF_8));
        }
    }

    private Object writeProject(String p, String text) throws Exception {
        if (!mayUse(p) || text.length() > MAX) return new JSONObject().put("ok", false);
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        try {
            if (p.startsWith("content://")) {
                try (OutputStream os = act.getContentResolver().openOutputStream(Uri.parse(p), "wt")) {
                    if (os == null) throw new Exception("cannot write");
                    os.write(b);
                }
                addRecent(p, displayName(Uri.parse(p), "Voltra").replaceAll("(?i)\\.(voltra|zingo|json)$", ""));
            } else {
                writeAtomic(new File(p), b);
                addRecent(p, new File(p).getName().replaceAll("(?i)\\.(voltra|zingo|json)$", ""));
            }
            return new JSONObject().put("ok", true);
        } catch (Exception e) { return new JSONObject().put("ok", false).put("error", String.valueOf(e.getMessage())); }
    }

    private Object createProject(String name) throws Exception {
        File d = projectsDir();
        String base = cleanName(name);
        File f = new File(d, base + ".voltra");
        for (int i = 2; f.exists(); i++) f = new File(d, base + " (" + i + ").voltra");
        return new JSONObject().put("path", f.getAbsolutePath()).put("name", f.getName().replaceAll("(?i)\\.(voltra|zingo)$", ""));
    }

    private Object renameProject(String p, String name) throws Exception {
        if (!mayUse(p)) return null;
        String base = cleanName(name);
        if (p.startsWith("content://")) {
            try {
                Uri n = DocumentsContract.renameDocument(act.getContentResolver(), Uri.parse(p), base + ".voltra");
                if (n == null) return null;
                allowed.add(n.toString()); dropRecent(p); addRecent(n.toString(), base);
                return new JSONObject().put("path", n.toString()).put("name", base);
            } catch (Exception e) { return null; }
        }
        File src = new File(p), dst = new File(src.getParentFile(), base + ".voltra");
        for (int i = 2; dst.exists() && !dst.equals(src); i++) dst = new File(src.getParentFile(), base + " (" + i + ").voltra");
        if (src.exists() && !src.renameTo(dst)) return null;
        dropRecent(p); addRecent(dst.getAbsolutePath(), dst.getName().replaceAll("(?i)\\.(voltra|zingo)$", ""));
        return new JSONObject().put("path", dst.getAbsolutePath()).put("name", dst.getName().replaceAll("(?i)\\.(voltra|zingo)$", ""));
    }

    // ------------------------------------------------------------------ recent list

    private synchronized JSONArray recentRaw() {
        try { return new JSONArray(prefs.getString("recent", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    private boolean inRecent(String p) {
        JSONArray a = recentRaw();
        for (int i = 0; i < a.length(); i++) if (p.equals(a.optJSONObject(i).optString("path"))) return true;
        return false;
    }

    private synchronized void addRecent(String p, String name) {
        try {
            JSONArray a = recentRaw(), o = new JSONArray();
            o.put(new JSONObject().put("path", p).put("name", name));
            for (int i = 0; i < a.length() && o.length() < 12; i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null && !p.equals(x.optString("path"))) o.put(x);
            }
            prefs.edit().putString("recent", o.toString()).apply();
        } catch (Exception ignored) { }
    }

    private synchronized void dropRecent(String p) {
        JSONArray a = recentRaw(), o = new JSONArray();
        for (int i = 0; i < a.length(); i++) { JSONObject x = a.optJSONObject(i); if (x != null && !p.equals(x.optString("path"))) o.put(x); }
        prefs.edit().putString("recent", o.toString()).apply();
    }

    private Object recent() {
        JSONArray a = recentRaw(), o = new JSONArray();
        for (int i = 0; i < a.length() && o.length() < 8; i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            String p = x.optString("path");
            if (!p.startsWith("content://") && !new File(p).isFile()) continue;
            o.put(x);
        }
        return o;
    }

    // ------------------------------------------------------------------ AI data files

    private File dataFile(String name) throws Exception {
        if (!DATA_NAME.matcher(name).matches()) throw new Exception("bad name");
        File d = new File(act.getFilesDir(), "ai");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return new File(d, name + ".json");
    }

    private Object dataRead(String name) {
        try (InputStream in = new FileInputStream(dataFile(name))) { return new String(readAll(in), StandardCharsets.UTF_8); }
        catch (Exception e) { return null; }
    }

    private Object dataWrite(String name, String text) throws Exception {
        try { writeAtomic(dataFile(name), text.getBytes(StandardCharsets.UTF_8)); return new JSONObject().put("ok", true); }
        catch (Exception e) { return new JSONObject().put("ok", false); }
    }

    // ------------------------------------------------------------------ network (brain sync)

    private Object net(JSONObject req) throws Exception {
        JSONObject out = new JSONObject();
        if (req == null) return out.put("ok", false).put("error", "bad request");
        String url = req.optString("url", ""), method = "POST".equals(req.optString("method")) ? "POST" : "GET";
        if (!NET_OK.matcher(url).matches()) return out.put("ok", false).put("error", "blocked url");
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            int to = Math.max(2000, Math.min(60000, req.optInt("timeout", 20000)));
            c.setConnectTimeout(to); c.setReadTimeout(to); c.setRequestMethod(method);
            if ("POST".equals(method)) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) { os.write(req.optString("body", "").getBytes(StandardCharsets.UTF_8)); }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
            boolean good = code < 400;
            return out.put("ok", good).put("status", code).put("text", good ? text : "").put("error", good ? "" : "HTTP " + code);
        } catch (Exception e) {
            return out.put("ok", false).put("error", String.valueOf(e.getMessage()));
        } finally { if (c != null) c.disconnect(); }
    }

    // ------------------------------------------------------------------ AI streaming (OpenAI-compatible chat/completions)

    private void aiEvent(int id, JSONObject ev) {
        act.runJs("window.__zfAi&&window.__zfAi(" + id + "," + JSONObject.quote(ev.toString()) + ")");
    }

    private void aiStream(JSONObject req) {
        final int id = req.optInt("id");
        aborted.remove(String.valueOf(id));
        pool.execute(() -> {
            HttpURLConnection c = null;
            try {
                if ("claude".equals(req.optString("provider"))) throw new Exception("Claude is available in the Windows app. Choose another provider here.");
                String url = req.optString("base", "").replaceAll("/+$", "") + "/chat/completions";
                if (!NET_OK.matcher(url).matches()) throw new Exception("Invalid API address");
                JSONArray msgs = new JSONArray().put(new JSONObject().put("role", "system").put("content", req.optString("system", "")));
                JSONArray in = req.optJSONArray("messages");
                if (in != null) for (int i = 0; i < in.length(); i++) msgs.put(in.get(i));
                JSONObject body = new JSONObject().put("model", req.optString("model")).put("messages", msgs).put("stream", true).put("temperature", req.optDouble("temperature", 0.3));
                c = (HttpURLConnection) new URL(url).openConnection();
                aiConns.put(id, c);
                c.setConnectTimeout(20000); c.setReadTimeout(120000);
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Accept", "text/event-stream");
                String key = req.optString("key", "");
                if (!key.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + key);
                try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int code = c.getResponseCode();
                if (code >= 400) {
                    InputStream es = c.getErrorStream();
                    String tx = es == null ? "" : new String(readAll(es), StandardCharsets.UTF_8);
                    throw new Exception(code == 401 || code == 403 ? "The API key was rejected." : code == 429 ? "Rate limit reached. Wait a moment and retry." : code == 404 ? "Model or address not found." : code >= 500 ? "The AI service is having problems. Retry in a moment." : "HTTP " + code + (tx.isEmpty() ? "" : ": " + tx.substring(0, Math.min(300, tx.length()))));
                }
                String finish = "";
                StringBuilder batch = new StringBuilder();
                long last = System.currentTimeMillis();
                try (BufferedReader rd = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = rd.readLine()) != null) {
                        if (aborted.contains(String.valueOf(id))) return;
                        line = line.trim();
                        if (!line.startsWith("data:")) continue;
                        String d = line.substring(5).trim();
                        if (d.equals("[DONE]")) continue;
                        try {
                            JSONObject j = new JSONObject(d);
                            JSONArray ch = j.optJSONArray("choices");
                            if (ch == null || ch.length() == 0) continue;
                            JSONObject c0 = ch.getJSONObject(0), delta = c0.optJSONObject("delta");
                            if (delta == null) delta = c0.optJSONObject("message");
                            String t = delta == null ? "" : delta.optString("content", "");
                            if (!t.isEmpty() && !"null".equals(t)) batch.append(t);
                            if (!c0.isNull("finish_reason")) finish = c0.optString("finish_reason", "");
                        } catch (Exception ignored) { }
                        long now = System.currentTimeMillis();
                        if (batch.length() > 0 && now - last > 60) {
                            aiEvent(id, new JSONObject().put("id", id).put("type", "delta").put("text", batch.toString()));
                            batch.setLength(0); last = now;
                        }
                    }
                }
                if (batch.length() > 0) aiEvent(id, new JSONObject().put("id", id).put("type", "delta").put("text", batch.toString()));
                aiEvent(id, new JSONObject().put("id", id).put("type", "done").put("finish", finish));
            } catch (Exception e) {
                if (aborted.contains(String.valueOf(id))) return;
                try { aiEvent(id, new JSONObject().put("id", id).put("type", "error").put("message", String.valueOf(e.getMessage()))); } catch (Exception ignored) { }
            } finally {
                aiConns.remove(id);
                if (c != null) c.disconnect();
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) != -1) {
            if (o.size() + n > MAX) throw new Exception("file too large");
            o.write(b, 0, n);
        }
        return o.toByteArray();
    }

    private static void writeAtomic(File f, byte[] data) throws Exception {
        File dir = f.getParentFile();
        if (dir != null) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        File t = new File(dir, f.getName() + ".tmp");
        try (FileOutputStream os = new FileOutputStream(t)) { os.write(data); os.getFD().sync(); }
        if (!t.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (!t.renameTo(f)) throw new Exception("rename failed");
        }
    }

    private String displayName(Uri u, String def) {
        if ("file".equals(u.getScheme())) return new File(String.valueOf(u.getPath())).getName();
        try (Cursor c = act.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Exception ignored) { }
        return def;
    }

    private static String cleanName(String s) {
        String n = String.valueOf(s).replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]+", " ").replaceAll("\\s+", " ").trim();
        if (n.length() > 80) n = n.substring(0, 80);
        n = n.replaceAll("[. ]+$", "");
        return n.isEmpty() ? "Voltra" : n;
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
