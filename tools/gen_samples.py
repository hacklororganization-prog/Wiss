"""Writes the 5 sample scenarios into core/src/main/resources/pyreels/samples/.
Common-mistake scenes are taken from errors.json so the interpreter messages are real."""
import json, os, sys
root = sys.argv[1]
errors = {e["id"]: e for e in json.load(open(os.path.join(root, "errors.json")))["errors"]}
T = {"python": "بايثون", "java": "جافا", "cpp": "سي++", "javascript": "جافاسكربت"}

def cast(*teachers):
    return [{"id": "ahmed", "role": "learner", "name": "أحمد"}] + [{"id": t, "role": "teacher", "language": t, "name": T[t]} for t in teachers]
def chat(f, text, **kw): return dict(type="chat", **{"from": f}, text=text, **kw)
def mistake(eid, frm="python", say=False):
    e = errors[eid]
    d = dict(type="common_mistake", language="python", code=e["snippet"], error=e["message"], explain=e["explain"], fix=e["fix"], libraryId=eid, **{"from": frm})
    if e.get("fixOutput"): d["fixOutput"] = e["fixOutput"]
    if say: d["say"] = True
    return d
def scenario(title, lid, concept, ep, recap, teaser, castl, scenes, theme="calm-dark", ambient="calm_pad"):
    return {"schema": "pyreels/1", "meta": {"title": title, "language": "ar", "lesson": {"id": lid, "concept": concept, "level": "beginner"},
        "targetSeconds": 90, "theme": theme, "ambient": ambient, "series": {"name": "بايثون مع أحمد", "episode": ep, "previousRecap": recap, "nextTeaser": teaser}},
        "cast": castl, "scenes": scenes}

S = {}
S["01-print"] = scenario("أول أمر: print", "py-print", "print", 1, "أهلًا بك في أول حلقة! اليوم نبدأ رحلتنا مع بايثون.", "في الحلقة القادمة: التعليقات… سطور لا يراها بايثون!", cast("python"), [
  dict(type="hook_predict", language="python", code='print("5 + 2")\nprint(5 + 2)'),
  dict(type="recap", text="أهلًا بك في أول حلقة! اليوم نبدأ رحلتنا مع بايثون.", say=True),
  chat("ahmed", "مرحبًا! أريد أن أتعلّم البرمجة… من أين أبدأ؟"),
  chat("python", "أهلًا يا أحمد! نبدأ بأول أمر: `print`", say=True, speakCode=True),
  chat("ahmed", "وماذا يفعل print؟"),
  chat("python", "يعرض على الشاشة أي شيء تعطيه له.", say=True),
  dict(type="analogy", title="print = مكبّر الصوت", items=[{"label": "تعطيه", "value": "\"مرحبا\""}, {"label": "يعلن", "value": "مرحبا"}], **{"from": "python"}, text="ما تضعه بين القوسين يعلنه للجميع.", say=True),
  chat("ahmed", "يعني أكتب الكلام بين القوسين؟"),
  chat("python", "نعم، والنص يوضع بين علامتي تنصيص.", reactions=["like"]),
  dict(type="code_type", language="python", code='print("مرحبا يا أحمد")\nprint(5 + 2)', typing="natural", focusLines=[1], caption="سطران فقط"),
  dict(type="run", language="python", exec="real", trace=[{"line": 1, "out": "مرحبا يا أحمد"}, {"line": 2, "out": "7"}], output="مرحبا يا أحمد\n7"),
  chat("ahmed", "واو! السطر الثاني حسب 5 + 2 بنفسه! 🎉"),
  dict(type="engage_moment", **{"from": "python"}, text="أول برنامج لك يعمل يا أحمد 👏 إن أفادك الدرس، تابعنا لتكمل معنا.", actions=["like", "follow", "share"], bridge="حسنًا يا أحمد، نكمل!"),
  dict(type="reveal", answer="5 + 2 ثم 7", text="الأول نص يُطبع كما هو، والثاني عملية حسابية تُطبع نتيجتها.", **{"from": "python"}, say=True),
  chat("ahmed", "وماذا لو نسيت علامة التنصيص؟"),
  mistake("print-missing-quote"),
  chat("ahmed", "خطأ هادئ… وفهمت السبب 😌"),
  chat("python", "قاعدة اليوم بسيطة جدًا:"),
  dict(type="key_sentence", text="print تُظهر ما بين القوسين.", say=True),
  chat("ahmed", "تحدٍّ: ماذا يطبع `print(\"2\" + \"2\")`؟ اكتبوا في التعليقات 👇", challenge=True),
  dict(type="teaser", text="في الحلقة القادمة: التعليقات… سطور لا يراها بايثون!", say=True),
])

