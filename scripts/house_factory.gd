extends RefCounted
## Builds one low block house with yard wall, metal gate, olive tree, props and outdoor lamp.
## House faces local +Z (the gate side). Variation comes from the seed.
const Geo = preload("res://scripts/geo.gd")

static func build(parent: Node3D, seed: int, pos: Vector3, yaw: float) -> Dictionary:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed
	var h := Node3D.new()
	h.position = pos
	h.rotation.y = yaw
	parent.add_child(h)
	var wall_c := Color(0.36, 0.34, 0.31).darkened(rng.randf() * 0.25)
	var house_c := Color(0.45, 0.42, 0.37).darkened(rng.randf() * 0.3)
	var W := rng.randf_range(12.5, 15.0)
	var D := 11.0
	var hw := W / 2.0
	var hd := D / 2.0
	var gap := 1.3
	Geo.box(h, Vector3(W, 1.6, 0.2), Vector3(0, 0.8, -hd), wall_c, true)
	Geo.box(h, Vector3(0.2, 1.6, D), Vector3(-hw, 0.8, 0), wall_c, true)
	Geo.box(h, Vector3(0.2, 1.6, D), Vector3(hw, 0.8, 0), wall_c, true)
	var seg := hw - gap
	Geo.box(h, Vector3(seg, 1.6, 0.2), Vector3(-gap - seg / 2.0, 0.8, hd), wall_c, true)
	Geo.box(h, Vector3(seg, 1.6, 0.2), Vector3(gap + seg / 2.0, 0.8, hd), wall_c, true)
	# house body + flat roof + door + window
	var bw := rng.randf_range(7.0, 9.0)
	var bd := rng.randf_range(5.0, 6.0)
	var bh := rng.randf_range(2.8, 3.3)
	var bz := -hd + bd / 2.0 + 0.8
	Geo.box(h, Vector3(bw, bh, bd), Vector3(0, bh / 2.0, bz), house_c, true)
	Geo.box(h, Vector3(bw + 0.3, 0.2, bd + 0.3), Vector3(0, bh + 0.1, bz), house_c.darkened(0.3))
	var front := bz + bd / 2.0 + 0.03
	Geo.box(h, Vector3(1.0, 2.1, 0.05), Vector3(0, 1.05, front), Color(0.2, 0.14, 0.1))
	var wm := Geo.mat(Color(0.05, 0.05, 0.06), true, 0.0, true)
	Geo.box(h, Vector3(1.1, 0.8, 0.05), Vector3(bw * 0.3, 1.6, front), Color.BLACK, false, wm)
	# metal gate on a hinge pivot (left side of the opening)
	var pivot := Node3D.new()
	pivot.position = Vector3(-gap, 0, hd)
	h.add_child(pivot)
	var gc := Color(0.16, 0.18, 0.2)
	Geo.box(pivot, Vector3(gap * 2.0, 1.5, 0.05), Vector3(gap, 0.8, 0), gc, true)
	Geo.box(pivot, Vector3(0.08, 1.7, 0.08), Vector3(0, 0.85, 0), gc.darkened(0.3))
	# outdoor lamp on the wall right of the gate
	var lamp := Geo.lamp(h, Vector3(gap + 0.5, 0, hd + 0.2), 2.3, 7.0)
	# olive tree + props
	Geo.tree(h, Vector3(rng.randf_range(-hw + 1.8, -2.5), 0, rng.randf_range(1.0, hd - 1.8)), rng)
	if rng.randf() < 0.7:
		Geo.cyl(h, 0.55, 0.55, 0.9, Vector3(bw * -0.25, bh + 0.65, bz), Color(0.22, 0.24, 0.27))
	if rng.randf() < 0.8:
		for i in rng.randi_range(3, 6):
			var lg := Geo.box(h, Vector3(1.1, 0.14, 0.14), Vector3(hw - 1.4, 0.08 + (i % 3) * 0.14, -hd + 1.0 + i * 0.12), Color(0.25, 0.18, 0.12))
			lg.rotation.y = rng.randf_range(-0.2, 0.2)
	var outside := func(lx: float, lz: float) -> Vector3:
		return pos + Vector3(lx, 0, lz).rotated(Vector3.UP, yaw)
	return {
		"node": h, "gate": pivot, "lamp": lamp, "window_mat": wm,
		"front": outside.call(0.0, hd + 1.6), "at_gate": outside.call(0.0, hd + 0.6),
		"hinge": outside.call(-gap + 0.3, hd + 0.35), "yard": outside.call(0.0, hd - 2.5),
		"mark": outside.call(1.6, hd + 2.4), "wall_top": outside.call(hw - 1.0, hd),
		"yaw": yaw,
	}

static func set_window(info: Dictionary, lit: bool) -> void:
	var wm: StandardMaterial3D = info["window_mat"]
	wm.albedo_color = Color(0.95, 0.7, 0.35) if lit else Color(0.05, 0.05, 0.06)
