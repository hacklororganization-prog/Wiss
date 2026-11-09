extends Node
## All sounds go through here. Files and variants come from data/audio.json.
## A file in res://audio_custom/<file>.ogg (or .wav/.mp3) overrides the generated one.

var _amb := {}
var _cache := {}

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	for b in ["Ambience", "SFX", "UI"]:
		if AudioServer.get_bus_index(b) == -1:
			AudioServer.add_bus()
			var i := AudioServer.bus_count - 1
			AudioServer.set_bus_name(i, b)
			AudioServer.set_bus_send(i, "Master")

func _cfg(n: String) -> Dictionary:
	var s: Dictionary = GameState.audio_cfg.get("sounds", {})
	return s.get(n, {})

func _load(file: String) -> AudioStream:
	if _cache.has(file):
		return _cache[file]
	var st: AudioStream = null
	for ext in [".ogg", ".wav", ".mp3"]:
		var p: String = "res://audio_custom/" + file + str(ext)
		if ResourceLoader.exists(p):
			st = load(p)
			break
	if st == null and ResourceLoader.exists("res://audio/" + file + ".ogg"):
		st = load("res://audio/" + file + ".ogg")
	if st == null:
		push_error("missing audio " + file)
	_cache[file] = st
	return st

func stream_for(n: String) -> AudioStream:
	var c := _cfg(n)
	var v := int(c.get("variants", 1))
	var file := n if v == 0 else "%s_%d" % [n, randi_range(1, maxi(v, 1))]
	return _load(file)

## Play a one-shot. pos == null -> non-positional.
func play(n: String, pos = null, db := 0.0, parent: Node = null) -> Node:
	var st := stream_for(n)
	if st == null:
		return null
	var c := _cfg(n)
	var p: Node
	var host: Node = parent if parent else get_tree().current_scene
	if pos is Vector3 and host is Node:
		var p3 := AudioStreamPlayer3D.new()
		p3.stream = st
		p3.volume_db = float(c.get("db", 0.0)) + db + randf_range(-2, 2)
		p3.pitch_scale = randf_range(0.94, 1.06)
		p3.bus = str(c.get("bus", "SFX"))
		p3.unit_size = 6.0
		p3.max_distance = 80.0
		p3.doppler_tracking = AudioStreamPlayer3D.DOPPLER_TRACKING_DISABLED
		host.add_child(p3)
		p3.global_position = pos
		p3.play()
		p3.finished.connect(p3.queue_free)
		p = p3
	else:
		var p2 := AudioStreamPlayer.new()
		p2.stream = st
		p2.volume_db = float(c.get("db", 0.0)) + db + randf_range(-2, 2)
		p2.pitch_scale = randf_range(0.94, 1.06)
		p2.bus = str(c.get("bus", "SFX"))
		add_child(p2)
		p2.play()
		p2.finished.connect(p2.queue_free)
		p = p2
	return p

func ambience(n: String, on: bool, db := 0.0) -> void:
	if on and not _amb.has(n):
		var st := stream_for(n)
		if st == null:
			return
		if "loop" in st:
			st.set("loop", true)
		var a := AudioStreamPlayer.new()
		a.stream = st
		a.bus = "Ambience"
		a.volume_db = float(_cfg(n).get("db", 0.0)) + db
		add_child(a)
		a.play()
		_amb[n] = a
	elif not on and _amb.has(n):
		_amb[n].queue_free()
		_amb.erase(n)

func stop_all_ambience() -> void:
	for k in _amb.keys():
		ambience(k, false)

func fade_ambience(to_db: float, time: float) -> void:
	var i := AudioServer.get_bus_index("Ambience")
	var from := AudioServer.get_bus_volume_db(i)
	var tw := create_tween()
	tw.tween_method(func(v: float): AudioServer.set_bus_volume_db(i, v), from, to_db, time)

func reset_buses() -> void:
	AudioServer.set_bus_volume_db(AudioServer.get_bus_index("Ambience"), 0.0)
	AudioServer.set_bus_volume_db(AudioServer.get_bus_index("SFX"), 0.0)
