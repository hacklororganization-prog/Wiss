extends CanvasLayer
## All UI: touch controls, subtitles, hints, fades, paper reader, splash, menu, settings, demo-end, hidden debug menu.

signal start_pressed
signal paper_closed

var move_vec := Vector2.ZERO
var holding := false
var _look := Vector2.ZERO
var _action := false
var _joy_idx := -1
var _joy_c := Vector2.ZERO
var _look_idx := -1
var _hold_idx := -1
var _taps: Array[float] = []
var player

var root: Control
var controls: Control
var joy: Control
var btn_action: Button
var btn_flash: Button
var btn_hold: Button
var sub: Label
var hint_lbl: Label
var center: Label
var center2: Label
var fade: ColorRect
var grain: ColorRect
var paper: Control
var paper_text: Label
var paper_title: Label
var menu: Control
var settings_box: Control
var end_box: Control
var debug_box: VBoxContainer
var fps_lbl: Label
const JOY_R := 95.0

const GRAIN_SHADER := """
shader_type canvas_item;
uniform sampler2D screen_tex : hint_screen_texture, filter_linear;
uniform float sat = 0.15;
float rnd(vec2 p){ return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void fragment(){
	vec3 c = texture(screen_tex, SCREEN_UV).rgb;
	float g = dot(c, vec3(0.299, 0.587, 0.114));
	c = mix(vec3(g), c, sat) * vec3(1.02, 1.0, 0.95);
	float n = rnd(floor(SCREEN_UV * vec2(480.0, 270.0)) + fract(TIME * 7.0) * 91.0) - 0.5;
	COLOR = vec4(c + n * 0.09, 1.0);
}
"""

func _ready() -> void:
	layer = 10
	process_mode = Node.PROCESS_MODE_ALWAYS
	root = Control.new()
	_full(root)
	add_child(root)
	grain = ColorRect.new()
	_full(grain)
	var sh := Shader.new()
	sh.code = GRAIN_SHADER
	var sm := ShaderMaterial.new()
	sm.shader = sh
	grain.material = sm
	grain.visible = false
	root.add_child(grain)
	_build_controls()
	sub = _label(26, Color(0.92, 0.9, 0.85))
	_wide(sub, 1.0, -150, 110)
	hint_lbl = _label(22, Color(0.75, 0.72, 0.65))
	_wide(hint_lbl, 0.0, 40, 50)
	fade = ColorRect.new()
	fade.color = Color(0, 0, 0, 1)
	_full(fade)
	root.add_child(fade)
	center = _label(64, Color(0.85, 0.83, 0.78))
	_wide(center, 0.5, -70, 90)
	center2 = _label(26, Color(0.6, 0.58, 0.55))
	_wide(center2, 0.5, 40, 50)
	_build_paper()
	_build_menu()
	fps_lbl = _label(18, Color(0.5, 1, 0.5))
	fps_lbl.horizontal_alignment = HORIZONTAL_ALIGNMENT_LEFT
	_place(fps_lbl, Vector2(0, 0), Vector2(130, 8), Vector2(160, 30))
	fps_lbl.visible = false
	debug_box = VBoxContainer.new()
	_place(debug_box, Vector2(0, 0), Vector2(20, 40), Vector2(300, 400))
	debug_box.visible = false
	root.add_child(debug_box)
	for pair in [["Intro", "intro"], ["Station 1", "station1"], ["Gate house", "gate"], ["Confrontation", "confront"], ["Ending", "ending"], ["Toggle FPS", ""]]:
		var b := Button.new()
		b.text = "Jump to: " + pair[0] if pair[1] != "" else pair[0]
		b.custom_minimum_size = Vector2(280, 52)
		var sec: String = pair[1]
		b.pressed.connect(func():
			if sec == "":
				GameState.show_fps = not GameState.show_fps
			else:
				GameState.jump_to(sec))
		debug_box.add_child(b)

