"""Generates core/src/main/resources/pyreels/curriculum.json (the app's built-in lesson roadmap)."""
import json, os, sys
L = [
 ("py-print","print","الطباعة بـ print","الأمر print يعرض أي شيء على الشاشة.","print مثل مكبّر الصوت: ما تعطيه له يعلنه للجميع.",'print("مرحبا")',"مرحبا","print تُظهر ما بين القوسين."),
 ("py-comments","comment","التعليقات","السطر الذي يبدأ بـ # لا يُنفَّذ، هو ملاحظة للبشر.","التعليق مثل ورقة لاصقة على الثلاجة: للتذكير فقط.",'# هذا تعليق\nprint("أهلًا")',"أهلًا","ما بعد # لا يراه بايثون."),
 ("py-variables","variable","المتغيرات","المتغير اسم نحفظ فيه قيمة لنستخدمها لاحقًا.","المتغير صندوق عليه اسم.","x = 5\nx = x + 2\nprint(x)","7","المتغير صندوق له اسم وقيمة."),
 ("py-var-names","naming","تسمية المتغيرات","الاسم يبدأ بحرف، بلا مسافات، ويصف المحتوى.","الاسم مثل ملصق على علبة: واضح يوفّر الوقت.","user_age = 20\nprint(user_age)","20","سمِّ المتغير بما يحتويه."),
 ("py-numbers","number","الأعداد والعمليات","بايثون يحسب: + - * / مثل الآلة الحاسبة.","بايثون آلة حاسبة لا تتعب.","a = 10\nb = 3\nprint(a + b)","13","العمليات الحسابية تعمل كما تعلّمتها."),
 ("py-strings","string","النصوص","النص يُكتب بين علامتي تنصيص.","النص مثل خرز في خيط: حروف متتالية.",'name = "أحمد"\nprint(name)',"أحمد","النص دائمًا بين علامتي تنصيص."),
 ("py-types","type","أنواع البيانات","لكل قيمة نوع: عدد صحيح، عشري، نص، منطقي.","الأنواع مثل أدراج المطبخ: لكل شيء مكانه.","x = 5\nprint(type(x))","<class 'int'>","type تخبرك بنوع القيمة."),
 ("py-input","input","الإدخال بـ input","input تنتظر المستخدم وتعيد ما كتبه كنص.","input مثل سؤال تطرحه وتنتظر جوابه.",'name = "سارة"  # بدل input\nprint("أهلًا " + name)',"أهلًا سارة","input تعيد نصًا دائمًا."),
 ("py-casting","casting","تحويل الأنواع","int و str تحوّلان القيمة من نوع لآخر.","التحويل مثل صبّ الماء من كوب إلى زجاجة.",'age = int("20")\nprint(age + 1)',"21","حوّل النص إلى عدد قبل الحساب."),
 ("py-fstrings","f-string","النصوص المنسّقة f-string","f قبل النص تسمح بوضع المتغيرات داخل {}.","f-string مثل قالب بطاقة دعوة تملأ فيه الاسم.",'name = "أحمد"\nprint(f"مرحبا {name}")',"مرحبا أحمد","ضع f قبل النص والمتغير بين {}."),
 ("py-booleans","boolean","القيم المنطقية","True و False هما جواب أي سؤال نعم/لا.","المنطقي مثل مفتاح الضوء: مُضاء أو مطفأ.","print(5 > 3)","True","المقارنة تعطي True أو False."),
 ("py-comparison","comparison","المقارنة","== تسأل: هل القيمتان متساويتان؟","== مثل ميزان يقارن كفّتين.","x = 5\nprint(x == 5)","True","= للتخزين و == للسؤال."),
 ("py-if","if","الشرط if","if تنفّذ الكود فقط إذا كان الشرط صحيحًا.","if مثل باب يفتح فقط لمن معه المفتاح.",'age = 18\nif age >= 18:\n    print("مسموح")',"مسموح","if تسأل أولًا ثم تنفّذ."),
 ("py-else","else","else و elif","else تنفّذ عندما يكون الشرط خاطئًا.","else هي الطريق الآخر عند مفترق الطرق.",'x = 3\nif x > 5:\n    print("كبير")\nelse:\n    print("صغير")',"صغير","else هي الخطة البديلة."),
 ("py-logic","logic","and و or و not","and تحتاج الشرطين معًا، or يكفيها واحد.","and مثل قفلين على باب، or مثل بابين.","print(True and False)","False","and تحتاج الكل، or يكفيها واحد."),
 ("py-while","while","الحلقة while","while تكرّر طالما الشرط صحيح.","while مثل غسل الصحون حتى تنتهي الكومة.","n = 3\nwhile n > 0:\n    print(n)\n    n = n - 1","3\n2\n1","while تكرّر حتى يصبح الشرط خطأ."),
 ("py-for","for","الحلقة for","for تمرّ على العناصر واحدًا واحدًا.","for مثل توزيع الحلوى على كل طفل في الصف.","for i in range(3):\n    print(i)","0\n1\n2","for تمرّ على كل عنصر مرة."),
 ("py-range","range","الدالة range","range(n) تعطي الأعداد من 0 حتى n-1.","range مثل مسطرة تبدأ من الصفر.","print(list(range(4)))","[0, 1, 2, 3]","range تبدأ من صفر وتتوقف قبل n."),
 ("py-break","break","break و continue","break تُوقف الحلقة، continue تتخطّى دورة.","break مثل زر الإيقاف في المصعد.","for i in range(5):\n    if i == 3:\n        break\n    print(i)","0\n1\n2","break تخرج من الحلقة فورًا."),
 ("py-lists","list","القوائم","القائمة تحفظ عدة قيم بترتيب داخل [ ].","القائمة مثل قطار بعربات مرقّمة من الصفر.",'fruits = ["تفاح", "موز"]\nprint(fruits[0])',"تفاح","العدّ في القوائم يبدأ من صفر."),
 ("py-list-methods","list-methods","دوال القوائم","append تضيف عنصرًا في آخر القائمة.","append مثل إضافة عربة لآخر القطار.","nums = [1, 2]\nnums.append(3)\nprint(nums)","[1, 2, 3]","append تضيف في النهاية."),
 ("py-tuples","tuple","الصفوف tuple","tuple قائمة لا تتغيّر.","tuple مثل صورة مطبوعة: لا تعدّلها.","point = (3, 4)\nprint(point[1])","4","tuple ثابتة لا تتغير."),
 ("py-dicts","dict","القواميس","القاموس يربط مفتاحًا بقيمة.","القاموس مثل دفتر هاتف: اسم ← رقم.",'ages = {"أحمد": 20}\nprint(ages["أحمد"])',"20","القاموس: مفتاح يدلّك على قيمة."),
 ("py-sets","set","المجموعات set","set تحفظ قيمًا بلا تكرار.","set مثل قائمة حضور: كل اسم مرة واحدة.","s = {1, 2, 2, 3}\nprint(len(s))","3","set تحذف التكرار تلقائيًا."),
 ("py-functions","function","الدوال","الدالة وصفة نكتبها مرة ونستخدمها كثيرًا.","الدالة مثل وصفة طبخ.",'def greet():\n    print("مرحبا")\n\ngreet()',"مرحبا","اكتب الدالة مرة واستدعها كثيرًا."),
 ("py-params","parameter","المعاملات","المعامل قيمة نمرّرها للدالة لتعمل بها.","المعامل مثل المكوّنات التي تعطيها للطبّاخ.",'def greet(name):\n    print("مرحبا " + name)\n\ngreet("أحمد")',"مرحبا أحمد","المعاملات تجعل الدالة مرنة."),
 ("py-return","return","القيمة المرجعة return","return تعيد نتيجة من الدالة.","return مثل الطبق الذي يخرج من المطبخ.","def double(n):\n    return n * 2\n\nprint(double(4))","8","return تعيد النتيجة لمن طلبها."),
 ("py-scope","scope","نطاق المتغيرات","المتغير داخل الدالة لا يُرى خارجها.","ما يحدث في غرفتك يبقى في غرفتك.","def f():\n    y = 1\n    return y\n\nprint(f())","1","متغيرات الدالة تعيش داخلها فقط."),
 ("py-string-methods","string-methods","دوال النصوص","upper و lower و replace تعدّل النص.","دوال النص مثل أدوات تجميل للكلمات.",'word = "python"\nprint(word.upper())',"PYTHON","النص له أدوات جاهزة."),
 ("py-slicing","slicing","التقطيع slicing","[a:b] تأخذ جزءًا من النص أو القائمة.","التقطيع مثل قصّ شريحة من الكعكة.",'word = "python"\nprint(word[0:3])',"pyt","[a:b] تأخذ من a حتى قبل b."),
 ("py-errors","exception","التعامل مع الأخطاء try","try تجرّب الكود وexcept تلتقط الخطأ بهدوء.","try مثل شبكة أمان تحت لاعب السيرك.",'try:\n    print(10 / 0)\nexcept ZeroDivisionError:\n    print("لا قسمة على صفر")',"لا قسمة على صفر","try تحميك من توقف البرنامج."),
 ("py-comprehension","comprehension","القوائم المختصرة","طريقة قصيرة لبناء قائمة من حلقة.","مثل آلة تصنع قائمة في سطر واحد.","squares = [n * n for n in range(4)]\nprint(squares)","[0, 1, 4, 9]","قائمة كاملة في سطر واحد."),
 ("py-files","file","قراءة الملفات","open تفتح الملف وwith تغلقه تلقائيًا.","with مثل باب يُغلق خلفك تلقائيًا.",'lines = ["سطر 1", "سطر 2"]  # محتوى ملف\nfor line in lines:\n    print(line)',"سطر 1\nسطر 2","with تغلق الملف عنك."),
 ("py-modules","module","الوحدات import","import تجلب أدوات جاهزة من مكتبة.","import مثل استعارة أداة من الجيران.","import math\nprint(math.sqrt(16))","4.0","import تجلب أدوات جاهزة."),
 ("py-classes","class","الأصناف class","الصنف قالب لصنع كائنات.","الصنف مثل قالب الكعك، والكائن هو الكعكة.",'class Cat:\n    name = "مشمش"\n\nc = Cat()\nprint(c.name)',"مشمش","الصنف قالب والكائن نسخة منه."),
 ("py-init","init","الدالة __init__","__init__ تجهّز الكائن عند إنشائه.","__init__ مثل تجهيز حقيبة الطفل في أول يوم دراسة.",'class Dog:\n    def __init__(self, name):\n        self.name = name\n\nd = Dog("ريكس")\nprint(d.name)',"ريكس","__init__ تعمل عند إنشاء الكائن."),
]
out=[]
for i,(id_,concept,title,summary,analogy,code,output,key) in enumerate(L):
    out.append({"id":id_,"order":i+1,"concept":concept,"title":title,"summary":summary,"analogy":analogy,
      "example":code,"expectedOutput":output,"keySentence":key,
      "next": L[i+1][0] if i+1<len(L) else None,
      "teaser": ("في الحلقة القادمة: "+L[i+1][2]) if i+1<len(L) else "تهانينا! أكملت المسار الأساسي."})
dst=sys.argv[1]
json.dump({"version":1,"language":"python","lessons":out},open(dst,"w"),ensure_ascii=False,indent=1)
print(len(out),"lessons")
