"""Generates core/src/main/resources/pyreels/errors.json.
The `message` of every entry is captured from REAL Python execution (run this with python3.11,
the same version bundled through Chaquopy), so the text is never invented."""
import json, sys, subprocess
E = [
 # id, snippet, explain, fix, lessons
 ("print-typo","pritn(\"مرحبا\")","كتبنا اسم الدالة بحرفين مقلوبين، فبايثون لا يعرف «pritn».","print(\"مرحبا\")",["py-print"]),
 ("print-no-paren","print \"مرحبا\"","في بايثون 3 تحتاج print إلى أقواس.","print(\"مرحبا\")",["py-print"]),
 ("print-missing-quote","print(\"مرحبا)","نسينا علامة التنصيص التي تُغلق النص.","print(\"مرحبا\")",["py-print","py-strings"]),
 ("print-missing-paren","print(\"مرحبا\"","نسينا القوس الذي يُغلق الدالة.","print(\"مرحبا\")",["py-print"]),
 ("print-capital","Print(\"مرحبا\")","بايثون يفرّق بين الحروف الكبيرة والصغيرة: print وليس Print.","print(\"مرحبا\")",["py-print"]),
 ("name-undefined","print(y)","استخدمنا المتغير y قبل أن ننشئه.","y = 3\nprint(y)",["py-variables"]),
 ("name-typo","score = 10\nprint(scroe)","اسم المتغير مكتوب بشكل مختلف عن الأصل.","score = 10\nprint(score)",["py-variables","py-var-names"]),
 ("name-space","my age = 20","اسم المتغير لا يحتوي مسافات؛ استخدم الشرطة السفلية.","my_age = 20",["py-var-names"]),
 ("name-digit-start","2name = \"سارة\"","اسم المتغير لا يبدأ برقم.","name2 = \"سارة\"",["py-var-names"]),
 ("name-keyword","class = 5","class كلمة محجوزة في بايثون ولا تصلح اسمًا لمتغير.","level = 5",["py-var-names"]),
 ("string-unquoted","name = أحمد\nprint(name)","النص يجب أن يوضع بين علامتي تنصيص.","name = \"أحمد\"\nprint(name)",["py-strings"]),
 ("type-str-plus-int","age = 20\nprint(\"عمري \" + age)","لا يمكن جمع نص مع عدد مباشرة؛ حوّل العدد إلى نص.","age = 20\nprint(\"عمري \" + str(age))",["py-casting","py-types","py-strings"]),
 ("type-int-plus-str","total = 5 + \"5\"","العدد 5 والنص \"5\" نوعان مختلفان.","total = 5 + 5",["py-types","py-casting"]),
 ("value-int-text","age = int(\"عشرون\")","int تحوّل الأرقام المكتوبة فقط، لا الكلمات.","age = int(\"20\")",["py-casting","py-input"]),
 ("value-int-float-text","n = int(\"3.5\")","int لا تقبل نصًا فيه فاصلة عشرية؛ استخدم float أولًا.","n = int(float(\"3.5\"))",["py-casting"]),
 ("input-math","age = \"20\"  # من input\nprint(age + 1)","input تعيد نصًا دائمًا، فحوّله إلى عدد قبل الحساب.","age = int(\"20\")\nprint(age + 1)",["py-input","py-casting"]),
 ("format-wrong-key","name = \"أحمد\"\nprint(\"مرحبا {nam}\".format(nme=name))","الاسم داخل الأقواس لا يطابق اسم المعامل.","name = \"أحمد\"\nprint(f\"مرحبا {name}\")",["py-fstrings"]),
 ("assign-in-if","x = 5\nif x = 5:\n    print(\"خمسة\")","= للتخزين، أما السؤال عن التساوي فيكون بـ ==.","x = 5\nif x == 5:\n    print(\"خمسة\")",["py-if","py-comparison"]),
 ("if-missing-colon","x = 5\nif x > 3\n    print(\"كبير\")","سطر if يجب أن ينتهي بنقطتين :","x = 5\nif x > 3:\n    print(\"كبير\")",["py-if"]),
 ("if-no-indent","x = 5\nif x > 3:\nprint(\"كبير\")","الكود داخل if يحتاج مسافة بادئة.","x = 5\nif x > 3:\n    print(\"كبير\")",["py-if"]),
 ("indent-unexpected","x = 5\n    print(x)","مسافة بادئة في مكان لا يحتاجها.","x = 5\nprint(x)",["py-if","py-variables"]),
 ("indent-mismatch","if True:\n    x = 1\n  print(x)","المسافات البادئة غير متساوية داخل نفس الكتلة.","if True:\n    x = 1\n    print(x)",["py-if"]),
 ("else-no-colon","x = 1\nif x > 3:\n    print(\"كبير\")\nelse\n    print(\"صغير\")","else تحتاج نقطتين بعدها.","x = 1\nif x > 3:\n    print(\"كبير\")\nelse:\n    print(\"صغير\")",["py-else"]),
 ("elif-typo","x = 4\nif x > 5:\n    print(\"كبير\")\nelse if x > 3:\n    print(\"وسط\")","في بايثون نكتب elif وليس else if.","x = 4\nif x > 5:\n    print(\"كبير\")\nelif x > 3:\n    print(\"وسط\")",["py-else"]),
 ("while-no-colon","n = 3\nwhile n > 0\n    n = n - 1","سطر while يجب أن ينتهي بنقطتين.","n = 3\nwhile n > 0:\n    n = n - 1",["py-while"]),
 ("for-no-colon","for i in range(3)\n    print(i)","سطر for يجب أن ينتهي بنقطتين.","for i in range(3):\n    print(i)",["py-for","py-range"]),
 ("for-int-not-iterable","for i in 5:\n    print(i)","لا يمكن المرور على عدد؛ استخدم range(5).","for i in range(5):\n    print(i)",["py-for","py-range"]),
 ("range-float","for i in range(2.5):\n    print(i)","range تقبل أعدادًا صحيحة فقط.","for i in range(2):\n    print(i)",["py-range"]),
 ("break-outside","break","break تُستخدم داخل الحلقات فقط.","for i in range(3):\n    break",["py-break"]),
 ("list-index","nums = [1, 2, 3]\nprint(nums[3])","العدّ يبدأ من صفر، فآخر عنصر رقمه 2.","nums = [1, 2, 3]\nprint(nums[2])",["py-lists"]),
 ("list-append-typo","nums = [1, 2]\nnums.add(3)","القوائم تستخدم append لا add.","nums = [1, 2]\nnums.append(3)",["py-list-methods"]),
 ("list-index-str","nums = [1, 2, 3]\nprint(nums[\"0\"])","رقم العنصر يجب أن يكون عددًا لا نصًا.","nums = [1, 2, 3]\nprint(nums[0])",["py-lists"]),
 ("list-remove-missing","nums = [1, 2]\nnums.remove(5)","العنصر 5 غير موجود في القائمة.","nums = [1, 2]\nif 5 in nums:\n    nums.remove(5)",["py-list-methods"]),
 ("tuple-assign","point = (3, 4)\npoint[0] = 5","tuple لا تتغيّر بعد إنشائها.","point = [3, 4]\npoint[0] = 5",["py-tuples"]),
 ("dict-key","ages = {\"أحمد\": 20}\nprint(ages[\"سارة\"])","المفتاح «سارة» غير موجود في القاموس.","ages = {\"أحمد\": 20}\nprint(ages.get(\"سارة\", 0))",["py-dicts"]),
 ("set-index","s = {1, 2, 3}\nprint(s[0])","المجموعة set بلا ترتيب، فلا نصل لعناصرها برقم.","s = {1, 2, 3}\nprint(list(s)[0])",["py-sets"]),
 ("str-assign","word = \"cat\"\nword[0] = \"b\"","النص لا يتغيّر حرفًا حرفًا؛ أنشئ نصًا جديدًا.","word = \"cat\"\nword = \"b\" + word[1:]",["py-strings","py-slicing"]),
 ("str-index","word = \"hi\"\nprint(word[5])","النص فيه حرفان فقط.","word = \"hi\"\nprint(word[1])",["py-slicing","py-strings"]),
 ("str-method-typo","word = \"python\"\nprint(word.uper())","اسم الدالة upper وليس uper.","word = \"python\"\nprint(word.upper())",["py-string-methods"]),
 ("zero-division","print(10 / 0)","لا يمكن القسمة على صفر.","print(10 / 2)",["py-numbers","py-errors"]),
 ("modulo-zero","print(7 % 0)","باقي القسمة على صفر غير معرّف.","print(7 % 2)",["py-numbers"]),
 ("func-not-called-def","def greet()\n    print(\"مرحبا\")","سطر def يجب أن ينتهي بنقطتين.","def greet():\n    print(\"مرحبا\")",["py-functions"]),
 ("func-before-def","greet()\ndef greet():\n    print(\"مرحبا\")","استدعينا الدالة قبل تعريفها.","def greet():\n    print(\"مرحبا\")\n\ngreet()",["py-functions"]),
 ("func-missing-arg","def greet(name):\n    print(name)\n\ngreet()","الدالة تنتظر معاملًا ولم نعطها شيئًا.","def greet(name):\n    print(name)\n\ngreet(\"أحمد\")",["py-params"]),
 ("func-extra-arg","def hi():\n    print(\"hi\")\n\nhi(\"أحمد\")","الدالة لا تأخذ معاملات وأعطيناها واحدًا.","def hi(name):\n    print(\"hi\", name)\n\nhi(\"أحمد\")",["py-params"]),
 ("return-outside","return 5","return تُستخدم داخل الدوال فقط.","def five():\n    return 5",["py-return"]),
 ("none-plus","def add(a, b):\n    print(a + b)\n\nx = add(1, 2) + 1","الدالة طبعت النتيجة ولم تُرجعها، فقيمتها None.","def add(a, b):\n    return a + b\n\nx = add(1, 2) + 1",["py-return"]),
 ("scope-local","def f():\n    y = 1\n\nf()\nprint(y)","y تعيش داخل الدالة فقط.","def f():\n    return 1\n\ny = f()\nprint(y)",["py-scope"]),
 ("unbound-local","count = 0\ndef add():\n    count = count + 1\n\nadd()","الدالة تعدّل متغيرًا خارجيًا دون أن تأخذه كمعامل.","def add(count):\n    return count + 1\n\ncount = add(0)",["py-scope"]),
 ("module-missing","import maths","اسم الوحدة math وليس maths.","import math",["py-modules"]),
 ("module-attr","import math\nprint(math.squareroot(16))","الدالة اسمها sqrt.","import math\nprint(math.sqrt(16))",["py-modules"]),
 ("attr-int","x = 5\nx.append(6)","العدد ليس قائمة فلا يملك append.","x = [5]\nx.append(6)",["py-types","py-list-methods"]),
 ("class-missing-self","class Dog:\n    def bark():\n        print(\"هاو\")\n\nDog().bark()","الدوال داخل الصنف تحتاج self أول معامل.","class Dog:\n    def bark(self):\n        print(\"هاو\")\n\nDog().bark()",["py-classes","py-init"]),
 ("init-typo","class Dog:\n    def __int__(self, name):\n        self.name = name\n\nd = Dog(\"ريكس\")","اسم الدالة __init__ وليس __int__.","class Dog:\n    def __init__(self, name):\n        self.name = name\n\nd = Dog(\"ريكس\")",["py-init"]),
 ("file-not-found","open(\"notes_missing.txt\")","الملف غير موجود في هذا المكان.","with open(\"notes.txt\", \"w\") as f:\n    f.write(\"hi\")",["py-files"]),
 ("comprehension-bracket","squares = [n * n for n in range(4)","نسينا القوس الذي يُغلق القائمة.","squares = [n * n for n in range(4)]",["py-comprehension"]),
 ("comparison-str-int","print(\"5\" > 3)","لا يمكن مقارنة نص بعدد.","print(5 > 3)",["py-comparison","py-types"]),
 ("bool-lowercase","x = true","القيمة المنطقية تُكتب True بحرف كبير.","x = True",["py-booleans"]),
 ("and-symbol","if 1 < 2 && 2 < 3:\n    print(\"نعم\")","في بايثون نكتب and وليس &&.","if 1 < 2 and 2 < 3:\n    print(\"نعم\")",["py-logic"]),
]
def run(code):
    p=subprocess.run([sys.executable,"-I","-c",code],capture_output=True,text=True,timeout=10,cwd="/tmp")
    lines=[l for l in p.stderr.strip().splitlines() if l.strip()]
    return lines[-1] if lines else None
out=[]
for id_,snip,ex,fix,lessons in E:
    msg=run(snip)
    assert msg, id_
    fx=subprocess.run([sys.executable,"-I","-c",fix],capture_output=True,text=True,timeout=10,cwd="/tmp")
    assert fx.returncode==0, (id_, fx.stderr)
    out.append({"id":id_,"errorType":msg.split(":")[0],"snippet":snip,"message":msg,"explain":ex,"fix":fix,
                "fixOutput":fx.stdout.strip() or None,"lessons":lessons})
v=sys.version_info
json.dump({"version":1,"python":f"{v.major}.{v.minor}","errors":out},open(sys.argv[1],"w"),ensure_ascii=False,indent=1)
print(len(out),"errors;", sorted(set(e["errorType"] for e in out)))
