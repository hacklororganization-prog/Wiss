# الطريقة B خطوة بخطوة — مسجّل مكتفٍ ذاتيًا (Sketchware)

الهدف: يحمل التطبيق `index.html` **من داخله**، فلا يحتاج المستخدم لإنشاء مجلد FILE PROG يدويًا.

الفكرة الذكية: **لا نعدّل كود التحميل المعقّد إطلاقًا.** نضيف فقط:
1. ملف `index.html` إلى **Assets**.
2. كتلة صغيرة في **onCreate** تنسخ الصفحة من assets إلى الأماكن التي يبحث فيها المسجّل أصلًا
   (نسخة داخلية بلا إذن + نسخة في FILE PROG عند توفّر الإذن).

بعدها يعمل كل شيء كالمعتاد: التسجيل، التخزين الداخلي، وخدمة الوسائط — بلا أي تدخّل يدوي.

---

## الخطوة 1 — افتح مشروع WatsREC في Sketchware

افتح مشروع المسجّل نفسه (الذي بنيت منه WatsREC).

## الخطوة 2 — أضف `index.html` إلى Assets

- **Sketchware Pro:** من شاشة المشروع افتح قسم **Assets** → زر **+** → اختر الملف `index.html`.
- **Sketchware العادي:** استخدم مدير الملفات/الإضافة إلى مجلد `assets` للمشروع.
- النتيجة المطلوبة: أن يصبح الملف متاحًا كـ `assets/index.html`.
  (لا تضعه داخل مجلد فرعي؛ اسمه بالضبط `index.html`.)

## الخطوة 3 — أضف كتلة النشر في onCreate

1. افتح حدث **MainActivity → onCreate** (أو `initialize`).
2. اسحب بلوك **«Add Source Directly»** وضعه **في أعلى onCreate** (قبل أي كود تحميل موجود).
3. الصق هذا الكود كما هو:

```java
try {
    // اقرأ الصفحة المدمجة من assets
    java.io.InputStream is = getAssets().open("index.html");
    java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[8192]; int n;
    while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
    is.close();
    byte[] html = bo.toByteArray();

    // (1) انسخها إلى النسخة الداخلية (بلا أي إذن) — يجدها التطبيق دائمًا
    java.io.FileOutputStream f1 = new java.io.FileOutputStream(new java.io.File(getFilesDir(), "last.html"));
    f1.write(html); f1.close();

    // (2) وإن توفّر الإذن: انسخها إلى FILE PROG لتحميل فوري وخدمة الوسائط
    try {
        java.io.File dir = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "FILE PROG");
        dir.mkdirs();
        java.io.FileOutputStream f2 = new java.io.FileOutputStream(new java.io.File(dir, "index.html"));
        f2.write(html); f2.close();
    } catch (Exception ignore) {}
} catch (Exception e) { e.printStackTrace(); }
```

> ماذا يفعل: ينسخ صفحتك المدمجة إلى **النسخة الداخلية** `last.html` (يقرأها المسجّل تلقائيًا
> بلا إذن)، وأيضًا إلى **FILE PROG** عند توفّر إذن الملفات (تحميل فوري + خدمة الوسائط).
> لا يحتاج المستخدم لإنشاء أي مجلد يدويًا.

## الخطوة 4 — ابنِ التطبيق (Build) وجرّب

- أول تشغيل: قد يطلب إذن «الوصول إلى كل الملفات» (كما كان) — فعّله مرة واحدة ليعمل التحميل الفوري وخدمة الوسائط.
- حتى لو رفضت الإذن: سيحمّل التطبيق من النسخة الداخلية بعد ثوانٍ قليلة ويسجّل عاديًا (دون وسائط خارجية).
- زر **Start Recording** داخل الصفحة يبدأ التسجيل عبر المسجّل الأصلي كالمعتاد.

---

## لتحديث الاستوديو مستقبلًا

كل ما عليك: استبدل `index.html` في **Assets** بالنسخة الجديدة، ثم أعد البناء.
الكتلة في onCreate ستنشر النسخة الجديدة تلقائيًا في كل تشغيل.

---

## ملاحظات مهمة

- **لا تحذف ولا تعدّل** كود التحميل الأصلي (الذي يقرأ FILE PROG ويطلب إذن التسجيل) — نحن فقط نضيف فوقه.
- تأكّد أن اسم الملف في assets هو `index.html` تمامًا (الاسم الذي يبحث عنه المسجّل).
- إن أردت إلغاء الحاجة لإذن «كل الملفات» نهائيًا والاكتفاء بالنسخة الداخلية فقط:
  احذف الجزء رقم (2) من الكود (كتلة FILE PROG). سيعمل التطبيق من `last.html` الداخلية
  بلا إذن تخزين، لكن مع تأخير بسيط عند الإقلاع (~4 ثوانٍ) وبلا خدمة وسائط خارجية.

لو أرسلت لي لقطة شاشة لحدث onCreate في مشروعك، أضبط لك مكان البلوك بدقّة.
