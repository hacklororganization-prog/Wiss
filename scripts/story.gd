extends Node
## The whole demo night as one scripted timeline: intro -> flashback -> station 1 (Omar) -> gate house -> confrontation -> ending.
## Hard rules: the Watcher never moves toward / touches / speaks; never within 20 m except the confrontation (~8 m);
## she disappears only when her lamp goes dark or on a camera cut. The cat jump is the only jump-scare.
const Geo = preload("res://scripts/geo.gd")
const HouseFactory = preload("res://scripts/house_factory.gd")

var world
var player
var ui
var watcher: Node3D
var cat: Node3D
var dog: Node3D
var omar: Node3D
var bot := false
var _w3_abort := false
const ORDER := ["intro", "flashback", "station1", "gate", "confront", "ending"]

func setup(m) -> void:
	world = m.world
	player = m.player
	ui = m.ui
	watcher = m.watcher
	cat = m.cat
	dog = m.dog
	bot = GameState.bot
	var oi: Dictionary = world.houses["omar"]
	omar = Geo.figure(world, oi["at_gate"], Color(0.3, 0.28, 0.25))
	omar.rotation.y = float(oi["yaw"])
	omar.visible = false

# ---------- helpers ----------
func st(id: String) -> Dictionary:
	return world.station_data.get(id, {})

func wait(t: float) -> void:
	await get_tree().create_timer(t).timeout

func frame() -> void:
	await get_tree().process_frame

func line(e) -> void:
	var txt: String = e if e is String else "%s: %s" % [e.get("who", ""), e.get("text", "")]
	ui.say(txt)
	await wait(2.2 + txt.length() * 0.06)
	ui.say("")

func controls(on: bool) -> void:
	player.controls = on
	ui.show_controls(on)

func wait_reach(p: Vector3, r: float) -> void:
	if bot:
		player.global_position = Vector3(p.x, 0.05, p.z)
		await wait(0.3)
		return
	while player.flat_dist(p) > r:
		await frame()

func wait_interact(label: String, p: Vector3, r := 2.3) -> void:
	if bot:
		player.global_position = Vector3(p.x + 0.8, 0.05, p.z + 0.8)
		await wait(0.3)
		return
	while true:
		var near: bool = player.flat_dist(p) < r
		ui.set_action(label if near else "")
		if near and ui.take_action():
			break
		await frame()
	ui.set_action("")

func paper_prop(p: Vector3) -> MeshInstance3D:
	var n := Geo.box(world, Vector3(0.24, 0.01, 0.32), p + Vector3(0, 0.02, 0), Color(0.82, 0.78, 0.68))
	n.rotation.y = randf() * TAU
	return n

func steps(dur: float) -> void:
	var t := 0.0
	while t < dur:
		AudioDirector.play("step_dirt", player.global_position)
		await wait(0.58)
		t += 0.58

func watcher_at(p: Vector3) -> void:
	if not bot:
		var guard := 0
		while player.looking_at(p + Vector3(0, 1.2, 0), 0.35) and guard < 600:
			guard += 1
			await frame()
	watcher.global_position = p
	var look := player.global_position
	look.y = p.y
	if look.distance_to(p) > 0.1:
		watcher.look_at(look, Vector3.UP, true)
	watcher.visible = true

## Lamp flickers then dies; anything in `hide` vanishes only once it is dark.
func flicker_out(lamp: Node3D, hide: Node3D = null) -> void:
	var lp: Vector3 = lamp.global_position + Vector3(0, 3, 0)
	AudioDirector.play("lamp_flicker", lp)
	for i in 7:
		Geo.set_lamp(lamp, i % 2 == 1)
		await wait(randf_range(0.05, 0.22))
	Geo.set_lamp(lamp, false)
	if hide:
		hide.visible = false
	AudioDirector.play("lamp_off", lp)

