// ============================================================================
//  MessageTIk - مسجّل احترافي (نسخة موحّدة v10: Reels 9:16 = تيك توك + إنستغرام، بلا YouTube)
//  الفيديو: بمقاس صفحة الـ HTML فقط (قص بالـ GPU) + 60fps + بدون صوت
//  الصوت  : أصوات الصفحة نفسها (PCM صافي) تُدمج مع الفيديو في النهاية
//           (لا كلام ولا TTS: المكالمات صامتة)
//  الملفات: (v11 — ملف واجهة واحد) التطبيق يفتح  /sdcard/FILE PROG/index.html  وهو ملف واحد يضم كل الـ CSS والـ JavaScript والشاشات والسيناريو الافتراضي.
//           لم تعد المجلدات Css و JavaScript و Screens مطلوبة. والمجلدات التالية اختيارية وتُخدَم على https://appassets.local/ :
//             FILE PROG/scenario.json (بجانب index.html مباشرة — يتجاوز السيناريو المضمّن)  FILE PROG/Sound  FILE PROG/Images  FILE PROG/Sounds  FILE PROG/Voices  FILE PROG/Videos
//           (الصفحة تقرأ scenario.json بمسار نسبي بجانبها: https://appassets.local/scenario.json)
//  السيناريو: من الصفحة نفسها «استيراد» يختار ملفًا .json من FILE PROG ، و«تصدير» يكتب FILE PROG/scenario-export.json (عبر AndroidFiles.jsonList / jsonSave)
//  المعرض : صور الجهاز تُعرض داخل الصفحة عبر AndroidFiles (لا حاجة لـ onActivityResult إضافي)
//  الصلاحيات المطلوبة (Sketchware Pro): READ_EXTERNAL_STORAGE, READ_MEDIA_IMAGES, MANAGE_EXTERNAL_STORAGE,
//                                      FOREGROUND_SERVICE_MEDIA_PROJECTION حسب نسخة الأندرويد
//  ضعه كله في onCreate (Add source directly) بدل الكود القديم.
//  يعتمد على resCode و resData اللذين يملؤهما onActivityResult عندك (requestCode == 100).
// ============================================================================

final android.app.Activity act = this;

// ---------------------------------------------------------------- 1) ملء الشاشة
if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
    getWindow().setDecorFitsSystemWindows(false);
    android.view.WindowInsetsController controller = getWindow().getInsetsController();
    if (controller != null) {
        controller.hide(android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }
} else {
    getWindow().getDecorView().setSystemUiVisibility(
        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    );
}
if (android.os.Build.VERSION.SDK_INT >= 28) {   // تغطية منطقة الكاميرا (النوتش)
    android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
    lp.layoutInDisplayCutoutMode = android.os.Build.VERSION.SDK_INT >= 30
        ? android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        : android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    getWindow().setAttributes(lp);
}
getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

// ---------------------------------------------------------------- 2) إعدادات الـ WebView
final android.webkit.WebView webview1 = (android.webkit.WebView) findViewById(R.id.webview1);
android.webkit.WebSettings webSettings = webview1.getSettings();
webSettings.setJavaScriptEnabled(true);
webSettings.setDomStorageEnabled(true);
webSettings.setAllowFileAccess(true);
webSettings.setAllowContentAccess(true);
webSettings.setAllowFileAccessFromFileURLs(true);
webSettings.setAllowUniversalAccessFromFileURLs(true);
webSettings.setMediaPlaybackRequiresUserGesture(false);
webview1.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null);   // تسريع العرض
webview1.setBackgroundColor(0xFF05070B);
webSettings.setTextZoom(100);                  // حجم خط النظام لا يكبّر النصوص ولا يقصّها
webSettings.setSupportZoom(false);
webSettings.setBuiltInZoomControls(false);
webSettings.setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);   // الخطوط تُخزَّن بعد أول تحميل فيفتح التطبيق أسرع
if (android.os.Build.VERSION.SDK_INT >= 21) webSettings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
webview1.setVerticalScrollBarEnabled(false);   // لا أشرطة تمرير تظهر في الفيديو
webview1.setHorizontalScrollBarEnabled(false);
webview1.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);


