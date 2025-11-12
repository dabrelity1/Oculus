package net.oculus.shaderpack.loading;

/**
 * Enumeration of shader program slots recognised by modern shader packs. The
 * list mirrors the identifiers used in the Iris 1.16 codebase and is sufficient
 * for routing lookups in the ported ProgramSet skeleton.
 */
public enum ProgramId {
	Shadow,
	Basic,
	Line,
	Textured,
	TexturedLit,
	SkyBasic,
	SkyTextured,
	Clouds,
	Terrain,
	DamagedBlock,
	Block,
	BeaconBeam,
	Entities,
	EntitiesTrans,
	EntitiesGlowing,
	ArmorGlint,
	SpiderEyes,
	Hand,
	Weather,
	Water,
	HandWater,
	Final
}
