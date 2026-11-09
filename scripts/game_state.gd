extends Node
## Global state: data files, settings, event log (used by the smoke-test bot), debug jumps.

signal event_fired(name: String)

var dialogue: Dictionary = {}
var stations: Dictionary = {}
var audio_cfg: Dictionary = {}
var settings := {"volume": 0.8, "quality": "med", "sens": 1.0}
var start_section := "splash"   # splash | menu | intro | station1 | gate | confront | ending
var bot := false                # smoke-test autoplay
var events: Array[String] = []
var show_fps := false
const SETTINGS_PATH := "user://settings.json"

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	dialogue = load_json("res://data/dialogue.json")
	stations = load_json("res://data/stations.json")
	audio_cfg = load_json("res://data/audio.json")
	_setup_font()
	load_settings()

func load_json(p: String) -> Dictionary:
	var f := FileAccess.open(p, FileAccess.READ)
	if f == null:
		push_error("missing json " + p)
		return {}
	var d = JSON.parse_string(f.get_as_text())
	if typeof(d) != TYPE_DICTIONARY:
		push_error("bad json " + p)
		return {}
	return d

func _setup_font() -> void:
	var ar = load("res://fonts/NotoSansArabic-ar.woff2")
	var la = load("res://fonts/NotoSansArabic-latin.woff2")
	if ar is FontFile:
		if la is FontFile:
			ar.fallbacks = [la]
		ThemeDB.fallback_font = ar
	ThemeDB.fallback_font_size = 26

func t(key: String) -> String:
	return str(dialogue.get(key, key))

func fire(e: String) -> void:
	events.append(e)
	print("[EVENT] ", e)
	event_fired.emit(e)

func v3(a) -> Vector3:
	return Vector3(float(a[0]), float(a[1]), float(a[2]))

func load_settings() -> void:
	if FileAccess.file_exists(SETTINGS_PATH):
		var f := FileAccess.open(SETTINGS_PATH, FileAccess.READ)
		var d = JSON.parse_string(f.get_as_text())
		if typeof(d) == TYPE_DICTIONARY:
			settings.merge(d, true)
	apply_settings()

func save_settings() -> void:
	var f := FileAccess.open(SETTINGS_PATH, FileAccess.WRITE)
	if f:
		f.store_string(JSON.stringify(settings))
	apply_settings()

func apply_settings() -> void:
	AudioServer.set_bus_volume_db(0, linear_to_db(maxf(float(settings.volume), 0.001)))
	Engine.max_fps = 30
	var vp := get_tree().root
	vp.msaa_3d = Viewport.MSAA_DISABLED
	vp.scaling_3d_scale = 0.6 if settings.quality == "low" else 0.75

func jump_to(section: String) -> void:
	start_section = section
	Engine.time_scale = 1.0
	get_tree().paused = false
	get_tree().reload_current_scene()
