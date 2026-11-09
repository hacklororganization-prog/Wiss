extends Node
## Headless smoke test + autoplay bot. Run: godot --headless --path . res://tests/smoke.tscn
## Checks JSON, audio resources, then plays the whole demo with the bot and asserts every event fired in order.
const REQUIRED := ["W0", "done_intro", "flashback_done", "mark_inspected", "tap_behind", "paper_2016",
	"gate_opened", "paper_2018", "W4", "cat_jump", "wake", "paper_final", "ending_done", "demo_end"]
var fails: Array[String] = []

func _ready() -> void:
	for k in ["dialogue", "stations", "audio_cfg"]:
		if (GameState.get(k) as Dictionary).is_empty():
			fails.append("json empty: " + k)
	var sounds: Dictionary = GameState.audio_cfg.get("sounds", {})
	for n in sounds.keys():
		var v := int(sounds[n].variants)
		var files: Array = [n] if v == 0 else range(1, v + 1).map(func(i): return "%s_%d" % [n, i])
		for f in files:
			if not ResourceLoader.exists("res://audio/%s.ogg" % f):
				fails.append("missing audio " + f)
	if not (ThemeDB.fallback_font is FontFile):
		fails.append("arabic font not loaded")
	GameState.bot = true
	GameState.start_section = "intro"
	Engine.time_scale = 8.0
	var main: Node = load("res://scenes/main.tscn").instantiate()
	add_child(main)
	var t0 := Time.get_ticks_msec()
	while not GameState.events.has("demo_end") and Time.get_ticks_msec() - t0 < 240000:
		await get_tree().process_frame
	var idx := -1
	for e in REQUIRED:
		var j := GameState.events.find(e)
		if j == -1:
			fails.append("event never fired: " + e)
		elif j < idx:
			fails.append("event out of order: " + e)
		else:
			idx = j
	print("EVENTS: ", GameState.events)
	if fails.is_empty():
		print("SMOKE PASS")
		get_tree().quit(0)
	else:
		for f in fails:
			print("SMOKE FAIL: ", f)
		get_tree().quit(1)
