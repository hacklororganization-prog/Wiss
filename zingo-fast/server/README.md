# خادم Voltra ‏(app.js)

هذا الخادم هو "العقل المشترك" للمساعد الذكي في Voltra:

1. كل نسخة من التطبيق تتعلّم محلياً: الإجابات التي يقيّمها المستخدم بـ 👍، والدوائر والقطع التي يطبّقها وتنجح في الفحص، والإصلاحات التي أزالت مشكلة.
2. إذا وافق المستخدم على المشاركة، يرسل التطبيق هذه العناصر إلى هذا الخادم بعد حذف البريد والأرقام والمفاتيح.
3. الخادم يدمجها: يتحقق منها، ويحذف المكرر، ولا ينشر العنصر إلا عندما يصوّت له عدد كافٍ من المستخدمين المختلفين (`minVotes`، الافتراضي 2) أو عندما توافق عليه أنت.
4. يحفظ النتيجة في `data/brain.json` (العقل الرئيسي)، ويرسله إلى كل التطبيقات، فتستخدمه في الإجابة.
5. عند التشغيل يفتح نفقاً مجانياً من Cloudflare (`cloudflared tunnel --url http://127.0.0.1:5000`) ويكتب رابطه في Firebase (المستند `zingo/server`). التطبيق يقرأ الرابط من هناك تلقائياً.

## التشغيل على Termux

```sh
pkg update && pkg install nodejs cloudflared
cd zingo-fast/server
node app.js
```

في أول تشغيل يُنشأ الملف `config.json` ويُطبع **رمز المدير** (Admin token). احتفظ به.
للتشغيل الدائم مع منع الهاتف من النوم: ‎`sh start.sh`‎.

## إعداد Firebase (مرة واحدة، 5 دقائق)

المشروع المستخدم: `z-ai-404d2`.

1. في Firebase Console → **Firestore Database** → أنشئ قاعدة البيانات (Production mode).
2. **Authentication** → Sign-in method → فعّل **Email/Password**، ثم Users → **Add user**: بريد وكلمة مرور خاصة بالخادم.
3. **Firestore → Rules**: الصق محتوى الملف `firestore.rules` وضع بريد حساب الخادم مكان `PUT-YOUR-OWNER-EMAIL-HERE`، ثم Publish.
   بهذا يستطيع الجميع قراءة رابط الخادم، ولا يستطيع تغييره أحد غير حسابك.
4. في `config.json` ضع نفس البريد وكلمة المرور:

```json
"firebase": { "apiKey": "...", "projectId": "z-ai-404d2", "doc": "zingo/server",
              "email": "server@example.com", "password": "كلمة-مرور-قوية" }
```

لا ترفع `config.json` إلى GitHub: فيه كلمة المرور ورمز المدير (الملف مستثنى في ‎`.gitignore`‎).

## إعدادات `config.json`

| المفتاح | المعنى |
|---|---|
| `port` | منفذ الخادم المحلي (5000) |
| `tunnel` | `true`: يفتح نفق Cloudflare تلقائياً |
| `publicUrl` | إن كان لديك خادم خارجي بنطاق ثابت: ضع رابطه واجعل `tunnel` = `false` (أو شغّل ‎`node app.js --no-tunnel`‎) |
| `minVotes` | عدد المستخدمين المختلفين الذين يجب أن يؤكدوا العنصر قبل نشره (2) |
| `autoApprove` | `true` ينشر كل عنصر صالح مباشرة (غير منصوح به) |
| `adminToken` | رمز أدوات المدير |

## واجهة الخادم

| الطلب | الوظيفة |
|---|---|
| `GET /api/health` | الحالة ورقم إصدار العقل |
| `GET /api/brain?rev=N` | العقل المشترك (أو `unchanged` إن لم يتغير) |
| `POST /api/contribute` | استقبال ما تعلّمه التطبيق |
| `GET /api/admin/pending` | العناصر التي تنتظر الأصوات (يتطلب `X-Admin-Token`) |
| `POST /api/admin/approve` ‏`{"id":N}` | نشر عنصر يدوياً |
| `POST /api/admin/reject` ‏`{"id":N}` | حذف عنصر من الانتظار أو من العقل |
| `POST /api/admin/config` ‏`{"minVotes":2,"autoApprove":false}` | تغيير قواعد النشر |

مثال:

```sh
curl -H "X-Admin-Token: رمزك" http://127.0.0.1:5000/api/admin/pending
curl -X POST -H "X-Admin-Token: رمزك" -H "Content-Type: application/json" -d '{"id":12}' http://127.0.0.1:5000/api/admin/approve
```

## الحماية

- لا يُنشر عنصر إلا بأصوات من مستخدمين مختلفين أو بموافقتك، فلا يستطيع مستخدم واحد إفساد العقل.
- حدود للطلبات لكل جهاز ولكل عنوان IP، وحجم أقصى لكل طلب، ورفض الرسائل المليئة بالروابط.
- يُحذف البريد وأرقام الهواتف ومفاتيح API من النصوص مرة أخرى في الخادم.
- معرّف كل مستخدم يُحفظ مُشفّراً بدالة hash، ولا تُحفظ عناوين IP.
- نسخة احتياطية يومية من العقل في `data/backup` (آخر 14 يوماً).
- رابط النفق المجاني يتغير عند كل تشغيل؛ الخادم يكتب الرابط الجديد في Firebase تلقائياً، والتطبيقات تقرؤه من هناك.