// ---------------------------------------------------------------- 2c) الملفات + المعرض + الذاكرة (جسر AndroidFiles) + خادم الملفات الداخلي
// - أي ملف داخل /sdcard/FILE PROG (html/js/css/json/صور/أصوات ogg·wav·mp3) يُخدَم على https://appassets.local/ بنفس مساره النسبي.
// - معرض الصور مبنيّ داخل الصفحة: الجافا تسرد صور الجهاز وتقدّم المصغّرات عبر https://appassets.local/__gal/...
// - الذاكرة kvGet/kvSet تحفظ اختيارات الشخصيات (صور/أصوات) بين مرّات التشغيل.
class FilesHelper {
    final java.io.File TK = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "FILE PROG");
    final java.io.File WEB = new java.io.File(act.getFilesDir(), "webcache");
    // نقطة الدخول: FILE PROG/index.html (Chat Studio) و scenario.json بجانبه مباشرة. اتركه كما هو.
    // ENTRY_DIR فارغ = الملف داخل FILE PROG نفسه (بلا مجلد HTML). إن أردت ملفًا آخر غيّر PREFERRED_HTML.
    final String ENTRY_DIR = "";
    final String PREFERRED_HTML = "index.html";
    volatile java.util.ArrayList<Long> gal = null;
    volatile boolean permAsked = false;

    // ---------- أدوات الملفات
    byte[] readAll(java.io.File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] bf = new byte[65536];
        int n;
        while ((n = in.read(bf)) > 0) bo.write(bf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    void writeBytes(java.io.File f, byte[] d) throws Exception {
        java.io.File p = f.getParentFile();
        if (p != null) p.mkdirs();
        java.io.FileOutputStream o = new java.io.FileOutputStream(f);
        o.write(d);
        o.close();
    }

    boolean isHtml(String n) {
        String s = n.toLowerCase();
        return s.endsWith(".html") || s.endsWith(".htm");
    }

    // 1) FILE PROG/index.html  2) (احتياطي) أحدث html داخل FILE PROG
    java.io.File pickHtml() {
        try {
            java.io.File dir = ENTRY_DIR.length() > 0 ? new java.io.File(TK, ENTRY_DIR) : TK;
            if (PREFERRED_HTML.length() > 0) {
                java.io.File p = new java.io.File(dir, PREFERRED_HTML);
                if (p.isFile() && p.canRead()) return p;
            }
            java.io.File best = newestHtml(dir);
            if (best == null) best = newestHtml(TK);
            return best;
        } catch (Throwable e) { return null; }
    }

    java.io.File newestHtml(java.io.File dir) {
        java.io.File[] all = dir.listFiles();
        if (all == null) return null;
        java.io.File best = null;
        for (java.io.File f : all) {
            if (f.isFile() && f.canRead() && isHtml(f.getName()) && (best == null || f.lastModified() > best.lastModified())) best = f;
        }
        return best;
    }

    // نسخة احتياطية داخل التطبيق (تُستعمل لو ضاع إذن التخزين): تحفظ last.html (الملف الموحّد) + scenario.json وبقية ملفات json في الجذر + Sound/Images بنفس المسارات
    void cacheWeb(byte[] htmlData) {
        try {
            WEB.mkdirs();
            writeBytes(new java.io.File(act.getFilesDir(), "last.html"), htmlData);
            String[] dirs = new String[] { "Sound", "Images" };   // الواجهة كلها داخل index.html فلا حاجة لنسخ Css/JavaScript/Screens
            for (int i = 0; i < dirs.length; i++) cacheDir(new java.io.File(TK, dirs[i]), dirs[i], 0);
            // scenario.json (وأي json آخر) بجانب index.html مباشرة: يُنسخ إلى جذر النسخة الاحتياطية
            java.io.File[] top = TK.listFiles();
            if (top != null) for (int i = 0; i < top.length; i++) {
                try {
                    java.io.File jf = top[i];
                    if (jf.isFile() && jf.length() < 6000000 && jf.getName().toLowerCase().endsWith(".json")) writeBytes(new java.io.File(WEB, jf.getName()), readAll(jf));
                } catch (Throwable e2) { }
            }
        } catch (Throwable e) { }
    }

    void cacheDir(java.io.File d, String rel, int depth) {
        java.io.File[] all = d.listFiles();
        if (all == null || depth > 3) return;
        for (java.io.File f : all) {
            try {
                if (f.isDirectory()) { cacheDir(f, rel + "/" + f.getName(), depth + 1); continue; }
                String n = f.getName().toLowerCase();
                boolean ok = n.endsWith(".js") || n.endsWith(".css") || n.endsWith(".json") || n.endsWith(".html") || n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".svg") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
                if (!ok || f.length() > 6000000) continue;
                java.io.File t = new java.io.File(WEB, rel + "/" + f.getName());
                if (t.isFile() && t.length() == f.length() && t.lastModified() >= f.lastModified()) continue;   // لم يتغيّر
                writeBytes(t, readAll(f));
            } catch (Throwable e) { }
        }
    }

    java.io.File safe(java.io.File root, String rel) {
        try {
            java.io.File f = new java.io.File(root, rel);
            String c = f.getCanonicalPath(), rc = root.getCanonicalPath();
            if (c.equals(rc) || c.startsWith(rc + java.io.File.separator)) return f;
        } catch (Throwable e) { }
        return null;
    }

    // ---------- الصور: فكّ بحجم صغير + قص مربّع اختياري + تدوير EXIF
    java.io.InputStream openSrc(android.net.Uri uri, java.io.File file) throws Exception {
        if (uri != null) return act.getContentResolver().openInputStream(uri);
        return new java.io.FileInputStream(file);
    }

    int exifRotation(android.net.Uri uri, java.io.File file) {
        try {
            android.media.ExifInterface ei = null;
            if (file != null) ei = new android.media.ExifInterface(file.getAbsolutePath());
            else if (android.os.Build.VERSION.SDK_INT >= 24) {
                java.io.InputStream in = act.getContentResolver().openInputStream(uri);
                ei = new android.media.ExifInterface(in);
                in.close();
            }
            if (ei == null) return 0;
            int o = ei.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            if (o == 6) return 90;
            if (o == 3) return 180;
            if (o == 8) return 270;
        } catch (Throwable e) { }
        return 0;
    }

    byte[] jpeg(android.net.Uri uri, java.io.File file, int maxSide, boolean square, int quality) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            java.io.InputStream in = openSrc(uri, file);
            android.graphics.BitmapFactory.decodeStream(in, null, o);
            in.close();
            int w = o.outWidth, h = o.outHeight;
            if (w <= 0 || h <= 0) return null;
            int target = square ? Math.min(w, h) : Math.max(w, h);
            int ss = 1;
            while (target / (ss * 2) >= maxSide) ss *= 2;
            android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
            o2.inSampleSize = ss;
            in = openSrc(uri, file);
            android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeStream(in, null, o2);
            in.close();
            if (bm == null) return null;
            int rot = exifRotation(uri, file);
            if (rot != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(rot);
                android.graphics.Bitmap r = android.graphics.Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                if (r != bm) bm.recycle();
                bm = r;
            }
            if (square) {
                int s = Math.min(bm.getWidth(), bm.getHeight());
                android.graphics.Bitmap c = android.graphics.Bitmap.createBitmap(bm, (bm.getWidth() - s) / 2, (bm.getHeight() - s) / 2, s, s);
                if (c != bm) bm.recycle();
                bm = c;
                if (s > maxSide) { android.graphics.Bitmap sc = android.graphics.Bitmap.createScaledBitmap(bm, maxSide, maxSide, true); if (sc != bm) bm.recycle(); bm = sc; }
            } else {
                int big = Math.max(bm.getWidth(), bm.getHeight());
                if (big > maxSide) {
                    float k = (float) maxSide / big;
                    android.graphics.Bitmap sc = android.graphics.Bitmap.createScaledBitmap(bm, Math.round(bm.getWidth() * k), Math.round(bm.getHeight() * k), true);
                    if (sc != bm) bm.recycle();
                    bm = sc;
                }
            }
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, bo);
            bm.recycle();
            return bo.toByteArray();
        } catch (Throwable e) { return null; }
    }

    // ---------- خادم الملفات (يُستدعى من WebViewClient.shouldInterceptRequest)
    android.webkit.WebResourceResponse resp(String mime, String enc, byte[] data, int code) {
        java.io.InputStream in = new java.io.ByteArrayInputStream(data == null ? new byte[0] : data);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            java.util.Map<String, String> h = new java.util.HashMap<String, String>();
            h.put("Access-Control-Allow-Origin", "*");
            h.put("Cache-Control", "no-cache");
            return new android.webkit.WebResourceResponse(mime, enc, code, code == 200 ? "OK" : "Not Found", h, in);
        }
        return new android.webkit.WebResourceResponse(mime, enc, in);
    }

    String mimeOf(String name) {
        int d = name.lastIndexOf('.');
        String ext = d < 0 ? "" : name.substring(d + 1).toLowerCase();
        if (ext.equals("js")) return "application/javascript";
        if (ext.equals("html") || ext.equals("htm")) return "text/html";
        if (ext.equals("css")) return "text/css";
        if (ext.equals("json")) return "application/json";
        if (ext.equals("svg")) return "image/svg+xml";
        if (ext.equals("woff2")) return "font/woff2";
        if (ext.equals("ogg") || ext.equals("oga")) return "audio/ogg";
        if (ext.equals("wav")) return "audio/wav";
        if (ext.equals("mp3")) return "audio/mpeg";
        if (ext.equals("m4a")) return "audio/mp4";
        String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return m == null ? "application/octet-stream" : m;
    }

    android.webkit.WebResourceResponse serve(String p, java.util.Map<String, String> hdr) {
        try {
            if (p == null) p = "/";
            if (p.startsWith("/__gal/")) {                       // /__gal/t/ID (مصغّرة مربعة)  |  /__gal/i/ID (1024)  |  /__gal/f/ID (1600 للصور داخل الدردشة)
                String[] a = p.split("/");
                if (a.length >= 4) {
                    boolean t = a[2].equals("t");
                    android.net.Uri uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Long.parseLong(a[3]));
                    byte[] b = jpeg(uri, null, t ? 200 : (a[2].equals("f") ? 1600 : 1024), t, t ? 78 : 90);
                    if (b != null) return resp("image/jpeg", null, b, 200);
                }
                return resp("text/plain", "UTF-8", null, 404);
            }
            if (p.startsWith("/__aud/")) {                       // /__aud/ID  ملف صوت من الهاتف
                android.net.Uri uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, Long.parseLong(p.substring(7)));
                String mime = act.getContentResolver().getType(uri);
                byte[] b = readStream(act.getContentResolver().openInputStream(uri));
                return resp(mime == null ? "audio/mpeg" : mime, null, b, 200);
            }
            if (p.startsWith("/__vid/")) {                       // /__vid/t/ID (مصغّرة)  |  /__vid/i/ID (الفيديو مع Range)
                String[] a = p.split("/");
                if (a.length >= 4) {
                    android.net.Uri uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Long.parseLong(a[3]));
                    if (a[2].equals("t")) {
                        byte[] b = videoThumb(uri, null);
                        if (b != null) return resp("image/jpeg", null, b, 200);
                    } else return serveVideo(uri, hdr);
                }
                return resp("text/plain", "UTF-8", null, 404);
            }
            if (p.startsWith("/__img/")) {                       // /__img/t|i|f/مسار نسبي داخل FILE PROG  (t مصغّرة · i 1024 · f 1600 للدردشة) — يدعم HEIC ويصحّح التدوير
                String rest = p.substring(7);
                boolean t = rest.startsWith("t/");
                boolean big = rest.startsWith("f/");
                java.io.File f = safe(TK, rest.substring(2));
                if (f != null && f.isFile()) {
                    byte[] b = jpeg(null, f, t ? 200 : (big ? 1600 : 1024), t, t ? 78 : (big ? 90 : 88));
                    if (b != null) return resp("image/jpeg", null, b, 200);
                }
                return resp("text/plain", "UTF-8", null, 404);
            }
            if (p.startsWith("/__vf/t/")) {                      // /__vf/t/مسار نسبي : مصغّرة فيديو من FILE PROG/Videos
                java.io.File f = safe(TK, p.substring(8));
                if (f != null && f.isFile()) {
                    byte[] b = videoThumb(null, f);
                    if (b != null) return resp("image/jpeg", null, b, 200);
                }
                return resp("text/plain", "UTF-8", null, 404);
            }
            String rel = p.startsWith("/") ? p.substring(1) : p;
            if (rel.length() == 0) return resp("text/plain", "UTF-8", null, 404);
            java.io.File f = safe(TK, rel);
            if (f == null || !f.isFile() || !f.canRead()) f = safe(WEB, rel);   // نسخة احتياطية
            if (f == null || !f.isFile()) return resp("text/plain", "UTF-8", null, 404);
            String mime = mimeOf(f.getName());
            if (mime.startsWith("video/") || mime.startsWith("audio/")) return serveFile(f, mime, hdr);   // بثّ مع Range بدل تحميل الملف كله في الذاكرة
            boolean text = mime.startsWith("text/") || mime.indexOf("javascript") >= 0 || mime.indexOf("json") >= 0;
            return resp(mime, text ? "UTF-8" : null, readAll(f), 200);
        } catch (Throwable e) {
            return resp("text/plain", "UTF-8", null, 404);
        }
    }

    // ---------- واجهة JavaScript
    @android.webkit.JavascriptInterface
    public boolean hasFiles() { return true; }

    @android.webkit.JavascriptInterface
    public String kvGet(String k) {
        try { return act.getSharedPreferences("mtk_kv", 0).getString(k, ""); } catch (Throwable e) { return ""; }
    }

    @android.webkit.JavascriptInterface
    public void kvSet(String k, String v) {
        try { act.getSharedPreferences("mtk_kv", 0).edit().putString(k, v == null ? "" : v).commit(); } catch (Throwable e) { }
    }

    // ---------- السيناريو: قائمة ملفات FILE PROG/*.json بجانب index.html (الأحدث أولًا) وكتابة ملف في المجلد نفسه (للاستيراد/التصدير من الصفحة)
    @android.webkit.JavascriptInterface
    public String jsonList() {
        org.json.JSONArray a = new org.json.JSONArray();
        try {
            java.io.File[] all = TK.listFiles();
            if (all != null) {
                java.util.Arrays.sort(all, new java.util.Comparator<java.io.File>() {
                    public int compare(java.io.File x, java.io.File y) { return x.lastModified() < y.lastModified() ? 1 : (x.lastModified() > y.lastModified() ? -1 : 0); }
                });
                for (int i = 0; i < all.length && a.length() < 60; i++) {
                    java.io.File f = all[i];
                    if (f.isFile() && f.canRead() && f.length() < 5000000 && f.getName().toLowerCase().endsWith(".json")) a.put(f.getName());
                }
            }
        } catch (Throwable e) { }
        return a.toString();
    }

    @android.webkit.JavascriptInterface
    public boolean jsonSave(String name, String text) {
        try {
            if (name == null || text == null || name.length() == 0 || name.length() > 80) return false;
            if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || !name.toLowerCase().endsWith(".json")) return false;   // اسم ملف فقط داخل FILE PROG
            writeBytes(new java.io.File(TK, name), text.getBytes("UTF-8"));
            return true;
        } catch (Throwable e) { return false; }
    }

    boolean mediaPerm() {
        try {
            if (android.os.Build.VERSION.SDK_INT < 23) return true;
            if (android.os.Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager()) return true;
            String perm = android.os.Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : "android.permission.READ_EXTERNAL_STORAGE";
            return act.checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) { return false; }
    }

    // ---------- صوت وفيديو الجهاز (للوسائط الخارجية): صلاحية حسب النوع
    //   أندرويد 13+: READ_MEDIA_AUDIO و READ_MEDIA_VIDEO (أضفهما في Sketchware Pro مع READ_MEDIA_IMAGES)
    boolean mediaPermKind(String kind) {
        try {
            if (android.os.Build.VERSION.SDK_INT < 23) return true;
            if (android.os.Build.VERSION.SDK_INT >= 30 && android.os.Environment.isExternalStorageManager()) return true;
            return act.checkSelfPermission(permName(kind)) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable e) { return false; }
    }

    String permName(String kind) {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (kind.equals("audio")) return "android.permission.READ_MEDIA_AUDIO";
            if (kind.equals("video")) return "android.permission.READ_MEDIA_VIDEO";
            return "android.permission.READ_MEDIA_IMAGES";
        }
        return "android.permission.READ_EXTERNAL_STORAGE";
    }

    volatile java.util.ArrayList<String[]> audL = null, vidL = null;
    volatile String permKinds = "";

    // قائمة الصوتيات/الفيديوهات: {"perm":true} أو {"total":N,"items":[{"id":..,"name":"..","dur":ثوانٍ}]}
    String mediaList(boolean video, int offset, int count, boolean refresh) {
        try {
            final String kind = video ? "video" : "audio";
            if (!mediaPermKind(kind)) {
                if (permKinds.indexOf(kind) < 0 && android.os.Build.VERSION.SDK_INT >= 23) {
                    permKinds = permKinds + kind + ",";
                    final String perm = permName(kind);
                    act.runOnUiThread(new Runnable() { public void run() {
                        try { act.requestPermissions(new String[] { perm }, 103); } catch (Throwable e) { }
                    }});
                }
                return "{\"perm\":true}";
            }
            java.util.ArrayList<String[]> l = video ? vidL : audL;
            if (l == null || refresh) {
                l = new java.util.ArrayList<String[]>();
                android.net.Uri base = video ? android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI : android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                android.database.Cursor cu = act.getContentResolver().query(base, new String[] { "_id", "_display_name", "duration" }, null, null, "date_added DESC");
                if (cu != null) {
                    while (cu.moveToNext()) {
                        String nm = cu.getString(1);
                        l.add(new String[] { String.valueOf(cu.getLong(0)), nm == null ? "" : nm, String.valueOf(cu.getLong(2)) });
                    }
                    cu.close();
                }
                if (video) vidL = l; else audL = l;
            }
            org.json.JSONObject r = new org.json.JSONObject();
            org.json.JSONArray arr = new org.json.JSONArray();
            for (int i = offset; i < Math.min(l.size(), offset + count); i++) {
                String[] e = l.get(i);
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("id", Long.parseLong(e[0]));
                o.put("name", e[1]);
                o.put("dur", Math.round(Long.parseLong(e[2]) / 100.0) / 10.0);
                arr.put(o);
            }
            r.put("total", l.size());
            r.put("items", arr);
            return r.toString();
        } catch (Throwable e) {
            return "{\"total\":0,\"items\":[],\"error\":" + org.json.JSONObject.quote(String.valueOf(e)) + "}";
        }
    }

    @android.webkit.JavascriptInterface
    public synchronized String audIds(int offset, int count, boolean refresh) { return mediaList(false, offset, count, refresh); }

    @android.webkit.JavascriptInterface
    public synchronized String vidIds(int offset, int count, boolean refresh) { return mediaList(true, offset, count, refresh); }

    // مصغّرة فيديو (إطار من الفيديو) بصيغة JPEG
    byte[] videoThumb(android.net.Uri uri, java.io.File file) {
        android.media.MediaMetadataRetriever mr = new android.media.MediaMetadataRetriever();
        try {
            if (uri != null) mr.setDataSource(act, uri); else mr.setDataSource(file.getAbsolutePath());
            android.graphics.Bitmap bm = mr.getFrameAtTime(300000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bm == null) bm = mr.getFrameAtTime();
            if (bm == null) return null;
            int big = Math.max(bm.getWidth(), bm.getHeight());
            if (big > 640) {
                float k = 640f / big;
                android.graphics.Bitmap sc = android.graphics.Bitmap.createScaledBitmap(bm, Math.round(bm.getWidth() * k), Math.round(bm.getHeight() * k), true);
                if (sc != bm) bm.recycle();
                bm = sc;
            }
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            bm.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bo);
            bm.recycle();
            return bo.toByteArray();
        } catch (Throwable e) { return null; }
        finally { try { mr.release(); } catch (Throwable x) { } }
    }

    byte[] readStream(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] bf = new byte[65536];
        int n;
        while ((n = in.read(bf)) > 0) bo.write(bf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    // مجرى محدود الطول (لدعم Range في الفيديو)
    class LimIn extends java.io.InputStream {
        final java.io.InputStream in;
        long left;
        LimIn(java.io.InputStream i, long n) { in = i; left = n; }
        public int read() throws java.io.IOException {
            if (left <= 0) return -1;
            int b = in.read();
            if (b >= 0) left--;
            return b;
        }
        public int read(byte[] b, int o, int l) throws java.io.IOException {
            if (left <= 0) return -1;
            int n = in.read(b, o, (int) Math.min((long) l, left));
            if (n > 0) left -= n;
            return n;
        }
        public void close() throws java.io.IOException { in.close(); }
    }

    // يخدم ملف صوت/فيديو من FILE PROG (Videos/Sounds/Voices) مع دعم Range — الفيديوهات الكبيرة لا تُحمَّل في الذاكرة
    android.webkit.WebResourceResponse serveFile(java.io.File f, String mime, java.util.Map<String, String> hdr) throws Exception {
        long total = f.length();
        java.io.FileInputStream fis = new java.io.FileInputStream(f);
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Cache-Control", "no-cache");
        h.put("Accept-Ranges", "bytes");
        if (total <= 0) return new android.webkit.WebResourceResponse(mime, null, 200, "OK", h, fis);
        long start = 0, end = total - 1;
        boolean part = false;
        String rg = hdr == null ? null : hdr.get("Range");
        if (rg == null && hdr != null) rg = hdr.get("range");
        if (rg != null && rg.startsWith("bytes=")) {
            try {
                String[] r = rg.substring(6).split("-", -1);
                if (r[0].trim().length() == 0) start = Math.max(0, total - Long.parseLong(r[1].trim()));
                else {
                    start = Long.parseLong(r[0].trim());
                    if (r.length > 1 && r[1].trim().length() > 0) end = Long.parseLong(r[1].trim());
                }
                part = true;
            } catch (Exception e) { start = 0; end = total - 1; part = false; }
        }
        if (end >= total) end = total - 1;
        if (start > end) { start = 0; end = total - 1; part = false; }
        long sk = 0;
        while (sk < start) { long s = fis.skip(start - sk); if (s <= 0) break; sk += s; }
        h.put("Content-Length", String.valueOf(end - start + 1));
        if (part) h.put("Content-Range", "bytes " + start + "-" + end + "/" + total);
        return new android.webkit.WebResourceResponse(mime, null, part ? 206 : 200, part ? "Partial Content" : "OK", h, new LimIn(fis, end - start + 1));
    }

    // يخدم الفيديو من MediaStore مع دعم Range (ضروري لتشغيل mp4 في الـ WebView)
    android.webkit.WebResourceResponse serveVideo(android.net.Uri uri, java.util.Map<String, String> hdr) throws Exception {
        android.content.res.AssetFileDescriptor afd = act.getContentResolver().openAssetFileDescriptor(uri, "r");
        if (afd == null) return resp("text/plain", "UTF-8", null, 404);
        long total = afd.getLength();
        String mime = act.getContentResolver().getType(uri);
        if (mime == null || mime.length() == 0) mime = "video/mp4";
        java.io.FileInputStream fis = afd.createInputStream();
        java.util.Map<String, String> h = new java.util.HashMap<String, String>();
        h.put("Access-Control-Allow-Origin", "*");
        h.put("Cache-Control", "no-cache");
        if (total <= 0) return new android.webkit.WebResourceResponse(mime, null, 200, "OK", h, fis);
        long start = 0, end = total - 1;
        String rg = hdr == null ? null : hdr.get("Range");
        if (rg == null && hdr != null) rg = hdr.get("range");
        boolean part = false;
        if (rg != null && rg.startsWith("bytes=")) {
            try {
                String[] r = rg.substring(6).split("-");
                start = Long.parseLong(r[0].trim());
                if (r.length > 1 && r[1].trim().length() > 0) end = Long.parseLong(r[1].trim());
                part = true;
            } catch (Exception e) { start = 0; end = total - 1; }
        }
        if (end >= total) end = total - 1;
        if (start > end) start = 0;
        long len = end - start + 1;
        long sk = 0;
        while (sk < start) { long s = fis.skip(start - sk); if (s <= 0) break; sk += s; }
        h.put("Accept-Ranges", "bytes");
        h.put("Content-Length", String.valueOf(len));
        if (part) h.put("Content-Range", "bytes " + start + "-" + end + "/" + total);
        return new android.webkit.WebResourceResponse(mime, null, part ? 206 : 200, part ? "Partial Content" : "OK", h, new LimIn(fis, len));
    }

    // صور الجهاز: {"perm":true} إن كنا ننتظر الإذن، وإلا {"total":N,"ids":[...]}
    @android.webkit.JavascriptInterface
    public synchronized String galIds(int offset, int count, boolean refresh) {
        try {
            if (!mediaPerm()) {
                if (!permAsked && android.os.Build.VERSION.SDK_INT >= 23) {
                    permAsked = true;
                    final String perm = android.os.Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_IMAGES" : "android.permission.READ_EXTERNAL_STORAGE";
                    act.runOnUiThread(new Runnable() { public void run() {
                        try { act.requestPermissions(new String[] { perm }, 102); } catch (Throwable e) { }
                    }});
                }
                return "{\"perm\":true}";
            }
            if (gal == null || refresh) {
                java.util.ArrayList<Long> l = new java.util.ArrayList<Long>();
                android.database.Cursor cu = act.getContentResolver().query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[] { "_id" }, null, null, "date_added DESC");
                if (cu != null) {
                    while (cu.moveToNext()) l.add(Long.valueOf(cu.getLong(0)));
                    cu.close();
                }
                gal = l;
            }
            org.json.JSONObject r = new org.json.JSONObject();
            org.json.JSONArray ids = new org.json.JSONArray();
            for (int i = offset; i < Math.min(gal.size(), offset + count); i++) ids.put(gal.get(i).longValue());
            r.put("total", gal.size());
            r.put("ids", ids);
            return r.toString();
        } catch (Throwable e) {
            return "{\"total\":0,\"ids\":[],\"error\":" + org.json.JSONObject.quote(String.valueOf(e)) + "}";
        }
    }

    // صور داخل FILE PROG (وما تحته حتى 3 مستويات) الأحدث أولاً
    @android.webkit.JavascriptInterface
    public String listImages() {
        final java.util.ArrayList<java.io.File> out = new java.util.ArrayList<java.io.File>();
        scan(TK, 0, out);
        java.util.Collections.sort(out, new java.util.Comparator<java.io.File>() {
            public int compare(java.io.File a, java.io.File b) { return a.lastModified() < b.lastModified() ? 1 : (a.lastModified() > b.lastModified() ? -1 : 0); }
        });
        org.json.JSONArray arr = new org.json.JSONArray();
        try {
            String root = TK.getCanonicalPath();
            for (int i = 0; i < Math.min(out.size(), 400); i++) {
                String c = out.get(i).getCanonicalPath();
                arr.put(c.substring(root.length() + 1));
            }
        } catch (Throwable e) { }
        return arr.toString();
    }

    void scan(java.io.File d, int depth, java.util.ArrayList<java.io.File> out) {
        java.io.File[] l = d.listFiles();
        if (l == null || depth > 3) return;
        for (java.io.File f : l) {
            if (f.isDirectory()) scan(f, depth + 1, out);
            else {
                String n = f.getName().toLowerCase();
                if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".gif")) out.add(f);
            }
        }
    }
}
final FilesHelper files = new FilesHelper();
webview1.addJavascriptInterface(files, "AndroidFiles");
webview1.setWebViewClient(new android.webkit.WebViewClient() {
    @Override
    public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view, android.webkit.WebResourceRequest req) {
        try {
            android.net.Uri u = req.getUrl();
            if (u != null && "appassets.local".equals(u.getHost())) return files.serve(u.getPath(), req.getRequestHeaders());
        } catch (Throwable e) { }
        return null;
    }
});

