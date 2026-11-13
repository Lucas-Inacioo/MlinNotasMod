package com.mlin.notas.tier;

/**
 * Escada unica de 12 materiais usada por tudo que tem nivel no mod: ferramentas, armaduras, varinhas,
 * baus e upgrades de fornalha. Logica pura (sem classes do Minecraft).
 *
 * <p>Os materiais vanilla (madeira, pedra, cobre, ferro, ouro, diamante, netherite) foram rebalanceados para
 * caber na escada; os novos (cobalto, infernita, endita, astralita, celestio) sao do mod. Nenhuma armadura abaixo
 * do celestio chega aos 20 pontos (barra cheia).</p>
 *
 * <p>Requisito de mineracao (minerio -> picareta minima): cobalto = ferro, diamante = cobalto,
 * detritos ancestrais = diamante, infernita = netherite, endita = infernita, astralita = endita.</p>
 */
public enum Tier {
	//         id           origem            vanilla  ferramenta: dur  vel    dano  ench   armadura [botas, calca, peito, capacete]  tenac  kb     durArm varinha bau  fornalha
	WOOD("wood", Origin.OVERWORLD, true, 59, 2.0F, 0.0F, 15, new int[] {1, 1, 2, 1}, 0.0F, 0.0F, 5, 9, 27, 1.0F),
	STONE("stone", Origin.OVERWORLD, true, 131, 4.0F, 1.0F, 5, new int[] {1, 2, 3, 1}, 0.0F, 0.0F, 10, 18, 36, 1.25F),
	COPPER("copper", Origin.OVERWORLD, true, 190, 5.0F, 1.0F, 13, new int[] {1, 2, 4, 1}, 0.0F, 0.0F, 12, 27, 45, 1.5F),
	IRON("iron", Origin.OVERWORLD, true, 250, 6.0F, 2.0F, 14, new int[] {1, 3, 4, 2}, 0.0F, 0.0F, 15, 45, 54, 2.0F),
	GOLD("gold", Origin.OVERWORLD, true, 400, 7.0F, 2.0F, 22, new int[] {2, 3, 5, 2}, 0.0F, 0.0F, 18, 64, 81, 3.0F),
	COBALT("cobalt", Origin.OVERWORLD, false, 650, 7.5F, 2.5F, 12, new int[] {2, 4, 5, 2}, 0.5F, 0.0F, 22, 81, 99, 4.0F),
	DIAMOND("diamond", Origin.OVERWORLD, true, 1000, 8.0F, 3.0F, 10, new int[] {2, 4, 6, 2}, 1.0F, 0.0F, 26, 128, 108, 6.0F),
	NETHERITE("netherite", Origin.NETHER, true, 1400, 8.5F, 3.5F, 15, new int[] {2, 5, 6, 3}, 2.0F, 0.05F, 30, 192, 135, 8.0F),
	INFERNITE("infernite", Origin.NETHER, false, 1900, 9.5F, 4.0F, 16, new int[] {3, 5, 7, 2}, 2.5F, 0.05F, 34, 256, 162, 12.0F),
	ENDITE("endite", Origin.END, false, 2500, 10.5F, 4.5F, 17, new int[] {3, 5, 7, 3}, 3.0F, 0.1F, 38, 384, 189, 16.0F),
	ASTRALITE("astralite", Origin.END, false, 3200, 11.5F, 5.0F, 18, new int[] {3, 6, 7, 3}, 3.5F, 0.1F, 43, 512, 216, 24.0F),
	CELESTIUM("celestium", Origin.END, false, 4000, 13.0F, 6.0F, 20, new int[] {3, 6, 8, 3}, 4.0F, 0.15F, 50, 1024, 243, 32.0F);

	/** Onde o material e obtido. */
	public enum Origin {
		OVERWORLD,
		NETHER,
		END
	}

	/** Pecas de armadura na ordem do array de defesa. */
	public enum ArmorPiece {
		BOOTS(13),
		LEGGINGS(15),
		CHESTPLATE(16),
		HELMET(11);

		private final int durabilityBase;

		ArmorPiece(int durabilityBase) {
			this.durabilityBase = durabilityBase;
		}

		/** Multiplicador vanilla de durabilidade por peca (capacete 11, peito 16, calca 15, botas 13). */
		public int durabilityBase() {
			return durabilityBase;
		}
	}

	/** Pontos de armadura que enchem a barra (e o maximo que reduz dano no calculo vanilla). */
	public static final int FULL_ARMOR = 20;