func wait_seen(p: Vector3, timeout: float) -> bool:
	if bot:
		return true
	var t := 0.0
	while t < timeout:
		if player.looking_at(p + Vector3(0, 1.2, 0), 0.94):
			return true
		t += get_process_delta_time()
		await frame()
	return false

func burst() -> void:
	var c: Color = [Color(1, 0.6, 0.3), Color(0.8, 0.9, 1), Color(1, 0.4, 0.4), Color(0.6, 1, 0.6)].pick_random()
	var m := Geo.mat(c, true, 0.0, true)
	m.disable_fog = true
	var s := Geo.sphere(world, 1.0, Vector3(randf_range(-40, 40), randf_range(28, 42), randf_range(-90, -60)), c, Vector3.ONE * 0.1, m)
	s.visibility_range_end = 0.0
	var tw := create_tween()
	tw.tween_property(s, "scale", Vector3.ONE * randf_range(2.5, 4.5), 0.5).set_ease(Tween.EASE_OUT)
	tw.tween_property(s, "scale", Vector3.ONE * 0.01, 0.6)
	tw.tween_callback(s.queue_free)

# ---------- flow ----------
func run(from: String) -> void:
	var i: int = maxi(0, ORDER.find(from))
	_prepare(ORDER[i])
	for s in ORDER.slice(i):
		GameState.fire("begin_" + s)
		match s:
			"intro": await intro()
			"flashback": await flashback()
			"station1": await station1()
			"gate": await gate()
			"confront": await confront()
			"ending": await ending()
		GameState.fire("done_" + s)
	ui.show_end()
	GameState.fire("demo_end")

func _prepare(s: String) -> void:
	AudioDirector.reset_buses()
	AudioDirector.ambience("wind_loop", true)
	AudioDirector.ambience("crickets_loop", true)
	ui.fade.color.a = 1.0
	var oi: Dictionary = world.houses["omar"]
	var hi: Dictionary = world.houses["ahmed"]
	match s:
		"gate":
			player.global_position = oi["front"]
			player.face_now(world.houses["gate_house"]["front"] + Vector3(0, 1.4, 0))
			player.set_flash(true)
		"confront":
			player.global_position = Vector3(14, 0.05, 12)
			player.face_now(hi["front"] + Vector3(0, 1.4, 0))
			player.set_flash(true)
		"ending":
			player.global_position = hi["front"]
			Geo.set_lamp(hi["lamp"], true)
			player.set_flash(true)
			AudioDirector.fade_ambience(-60, 0.1)

func intro() -> void:
	controls(false)
	var hi: Dictionary = world.houses["ahmed"]
	player.global_position = GameState.v3(GameState.stations.get("player_start_intro", [0, 0, 17.6]))
	player.face_now(hi["at_gate"] + Vector3(0, 5.0, -4.0))
	Geo.set_lamp(hi["lamp"], true)
	Geo.set_lamp(world.lamps["alley"], true)
	dog.global_position = GameState.v3(GameState.stations.get("dog_start", [3.5, 0, 11.4]))
	dog.rotation.y = PI * 0.5
	dog.visible = true
	ui.show_center(GameState.t("time_1159"), "", 1.0)
	await wait(3.0)
	await ui.hide_center(0.8)
	await ui.fade_to(0.0, 2.5)
	await wait(2.0)
	ui.hint(GameState.t("time_1200"), 3.0)
	for k in 5:
		AudioDirector.play("fireworks")
		burst()
		await wait(randf_range(0.6, 1.4))
	await wait(1.5)
	ui.hint(GameState.t("time_1201"), 3.0)
	await wait(1.0)
	for k in 3:
		AudioDirector.play("dog_near", dog.global_position)
		await wait(randf_range(0.9, 1.5))
	player.set_flash(true)
	await wait(0.7)
	await player.turn_to(hi["at_gate"] + Vector3(0, 1.4, 0), 1.2)
	steps(4.0)
	var tw := create_tween()
	tw.tween_property(player, "global_position", hi["front"] + Vector3(0, 0.05, 0), 4.0).set_trans(Tween.TRANS_SINE)
	await tw.finished
	await player.turn_to(dog.global_position + Vector3(0, 0.5, 0), 1.0)
	await wait(1.8)  # the dog has gone silent
	var tw2 := create_tween()
	tw2.tween_property(dog, "global_position", dog.global_position + Vector3(14, 0, 0), 3.5)
	await tw2.finished
	dog.visible = false
	await wait(1.0)
	var w0: Vector3 = world.points.get("W0", Vector3(-21, 0, 12.2))
	await watcher_at(w0)
	await player.turn_to(w0 + Vector3(0, 1.3, 0), 2.6)
	GameState.fire("W0")
	await wait(3.5)
	await flicker_out(world.lamps["alley"], watcher)
	await wait(0.9)
	AudioDirector.play("cane", w0)
	await wait(0.5)
	ui.fade.color.a = 1.0
	await wait(1.2)
	await ui.show_center(GameState.t("title"), "", 2.0)
	await wait(2.5)
	await ui.hide_center(1.5)
	Geo.set_lamp(hi["lamp"], false)