// ---------------------------------------------------------------- 2d) إذن تسجيل الشاشة (يعتمد على resCode/resData من onActivityResult عندك)
class ScreenPerm {
    final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    int polls = 0;

    void js(String m) {
        try { webview1.evaluateJavascript("window.__recMsg&&window.__recMsg(" + org.json.JSONObject.quote(m) + ")", null); } catch (Throwable e) {}
    }

    boolean has() { return resCode != 0 && resData != null; }
    int getCode() { return (int) resCode; }
    android.content.Intent getData() { return resData; }
    void clear() { resData = null; resCode = 0; }

    final Runnable poll = new Runnable() { public void run() {
        if (has()) { js("تم منح إذن التسجيل. اضغط Start Recording الآن"); return; }
        if (++polls < 200) h.postDelayed(this, 400);
    }};

    void ask() {
        try {
            android.media.projection.MediaProjectionManager m =
                (android.media.projection.MediaProjectionManager) act.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
            polls = 0;
            act.startActivityForResult(m.createScreenCaptureIntent(), 100);
            h.postDelayed(poll, 800);
        } catch (Throwable e) { js("تعذّر طلب إذن التسجيل: " + e); }
    }
}
final ScreenPerm perm = new ScreenPerm();

// ---------------------------------------------------------------- 3) المسجّل
webview1.addJavascriptInterface(new Object() {

    // ====== إعدادات يمكنك تعديلها ======
    final int FPS = 60;                  // إن كان هاتفك ضعيفاً وظهر تقطيع: اجعلها 30
    final float QUALITY_BPP = 0.26f;     // جودة الفيديو (بت لكل بكسل): 0.12 عادية · 0.19 عالية · 0.26 عالية جداً (~32Mbps) · 0.32 أقصى. 1080p60
    final int MAX_BITRATE = 55000000;    // سقف أمان (مرفوع ليسمح بالجودة الأعلى)
    final int AUDIO_BITRATE = 256000;    // صوت أوضح (AAC)
    final int AUDIO_OFFSET_MS = 0;       // لو الصوت متأخر عن الصورة اجعلها سالبة (-80)، ولو متقدم اجعلها موجبة (80)

    // ====== الحالة ======
    volatile boolean recording = false, stopping = false, busy = false;
    int outW = 1080, outH = 1920;
    String fmtKey = "reels";             // الشكل الوحيد: 9:16 لتيك توك وإنستغرام Reels معًا (1080×1920)
    String recFolder = "Reels";           // مجلد الحفظ الخاص بالتسجيل الحالي (يُثبَّت عند بدء التسجيل)
    final String ROOT_FOLDER = "MessageTIk";   // المجلد الأب داخل Movies
    int reqX = 0, reqY = 0, reqW = 0, reqH = 0;
    int cropX, cropY, cropW, cropH, scrW, scrH, scrDpi;
    boolean dndAsked = false, dndChanged = false, goSent = false, vstarted = false, videoOk = true;
    int vTrack = -1, oldFilter = 1, frameDiv = 1, frameCount = 0;
    long firstTs = -1, lastTs = -1, lastPts = 0, lastWall = 0, lastVideoUs = 0;
    volatile long markUs = -1;           // طول الجزء الذي يسبق المشهد (µs): يُقصّ من الصوت
    volatile boolean marked = false;     // true بعد markScene: من هنا فقط يبدأ ترميز الفيديو
    long markTs = -1, lastSeenTs = 0, lastSeenWall = 0;
    java.io.File vFile, aFile;
    java.io.BufferedOutputStream aOut;
    int audioRate = 0;
    final Object audioLock = new Object();
    java.util.ArrayList<byte[]> aData;
    java.util.ArrayList<long[]> aMeta;
    android.media.MediaFormat aFmt;

    android.os.HandlerThread glThread;
    android.os.Handler glHandler;
    android.media.projection.MediaProjection projection;
    android.hardware.display.VirtualDisplay vd;
    android.media.MediaCodec venc;
    android.media.MediaMuxer vmux;
    android.view.Surface encSurface, vdSurface;
    android.graphics.SurfaceTexture st;
    android.opengl.EGLDisplay eglDisplay;
    android.opengl.EGLContext eglCtx;
    android.opengl.EGLSurface eglSurf;
    int prog, texId, aPosLoc, aTexLoc, uTexLoc, sTexLoc;
    java.nio.FloatBuffer vbuf;
    final float[] tm = new float[16];
    android.app.NotificationManager nm;
    android.app.NotificationManager.Policy oldPolicy;

    // ============================================================ أدوات
    // لا يوجد Toast نظام إطلاقاً (كي لا يظهر في الفيديو). الرسائل تظهر داخل الصفحة وبعد انتهاء التسجيل فقط.
    void toast(final String s) {
        act.runOnUiThread(new Runnable() { public void run() {
            try { webview1.evaluateJavascript("window.__recMsg&&window.__recMsg(" + org.json.JSONObject.quote(s) + ")", null); } catch (Exception e) {}
        }});
    }

    void jsGo(final boolean ok, final String msg) {
        act.runOnUiThread(new Runnable() { public void run() {
            try {
                webview1.evaluateJavascript("window.__recGo&&window.__recGo(" + ok + "," + org.json.JSONObject.quote(msg == null ? "" : msg) + ")", null);
            } catch (Exception e) {}
        }});
    }

    // ============================================================ عدم الإزعاج (لا إشعارات ولا مكالمات ولا أصوات ولا نوافذ منبثقة)
    void dndOn() {
        if (dndChanged) return;
        try {
            nm = (android.app.NotificationManager) act.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                oldFilter = nm.getCurrentInterruptionFilter();
                oldPolicy = nm.getNotificationPolicy();
                try {   // إخفاء النوافذ المنبثقة (Heads-up) والأضواء والشاشة المنبثقة
                    nm.setNotificationPolicy(new android.app.NotificationManager.Policy(0, 0, 0, 4 | 8 | 16 | 128));
                } catch (Exception e) {}
                nm.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_NONE);
                dndChanged = true;
            }
        } catch (Exception e) {}
    }

    void dndOff() {
        if (!dndChanged) return;
        try {
            nm.setInterruptionFilter(oldFilter);
            if (oldPolicy != null) nm.setNotificationPolicy(oldPolicy);
        } catch (Exception e) {}
        dndChanged = false;
    }

    // ============================================================ واجهات JavaScript
    @android.webkit.JavascriptInterface
    public void setFormat(final String key, int w, int h, final String ori) {
        fmtKey = "reels";                       // أي قيمة قادمة من الصفحة تُحوَّل إلى Reels 9:16 (تمت إزالة YouTube)
        outW = 1080;                            // المقاس ثابت: مناسب لتيك توك وإنستغرام معًا
        outH = 1920;
        act.runOnUiThread(new Runnable() { public void run() {
            try {
                act.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);   // الفيديو عمودي دائمًا
            } catch (Exception e) {}
        }});
    }

    @android.webkit.JavascriptInterface
    public void setCropRect(int x, int y, int w, int h) { reqX = x; reqY = y; reqW = w; reqH = h; }

    @android.webkit.JavascriptInterface
    public void audioStart(int rate) {
        synchronized (audioLock) {
            try {
                audioRate = rate;
                if (aFile != null) aOut = new java.io.BufferedOutputStream(new java.io.FileOutputStream(aFile), 1 << 16);
            } catch (Exception e) { aOut = null; }
        }
    }

    @android.webkit.JavascriptInterface
    public void audioChunk(String b64) {
        synchronized (audioLock) {
            if (aOut == null) return;
            try { aOut.write(android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)); } catch (Exception e) {}
        }
    }

    @android.webkit.JavascriptInterface
    public String dnd(boolean on) {
        if (!on) { dndOff(); return "ok"; }
        try {
            if (android.os.Build.VERSION.SDK_INT < 23) return "skip";
            android.app.NotificationManager n0 = (android.app.NotificationManager) act.getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            if (n0 == null) return "skip";
            if (!n0.isNotificationPolicyAccessGranted()) {
                if (dndAsked) return "skip";     // نطلبه مرة واحدة فقط، وبعدها نكمل بدونه
                dndAsked = true;
                act.runOnUiThread(new Runnable() { public void run() {
                    try { act.startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); } catch (Exception e) {}
                }});
                return "ask";
            }
            dndOn();
            return "ok";
        } catch (Exception e) { return "skip"; }
    }

    @android.webkit.JavascriptInterface
    public void startRecording() {
        act.runOnUiThread(new Runnable() { public void run() { beginOnUi(); } });
    }

    @android.webkit.JavascriptInterface
    public void stopRecording() {
        if (!recording) return;
        recording = false;
        stopping = true;
        busy = true;
        final android.os.Handler h = glHandler;
        if (h == null) { busy = false; return; }
        h.post(new Runnable() { public void run() {
            teardown(true);
            dndOff();                       // الإشعارات تعود بعد انتهاء التصوير
            toast("جاري دمج الصوت مع الفيديو… لا تغلق التطبيق");
            if (videoOk && vFile != null && vFile.length() > 0) muxFinal(); else toast("لم يُحفظ الفيديو: " + videoErr);
            try { if (vFile != null) vFile.delete(); if (aFile != null) aFile.delete(); } catch (Exception e) {}
            busy = false;
            try { glThread.quitSafely(); } catch (Exception e) {}
        }});
    }

    // تستدعيها الصفحة لحظة بدء المشهد الفعلي: من هنا يبدأ ترميز الفيديو (أول إطار = المشهد تماماً)
    // وما قبلها (قائمة النظام + شاشة التجهيز) لا يُشفَّر أصلاً، ويُقصّ مقابله من الصوت
    @android.webkit.JavascriptInterface
    public void markScene() {
        final android.os.Handler h = glHandler;
        if (!recording || h == null) return;
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        h.post(new Runnable() { public void run() {
            try {
                if (venc != null && !stopping && firstTs >= 0 && !marked) {
                    markTs = lastSeenTs + (System.nanoTime() - lastSeenWall);   // الآن بمقياس زمن الإطارات
                    markUs = Math.max(0L, (markTs - firstTs) / 1000L);
                    draw(0);                         // أول إطار: آخر صورة على الشاشة = المشهد قبل أي حركة
                    lastPts = 0; lastWall = System.nanoTime(); frameCount = 0;
                    marked = true;
                    drain(false);
                }
            } catch (Exception e) { videoOk = false; videoErr = "mark: " + e.toString(); }
            done.countDown();
        }});
        try { done.await(600, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (Exception e) {}   // لا يبدأ المشهد قبل رسم أول إطار
    }

    // خدمة المقدمة MediaProjectionService (مطلوبة من أندرويد 10+ قبل getMediaProjection). تُستدعى بالاسم كي لا يتعطل التجميع قبل إنشاء الكلاس.
    void startSvc() {
        try {
            android.content.Intent serviceIntent = new android.content.Intent(act.getApplicationContext(), Class.forName(act.getPackageName() + ".MediaProjectionService"));
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                act.startForegroundService(serviceIntent);
            } else {
                act.startService(serviceIntent);
            }
        } catch (Throwable e) { }
    }

    void stopSvc() {
        try { act.stopService(new android.content.Intent(act.getApplicationContext(), Class.forName(act.getPackageName() + ".MediaProjectionService"))); } catch (Throwable e) { }
    }

    // ============================================================ بدء التسجيل (خيط الواجهة)
    void beginOnUi() {
        if (recording || busy) { jsGo(false, "انتظر لحظة حتى ينتهي الحفظ ثم أعد المحاولة"); return; }
        try {
            if (!perm.has()) {
                jsGo(false, "امنح إذن تسجيل الشاشة (Start now) ثم اضغط Start Recording");
                perm.ask();
                return;
            }
            // (عدم الإزعاج يُفعَّل قبل التسجيل بوقت كافٍ من صفحة الـ HTML عبر dnd(true) كي لا يظهر أي إشعار نظام في الفيديو)
            float hz = act.getWindowManager().getDefaultDisplay().getRefreshRate();
            frameDiv = Math.max(1, Math.round(hz / FPS)); frameCount = 0;   // شاشة 120Hz → نأخذ فريماً من كل 2 بإيقاع ثابت

            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            act.getWindowManager().getDefaultDisplay().getRealMetrics(dm);
            scrW = dm.widthPixels; scrH = dm.heightPixels; scrDpi = dm.densityDpi;
            int[] loc = new int[2];
            webview1.getLocationOnScreen(loc);
            cropX = reqX + loc[0]; cropY = reqY + loc[1]; cropW = reqW; cropH = reqH;
            if (cropW <= 0 || cropH <= 0) { cropX = 0; cropY = 0; cropW = scrW; cropH = scrH; }
            if (cropX < 0) cropX = 0;
            if (cropY < 0) cropY = 0;
            if (cropX + cropW > scrW) cropW = scrW - cropX;
            if (cropY + cropH > scrH) cropH = scrH - cropY;
            // لو المنطقة قريبة جداً من مقاس الفيديو نطابقهما تماماً: نسخ بكسل-ببكسل بلا تكبير ولا تصغير = أوضح صورة
            if (Math.abs(cropW - outW) <= 12 && Math.abs(cropH - outH) <= 12 && outW <= scrW && outH <= scrH) {
                cropX = Math.max(0, Math.min(scrW - outW, cropX + (cropW - outW) / 2));
                cropY = Math.max(0, Math.min(scrH - outH, cropY + (cropH - outH) / 2));
                cropW = outW; cropH = outH;
            }

            recFolder = "Reels";   // فيديو واحد يصلح للمنصتين: لا مجلد YouTube/Instagram/TikTok منفصل
            long stamp = System.currentTimeMillis();
            vFile = new java.io.File(act.getCacheDir(), "v_" + stamp + ".mp4");
            aFile = new java.io.File(act.getCacheDir(), "a_" + stamp + ".pcm");
            synchronized (audioLock) { aOut = null; audioRate = 0; }
            firstTs = -1; lastTs = -1; lastPts = 0; lastVideoUs = 0; lastWall = System.nanoTime(); markUs = -1; marked = false; markTs = -1; lastSeenTs = 0; lastSeenWall = 0;
            goSent = false; vstarted = false; videoOk = true; stopping = false;

            startSvc();   // الخدمة تبدأ قبل getMediaProjection
            glThread = new android.os.HandlerThread("rec-gl", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
            glThread.start();
            glHandler = new android.os.Handler(glThread.getLooper());
            recording = true;
            glHandler.post(new Runnable() { public void run() {
                try { glStart(); } catch (Exception e) { failStart(e); }
            }});
        } catch (Exception e) {
            failStart(e);
        }
    }

    void failStart(Exception e) {
        boolean secure = (e instanceof SecurityException);
        recording = false;
        try { teardown(false); } catch (Exception x) {}
        dndOff();
        try { if (vFile != null) vFile.delete(); if (aFile != null) aFile.delete(); } catch (Exception x) {}
        try { if (glThread != null) glThread.quitSafely(); } catch (Exception x) {}
        if (secure) {   // أندرويد 14+: إذن التسجيل يُستعمل مرة واحدة
            perm.clear();
            jsGo(false, "رفض النظام بدء التسجيل (SecurityException): " + e.getMessage());
        } else {
            jsGo(false, "خطأ في بدء التسجيل: " + e.getMessage());
        }
    }

    // ============================================================ خيط GL: تجهيز المشفّر + الـ GPU + الشاشة الافتراضية
    int clampBitrate(int br) {
        try {
            return venc.getCodecInfo().getCapabilitiesForType("video/avc").getVideoCapabilities().getBitrateRange().clamp(br);
        } catch (Throwable t) { return br; }
    }

    // tier 0 = أعلى جودة (High + Level 4.2 + ألوان BT.709 مطابقة للشاشة)، 1 = High فقط، 2 = الإعدادات الأساسية
    android.media.MediaFormat videoFormat(int tier) {
        android.media.MediaFormat f = android.media.MediaFormat.createVideoFormat("video/avc", outW, outH);
        int br = (int) Math.min((long) MAX_BITRATE, (long) (outW * (long) outH * FPS * QUALITY_BPP));
        f.setInteger(android.media.MediaFormat.KEY_COLOR_FORMAT, android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(android.media.MediaFormat.KEY_BIT_RATE, clampBitrate(br));
        f.setInteger(android.media.MediaFormat.KEY_FRAME_RATE, FPS);
        f.setInteger(android.media.MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        f.setInteger(android.media.MediaFormat.KEY_BITRATE_MODE, android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            f.setInteger(android.media.MediaFormat.KEY_PRIORITY, 0);
            f.setInteger(android.media.MediaFormat.KEY_OPERATING_RATE, FPS);
        }
        if (tier <= 1) f.setInteger(android.media.MediaFormat.KEY_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
        if (tier == 0) {
            f.setInteger(android.media.MediaFormat.KEY_LEVEL, android.media.MediaCodecInfo.CodecProfileLevel.AVCLevel42);
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                f.setInteger(android.media.MediaFormat.KEY_COLOR_STANDARD, android.media.MediaFormat.COLOR_STANDARD_BT709);
                f.setInteger(android.media.MediaFormat.KEY_COLOR_RANGE, android.media.MediaFormat.COLOR_RANGE_LIMITED);
                f.setInteger(android.media.MediaFormat.KEY_COLOR_TRANSFER, android.media.MediaFormat.COLOR_TRANSFER_SDR_VIDEO);
            }
        }
        return f;
    }

    int mkShader(int type, String src) {
        int s = android.opengl.GLES20.glCreateShader(type);
        android.opengl.GLES20.glShaderSource(s, src);
        android.opengl.GLES20.glCompileShader(s);
        return s;
    }

    void glStart() throws Exception {
        // ---- المشفّر
        boolean cfgOk = false;
        for (int tier = 0; tier <= 2 && !cfgOk; tier++) {
            venc = android.media.MediaCodec.createEncoderByType("video/avc");
            try {
                venc.configure(videoFormat(tier), null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
                cfgOk = true;
            } catch (Exception e) {
                venc.release();
                venc = null;
                if (tier == 2) throw e;
            }
        }
        encSurface = venc.createInputSurface();
        venc.start();
        vmux = new android.media.MediaMuxer(vFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        // ---- EGL
        eglDisplay = android.opengl.EGL14.eglGetDisplay(android.opengl.EGL14.EGL_DEFAULT_DISPLAY);
        int[] ver = new int[2];
        android.opengl.EGL14.eglInitialize(eglDisplay, ver, 0, ver, 1);
        int[] attrs = {
            android.opengl.EGL14.EGL_RED_SIZE, 8, android.opengl.EGL14.EGL_GREEN_SIZE, 8,
            android.opengl.EGL14.EGL_BLUE_SIZE, 8, android.opengl.EGL14.EGL_ALPHA_SIZE, 8,
            android.opengl.EGL14.EGL_RENDERABLE_TYPE, android.opengl.EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1,   // EGL_RECORDABLE_ANDROID
            android.opengl.EGL14.EGL_NONE };
        android.opengl.EGLConfig[] cfgs = new android.opengl.EGLConfig[1];
        int[] num = new int[1];
        android.opengl.EGL14.eglChooseConfig(eglDisplay, attrs, 0, cfgs, 0, 1, num, 0);
        int[] ctxAttr = { android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, android.opengl.EGL14.EGL_NONE };
        eglCtx = android.opengl.EGL14.eglCreateContext(eglDisplay, cfgs[0], android.opengl.EGL14.EGL_NO_CONTEXT, ctxAttr, 0);
        int[] sAttr = { android.opengl.EGL14.EGL_NONE };
        eglSurf = android.opengl.EGL14.eglCreateWindowSurface(eglDisplay, cfgs[0], encSurface, sAttr, 0);
        android.opengl.EGL14.eglMakeCurrent(eglDisplay, eglSurf, eglSurf, eglCtx);

        // ---- الشيدر (يقص منطقة الـ HTML فقط عبر إحداثيات الخامة)
        String vs = "uniform mat4 uTex; attribute vec2 aPos; attribute vec2 aTex; varying vec2 vTex;"
            + "void main(){ gl_Position=vec4(aPos,0.0,1.0); vTex=(uTex*vec4(aTex,0.0,1.0)).xy; }";
        String fs = "#extension GL_OES_EGL_image_external : require\n"
            + "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n"
            + "varying vec2 vTex; uniform samplerExternalOES sTex;"
            + "void main(){ gl_FragColor=texture2D(sTex,vTex); }";
        prog = android.opengl.GLES20.glCreateProgram();
        android.opengl.GLES20.glAttachShader(prog, mkShader(android.opengl.GLES20.GL_VERTEX_SHADER, vs));
        android.opengl.GLES20.glAttachShader(prog, mkShader(android.opengl.GLES20.GL_FRAGMENT_SHADER, fs));
        android.opengl.GLES20.glLinkProgram(prog);
        aPosLoc = android.opengl.GLES20.glGetAttribLocation(prog, "aPos");
        aTexLoc = android.opengl.GLES20.glGetAttribLocation(prog, "aTex");
        uTexLoc = android.opengl.GLES20.glGetUniformLocation(prog, "uTex");
        sTexLoc = android.opengl.GLES20.glGetUniformLocation(prog, "sTex");

        float u0 = (float) cropX / scrW, u1 = (float) (cropX + cropW) / scrW;
        float vTop = 1f - (float) cropY / scrH, vBot = 1f - (float) (cropY + cropH) / scrH;
        float[] quad = { -1, -1, u0, vBot,   1, -1, u1, vBot,   -1, 1, u0, vTop,   1, 1, u1, vTop };
        vbuf = java.nio.ByteBuffer.allocateDirect(quad.length * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        vbuf.put(quad);

        int[] tex = new int[1];
        android.opengl.GLES20.glGenTextures(1, tex, 0);
        texId = tex[0];
        int T = android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES;
        android.opengl.GLES20.glBindTexture(T, texId);
        android.opengl.GLES20.glTexParameteri(T, android.opengl.GLES20.GL_TEXTURE_MIN_FILTER, android.opengl.GLES20.GL_LINEAR);
        android.opengl.GLES20.glTexParameteri(T, android.opengl.GLES20.GL_TEXTURE_MAG_FILTER, android.opengl.GLES20.GL_LINEAR);
        android.opengl.GLES20.glTexParameteri(T, android.opengl.GLES20.GL_TEXTURE_WRAP_S, android.opengl.GLES20.GL_CLAMP_TO_EDGE);
        android.opengl.GLES20.glTexParameteri(T, android.opengl.GLES20.GL_TEXTURE_WRAP_T, android.opengl.GLES20.GL_CLAMP_TO_EDGE);

        st = new android.graphics.SurfaceTexture(texId);
        st.setDefaultBufferSize(scrW, scrH);
        vdSurface = new android.view.Surface(st);
        st.setOnFrameAvailableListener(new android.graphics.SurfaceTexture.OnFrameAvailableListener() {
            public void onFrameAvailable(android.graphics.SurfaceTexture t) { onFrame(); }
        }, glHandler);

        // ---- الشاشة الافتراضية
        android.media.projection.MediaProjectionManager mpm =
            (android.media.projection.MediaProjectionManager) act.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE);
        try { Thread.sleep(400); } catch (Exception e) {}   // ندع الخدمة تستدعي startForeground أولاً
        projection = mpm.getMediaProjection(perm.getCode(), perm.getData());
        projection.registerCallback(new android.media.projection.MediaProjection.Callback() {
            public void onStop() { if (recording) stopRecording(); }
        }, glHandler);
        vd = projection.createVirtualDisplay("MessageTIkRec", scrW, scrH, scrDpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, vdSurface, null, null);
    }

    void draw(long ptsNs) {
        android.opengl.GLES20.glViewport(0, 0, outW, outH);
        android.opengl.GLES20.glUseProgram(prog);
        android.opengl.GLES20.glActiveTexture(android.opengl.GLES20.GL_TEXTURE0);
        android.opengl.GLES20.glBindTexture(android.opengl.GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        android.opengl.GLES20.glUniform1i(sTexLoc, 0);
        android.opengl.GLES20.glUniformMatrix4fv(uTexLoc, 1, false, tm, 0);
        vbuf.position(0);
        android.opengl.GLES20.glVertexAttribPointer(aPosLoc, 2, android.opengl.GLES20.GL_FLOAT, false, 16, vbuf);
        android.opengl.GLES20.glEnableVertexAttribArray(aPosLoc);
        vbuf.position(2);
        android.opengl.GLES20.glVertexAttribPointer(aTexLoc, 2, android.opengl.GLES20.GL_FLOAT, false, 16, vbuf);
        android.opengl.GLES20.glEnableVertexAttribArray(aTexLoc);
        android.opengl.GLES20.glDrawArrays(android.opengl.GLES20.GL_TRIANGLE_STRIP, 0, 4);
        android.opengl.EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurf, ptsNs);
        android.opengl.EGL14.eglSwapBuffers(eglDisplay, eglSurf);
    }

    void onFrame() {
        if (stopping || st == null) return;
        try {
            st.updateTexImage();
            st.getTransformMatrix(tm);
            long ts = st.getTimestamp();
            if (ts <= 0) ts = System.nanoTime();
            if (firstTs < 0) firstTs = ts;
            lastSeenTs = ts; lastSeenWall = System.nanoTime();
            if (!goSent) { goSent = true; jsGo(true, ""); }   // أول فريم حقيقي ← الآن يبدأ الصوت
            if (!marked) return;                              // قبل بدء المشهد: نستهلك الإطارات فقط ولا نشفّرها
            if ((frameCount++ % frameDiv) != 0) return;       // إيقاع ثابت بلا اهتزاز في السلاسة
            lastTs = ts;
            long pts = ts - markTs;
            if (pts <= lastPts) pts = lastPts + 1000000L;     // الزمن يجب أن يتصاعد دائماً
            draw(pts);
            lastPts = pts;
            lastWall = System.nanoTime();
            drain(false);
        } catch (Exception e) { videoOk = false; videoErr = "frame: " + e.toString(); }
    }

    void drain(boolean eos) {
        if (venc == null) return;
        if (eos) { try { venc.signalEndOfInputStream(); } catch (Exception e) {} }
        android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();
        int guard = 0;
        while (true) {
            int i = venc.dequeueOutputBuffer(bi, eos ? 10000 : 0);
            if (i == android.media.MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!eos || ++guard > 300) break;
            } else if (i == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                vTrack = vmux.addTrack(venc.getOutputFormat());
                vmux.start();
                vstarted = true;
            } else if (i >= 0) {
                java.nio.ByteBuffer b = venc.getOutputBuffer(i);
                if ((bi.flags & android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
                if (bi.size > 0 && vstarted && b != null) {
                    b.position(bi.offset);
                    b.limit(bi.offset + bi.size);
                    vmux.writeSampleData(vTrack, b, bi);
                    if (bi.presentationTimeUs > lastVideoUs) lastVideoUs = bi.presentationTimeUs;
                }
                venc.releaseOutputBuffer(i, false);
                if ((bi.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        }
    }

    // ============================================================ إنهاء الفيديو وتحرير كل شيء
    void teardown(boolean graceful) {
        stopping = true;
        try { if (vd != null) vd.release(); } catch (Exception e) {}
        vd = null;
        try { if (projection != null) projection.stop(); } catch (Exception e) {}
        projection = null;
        if (graceful) {
            try {
                if (marked) {
                    long endPts = lastPts + (System.nanoTime() - lastWall);   // نثبّت آخر فريم حتى لحظة الإيقاف الفعلية
                    if (endPts > lastPts + 20000000L) { draw(endPts); }
                    drain(false);
                    drain(true);
                } else { videoOk = false; videoErr = (firstTs < 0) ? "لم يصل أي فريم من الشاشة" : "لم يبدأ المشهد قبل الإيقاف"; }
            } catch (Exception e) { videoOk = false; videoErr = "end: " + e.toString(); }
        }
        try { if (venc != null) venc.stop(); } catch (Exception e) {}
        try { if (venc != null) venc.release(); } catch (Exception e) {}
        venc = null;
        try { if (vmux != null) { if (vstarted) vmux.stop(); vmux.release(); } } catch (Exception e) { videoOk = false; videoErr = "muxer: " + e.toString(); }
        vmux = null;
        try {
            if (eglDisplay != null) {
                android.opengl.EGL14.eglMakeCurrent(eglDisplay, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_SURFACE, android.opengl.EGL14.EGL_NO_CONTEXT);
                if (eglSurf != null) android.opengl.EGL14.eglDestroySurface(eglDisplay, eglSurf);
                if (eglCtx != null) android.opengl.EGL14.eglDestroyContext(eglDisplay, eglCtx);
                android.opengl.EGL14.eglTerminate(eglDisplay);
            }
        } catch (Exception e) {}
        eglDisplay = null; eglSurf = null; eglCtx = null;
        try { if (st != null) st.release(); } catch (Exception e) {}
        st = null;
        try { if (vdSurface != null) vdSurface.release(); } catch (Exception e) {}
        try { if (encSurface != null) encSurface.release(); } catch (Exception e) {}
        vdSurface = null; encSurface = null;
        synchronized (audioLock) {
            try { if (aOut != null) { aOut.flush(); aOut.close(); } } catch (Exception e) {}
            aOut = null;
        }
        stopSvc();
    }

    // ============================================================ ترميز الصوت AAC
    byte[] readAll(java.io.File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        int o = 0;
        while (o < b.length) { int n = in.read(b, o, b.length - o); if (n < 0) break; o += n; }
        in.close();
        return b;
    }

    void encodeAac(byte[] pcm, int rate) throws Exception {
        aData = new java.util.ArrayList<byte[]>();
        aMeta = new java.util.ArrayList<long[]>();
        aFmt = null;
        android.media.MediaFormat f = android.media.MediaFormat.createAudioFormat("audio/mp4a-latm", rate, 1);
        f.setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        f.setInteger(android.media.MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
        f.setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
        android.media.MediaCodec enc = android.media.MediaCodec.createEncoderByType("audio/mp4a-latm");
        enc.configure(f, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE);
        enc.start();
        android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();
        int pos = 0, idle = 0;
        boolean inDone = false, outDone = false;
        long fed = 0;
        while (!outDone) {
            if (!inDone) {
                int ii = enc.dequeueInputBuffer(10000);
                if (ii >= 0) {
                    java.nio.ByteBuffer ib = enc.getInputBuffer(ii);
                    ib.clear();
                    int n = Math.min(ib.remaining(), pcm.length - pos);
                    n -= n % 2;
                    if (n <= 0) {
                        enc.queueInputBuffer(ii, 0, 0, fed * 1000000L / rate, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inDone = true;
                    } else {
                        ib.put(pcm, pos, n);
                        enc.queueInputBuffer(ii, 0, n, fed * 1000000L / rate, 0);
                        pos += n;
                        fed += n / 2;
                    }
                }
            }
            int oi = enc.dequeueOutputBuffer(bi, 10000);
            if (oi >= 0) {
                idle = 0;
                java.nio.ByteBuffer ob = enc.getOutputBuffer(oi);
                if ((bi.flags & android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bi.size > 0) {
                    byte[] d = new byte[bi.size];
                    ob.position(bi.offset);
                    ob.limit(bi.offset + bi.size);
                    ob.get(d);
                    aData.add(d);
                    aMeta.add(new long[] { bi.presentationTimeUs });
                }
                enc.releaseOutputBuffer(oi, false);
                if ((bi.flags & android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outDone = true;
            } else if (oi == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                aFmt = enc.getOutputFormat();
            } else if (inDone && ++idle > 500) {
                break;
            }
        }
        enc.stop();
        enc.release();
    }

    // ============================================================ الدمج النهائي: فيديو (بلا إعادة ترميز) + صوت AAC → MP4 في المعرض
    String lastErr = "", videoErr = "";

    void muxFinal() {
        boolean ok = muxOnce(true);
        if (!ok) ok = muxOnce(false);   // محاولة ثانية: الفيديو وحده بدون صوت (أفضل من لا شيء)
        if (ok) { toast("تم حفظ الفيديو في Movies/" + ROOT_FOLDER + "/" + recFolder); }
        else toast("فشل الحفظ: " + lastErr);
    }

    boolean muxOnce(boolean withAudio) {
        String stage = "";
        android.os.ParcelFileDescriptor pfd = null;
        android.net.Uri uri = null;
        android.media.MediaMuxer mux = null;
        android.media.MediaExtractor ex = null;
        java.io.File outFile = null;
        boolean ok = false;
        try {
            // ---- الفيديو المؤقت
            stage = "video";
            ex = new android.media.MediaExtractor();
            ex.setDataSource(vFile.getAbsolutePath());
            int vi = -1;
            android.media.MediaFormat vf = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                android.media.MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) { vi = i; vf = f; break; }
            }
            ex.selectTrack(vi);
            long cutUs = (markUs > 0) ? markUs : 0;   // الفيديو يبدأ أصلاً من المشهد: نقصّ هذا المقدار من بداية الصوت فقط

            // ---- الصوت (يُقصّ منه نفس المقدار المقصوص من الفيديو)
            stage = "audio";
            aFmt = null;
            try {
                if (withAudio && audioRate > 0 && aFile != null && aFile.length() > 2000 && lastVideoUs > 0) {
                    byte[] pcm = readAll(aFile);
                    int cutBytes = (int) (cutUs * audioRate / 1000000L) * 2;
                    if (cutBytes > 0) pcm = (cutBytes < pcm.length) ? java.util.Arrays.copyOfRange(pcm, cutBytes, pcm.length) : new byte[0];
                    int off = (int) ((long) audioRate * AUDIO_OFFSET_MS / 1000) * 2;
                    int target = (int) (lastVideoUs * audioRate / 1000000L) * 2;   // نطابق طول الصوت لطول الفيديو بالضبط
                    byte[] adj = new byte[target];
                    if (off >= 0) {
                        int n = Math.min(pcm.length, target - off);
                        if (n > 0) System.arraycopy(pcm, 0, adj, off, n);
                    } else {
                        int n = Math.min(pcm.length + off, target);
                        if (n > 0) System.arraycopy(pcm, -off, adj, 0, n);
                    }
                    encodeAac(adj, audioRate);
                }
            } catch (Exception e) { aFmt = null; }   // لو فشل الصوت نحفظ الفيديو وحده

            // ---- الوجهة
            stage = "dest";
            String name = "Message_Chat_" + System.currentTimeMillis() + ".mp4";
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Video.Media.DISPLAY_NAME, name);
                v.put(android.provider.MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                v.put(android.provider.MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + ROOT_FOLDER + "/" + recFolder);
                v.put(android.provider.MediaStore.Video.Media.IS_PENDING, 1);
                uri = act.getContentResolver().insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
                pfd = act.getContentResolver().openFileDescriptor(uri, "rw");
                mux = new android.media.MediaMuxer(pfd.getFileDescriptor(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } else {
                java.io.File dir = new java.io.File(new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), ROOT_FOLDER), recFolder);
                dir.mkdirs();
                outFile = new java.io.File(dir, name);
                mux = new android.media.MediaMuxer(outFile.getAbsolutePath(), android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            }

            stage = "video";
            int vt = mux.addTrack(vf);
            int at = (aFmt != null && aData != null && aData.size() > 0) ? mux.addTrack(aFmt) : -1;
            stage = "write";
            mux.start();

            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(4 * 1024 * 1024);
            android.media.MediaCodec.BufferInfo bi = new android.media.MediaCodec.BufferInfo();
            int ai = 0, na = (at >= 0) ? aMeta.size() : 0;
            boolean vDone = false;
            while (true) {
                long vtm = vDone ? -1 : ex.getSampleTime();
                long atm = (ai < na) ? aMeta.get(ai)[0] : Long.MAX_VALUE;
                if (vtm < 0 && ai >= na) break;
                if (vtm >= 0 && vtm <= atm) {
                    buf.clear();
                    int sz = ex.readSampleData(buf, 0);
                    if (sz < 0) { vDone = true; continue; }
                    bi.set(0, sz, vtm, (ex.getSampleFlags() & android.media.MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
                    mux.writeSampleData(vt, buf, bi);
                    ex.advance();
                } else {
                    byte[] d = aData.get(ai);
                    buf.clear();
                    buf.put(d);
                    buf.flip();
                    bi.set(0, d.length, atm, 0);
                    mux.writeSampleData(at, buf, bi);
                    ai++;
                }
            }
            mux.stop();
            ok = true;
        } catch (Exception e) {
            e.printStackTrace();
            lastErr = stage + " - " + e.toString();
        }
        try { if (ex != null) ex.release(); } catch (Exception e) {}
        try { if (mux != null) mux.release(); } catch (Exception e) {}
        try { if (pfd != null) pfd.close(); } catch (Exception e) {}
        try {
            if (uri != null) {
                if (ok) {
                    android.content.ContentValues v2 = new android.content.ContentValues();
                    v2.put(android.provider.MediaStore.Video.Media.IS_PENDING, 0);
                    act.getContentResolver().update(uri, v2, null, null);
                } else {
                    act.getContentResolver().delete(uri, null, null);
                }
            } else if (outFile != null && ok) {
                android.media.MediaScannerConnection.scanFile(act, new String[] { outFile.getAbsolutePath() }, null, null);
            }
        } catch (Exception e) {}
        return ok;
    }

}, "AndroidRecorder");

// ---------------------------------------------------------------- 4) تحميل المحرّك من /sdcard/FILE PROG/index.html ثم طلب إذن تسجيل الشاشة
// يحتاج في Sketchware Pro: صلاحية READ_EXTERNAL_STORAGE، وعلى أندرويد 11+ (إن كان Target SDK 30+) صلاحية MANAGE_EXTERNAL_STORAGE أيضاً.
// نقطة الدخول: FILE PROG/index.html (و scenario.json بجانبه) — لتغييرها عدّل ENTRY_DIR و PREFERRED_HTML داخل FilesHelper.
final android.os.Handler mainH = new android.os.Handler(android.os.Looper.getMainLooper());
final int[] S = new int[5];   // 0=تم التحميل 1=طُلب إذن القراءة 2=فُتحت صفحة كل الملفات 3=عدد المحاولات 4=آخر رسالة معروضة
final Runnable[] loader = new Runnable[1];

loader[0] = new Runnable() { public void run() {
    if (S[0] == 1 || act.isFinishing()) return;
    S[3]++;
    java.io.File htmlFile = files.pickHtml();
    if (htmlFile != null) {
        S[0] = 1;
        try {
            // نقرأ الملف من /sdcard/FILE PROG ونحفظ نسخة داخل التطبيق، ثم نعرضه كصفحة https عادية (الخطوط والصوت يعملان كما على الإنترنت)
            byte[] data = files.readAll(htmlFile);
            files.cacheWeb(data);
            webview1.loadDataWithBaseURL("https://appassets.local/", new String(data, "UTF-8"), "text/html", "UTF-8", null);
        } catch (Exception e) { S[0] = 0; mainH.postDelayed(loader[0], 700); return; }
        perm.ask();   // طلب إذن تسجيل الشاشة بعد ظهور الصفحة
        return;
    }
    // ====== جديد: لا يوجد ملف في FILE PROG → حمّل الصفحة المدمجة من assets مباشرة ======
    // (مكتفٍ ذاتيًا: بلا مجلد خارجي وبلا إذن تخزين. يظل بإمكان المستخدم وضع نسخته في FILE PROG لتتقدّم.)
    try {
        java.io.InputStream ais = act.getAssets().open("index.html");
        java.io.ByteArrayOutputStream abo = new java.io.ByteArrayOutputStream();
        byte[] abuf = new byte[8192]; int an;
        while ((an = ais.read(abuf)) > 0) abo.write(abuf, 0, an);
        ais.close();
        byte[] adata = abo.toByteArray();
        S[0] = 1;
        try { files.cacheWeb(adata); } catch (Exception ce) {}   // نسخة داخلية احتياطية (اختياري)
        webview1.loadDataWithBaseURL("https://appassets.local/", new String(adata, "UTF-8"), "text/html", "UTF-8", null);
        perm.ask();
        return;
    } catch (Exception ae) { /* لا يوجد أصل مدمج باسم index.html — نكمل بالسلوك القديم */ }
    // ====== نهاية الإضافة ======
    java.io.File cached = new java.io.File(act.getFilesDir(), "last.html");
    if (S[3] >= 6 && cached.isFile()) {   // لم نستطع القراءة من الذاكرة: نستخدم آخر نسخة محفوظة داخل التطبيق
        try {
            byte[] d2 = files.readAll(cached);
            S[0] = 1;
            webview1.loadDataWithBaseURL("https://appassets.local/", new String(d2, "UTF-8"), "text/html", "UTF-8", null);
            perm.ask();
            return;
        } catch (Exception e) {}
    }
    int msg = 2;   // 1=ننتظر إذن القراءة  2=لا يوجد ملف html  3=ننتظر "الوصول لكل الملفات"
    try {
        boolean hasRead = android.os.Build.VERSION.SDK_INT < 23
            || act.checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean allFiles = android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager();
        boolean needAll = android.os.Build.VERSION.SDK_INT >= 30 && act.getApplicationInfo().targetSdkVersion >= 30;
        if (needAll) {
            if (!allFiles) {
                if (S[2] == 0) {
                    S[2] = 1;
                    try {
                        act.startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            android.net.Uri.parse("package:" + act.getPackageName())));
                    } catch (Exception e) {
                        act.startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                }
                msg = 3;
            }
        } else if (!hasRead) {
            if (S[1] == 0) { S[1] = 1; act.requestPermissions(new String[] { "android.permission.READ_EXTERNAL_STORAGE" }, 101); }
            msg = 1;
        }
    } catch (Exception e) {}
    if (msg != S[4]) {   // صفحة توضيحية بدل شاشة سوداء
        S[4] = msg;
        String t = msg == 1 ? "اسمح للتطبيق بقراءة الملفات من الذاكرة"
            : msg == 3 ? "فعّل «السماح بالوصول إلى كل الملفات» لهذا التطبيق ثم ارجع"
            : "لم أجد المحرّك. ضع الملف index.html داخل:<br><b dir='ltr'>/sdcard/FILE PROG/</b><br><small dir='ltr'>index.html (Chat Studio) · scenario.json بجانبه (اختياري) · Sound/ (اختياري)</small>";
        webview1.loadDataWithBaseURL(null, "<html dir='rtl'><body style='margin:0;height:100vh;display:grid;place-items:center;background:#05070B;color:#fff;font:700 20px sans-serif;text-align:center;line-height:1.9;padding:24px;box-sizing:border-box'><div>" + t + "</div></body></html>", "text/html", "utf-8", null);
    }
    mainH.postDelayed(loader[0], 700);   // نعيد الفحص حتى يصبح الملف جاهزاً
}};
loader[0].run();
