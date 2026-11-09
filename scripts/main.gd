extends Node3D
## Entry point: builds world, player, creatures, UI and starts the story at GameState.start_section.
const Creatures = preload("res://scripts/creatures.gd")

var world
var player
var ui
var story
var watcher: Node3D
var cat: Node3D
var dog: Node3D
var _fps_t := 0.0
var _fps_frames := 0
var _warmup := 5.0

func _ready() -> void:
	world = preload("res://scripts/world.gd").new()
	add_child(world)
	world.build()
	player = preload("res://scripts/player.gd").new()
	add_child(player)
	player.global_position = Vector3(0, 0.05, 12.6)
	ui = preload("res://scripts/ui.gd").new()
	add_child(ui)
	ui.player = player
	player.hud = ui
	player.cam.make_current()
	watcher = Creatures.watcher(self)
	cat = Creatures.cat(self)
	dog = Creatures.dog(self)
	dog.visible = false
	story = preload("res://scripts/story.gd").new()
	add_child(story)
	story.setup(self)
	GameState.apply_settings()
	match GameState.start_section:
		"splash":
			await ui.splash()
			ui.show_menu()
			await ui.start_pressed
			story.run("intro")
		"menu":
			ui.show_menu()
			await ui.start_pressed
			story.run("intro")
		_:
			story.run(GameState.start_section)

func _process(dt: float) -> void:
	# auto-drop quality: average FPS < 24 over 10 s -> Low
	if GameState.bot or GameState.settings.quality == "low":
		return
	if _warmup > 0.0:
		_warmup -= dt
		return
	_fps_t += dt
	_fps_frames += 1
	if _fps_t >= 10.0:
		if _fps_frames / _fps_t < 24.0:
			GameState.settings.quality = "low"
			GameState.save_settings()
			world.env.fog_density = 0.045
		_fps_t = 0.0
		_fps_frames = 0