## الإصدار 2: الحسابات، متجر الإضافات، لوحة المالك، رسائل Gmail

عند تشغيل `node app.js` تُفتح **لوحة المالك** تلقائياً في Chrome على `http://127.0.0.1:5000/admin`
(على Termux عبر `am start` أو `termux-open-url`). لإيقاف الفتح التلقائي: `node app.js --no-browser` أو `"openDashboard": false`.

في اللوحة: إحصاءات، المستخدمون (منح Pro، توثيق مطوّر، مختبِر، حظر، رسالة)، الإضافات (إخفاء/حذف)، العقل (نشر/حذف)، الرسائل، الإعدادات.

### إعدادات جديدة في `config.json`

| المفتاح | المعنى |
|---|---|
| `owners` | قائمة بريدك/بريد فريقك، مثل `["you@gmail.com"]`: هذه الحسابات تصبح "مختبِرين" وترى في شاشة الاشتراك أزرار **مجاني / تجربة / Pro** للاختبار |
| `trialMs` | مدة التجربة لكل حساب (الافتراضي 3 أيام) |
| `google.webClientId` | معرّف عميل الويب (لتسجيل الدخول بـ Google في Android) |
| `google.desktopClientId` و `google.desktopSecret` | عميل "Desktop app" (لتسجيل الدخول بـ Google في Windows) |
| `smtp.user`, `smtp.pass`, `smtp.from` | حساب Gmail المُرسِل وكلمة مرور التطبيقات (يمكن ضبطها من تبويب الرسائل في اللوحة) |
| `verify` | شروط شارة "مطوّر موثّق": تنزيلات ≥ `max(minDownloads, share × عدد المستخدمين)` وتقييمات ≥ `minRatings` بمتوسط ≥ `minRating` |

### تفعيل تسجيل الدخول

1. Firebase Console → Authentication → Sign-in method: فعّل **Email/Password** (مفعّل مسبقاً) و **Google**.
2. **Windows**: Google Cloud Console (نفس المشروع z-ai-404d2) → APIs & Services → Credentials → Create credentials → OAuth client ID → النوع **Desktop app**. ضع المعرّف والسر في `google.desktopClientId` و `google.desktopSecret`.
3. **Android**: في Firebase → Project settings → Add app → Android، اسم الحزمة `com.zingofast.app`، وأضف بصمة **SHA-1** لمفتاح التوقيع (من ملف keystore الذي أرسلته لك سابقاً: `keytool -list -v -keystore zingo-release.jks -alias zingo`). ثم ضع **Web client ID** (من Authentication → Google → Web SDK configuration) في `google.webClientId`.
4. أعد تشغيل `app.js`: ينشر هذه المعرّفات في Firebase فيقرؤها التطبيق تلقائياً.

الدخول بالبريد وكلمة المرور يعمل فوراً بدون أي إعداد إضافي.

### الحسابات والتجربة

- كل حساب يحصل على تجربة مستقلة تبدأ عند أول دخول له، ويحفظ الخادم تاريخ بدايتها في `data/users.json`.
- منح Pro لمستخدم: من تبويب المستخدمين في اللوحة.

### متجر الإضافات

الإضافات بيانات فقط (قطع، دوائر، أكواد Arduino، بوتات مساعدة بتعليمات نصية، حزم) حتى 20 ميغابايت، ولا تُشغّل أي كود على أجهزة الآخرين.
تُحفظ في `data/extensions.json` و `data/ext/`. الترتيب يجمع التقييم (متوسط بايزي) وعدد التنزيلات.

| الطلب | الوظيفة |
|---|---|
| `GET /api/ext?q=&cat=&sort=top\|downloads\|rating\|new&page=` | البحث والقائمة |
| `GET /api/ext/:id` و `GET /api/ext/:id/download` | التفاصيل والتنزيل |
| `POST /api/ext` و `PUT/DELETE /api/ext/:id` | النشر والتحديث والحذف (يتطلب تسجيل الدخول) |
| `POST /api/ext/:id/rate` ‏`{"stars":1..5}` | التقييم |
| `GET /api/creator/:uid` | ملف المطوّر |
| `GET /api/me` | الحساب: بداية التجربة والخطة |

### رسائل Gmail

في Google: الأمان → التحقق بخطوتين → **كلمات مرور التطبيقات** → أنشئ كلمة مرور، وضعها مع بريدك في تبويب الرسائل. يمكنك الإرسال لكل المستخدمين، أو الموثّقين، أو مشتركي Pro، أو غير المشتركين، أو مستخدم واحد. اكتب `{name}` ليُستبدل باسم كل مستخدم. يسمح Gmail بنحو 500 رسالة يومياً.