func flashback() -> void:
	var room := Node3D.new()
	room.position = Vector3(0, 0, 300)
	world.add_child(room)
	var wc := Color(0.5, 0.46, 0.4)
	Geo.box(room, Vector3(8, 0.2, 8), Vector3(0, -0.1, 0), Color(0.35, 0.3, 0.25))
	Geo.box(room, Vector3(8, 3, 0.2), Vector3(0, 1.5, -4), wc)
	Geo.box(room, Vector3(0.2, 3, 8), Vector3(-4, 1.5, 0), wc)
	Geo.box(room, Vector3(0.2, 3, 8), Vector3(4, 1.5, 0), wc)
	Geo.box(room, Vector3(8, 0.2, 8), Vector3(0, 3.0, 0), wc.darkened(0.3))
	Geo.box(room, Vector3(1.4, 0.4, 0.9), Vector3(0, 0.2, -0.6), Color(0.3, 0.22, 0.15))
	var cols := [Color(0.35, 0.33, 0.3), Color(0.3, 0.32, 0.36), Color(0.4, 0.36, 0.3), Color(0.32, 0.3, 0.28), Color(0.36, 0.34, 0.32)]
	var spots := [Vector3(-1.9, 0, -0.9), Vector3(-1.1, 0, -2.0), Vector3(0, 0, -2.3), Vector3(1.1, 0, -2.0), Vector3(1.9, 0, -0.9)]
	for i in 5:
		var f := Geo.figure(room, spots[i], cols[i], true)
		var c: Vector3 = room.global_position + Vector3(0, 0, -0.6)
		f.look_at(Vector3(c.x, f.global_position.y, c.z), Vector3.UP, true)
	var o := OmniLight3D.new()
	o.position = Vector3(0, 2.6, -1)
	o.light_color = Color(1, 0.8, 0.55)
	o.omni_range = 7.0
	o.light_energy = 1.3
	room.add_child(o)
	var cam := Camera3D.new()
	cam.position = Vector3(0, 1.5, 2.6)
	cam.fov = 60
	room.add_child(cam)
	cam.look_at(room.global_position + Vector3(0, 0.9, -1.2))
	cam.make_current()
	ui.grain.visible = true
	await ui.fade_to(0.0, 2.0)
	await wait(1.5)
	for e in GameState.dialogue.get("flashback", []):
		await line(e)
	await wait(1.2)
	await ui.fade_to(1.0, 1.5)
	ui.grain.visible = false
	player.cam.make_current()
	room.queue_free()
	GameState.fire("flashback_done")