# ---------- layout helpers ----------
func _full(c: Control) -> void:
	c.set_anchors_preset(Control.PRESET_FULL_RECT)
	c.mouse_filter = Control.MOUSE_FILTER_IGNORE

func _place(c: Control, anchor: Vector2, off: Vector2, size: Vector2) -> void:
	c.anchor_left = anchor.x
	c.anchor_right = anchor.x
	c.anchor_top = anchor.y
	c.anchor_bottom = anchor.y
	c.offset_left = off.x
	c.offset_top = off.y
	c.offset_right = off.x + size.x
	c.offset_bottom = off.y + size.y

func _wide(c: Control, ay: float, off_y: float, h: float) -> void:
	c.anchor_left = 0.0
	c.anchor_right = 1.0
	c.anchor_top = ay
	c.anchor_bottom = ay
	c.offset_left = 60
	c.offset_right = -60
	c.offset_top = off_y
	c.offset_bottom = off_y + h

func _label(size: int, col: Color) -> Label:
	var l := Label.new()
	var ls := LabelSettings.new()
	ls.font_size = size
	ls.font_color = col
	ls.outline_size = 6
	ls.outline_color = Color(0, 0, 0, 0.85)
	l.label_settings = ls
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(l)
	return l

func _btn(text: String, parent: Control) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(300, 64)
	b.add_theme_font_size_override("font_size", 28)
	b.flat = true
	b.add_theme_color_override("font_color", Color(0.78, 0.76, 0.7))
	b.add_theme_color_override("font_hover_color", Color(1, 0.95, 0.85))
	b.pressed.connect(func(): AudioDirector.play("ui_click"))
	parent.add_child(b)
	return b

# ---------- touch controls ----------
class Joy extends Control:
	var knob := Vector2.ZERO
	var active := false
	func _draw() -> void:
		var c := size / 2.0
		draw_arc(c, 95.0, 0, TAU, 40, Color(1, 1, 1, 0.18), 3.0)
		draw_circle(c + knob, 38.0, Color(1, 1, 1, 0.22 if active else 0.12))

func _build_controls() -> void:
	controls = Control.new()
	_full(controls)
	root.add_child(controls)
	joy = Joy.new()
	joy.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_place(joy, Vector2(0, 1), Vector2(70, -290), Vector2(220, 220))
	controls.add_child(joy)
	btn_action = _round_btn(Vector2(-250, -260), Vector2(190, 110))
	btn_flash = _round_btn(Vector2(-150, -400), Vector2(120, 80))
	btn_flash.text = GameState.t("act_flash")
	btn_hold = _round_btn(Vector2(-450, -230), Vector2(170, 110))
	btn_hold.text = GameState.t("act_hold")
	btn_action.visible = false
	btn_hold.visible = false
	controls.visible = false

func _round_btn(off: Vector2, size: Vector2) -> Button:
	var b := Button.new()
	b.mouse_filter = Control.MOUSE_FILTER_IGNORE   # presses handled in _input (multitouch-safe)
	b.add_theme_font_size_override("font_size", 30)
	b.modulate = Color(1, 1, 1, 0.75)
	_place(b, Vector2(1, 1), off, size)
	controls.add_child(b)
	return b

func show_controls(on: bool) -> void:
	controls.visible = on
	if not on:
		move_vec = Vector2.ZERO
		_joy_idx = -1
		_look_idx = -1
		holding = false
		set_action("")

func set_action(text: String) -> void:
	btn_action.text = text
	btn_action.visible = text != ""
	if text == "":
		_action = false

func take_action() -> bool:
	var a := _action
	_action = false
	return a

func show_hold(on: bool, progress := 0.0) -> void:
	btn_hold.visible = on
	btn_hold.text = GameState.t("act_hold") + ("" if progress <= 0.0 else "  %d%%" % int(progress * 100))
	if not on:
		holding = false

func take_look() -> Vector2:
	var l := _look
	_look = Vector2.ZERO
	return l

