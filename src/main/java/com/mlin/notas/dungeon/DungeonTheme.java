package com.mlin.notas.dungeon;

/**
 * Tema de uma masmorra media: estrutura ({@code mlinnotas:<id>}), predio da entrada na superficie, sala especial do
 * tema, mob dos geradores e do mini-chefe. Logica pura (os blocos de cada tema ficam em {@code DungeonPalette}).
 */
public enum DungeonTheme {
	/** Cripta sob um mausoleu (planicies e florestas). Mini-chefe: Senhor da Cripta (esqueleto wither). */
	CRYPT("forgotten_crypt", "minecraft:skeleton", "minecraft:zombie"),
	/** Fortaleza ana afundada nas montanhas, de ardosia com veios de minerio. Mini-chefe: Rei Anao Caido (vingador). */
	DWARF("sunken_hold", "minecraft:zombie", "minecraft:cave_spider"),
	/** Templo afogado na costa, de prismarinho com pocos de agua e coral. Mini-chefe: Sumo Sacerdote Afogado. */
	DROWNED("drowned_temple", "minecraft:drowned", "minecraft:drowned"),
	/** Catacumba de arenito sob uma piramide (deserto e badlands). Mini-chefe: Farao Mumificado (zumbi do deserto). */
	SAND("sand_catacomb", "minecraft:husk", "minecraft:skeleton");

	private final String id;
	private final String spawnerMob;
	private final String spawnerMobAlt;

	DungeonTheme(String id, String spawnerMob, String spawnerMobAlt) {
		this.id = id;
		this.spawnerMob = spawnerMob;
		this.spawnerMobAlt = spawnerMobAlt;
	}

	/** Id da estrutura ({@code /locate structure mlinnotas:<id>}). */
	public String id() {
		return id;
	}

	/** Mob do gerador; {@code alt} escolhe o segundo mob (salas com dois geradores). */
	public String spawnerMob(boolean alt) {
		return alt ? spawnerMobAlt : spawnerMob;
	}

	/** O templo afogado pode aparecer com a carcaca exposta no fundo do mar (o predio da entrada fica em terra). */
	public boolean allowsExposedShell() {
		return this == DROWNED;
	}

	/** Desnivel maximo do terreno sob o predio da entrada. */
	public int maxSlope() {
		return this == DWARF ? 7 : 4;
	}

	public static DungeonTheme byId(String id) {
		for (DungeonTheme theme : values()) {
			if (theme.id.equals(id) || theme.name().equalsIgnoreCase(id)) {
				return theme;
			}
		}
		return CRYPT;
	}
}