func station1() -> void:
	var s := st("omar")
	var oi: Dictionary = world.houses["omar"]
	player.global_position = GameState.v3(GameState.stations.get("player_start_play", [0, 0, 12.6]))
	player.face_now(oi["front"] + Vector3(0, 1.4, 0))
	player.set_flash(true)
	Geo.set_lamp(oi["lamp"], true)
	HouseFactory.set_window(oi, bool(s.get("lit_window", true)))
	omar.visible = true
	var mark := Geo.cyl(world, 0.55, 0.6, 0.012, oi["mark"] + Vector3(0, 0.03, 0), Color(0.09, 0.075, 0.06))
	mark.scale = Vector3(1.0, 1.0, 0.55)
	await ui.fade_to(0.0, 1.5)
	controls(true)
	ui.hint(GameState.t("hint_station1"), 6.0)
	await wait_reach(oi["front"], 4.0)
	controls(false)
	await player.turn_to(omar.global_position + Vector3(0, 1.5, 0), 1.0)
	for e in GameState.dialogue.get(str(s.get("dialogue_key", "omar_station1")), []):
		await line(e)
	controls(true)
	await wait_interact(GameState.t("act_inspect"), oi["mark"], 2.2)
	GameState.fire("mark_inspected")
	controls(false)
	await player.turn_to(oi["mark"], 0.8)
	await wait(1.4)
	var behind: Vector3 = player.eye() - player.forward() * 3.5
	AudioDirector.play("cane", Vector3(behind.x, 0.2, behind.z))
	GameState.fire("tap_behind")
	await wait(1.6)
	controls(true)
	var pp: Vector3 = oi["mark"] + Vector3(0.5, 0, 0.4)
	var paper := paper_prop(pp)
	await wait_interact(GameState.t("act_pick"), pp, 2.0)
	paper.queue_free()
	controls(false)
	await ui.read_paper(str(s.get("year", 2016)), GameState.t(str(s.get("paper_key", "paper_2016"))))
	GameState.fire("paper_2016")
	Geo.set_lamp(oi["lamp"], false)
	controls(true)

func _w1() -> void:
	var p: Vector3 = world.points.get("W1", Vector3(-8, 0, -10.9))
	if player.flat_dist(p) < 20.0:
		return
	var lamp: Node3D = world.lamps["w1"]
	await watcher_at(p)
	Geo.set_lamp(lamp, true)
	if await wait_seen(p, 45.0):
		GameState.fire("W1")
		await wait(3.0)
		await flicker_out(lamp, watcher)
	else:
		while player.looking_at(p, 0.3):
			await frame()
		Geo.set_lamp(lamp, false)
		watcher.visible = false

func _cat_wall(info: Dictionary) -> void:
	var p: Vector3 = info["wall_top"] + Vector3(0, 1.62, 0)
	cat.global_position = p
	cat.rotation.y = float(info["yaw"])
	cat.visible = true
	var seen := false
	var t := 0.0
	while t < 90.0 and not bot:
		if player.looking_at(p, 0.9) and player.flat_dist(p) < 16.0:
			seen = true
			break
		t += get_process_delta_time()
		await frame()
	if seen or bot:
		GameState.fire("cat_wall")
		await wait(2.5)
		AudioDirector.play("cat_meow", p)
		await wait(0.8)
	var back: Vector3 = p + Vector3(0, -0.9, -0.6).rotated(Vector3.UP, float(info["yaw"]))
	var tw := create_tween()
	tw.tween_property(cat, "global_position", back, 0.35)
	await tw.finished
	cat.visible = false