func _hit(b: Button, p: Vector2) -> bool:
	return controls.visible and b.visible and b.get_global_rect().has_point(p)

func _press(p: Vector2, idx: int) -> bool:
	if _hit(btn_action, p):
		_action = true
		return true
	if _hit(btn_flash, p):
		if player:
			player.toggle_flash()
		return true
	if _hit(btn_hold, p):
		holding = true
		_hold_idx = idx
		return true
	return false

func _input(e: InputEvent) -> void:
	var mobile := OS.has_feature("mobile")
	if e is InputEventScreenTouch:
		if e.pressed:
			_debug_tap(e.position)
			if _press(e.position, e.index):
				return
			if controls.visible and e.position.x < root.size.x * 0.4 and _joy_idx == -1:
				_joy_idx = e.index
				_joy_c = e.position
				joy.active = true
				joy.queue_redraw()
			elif _look_idx == -1:
				_look_idx = e.index
		else:
			if e.index == _joy_idx:
				_joy_idx = -1
				move_vec = Vector2.ZERO
				joy.knob = Vector2.ZERO
				joy.active = false
				joy.queue_redraw()
			if e.index == _look_idx:
				_look_idx = -1
			if e.index == _hold_idx:
				_hold_idx = -1
				holding = false
	elif e is InputEventScreenDrag:
		if e.index == _joy_idx:
			var d: Vector2 = (e.position - _joy_c).limit_length(JOY_R)
			move_vec = d / JOY_R
			joy.knob = d
			joy.queue_redraw()
		elif e.index == _look_idx and controls.visible:
			_look += e.relative
	elif not mobile:
		if e is InputEventMouseButton and e.button_index == MOUSE_BUTTON_LEFT:
			if e.pressed:
				_debug_tap(e.position)
				_press(e.position, 99)
			elif _hold_idx == 99:
				_hold_idx = -1
				holding = false
		elif e is InputEventKey and not e.echo and controls.visible:
			if e.physical_keycode == KEY_E and e.pressed:
				_action = btn_action.visible
			elif e.physical_keycode == KEY_F and e.pressed and player:
				player.toggle_flash()
			elif e.physical_keycode == KEY_SPACE:
				holding = e.pressed and btn_hold.visible

func _debug_tap(p: Vector2) -> void:
	if p.x > 120 or p.y > 120:
		return
	var now := Time.get_ticks_msec() / 1000.0
	_taps.append(now)
	while _taps.size() > 0 and now - _taps[0] > 3.0:
		_taps.pop_front()
	if _taps.size() >= 5:
		_taps.clear()
		debug_box.visible = not debug_box.visible

func _process(_dt: float) -> void:
	fps_lbl.visible = GameState.show_fps
	if fps_lbl.visible:
		fps_lbl.text = "FPS %d" % Engine.get_frames_per_second()

# ---------- text / fades ----------
func say(text: String) -> void:
	sub.text = text

func hint(text: String, secs := 5.0) -> void:
	hint_lbl.text = text
	hint_lbl.modulate.a = 0.0
	var tw := create_tween()
	tw.tween_property(hint_lbl, "modulate:a", 1.0, 1.0)
	tw.tween_interval(secs)
	tw.tween_property(hint_lbl, "modulate:a", 0.0, 1.5)

func fade_to(a: float, t: float) -> void:
	var tw := create_tween()
	tw.tween_property(fade, "color:a", a, t)
	await tw.finished

func show_center(big: String, small := "", t := 1.5) -> void:
	center.text = big
	center2.text = small
	center.modulate.a = 0.0
	center2.modulate.a = 0.0
	var tw := create_tween()
	tw.tween_property(center, "modulate:a", 1.0, t)
	if small != "":
		tw.tween_property(center2, "modulate:a", 1.0, t)
	await tw.finished

func hide_center(t := 1.0) -> void:
	var tw := create_tween().set_parallel()
	tw.tween_property(center, "modulate:a", 0.0, t)
	tw.tween_property(center2, "modulate:a", 0.0, t)
	await tw.finished

