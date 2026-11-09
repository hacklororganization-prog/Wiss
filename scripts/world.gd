extends Node3D
## Builds the tiny night village from data/stations.json: ring dirt road, 3 houses, street lamps, fog, moon, stars.
const Geo = preload("res://scripts/geo.gd")
const HouseFactory = preload("res://scripts/house_factory.gd")

var env: Environment
var houses := {}        # id -> info dict from HouseFactory
var station_data := {}  # id -> station dict
var lamps := {}         # name -> lamp node
var points := {}        # watcher points

func build() -> void:
	var S: Dictionary = GameState.stations
	_environment()
	_ground()
	var home: Dictionary = S.get("home", {})
	houses["ahmed"] = HouseFactory.build(self, int(home.get("seed", 7)), GameState.v3(home.get("pos", [0, 0, 20])), deg_to_rad(float(home.get("yaw_deg", 180))))
	for st in S.get("stations", []):
		station_data[st.id] = st
		houses[st.id] = HouseFactory.build(self, int(st.seed), GameState.v3(st.pos), deg_to_rad(float(st.yaw_deg)))
	var sl: Dictionary = S.get("street_lamps", {})
	for k in sl.keys():
		lamps[k] = Geo.lamp(self, GameState.v3(sl[k]), 3.4, 9.0)
	for k in houses.keys():
		lamps["house_" + k] = houses[k]["lamp"]
	var wp: Dictionary = S.get("watcher_points", {})
	for k in wp.keys():
		if wp[k] is Array:
			points[k] = GameState.v3(wp[k])
	_scatter()

func _environment() -> void:
	var we := WorldEnvironment.new()
	env = Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.015, 0.02, 0.04)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.12, 0.14, 0.22)
	env.ambient_light_energy = 0.5
	env.fog_enabled = true
	env.fog_light_color = Color(0.03, 0.035, 0.05)
	env.fog_density = 0.035
	env.tonemap_mode = Environment.TONE_MAPPER_LINEAR
	env.glow_enabled = false
	env.ssao_enabled = false
	env.ssr_enabled = false
	we.environment = env
	add_child(we)
	var moon := DirectionalLight3D.new()
	moon.light_color = Color(0.55, 0.62, 0.85)
	moon.light_energy = 0.18
	moon.shadow_enabled = false
	moon.rotation = Vector3(deg_to_rad(-50), deg_to_rad(30), 0)
	add_child(moon)
	# stars: one MultiMesh of tiny unshaded quads on a far dome (fog does not affect unshaded+no-fog material)
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	var q := QuadMesh.new()
	q.size = Vector2(0.5, 0.5)
	mm.mesh = q
	mm.instance_count = 160
	var rng := RandomNumberGenerator.new()
	rng.seed = 99
	for i in mm.instance_count:
		var d := Vector3(rng.randf_range(-1, 1), rng.randf_range(0.25, 1), rng.randf_range(-1, 1)).normalized() * 140.0
		var tr := Transform3D(Basis.IDENTITY.scaled(Vector3.ONE * rng.randf_range(0.5, 1.3)), d)
		mm.set_instance_transform(i, tr.looking_at(Vector3.ZERO, Vector3.UP) if absf(d.normalized().y) < 0.99 else tr)
	var mmi := MultiMeshInstance3D.new()
	mmi.multimesh = mm
	var sm := Geo.mat(Color(0.8, 0.85, 1.0), true, 0.0, true)
	sm.disable_fog = true
	sm.cull_mode = BaseMaterial3D.CULL_DISABLED
	mmi.material_override = sm
	mmi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(mmi)

func _ground() -> void:
	var g := Geo.box(self, Vector3(200, 1, 200), Vector3(0, -0.5, 0), Color(0.13, 0.12, 0.09), true)
	g.visibility_range_end = 0.0
	var road := Geo.mat(Color(0.26, 0.22, 0.17))
	for r in [[Vector3(44, 0.02, 4), Vector3(0, 0.01, -12)], [Vector3(44, 0.02, 4), Vector3(0, 0.01, 12)],
			[Vector3(4, 0.02, 20), Vector3(-20, 0.01, 0)], [Vector3(4, 0.02, 20), Vector3(20, 0.01, 0)],
			[Vector3(10, 0.02, 3), Vector3(-26, 0.01, 12)]]:
		var n := Geo.box(self, r[0], r[1], Color.BLACK, false, road)
		n.visibility_range_end = 0.0
	# invisible boundary
	for b in [[Vector3(90, 4, 1), Vector3(0, 2, -36)], [Vector3(90, 4, 1), Vector3(0, 2, 34)],
			[Vector3(1, 4, 80), Vector3(-42, 2, 0)], [Vector3(1, 4, 80), Vector3(42, 2, 0)]]:
		var sb := StaticBody3D.new()
		var cs := CollisionShape3D.new()
		var sh := BoxShape3D.new()
		sh.size = b[0]
		cs.shape = sh
		sb.position = b[1]
		sb.add_child(cs)
		add_child(sb)

func _scatter() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 4242
	var spots := [Vector3(-10, 0, 0), Vector3(-4, 0, 3), Vector3(8, 0, -3), Vector3(12, 0, 5), Vector3(-14, 0, -5),
		Vector3(-30, 0, -8), Vector3(-30, 0, 20), Vector3(14, 0, 24), Vector3(-16, 0, -24), Vector3(16, 0, -26), Vector3(34, 0, 18)]
	for p in spots:
		Geo.tree(self, p, rng)
	# low block field walls inside the ring and a dark shed: silhouettes only
	var wc := Color(0.3, 0.29, 0.27)
	Geo.box(self, Vector3(14, 1.1, 0.2), Vector3(0, 0.55, -7), wc, true)
	Geo.box(self, Vector3(0.2, 1.1, 8), Vector3(-15, 0.55, 2), wc, true)
	Geo.box(self, Vector3(3, 2.2, 3), Vector3(10, 1.1, 7), Color(0.25, 0.23, 0.2), true)

## Rough cat-on-wall spot: top of Omar's front wall, road side.
func cat_wall_spot() -> Vector3:
	var info: Dictionary = houses.get("omar", {})
	return info.get("wall_top", Vector3(22.5, 0, 6.0)) + Vector3(0, 1.62, 0)