	private final String id;
	private final Origin origin;
	private final boolean vanilla;
	private final int toolDurability;
	private final float miningSpeed;
	private final float attackBonus;
	private final int enchantability;
	private final int[] defense;
	private final float toughness;
	private final float knockbackResistance;
	private final int armorDurabilityMultiplier;
	private final int wandBlocks;
	private final int chestSlots;
	private final float furnaceSpeed;

	Tier(String id, Origin origin, boolean vanilla, int toolDurability, float miningSpeed, float attackBonus, int enchantability,
			int[] defense, float toughness, float knockbackResistance, int armorDurabilityMultiplier, int wandBlocks, int chestSlots,
			float furnaceSpeed) {
		this.id = id;
		this.origin = origin;
		this.vanilla = vanilla;
		this.toolDurability = toolDurability;
		this.miningSpeed = miningSpeed;
		this.attackBonus = attackBonus;
		this.enchantability = enchantability;
		this.defense = defense;
		this.toughness = toughness;
		this.knockbackResistance = knockbackResistance;
		this.armorDurabilityMultiplier = armorDurabilityMultiplier;
		this.wandBlocks = wandBlocks;
		this.chestSlots = chestSlots;
		this.furnaceSpeed = furnaceSpeed;
	}

	public String id() {
		return id;
	}

	/** Nivel 1..12 (madeira = 1). */
	public int level() {
		return ordinal() + 1;
	}

	public Origin origin() {
		return origin;
	}

	/** Material vanilla (rebalanceado) ou novo do mod. */
	public boolean vanilla() {
		return vanilla;
	}

	public int toolDurability() {
		return toolDurability;
	}

	public float miningSpeed() {
		return miningSpeed;
	}

	public float attackBonus() {
		return attackBonus;
	}

	public int enchantability() {
		return enchantability;
	}

	public int defense(ArmorPiece piece) {
		return defense[piece.ordinal()];
	}

	/** Soma das 4 pecas. */
	public int fullSetDefense() {
		int total = 0;
		for (int value : defense) {
			total += value;
		}
		return total;
	}

	/** Tenacidade por peca. */
	public float toughness() {
		return toughness;
	}

	/** Resistencia a repulsao por peca. */
	public float knockbackResistance() {
		return knockbackResistance;
	}

	public int armorDurabilityMultiplier() {
		return armorDurabilityMultiplier;
	}

	public int armorDurability(ArmorPiece piece) {
		return armorDurabilityMultiplier * piece.durabilityBase();
	}

	/** Blocos que a varinha deste nivel coloca por uso. */
	public int wandBlocks() {
		return wandBlocks;
	}

	/** Durabilidade da varinha: cerca de 16 usos cheios. */
	public int wandDurability() {
		return Math.max(64, wandBlocks * 16);
	}

	/** Slots do bau deste nivel (madeira = bau vanilla, 27). Sempre multiplo de 9. */
	public int chestSlots() {
		return chestSlots;
	}

	/** Multiplicador de velocidade do upgrade de fornalha (madeira = fornalha sem upgrade). */
	public float furnaceSpeed() {
		return furnaceSpeed;
	}

	/** Item/bloco resistente a fogo e lava (como netherite). */
	public boolean fireResistant() {
		return ordinal() >= NETHERITE.ordinal();
	}

	/** Material novo que vem de minerio (celestio e liga, nao tem minerio). */
	public boolean hasOre() {
		return !vanilla && this != CELESTIUM;
	}

	/** Material novo com ferramentas/armaduras registradas pelo mod. */
	public boolean modGear() {
		return !vanilla;
	}

	public Tier next() {
		int i = ordinal() + 1;
		return i < values().length ? values()[i] : null;
	}

	public Tier previous() {
		int i = ordinal() - 1;
		return i >= 0 ? values()[i] : null;
	}

	/**
	 * Picareta minima para quebrar o minerio deste material com drop (null para materiais sem minerio do mod).
	 */
	public Tier oreRequires() {
		return switch (this) {
			case COBALT -> IRON;
			case INFERNITE -> NETHERITE;
			case ENDITE -> INFERNITE;
			case ASTRALITE -> ENDITE;
			default -> null;
		};
	}

	/** Uma ferramenta deste nivel quebra (com drop) um bloco que exige {@code required}. */
	public boolean canMine(Tier required) {
		return required == null || ordinal() >= required.ordinal();
	}

	public static Tier byId(String id) {
		for (Tier tier : values()) {
			if (tier.id.equals(id)) {
				return tier;
			}
		}
		return null;
	}
}