S["02-variables"] = scenario("المتغيرات في بايثون", "py-variables", "variable", 3, "تعلّمنا أن print تُظهر ما بين القوسين.", "في الحلقة القادمة: كيف نسمّي المتغيرات بذكاء؟", cast("python", "java"), [
  dict(type="hook_predict", language="python", code="x = 5\nx = x + 2\nprint(x)", question="ماذا تتوقع أن يحدث؟", holdMs=2000),
  dict(type="recap", text="تعلّمنا أن print تُظهر ما بين القوسين."),
  chat("ahmed", "عندي سؤال… كيف يتذكّر البرنامج الأرقام؟"),
  chat("python", "سؤال ممتاز! نستخدم شيئًا اسمه المتغير.", say=True),
  chat("ahmed", "متغير؟ يبدو صعبًا 😅"),
  chat("python", "أبدًا! تخيّل صندوقًا مكتوبًا عليه اسم.", say=True),
  dict(type="analogy", title="المتغير = صندوق عليه اسم", items=[{"label": "x", "value": "5"}, {"label": "name", "value": "\"أحمد\""}], **{"from": "python"}, text="الاسم على الصندوق، والقيمة بداخله."),
  chat("ahmed", "يعني أضع الرقم 5 في صندوق اسمه x؟"),
  chat("python", "بالضبط! ونكتبها هكذا: `x = 5`", speakCode=True, say=True),
  chat("java", "وعندي في جافا أكتب نوع الصندوق أولًا: `int x = 5;`", speakCode=True),
  chat("python", "أما أنا فأعرف النوع وحدي 😌", reactions=["laugh"]),
  dict(type="code_type", language="python", code="x = 5\nx = x + 2\nprint(x)", typing="natural", focusLines=[2], typo=True),
  dict(type="run", language="python", exec="real", trace=[{"line": 1, "vars": {"x": 5}}, {"line": 2, "vars": {"x": 7}}, {"line": 3, "vars": {"x": 7}, "out": "7"}], output="7"),
  chat("ahmed", "طلعت 7! الصندوق تغيّر 🎉"),
  dict(type="engage_moment", **{"from": "python"}, text="أحسنت يا أحمد! إن أعجبك الصندوق، ضع لايك وتابع الرحلة 💛", actions=["like", "follow", "share"], bridge="حسنًا يا أحمد، نكمل!"),
  dict(type="reveal", answer="7", text="السطر الثاني أخذ القيمة القديمة 5 وأضاف 2، ثم وضع 7 في الصندوق.", **{"from": "python"}, say=True),
  chat("ahmed", "وماذا لو طبعت صندوقًا لم أصنعه؟"),
  mistake("name-undefined"),
  chat("java", "لهذا أحب القواعد الواضحة: اصنع ثم استخدم."),
  dict(type="key_sentence", text="المتغير صندوق له اسم وقيمة.", say=True),
  chat("ahmed", "تحدٍّ: لو كتبنا `x = x * 2` بعد السطر الثاني، كم يصبح x؟ 👇", challenge=True, reactions=["wow"]),
  dict(type="teaser", text="في الحلقة القادمة: كيف نسمّي المتغيرات بذكاء؟", challenge="اكتب اسم متغير جميل في التعليقات"),
])

