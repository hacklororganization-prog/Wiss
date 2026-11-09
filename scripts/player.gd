extends CharacterBody3D
## First-person player: virtual joystick / swipe look (via HUD), WASD + mouse fallback, flashlight, footsteps.

var cam: Camera3D
var flash: SpotLight3D
var hud
var controls := false
var speed := 2.2
var _step := 0.0
var _shake := 0.0

func _ready() -> void:
	var cs := CollisionShape3D.new()
	var cap := CapsuleShape3D.new()
	cap.radius = 0.3
	cap.height = 1.7
	cs.shape = cap
	cs.position.y = 0.85
	add_child(cs)
	cam = Camera3D.new()
	cam.position.y = 1.6
	cam.fov = 70.0
	cam.near = 0.05
	cam.far = 160.0
	add_child(cam)
	flash = SpotLight3D.new()
	flash.spot_range = 18.0
	flash.spot_angle = 26.0
	flash.spot_attenuation = 1.2
	flash.light_energy = 2.2
	flash.light_color = Color(1.0, 0.95, 0.85)
	flash.shadow_enabled = false
	flash.position = Vector3(0.15, -0.15, 0)
	flash.visible = false
	cam.add_child(flash)

func set_flash(on: bool) -> void:
	if flash.visible != on:
		flash.visible = on
		AudioDirector.play("flash_click")

func toggle_flash() -> void:
	set_flash(not flash.visible)

func _unhandled_input(e: InputEvent) -> void:
	if not controls or OS.has_feature("mobile"):
		return
	if e is InputEventMouseButton and e.pressed and Input.mouse_mode != Input.MOUSE_MODE_CAPTURED:
		Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
	elif e is InputEventMouseMotion and Input.mouse_mode == Input.MOUSE_MODE_CAPTURED:
		_look(e.relative * 0.6)
	elif e is InputEventKey and e.pressed and not e.echo and e.physical_keycode == KEY_ESCAPE:
		Input.mouse_mode = Input.MOUSE_MODE_VISIBLE

func _look(d: Vector2) -> void:
	var s := 0.0045 * float(GameState.settings.sens)
	rotation.y -= d.x * s
	cam.rotation.x = clampf(cam.rotation.x - d.y * s, -1.3, 1.3)

func _physics_process(dt: float) -> void:
	var mv := Vector2.ZERO
	if controls and hud:
		mv = hud.move_vec
		mv += Vector2(float(Input.is_physical_key_pressed(KEY_D)) - float(Input.is_physical_key_pressed(KEY_A)),
			float(Input.is_physical_key_pressed(KEY_S)) - float(Input.is_physical_key_pressed(KEY_W)))
		mv = mv.limit_length(1.0)
		_look(hud.take_look())
	var dir := transform.basis * Vector3(mv.x, 0, mv.y)
	velocity.x = dir.x * speed
	velocity.z = dir.z * speed
	if is_on_floor():
		velocity.y = 0.0
	else:
		velocity.y -= 9.8 * dt
	move_and_slide()
	var hs := Vector2(velocity.x, velocity.z).length()
	if hs > 0.3 and is_on_floor():
		_step += hs * dt
		if _step > 0.72:
			_step = 0.0
			AudioDirector.play("step_dirt", global_position, 0.0)
	if _shake > 0.0:
		_shake -= dt
		cam.h_offset = randf_range(-0.04, 0.04)
		cam.v_offset = randf_range(-0.04, 0.04)
		if _shake <= 0.0:
			cam.h_offset = 0.0
			cam.v_offset = 0.0

func shake(t: float) -> void:
	_shake = t

func forward() -> Vector3:
	return -cam.global_transform.basis.z

func eye() -> Vector3:
	return cam.global_position

## Is the camera looking toward p (cos of angle > d)?
func looking_at(p: Vector3, d := 0.85) -> bool:
	return forward().dot((p - eye()).normalized()) > d

func flat_dist(p: Vector3) -> float:
	return Vector2(global_position.x - p.x, global_position.z - p.z).length()

## Smoothly turn body (yaw) and camera (pitch) to face p.
func turn_to(p: Vector3, t: float) -> void:
	var d := p - eye()
	var yaw := atan2(-d.x, -d.z)
	var pitch := atan2(d.y, Vector2(d.x, d.z).length())
	yaw = rotation.y + wrapf(yaw - rotation.y, -PI, PI)
	var tw := create_tween().set_parallel().set_trans(Tween.TRANS_SINE)
	tw.tween_property(self, "rotation:y", yaw, t)
	tw.tween_property(cam, "rotation:x", pitch, t)
	await tw.finished

func face_now(p: Vector3) -> void:
	var d := p - eye()
	rotation.y = atan2(-d.x, -d.z)
	cam.rotation.x = atan2(d.y, Vector2(d.x, d.z).length())
