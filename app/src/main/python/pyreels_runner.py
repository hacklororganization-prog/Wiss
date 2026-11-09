"""Sandboxed execution of short beginner Python snippets for PyReels Studio.

- stdout/stderr captured, hard time limit (trace function aborts long runs)
- restricted builtins: no open/exec/eval/compile/__import__ except an allow-list of safe modules
- line trace via sys.settrace: one step per executed line with the user's variables
Returns a JSON string: {"ok", "output", "error", "trace": [{"line", "vars", "out", "iteration", "stack"}]}
"""
import ast
import builtins
import io
import json
import sys
import time

SAFE_MODULES = {"math", "random", "string", "itertools", "functools", "collections", "statistics", "datetime", "time", "re", "fractions", "decimal"}
BLOCKED_BUILTINS = {"open", "exec", "eval", "compile", "breakpoint", "help", "exit", "quit", "memoryview", "globals", "vars", "locals"}
MAX_STEPS = 400
MAX_TRACE = 16
MAX_OUTPUT = 4000


class _Timeout(Exception):
    pass


def _safe_import(name, globals=None, locals=None, fromlist=(), level=0):
    root = name.split(".")[0]
    if root not in SAFE_MODULES:
        raise ModuleNotFoundError("No module named '%s'" % name)
    return __import__(name, globals, locals, fromlist, level)


def _input(prompt=""):
    # Videos are non-interactive: input() returns a fixed sample answer.
    sys.stdout.write(str(prompt))
    return "20"


def _fmt(v):
    try:
        if isinstance(v, (int, float, bool)) or v is None:
            return v
        if isinstance(v, str):
            return v[:24]
        r = repr(v)
        return r[:24]
    except Exception:
        return "?"


def run(code, timeout_s=2.0):
    safe_builtins = {k: getattr(builtins, k) for k in dir(builtins) if k not in BLOCKED_BUILTINS}
    safe_builtins["__import__"] = _safe_import
    safe_builtins["input"] = _input
    env = {"__builtins__": safe_builtins, "__name__": "__main__"}
    out = io.StringIO()
    trace = []
    deadline = time.monotonic() + float(timeout_s)
    steps = [0]
    loop_counts = {}
    last_out_len = [0]
    pending = [None]

    loop_lines = set()
    try:
        for node in ast.walk(ast.parse(code)):
            if isinstance(node, (ast.For, ast.While)):
                loop_lines.update(range(node.lineno, (node.end_lineno or node.lineno) + 1))
    except SyntaxError:
        pass

    def snapshot(frame):
        user_vars = {}
        scope = frame.f_locals if frame.f_code.co_name != "<module>" else frame.f_globals
        for k, v in list(scope.items()):
            if k.startswith("__") or callable(v) or type(v).__name__ == "module":
                continue
            user_vars[k] = _fmt(v)
            if len(user_vars) >= 6:
                break
        return user_vars

    def flush_pending():
        p = pending[0]
        if p is not None:
            # variables AFTER the line ran
            p["vars"] = snapshot(p.pop("_frame"))
            text = out.getvalue()
            new = text[last_out_len[0]:]
            last_out_len[0] = len(text)
            if new.strip():
                p["out"] = new.rstrip("\n")[:80]
            if len(trace) < MAX_TRACE:
                trace.append(p)
            pending[0] = None

    def tracer(frame, event, arg):
        if time.monotonic() > deadline:
            raise _Timeout()
        if frame.f_code.co_filename != "<lesson>":
            return tracer
        if event == "return":
            flush_pending()
        if event == "line":
            steps[0] += 1
            if steps[0] > MAX_STEPS:
                raise _Timeout()
            flush_pending()
            line = frame.f_lineno
            loop_counts[line] = loop_counts.get(line, 0) + 1
            stack = []
            f = frame
            while f is not None and f.f_code.co_filename == "<lesson>":
                if f.f_code.co_name != "<module>":
                    stack.append(f.f_code.co_name + "()")
                f = f.f_back
            step = {"line": line, "_frame": frame}
            if line in loop_lines:
                header = min((l for l in loop_lines if l <= line), default=line)
                step["iteration"] = max(1, loop_counts.get(header, 1))
            if stack:
                step["stack"] = list(reversed(stack))[:4]
            pending[0] = step
        return tracer

    old_stdout, old_stderr = sys.stdout, sys.stderr
    error = None
    try:
        compiled = compile(code, "<lesson>", "exec")
        sys.stdout = out
        sys.stderr = out
        sys.settrace(tracer)
        try:
            exec(compiled, env)
        finally:
            sys.settrace(None)
            sys.stdout, sys.stderr = old_stdout, old_stderr
        flush_pending()
    except _Timeout:
        sys.settrace(None)
        sys.stdout, sys.stderr = old_stdout, old_stderr
        pending[0] = None
        error = "TimeoutError: the lesson code ran too long"
    except BaseException as e:  # report like the interpreter's last traceback line
        sys.settrace(None)
        sys.stdout, sys.stderr = old_stdout, old_stderr
        flush_pending()
        msg = str(e)
        error = type(e).__name__ + (": " + msg if msg else "")
        if isinstance(e, SyntaxError) and e.msg:
            error = type(e).__name__ + ": " + e.msg
    output = out.getvalue()[:MAX_OUTPUT].rstrip("\n")
    return json.dumps({"ok": error is None, "output": output, "error": error, "trace": trace}, ensure_ascii=False)