S["03-if"] = scenario("الشرط if", "py-if", "if", 13, "تعلّمنا أن == تسأل: هل القيمتان متساويتان؟", "في الحلقة القادمة: else… الخطة البديلة!", cast("python", "javascript"), [
  dict(type="hook_predict", language="python", code='age = 15\nif age >= 18:\n    print("مسموح")\nprint("انتهى")'),
  dict(type="recap", text="تعلّمنا أن == تسأل: هل القيمتان متساويتان؟"),
  chat("ahmed", "كيف يتخذ البرنامج قرارًا؟ هل يفكّر مثلنا؟"),
  chat("python", "يسأل سؤالًا واحدًا: هل الشرط صحيح؟", say=True),
  chat("javascript", "وإن كان صحيحًا… ينفّذ! بسيطة 🎉"),
  dict(type="analogy", title="if = باب له مفتاح", items=[{"label": "الشرط", "value": "المفتاح"}, {"label": "الكود", "value": "الغرفة"}], **{"from": "python"}, text="من معه المفتاح يدخل، ومن لا يملكه يكمل طريقه."),
  chat("ahmed", "يعني إن لم يتحقق الشرط يتجاوز الكود؟"),
  chat("python", "نعم، ويكمل ما بعده بهدوء.", say=True),
  chat("python", "ولاحظ النقطتين : والمسافة البادئة.", reactions=["heart"]),
  dict(type="code_type", language="python", code='age = 20\nif age >= 18:\n    print("مسموح")\nprint("انتهى")', typing="natural", focusLines=[2, 3]),
  dict(type="run", language="python", exec="real", trace=[{"line": 1, "vars": {"age": 20}}, {"line": 2, "vars": {"age": 20}}, {"line": 3, "vars": {"age": 20}, "out": "مسموح"}, {"line": 4, "vars": {"age": 20}, "out": "انتهى"}], output="مسموح\nانتهى"),
  chat("ahmed", "دخل الغرفة لأن 20 أكبر من 18! 🎉"),
  dict(type="engage_moment", **{"from": "javascript"}, text="قرار ذكي يا أحمد 😄 إن استمتعت، شارك الدرس مع صديق يتعلّم معك!", actions=["share", "like", "follow"], bridge="حسنًا يا أحمد، نكمل!"),
  chat("ahmed", "لكن ماذا عن الكود الأول؟ العمر كان 15."),
  dict(type="reveal", answer="انتهى", text="15 أصغر من 18، فلم يُفتح الباب، وطُبع «انتهى» فقط.", **{"from": "python"}, say=True),
  chat("ahmed", "ولو نسيت النقطتين بعد الشرط؟"),
  mistake("if-missing-colon"),
  chat("javascript", "أنا أستخدم أقواسًا { }، وبايثون يستخدم : والمسافة."),
  dict(type="key_sentence", text="if تسأل أولًا ثم تنفّذ.", say=True),
  chat("ahmed", "تحدٍّ: لو كان العمر 18 بالضبط، ماذا يُطبع؟ 👇", challenge=True),
  dict(type="teaser", text="في الحلقة القادمة: else… الخطة البديلة!"),
], theme="calm-light", ambient="warm_keys")

S["04-for-loop"] = scenario("الحلقة for", "py-for", "for", 17, "تعلّمنا أن while تكرّر حتى يصبح الشرط خطأ.", "في الحلقة القادمة: range… مسطرة تبدأ من الصفر!", cast("python", "java", "cpp"), [
  dict(type="hook_predict", language="python", code="for i in range(3):\n    print(i)"),
  dict(type="recap", text="تعلّمنا أن while تكرّر حتى يصبح الشرط خطأ."),
  chat("ahmed", "أريد أن أطبع رسالة لكل صديق… هل أكتبها مرارًا؟"),
  chat("python", "لا داعي! الحلقة for تكرّر عنك.", say=True),
  dict(type="analogy", title="for = توزيع الحلوى على الصف", items=[{"label": "الطالب 1", "value": "🍬"}, {"label": "الطالب 2", "value": "🍬"}, {"label": "الطالب 3", "value": "🍬"}], **{"from": "python"}, text="تمرّ على كل طالب مرة واحدة، ثم تتوقف."),
  chat("ahmed", "وكيف تعرف متى تتوقف؟"),
  chat("python", "عندما ينتهي العدّ. `range(3)` تعني: 0 ثم 1 ثم 2.", speakCode=True, say=True),
  chat("java", "وعندي: `for (int i = 0; i < 3; i++)` بالتفصيل الممل 😅"),
  chat("cpp", "ونفسها عندي… لكني أنهيها قبلكم 😎", reactions=["laugh"]),
  dict(type="code_type", language="python", code="for i in range(3):\n    print(i)", typing="natural", focusLines=[1]),
  dict(type="run", language="python", exec="real", trace=[{"line": 1, "vars": {"i": 0}, "iteration": 1}, {"line": 2, "vars": {"i": 0}, "iteration": 1, "out": "0"}, {"line": 1, "vars": {"i": 1}, "iteration": 2}, {"line": 2, "vars": {"i": 1}, "iteration": 2, "out": "1"}, {"line": 1, "vars": {"i": 2}, "iteration": 3}, {"line": 2, "vars": {"i": 2}, "iteration": 3, "out": "2"}], output="0\n1\n2"),
  chat("ahmed", "ثلاث دورات! وi تغيّرت كل مرة 🎉"),
  dict(type="engage_moment", **{"from": "python"}, text="ممتاز يا أحمد! لو أردت حلقة جديدة كل أسبوع، تابعنا 🌱", actions=["follow", "like"], bridge="حسنًا يا أحمد، نكمل!"),
  dict(type="reveal", answer="0 ثم 1 ثم 2", text="range(3) تبدأ من صفر وتتوقف قبل 3، فلا تُطبع 3 أبدًا.", **{"from": "python"}, say=True, speakCode=True),
  chat("ahmed", "وماذا لو كتبت الرقم مباشرة بدل range؟"),
  mistake("for-int-not-iterable"),
  chat("java", "القاعدة واضحة: نمرّ على مجموعة، لا على رقم."),
  dict(type="key_sentence", text="for تمرّ على كل عنصر مرة.", say=True),
  chat("ahmed", "تحدٍّ: كم مرة يُطبع السطر في `range(5)`؟ 👇", challenge=True),
  dict(type="teaser", text="في الحلقة القادمة: range… مسطرة تبدأ من الصفر!"),
], ambient="soft_rain")