func gate() -> void:
	var s := st("gate_house")
	var gi: Dictionary = world.houses["gate_house"]
	if ui.fade.color.a > 0.5:
		await ui.fade_to(0.0, 1.0)
	controls(true)
	_w1()
	Geo.set_lamp(gi["lamp"], true)
	HouseFactory.set_window(gi, false)
	_cat_wall(gi)
	await wait_reach(gi["front"], 3.5)
	AudioDirector.ambience("wind_loop", false)   # no wind: the gate moves anyway
	var secs := float(s.get("hold_seconds", 8.0))
	var open := deg_to_rad(float(s.get("gate_open_deg", 22.0)))
	var pivot: Node3D = gi["gate"]
	var prog := 0.0
	var creak_t := 0.0
	ui.show_hold(true)
	while prog < secs:
		var dt := get_process_delta_time()
		var still: bool = bot or (ui.holding and Vector2(player.velocity.x, player.velocity.z).length() < 0.05 and player.flat_dist(gi["hinge"]) < 5.0)
		prog = prog + dt if still else maxf(0.0, prog - dt * 0.5)
		pivot.rotation.y = open * (prog / secs)
		ui.show_hold(true, prog / secs)
		creak_t -= dt
		if still and creak_t <= 0.0 and prog > 0.8:
			AudioDirector.play("gate_creak", gi["hinge"] + Vector3(0, 1, 0))
			creak_t = 2.6
		await frame()
	ui.show_hold(false)
	GameState.fire("gate_opened")
	await wait(1.0)
	var pp: Vector3 = gi["hinge"]
	var paper := paper_prop(pp)
	await wait_interact(GameState.t("act_pick"), pp, 2.0)
	paper.queue_free()
	controls(false)
	await ui.read_paper(str(s.get("year", 2018)), GameState.t(str(s.get("paper_key", "paper_2018"))))
	GameState.fire("paper_2018")
	AudioDirector.ambience("wind_loop", true)
	controls(true)

func _w3() -> void:
	var p: Vector3 = world.points.get("W3", Vector3(1, 0, -13.4))
	var lamp: Node3D = world.houses["gate_house"]["lamp"]
	while not _w3_abort and (player.flat_dist(p) < 22.0 or player.looking_at(p, 0.3)):
		await frame()
	if _w3_abort:
		Geo.set_lamp(lamp, false)
		return
	watcher.global_position = p
	watcher.look_at(Vector3(player.global_position.x, 0, player.global_position.z), Vector3.UP, true)
	watcher.visible = true
	var t := 0.0
	while not _w3_abort and not player.looking_at(p + Vector3(0, 1.2, 0), 0.95) and not bot:
		t += get_process_delta_time()
		await frame()
	if not _w3_abort:
		GameState.fire("W3")
		await wait(2.0)
	await flicker_out(lamp, watcher)

