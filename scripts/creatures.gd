extends RefCounted
## Builders for the Watcher (the woman), the black cat and the dog. Low-poly, primitives only.
## Watcher: black tapered cloak, forward-leaning hood, cane. Hood interior pure unlit black. NO face geometry.
const Geo = preload("res://scripts/geo.gd")

static func watcher(parent: Node) -> Node3D:
	var w := Node3D.new()
	w.name = "Watcher"
	parent.add_child(w)
	var cloak := Geo.mat(Color(0.035, 0.035, 0.04))
	var void_black := Geo.mat(Color(0, 0, 0), true)
	Geo.cyl(w, 0.16, 0.48, 1.45, Vector3(0, 0.72, 0), Color.BLACK, cloak)
	Geo.cyl(w, 0.2, 0.16, 0.25, Vector3(0, 1.5, 0.02), Color.BLACK, cloak)   # shoulders
	var hood := Node3D.new()
	hood.position = Vector3(0, 1.62, 0.06)
	hood.rotation.x = -0.45  # leans forward (figure faces +Z)
	w.add_child(hood)
	Geo.sphere(hood, 0.2, Vector3(0, 0.02, -0.03), Color.BLACK, Vector3(1.0, 1.25, 1.15), cloak)
	Geo.sphere(hood, 0.13, Vector3(0, -0.02, 0.13), Color.BLACK, Vector3(1.0, 1.25, 0.5), void_black)
	var cane := Geo.cyl(w, 0.018, 0.018, 1.25, Vector3(0.36, 0.62, 0.22), Color.BLACK, Geo.mat(Color(0.07, 0.055, 0.045)))
	cane.rotation.z = -0.12
	w.visible = false
	return w

static func cat(parent: Node) -> Node3D:
	var c := Node3D.new()
	c.name = "Cat"
	parent.add_child(c)
	var fur := Geo.mat(Color(0.02, 0.02, 0.022))
	var body := Geo.sphere(c, 0.14, Vector3(0, 0.2, 0), Color.BLACK, Vector3(1.0, 1.0, 2.0), fur)
	body.rotation.x = 0.0
	Geo.sphere(c, 0.1, Vector3(0, 0.34, 0.25), Color.BLACK, Vector3.ONE, fur)
	for s in [-1.0, 1.0]:
		var pm := PrismMesh.new()
		pm.size = Vector3(0.06, 0.07, 0.03)
		Geo.mi(c, pm, Vector3(0.05 * s, 0.45, 0.24), fur)
		var eye := Geo.sphere(c, 0.018, Vector3(0.04 * s, 0.36, 0.34), Color.WHITE, Vector3.ONE, Geo.mat(Color(0.85, 0.95, 0.3), true, 3.0))
		eye.visibility_range_end = 0.0
	var tail := Geo.cyl(c, 0.02, 0.03, 0.4, Vector3(0, 0.32, -0.32), Color.BLACK, fur)
	tail.rotation.x = -0.6
	for x in [-0.07, 0.07]:
		for z in [-0.15, 0.15]:
			Geo.cyl(c, 0.025, 0.025, 0.14, Vector3(x, 0.07, z), Color.BLACK, fur)
	c.visible = false
	return c

static func dog(parent: Node) -> Node3D:
	var d := Node3D.new()
	d.name = "Dog"
	parent.add_child(d)
	var fur := Geo.mat(Color(0.3, 0.25, 0.18))
	Geo.box(d, Vector3(0.3, 0.3, 0.75), Vector3(0, 0.5, 0), Color.BLACK, false, fur)
	Geo.box(d, Vector3(0.22, 0.22, 0.28), Vector3(0, 0.72, 0.45), Color.BLACK, false, fur)
	for x in [-0.1, 0.1]:
		for z in [-0.28, 0.28]:
			Geo.box(d, Vector3(0.07, 0.38, 0.07), Vector3(x, 0.19, z), Color.BLACK, false, fur)
	var tail := Geo.box(d, Vector3(0.05, 0.05, 0.3), Vector3(0, 0.66, -0.48), Color.BLACK, false, fur)
	tail.rotation.x = 0.6
	return d