# ---------- paper reader ----------
func _build_paper() -> void:
	paper = ColorRect.new()
	(paper as ColorRect).color = Color(0.02, 0.02, 0.02, 0.96)
	_full(paper)
	paper.mouse_filter = Control.MOUSE_FILTER_STOP
	root.add_child(paper)
	var sheet := ColorRect.new()
	sheet.color = Color(0.78, 0.74, 0.64)
	sheet.anchor_left = 0.2
	sheet.anchor_right = 0.8
	sheet.anchor_top = 0.08
	sheet.anchor_bottom = 0.82
	paper.add_child(sheet)
	paper_title = Label.new()
	paper_title.add_theme_color_override("font_color", Color(0.25, 0.2, 0.15))
	paper_title.add_theme_font_size_override("font_size", 24)
	paper_title.set_anchors_preset(Control.PRESET_TOP_WIDE)
	paper_title.offset_top = 16
	paper_title.offset_bottom = 56
	paper_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	sheet.add_child(paper_title)
	paper_text = Label.new()
	paper_text.add_theme_color_override("font_color", Color(0.12, 0.1, 0.08))
	paper_text.add_theme_font_size_override("font_size", 28)
	paper_text.set_anchors_preset(Control.PRESET_FULL_RECT)
	paper_text.offset_left = 40
	paper_text.offset_right = -40
	paper_text.offset_top = 70
	paper_text.offset_bottom = -20
	paper_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	paper_text.horizontal_alignment = HORIZONTAL_ALIGNMENT_RIGHT
	paper_text.text_direction = Control.TEXT_DIRECTION_RTL
	sheet.add_child(paper_text)
	var close := _btn(GameState.t("ui_close"), paper)
	_place(close, Vector2(0.5, 1), Vector2(-150, -110), Vector2(300, 64))
	close.pressed.connect(_close_paper)
	paper.visible = false

func read_paper(title: String, text: String) -> void:
	AudioDirector.play("paper")
	paper_title.text = title
	paper_text.text = text
	paper.visible = true
	if GameState.bot:
		get_tree().create_timer(0.5).timeout.connect(_close_paper)
	await paper_closed

# ---------- splash / menu / settings / end ----------
func splash() -> void:
	fade.color.a = 1.0
	var l := center
	l.text = GameState.t("credit")
	l.label_settings.font_size = 40
	l.modulate = Color(0.75, 0.75, 0.72, 0.0)
	AudioDirector.play("splash")
	var t0 := Time.get_ticks_msec()
	var tw := create_tween()
	for i in 6:
		tw.tween_property(l, "modulate:a", randf_range(0.15, 0.55), randf_range(0.08, 0.3))
	tw.tween_property(l, "modulate:a", 0.6, 1.2)
	tw.tween_interval(1.4)
	tw.tween_property(l, "modulate:a", 0.0, 0.8)
	var base := l.position
	while tw.is_running():
		l.position = base + Vector2(randf_range(-1.2, 1.2), randf_range(-0.8, 0.8))
		if Time.get_ticks_msec() - t0 > 2000 and _action_any():
			break
		await get_tree().process_frame
	tw.kill()
	l.position = base
	l.modulate.a = 0.0
	l.label_settings.font_size = 64

var _any := false
func _unhandled_input(e: InputEvent) -> void:
	if (e is InputEventScreenTouch or e is InputEventMouseButton or e is InputEventKey) and e.pressed:
		_any = true

func _action_any() -> bool:
	var a := _any
	_any = false
	return a

