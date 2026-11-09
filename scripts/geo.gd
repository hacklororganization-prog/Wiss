extends RefCounted
## Primitive-mesh helpers. Everything visual in the game is built from these.

static var _mats := {}

static func mat(c: Color, unshaded := false, emit := 0.0, unique := false) -> StandardMaterial3D:
	var key := "%s|%s|%s" % [c, unshaded, emit]
	if not unique and _mats.has(key):
		return _mats[key]
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	m.roughness = 1.0
	if unshaded:
		m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	if emit > 0.0:
		m.emission_enabled = true
		m.emission = c
		m.emission_energy_multiplier = emit
	if not unique:
		_mats[key] = m
	return m

static func mi(parent: Node, mesh: Mesh, pos: Vector3, m: Material, rot := Vector3.ZERO) -> MeshInstance3D:
	var n := MeshInstance3D.new()
	n.mesh = mesh
	n.material_override = m
	n.position = pos
	n.rotation = rot
	n.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	n.visibility_range_end = 75.0
	parent.add_child(n)
	return n

static func box(parent: Node, size: Vector3, pos: Vector3, c: Color, collide := false, m: Material = null) -> MeshInstance3D:
	var bm := BoxMesh.new()
	bm.size = size
	var n := mi(parent, bm, pos, m if m else mat(c))
	if collide:
		var sb := StaticBody3D.new()
		var cs := CollisionShape3D.new()
		var sh := BoxShape3D.new()
		sh.size = size
		cs.shape = sh
		sb.add_child(cs)
		n.add_child(sb)
	return n

static func cyl(parent: Node, r_top: float, r_bot: float, h: float, pos: Vector3, c: Color, m: Material = null) -> MeshInstance3D:
	var cm := CylinderMesh.new()
	cm.top_radius = r_top
	cm.bottom_radius = r_bot
	cm.height = h
	cm.radial_segments = 8
	cm.rings = 1
	return mi(parent, cm, pos, m if m else mat(c))

static func sphere(parent: Node, r: float, pos: Vector3, c: Color, scl := Vector3.ONE, m: Material = null) -> MeshInstance3D:
	var sm := SphereMesh.new()
	sm.radius = r
	sm.height = r * 2.0
	sm.radial_segments = 10
	sm.rings = 5
	var n := mi(parent, sm, pos, m if m else mat(c))
	n.scale = scl
	return n

static func tree(parent: Node, pos: Vector3, rng: RandomNumberGenerator) -> void:
	var t := Node3D.new()
	t.position = pos
	t.rotation.y = rng.randf() * TAU
	parent.add_child(t)
	var trunk := cyl(t, 0.12, 0.22, 1.6, Vector3(0, 0.8, 0), Color(0.22, 0.17, 0.12))
	trunk.rotation.z = rng.randf_range(-0.2, 0.2)
	var leaf := Color(0.16, 0.2, 0.13)
	for i in 3:
		var off := Vector3(rng.randf_range(-0.7, 0.7), 1.9 + rng.randf() * 0.5, rng.randf_range(-0.7, 0.7))
		sphere(t, 1.0, off, leaf, Vector3(1.2, 0.6, 1.0) * rng.randf_range(0.8, 1.2))

## Faceless low-poly person. Returns the root node.
static func figure(parent: Node, pos: Vector3, c: Color, seated := false) -> Node3D:
	var f := Node3D.new()
	f.position = pos
	parent.add_child(f)
	var y := 0.45 if seated else 0.0
	var cap := CapsuleMesh.new()
	cap.radius = 0.24
	cap.height = 1.0 if seated else 1.45
	cap.radial_segments = 8
	cap.rings = 2
	mi(f, cap, Vector3(0, y + cap.height / 2.0 + (0.0 if seated else 0.05), 0), mat(c))
	sphere(f, 0.15, Vector3(0, y + cap.height + 0.2, 0), c.lightened(0.15))
	if seated:
		box(f, Vector3(0.5, 0.45, 0.5), Vector3(0, 0.22, 0), Color(0.25, 0.2, 0.16))
	return f

## Lamp: pole (optional) + emissive bulb + OmniLight (no shadows). Toggle with set_lamp().
static func lamp(parent: Node, pos: Vector3, h := 3.2, light_range := 8.0, pole := true) -> Node3D:
	var l := Node3D.new()
	l.position = pos
	parent.add_child(l)
	if pole:
		cyl(l, 0.05, 0.07, h, Vector3(0, h / 2.0, 0), Color(0.15, 0.15, 0.15))
	var bm := mat(Color(1.0, 0.8, 0.5), true, 0.0, true)
	l.set_meta("bulb_mat", bm)
	sphere(l, 0.12, Vector3(0, h, 0), Color.WHITE, Vector3.ONE, bm)
	var o := OmniLight3D.new()
	o.name = "Light"
	o.position = Vector3(0, h - 0.2, 0)
	o.light_color = Color(1.0, 0.75, 0.45)
	o.light_energy = 1.4
	o.omni_range = light_range
	o.omni_attenuation = 1.2
	o.shadow_enabled = false
	l.add_child(o)
	set_lamp(l, false)
	return l

static func set_lamp(l: Node3D, on: bool) -> void:
	(l.get_node("Light") as Light3D).visible = on
	var bm: StandardMaterial3D = l.get_meta("bulb_mat")
	bm.albedo_color = Color(1.0, 0.82, 0.55) if on else Color(0.12, 0.11, 0.1)

static func lamp_on(l: Node3D) -> bool:
	return (l.get_node("Light") as Light3D).visible
