package com.mlin.notas.storage;

import com.mlin.notas.tier.Tier;

/**
 * Niveis da Unidade de Armazenamento, um por material da escada {@link Tier} a partir do cobre. Cada nivel guarda
 * ate {@link #types()} tipos de item diferentes e ate {@link #perType()} unidades de cada tipo, sem limite de pilha
 * (como gavetas): e isso que deixa "empilhar" muito mais que um bau. Logica pura.
 */
public enum StorageTier {
	COPPER(Tier.COPPER, 16, 1_024),
	IRON(Tier.IRON, 24, 4_096),
	GOLD(Tier.GOLD, 32, 8_192),
	COBALT(Tier.COBALT, 40, 16_384),
	DIAMOND(Tier.DIAMOND, 48, 65_536),
	NETHERITE(Tier.NETHERITE, 64, 262_144),
	INFERNITE(Tier.INFERNITE, 80, 1_048_576),
	ENDITE(Tier.ENDITE, 96, 4_194_304),
	ASTRALITE(Tier.ASTRALITE, 112, 16_777_216),
	CELESTIUM(Tier.CELESTIUM, 128, 67_108_864);

	private final Tier material;
	private final int types;
	private final long perType;

	StorageTier(Tier material, int types, long perType) {
		this.material = material;
		this.types = types;
		this.perType = perType;
	}

	public Tier material() {
		return material;
	}

	/** Tipos de item diferentes que cabem. */
	public int types() {
		return types;
	}

	/** Quantidade maxima de cada tipo. */
	public long perType() {
		return perType;
	}

	/** Capacidade total (todos os tipos cheios). */
	public long capacity() {
		return types * perType;
	}

	/** Id do bloco: storage_unit_copper, storage_unit_iron... */
	public String blockName() {
		return "storage_unit_" + material.id();
	}

	/** Nivel anterior (o que vai na receita de melhoria), ou null para o cobre. */
	public StorageTier previous() {
		return ordinal() == 0 ? null : values()[ordinal() - 1];
	}

	/** Prioridade de insercao da unidade na rede: unidades maiores recebem primeiro (os baus ficam com 0). */
	public int priority() {
		return 10 + ordinal();
	}
}