func _build_menu() -> void:
	menu = ColorRect.new()
	(menu as ColorRect).color = Color(0.01, 0.01, 0.015)
	_full(menu)
	menu.mouse_filter = Control.MOUSE_FILTER_STOP
	root.add_child(menu)
	var title := Label.new()
	title.text = GameState.t("title")
	title.add_theme_font_size_override("font_size", 84)
	title.add_theme_color_override("font_color", Color(0.75, 0.72, 0.66))
	title.set_anchors_preset(Control.PRESET_TOP_WIDE)
	title.offset_top = 70
	title.offset_bottom = 200
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	menu.add_child(title)
	var vb := VBoxContainer.new()
	_place(vb, Vector2(0.5, 0.5), Vector2(-150, -40), Vector2(300, 260))
	menu.add_child(vb)
	_btn(GameState.t("ui_start"), vb).pressed.connect(_on_start)
	_btn(GameState.t("ui_settings"), vb).pressed.connect(func(): settings_box.visible = true)
	_btn(GameState.t("ui_quit"), vb).pressed.connect(func(): get_tree().quit())
	# settings
	settings_box = ColorRect.new()
	(settings_box as ColorRect).color = Color(0.01, 0.01, 0.015)
	_full(settings_box)
	settings_box.mouse_filter = Control.MOUSE_FILTER_STOP
	menu.add_child(settings_box)
	var sv := VBoxContainer.new()
	_place(sv, Vector2(0.5, 0.5), Vector2(-260, -230), Vector2(520, 460))
	sv.add_theme_constant_override("separation", 14)
	settings_box.add_child(sv)
	_slider(sv, GameState.t("ui_volume"), "volume", 0.0, 1.0)
	_slider(sv, GameState.t("ui_sens"), "sens", 0.3, 2.5)
	var q := OptionButton.new()
	q.add_item(GameState.t("ui_quality_low"))
	q.add_item(GameState.t("ui_quality_med"))
	q.selected = 0 if GameState.settings.quality == "low" else 1
	q.custom_minimum_size = Vector2(0, 56)
	q.item_selected.connect(_on_quality)
	var ql := Label.new()
	ql.text = GameState.t("ui_quality")
	sv.add_child(ql)
	sv.add_child(q)
	_btn(GameState.t("ui_back"), sv).pressed.connect(func(): settings_box.visible = false)
	settings_box.visible = false
	# demo end
	end_box = ColorRect.new()
	(end_box as ColorRect).color = Color(0, 0, 0)
	_full(end_box)
	end_box.mouse_filter = Control.MOUSE_FILTER_STOP
	root.add_child(end_box)
	var el := Label.new()
	el.text = GameState.t("ui_demo_end")
	el.add_theme_font_size_override("font_size", 44)
	el.add_theme_color_override("font_color", Color(0.7, 0.68, 0.62))
	el.set_anchors_preset(Control.PRESET_CENTER)
	el.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_place(el, Vector2(0.5, 0.4), Vector2(-400, -40), Vector2(800, 80))
	end_box.add_child(el)
	var eb := _btn(GameState.t("ui_to_menu"), end_box)
	_place(eb, Vector2(0.5, 0.6), Vector2(-170, 0), Vector2(340, 64))
	eb.pressed.connect(func(): GameState.jump_to("menu"))
	end_box.visible = false
	menu.visible = false

func _slider(parent: Control, text: String, key: String, lo: float, hi: float) -> void:
	var l := Label.new()
	l.text = text
	parent.add_child(l)
	var s := HSlider.new()
	s.min_value = lo
	s.max_value = hi
	s.step = 0.05
	s.value = float(GameState.settings[key])
	s.custom_minimum_size = Vector2(0, 48)
	s.value_changed.connect(func(v: float):
		GameState.settings[key] = v
		GameState.save_settings())
	parent.add_child(s)

func show_menu() -> void:
	fade.color.a = 0.0
	menu.visible = true
	settings_box.visible = false

func show_end() -> void:
	end_box.visible = true
	fade.color.a = 0.0

func _close_paper() -> void:
	if paper.visible:
		paper.visible = false
		paper_closed.emit()

func _on_start() -> void:
	menu.visible = false
	start_pressed.emit()

func _on_quality(i: int) -> void:
	GameState.settings.quality = "low" if i == 0 else "med"
	GameState.save_settings()