func confront() -> void:
	var hi: Dictionary = world.houses["ahmed"]
	if ui.fade.color.a > 0.5:
		await ui.fade_to(0.0, 1.0)
	controls(true)
	_w3_abort = false
	_w3()
	Geo.set_lamp(hi["lamp"], true)
	await wait_reach(hi["front"], 13.0)
	AudioDirector.fade_ambience(-36.0, 6.0)
	await wait_reach(hi["front"], 3.0)
	_w3_abort = true
	await wait(0.1)
	controls(false)
	watcher.visible = false
	var dist := float(GameState.stations.get("watcher_points", {}).get("W4_distance", 8.0))
	var f: Vector3 = hi["front"]
	var a := Vector3(f.x - dist, 0, f.z - 0.4)
	var b := Vector3(f.x + dist, 0, f.z - 0.4)
	var p := a if player.forward().dot((a - player.eye()).normalized()) < player.forward().dot((b - player.eye()).normalized()) else b
	await watcher_at(p)
	player.set_flash(true)
	await wait(1.2)
	await player.turn_to(p + Vector3(0, 1.3, 0), 2.6)
	GameState.fire("W4")
	await wait(2.0)
	ui.show_controls(true)
	if bot:
		await wait(0.3)
	else:
		ui.set_action(GameState.t("act_speak"))
		while not ui.take_action():
			await frame()
	ui.show_controls(false)
	ui.say(GameState.t("ahmed_ask"))
	await wait(2.4)
	ui.say("")
	await wait(0.7)
	# the cat jumps from the right
	var right: Vector3 = player.cam.global_transform.basis.x
	var eye: Vector3 = player.eye()
	cat.global_position = eye + right * 2.0 + player.forward() * 0.7 + Vector3(0, -0.7, 0)
	cat.look_at(eye, Vector3.UP, true)
	cat.visible = true
	AudioDirector.play("cat_hiss")
	AudioDirector.play("cat_jump")
	var tw := create_tween()
	tw.tween_property(cat, "global_position", eye + player.forward() * 0.25 + Vector3(0, -0.1, 0), 0.22)
	player.shake(0.45)
	GameState.fire("cat_jump")
	await tw.finished
	cat.visible = false
	AudioDirector.play("fall")
	var cam: Camera3D = player.cam
	var fall := create_tween().set_parallel()
	fall.tween_property(cam, "position:y", 0.22, 0.85).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_IN)
	fall.tween_property(cam, "rotation:z", 1.25, 0.85)
	fall.tween_property(cam, "rotation:x", 0.15, 0.85)
	await fall.finished
	AudioDirector.fade_ambience(-80.0, 0.6)
	await ui.fade_to(1.0, 0.6)
	watcher.visible = false      # gone on the cut
	await wait(3.5)
	var pp: Vector3 = f + (p - f).normalized() * 2.2
	pp.y = 0.0
	var paper := paper_prop(pp)
	cat.global_position = pp + Vector3(0.45, 0, 0.35)
	cat.look_at(Vector3(player.global_position.x, 0, player.global_position.z), Vector3.UP, true)
	cat.visible = true
	player.face_now(pp)
	cam.rotation.z = 1.25
	cam.rotation.x = -0.2
	AudioDirector.play("wake")
	AudioDirector.fade_ambience(-30.0, 6.0)
	await ui.fade_to(0.0, 3.0)
	GameState.fire("wake")
	await wait(1.2)
	var up := create_tween().set_parallel()
	up.tween_property(cam, "rotation:z", 0.0, 2.5)
	up.tween_property(cam, "position:y", 0.9, 2.5)
	await up.finished
	await wait(1.2)
	AudioDirector.play("cat_meow", cat.global_position)
	await wait(1.8)
	var away: Vector3 = cat.global_position + (cat.global_position - player.global_position).normalized() * 9.0
	away.y = 0.0
	cat.look_at(away, Vector3.UP, true)
	var go := create_tween()
	go.tween_property(cat, "global_position", away, 4.0)
	await go.finished
	cat.visible = false
	var stand := create_tween().set_parallel()
	stand.tween_property(cam, "position:y", 1.6, 1.6)
	stand.tween_property(cam, "rotation:x", 0.0, 1.6)
	await stand.finished
	controls(true)
	await wait_interact(GameState.t("act_pick"), pp, 2.2)
	paper.queue_free()
	controls(false)
	await ui.read_paper("", GameState.t("paper_final"))
	GameState.fire("paper_final")

func ending() -> void:
	controls(false)
	var hi: Dictionary = world.houses["ahmed"]
	var lamp: Node3D = hi["lamp"]
	if ui.fade.color.a > 0.5:
		await ui.fade_to(0.0, 1.0)
	await player.turn_to(lamp.global_position + Vector3(0, 2.3, 0), 1.6)
	await wait(1.5)
	AudioDirector.play("lamp_flicker", lamp.global_position + Vector3(0, 2.3, 0))
	Geo.set_lamp(lamp, false)
	await wait(0.15)
	Geo.set_lamp(lamp, true)
	await wait(0.5)
	Geo.set_lamp(lamp, false)
	AudioDirector.play("lamp_off", lamp.global_position + Vector3(0, 2.3, 0))
	AudioDirector.stop_all_ambience()
	await wait(3.0)
	await ui.fade_to(1.0, 2.0)
	await wait(1.5)
	await ui.show_center(GameState.t("title"), GameState.t("credit"), 2.0)
	await wait(4.0)
	await ui.hide_center(1.5)
	GameState.fire("ending_done")
