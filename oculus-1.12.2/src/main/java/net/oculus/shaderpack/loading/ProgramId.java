package net.oculus.shaderpack.loading;

import java.util.Objects;
import java.util.Optional;

import net.oculus.gl.blending.BlendModeOverride;

/**
 * Enumeration of shader program identifiers along with bookkeeping metadata.
 */
public enum ProgramId {
	Shadow(ProgramGroup.Shadow, "", BlendModeOverride.OFF),

	Basic(ProgramGroup.Gbuffers, "basic"),
	Line(ProgramGroup.Gbuffers, "line", Basic),

	Textured(ProgramGroup.Gbuffers, "textured", Basic),
	TexturedLit(ProgramGroup.Gbuffers, "textured_lit", Textured),
	SkyBasic(ProgramGroup.Gbuffers, "skybasic", Basic),
	SkyTextured(ProgramGroup.Gbuffers, "skytextured", Textured),
	Clouds(ProgramGroup.Gbuffers, "clouds", Textured),

	Terrain(ProgramGroup.Gbuffers, "terrain", TexturedLit),
	DamagedBlock(ProgramGroup.Gbuffers, "damagedblock", Terrain),

	Block(ProgramGroup.Gbuffers, "block", Terrain),
	BeaconBeam(ProgramGroup.Gbuffers, "beaconbeam", Textured),

	Entities(ProgramGroup.Gbuffers, "entities", TexturedLit),
	EntitiesTrans(ProgramGroup.Gbuffers, "entities_translucent", Entities),
	EntitiesGlowing(ProgramGroup.Gbuffers, "entities_glowing", Entities),
	ArmorGlint(ProgramGroup.Gbuffers, "armor_glint", Textured),
	SpiderEyes(ProgramGroup.Gbuffers, "spidereyes", Textured, BlendModeOverride.DEFAULT),

	Hand(ProgramGroup.Gbuffers, "hand", TexturedLit),
	Weather(ProgramGroup.Gbuffers, "weather", TexturedLit),
	Water(ProgramGroup.Gbuffers, "water", Terrain),
	HandWater(ProgramGroup.Gbuffers, "hand_water", Hand),

	Final(ProgramGroup.Final, "");

	private final ProgramGroup group;
	private final String sourceName;
	private final ProgramId fallback;
	private final BlendModeOverride defaultBlendOverride;

	ProgramId(ProgramGroup group, String name) {
		this(group, name, null, null);
	}

	ProgramId(ProgramGroup group, String name, BlendModeOverride defaultBlendOverride) {
		this(group, name, null, defaultBlendOverride);
	}

	ProgramId(ProgramGroup group, String name, ProgramId fallback) {
		this(group, name, fallback, null);
	}

	ProgramId(ProgramGroup group, String name, ProgramId fallback, BlendModeOverride defaultBlendOverride) {
		this.group = Objects.requireNonNull(group, "group");
		this.sourceName = name.isEmpty() ? group.getBaseName() : group.getBaseName() + "_" + name;
		this.fallback = fallback;
		this.defaultBlendOverride = defaultBlendOverride;
	}

	public ProgramGroup getGroup() {
		return group;
	}

	public String getSourceName() {
		return sourceName;
	}

	public Optional<ProgramId> getFallback() {
		return Optional.ofNullable(fallback);
	}

	public Optional<BlendModeOverride> getBlendModeOverride() {
		return Optional.ofNullable(defaultBlendOverride);
	}
}