S["05-functions"] = scenario("الدوال", "py-functions", "function", 25, "تعلّمنا أن set تحذف التكرار تلقائيًا.", "في الحلقة القادمة: المعاملات… مكوّنات الوصفة!", cast("python", "java", "cpp", "javascript"), [
  dict(type="hook_predict", language="python", code='def greet():\n    print("مرحبا")\n\ngreet()\ngreet()'),
  dict(type="recap", text="تعلّمنا أن set تحذف التكرار تلقائيًا."),
  chat("ahmed", "أكتب نفس الأسطر في أماكن كثيرة… هل هذا طبيعي؟"),
  chat("python", "حان وقت الدوال يا أحمد!", say=True),
  dict(type="analogy", title="الدالة = وصفة طبخ", items=[{"label": "التعريف", "value": "الوصفة"}, {"label": "الاستدعاء", "value": "الطبخ"}], **{"from": "python"}, text="تكتب الوصفة مرة، وتطبخها متى شئت."),
  chat("ahmed", "يعني أعرّفها مرة وأستدعيها كثيرًا؟"),
  chat("python", "بالضبط: `def` للتعريف، والاسم مع () للاستدعاء.", speakCode=True),
  chat("java", "عندي اسمها method وتعيش داخل class."),
  chat("cpp", "وعندي function سريعة جدًا ⚡"),
  chat("javascript", "وعندي function أو سهم => 🎯", reactions=["clap"]),
  dict(type="code_type", language="python", code='def greet():\n    print("مرحبا")\n\ngreet()', typing="natural", focusLines=[1, 4]),
  dict(type="run", language="python", exec="real", trace=[{"line": 1}, {"line": 4, "stack": ["greet()"]}, {"line": 2, "stack": ["greet()"], "out": "مرحبا"}], output="مرحبا"),
  chat("ahmed", "الوصفة اشتغلت من سطر واحد! 🎉"),
  dict(type="engage_moment", **{"from": "python"}, text="أحمد يكتب دواله الأولى 💛 إن أعجبتك السلسلة، احفظها وشاركها.", actions=["like", "share"], bridge="حسنًا يا أحمد، نكمل!"),
  dict(type="reveal", answer="مرحبا مرتين", text="كل استدعاء لـ greet() ينفّذ الوصفة من جديد، فطُبعت مرتين.", **{"from": "python"}, say=True, speakCode=True),
  chat("ahmed", "وماذا لو استدعيتها قبل أن أكتبها؟"),
  mistake("func-before-def", say=True),
  chat("javascript", "عندي أحيانًا تعمل! لكن هذه قصة أخرى 😉"),
  dict(type="key_sentence", text="اكتب الدالة مرة واستدعها كثيرًا.", say=True),
  chat("ahmed", "تحدٍّ: اكتب دالة تطبع اسمك ثلاث مرات 👇", challenge=True),
  dict(type="teaser", text="في الحلقة القادمة: المعاملات… مكوّنات الوصفة!"),
])
os.makedirs(os.path.join(root, "samples"), exist_ok=True)
for k, v in S.items():
    json.dump(v, open(os.path.join(root, "samples", k + ".pyreels.json"), "w"), ensure_ascii=False, indent=2)
print("wrote", len(S))
