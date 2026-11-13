package com.mlin.notas.ruins;

import java.util.Locale;

/** Os tipos de ruina de superficie. O id e o nome da estrutura em {@code data/mlinnotas/worldgen/structure}. Logica pura. */
public enum RuinKind {
	/** Torre de vigia caida (planicies, savanas, colinas): 2 andares desabados, bau no topo, pedra de teleporte. */
	WATCHTOWER("ruined_watchtower", 4),
	/** Acampamento abandonado (florestas, taigas): fogueira apagada, barracas, bau e barril. */
	CAMP("abandoned_camp", 3),
	/** Poco antigo (biomas secos): escada de mao ate uma salinha com bau. */
	WELL("old_well", 3),
	/** Capela em ruinas (floresta escura, pantano): altar com atril (futuro item de invocar chefe) e pedra de teleporte. */
	CHAPEL("ruined_chapel", 4),
	/** Mina desativada (montanhas): entrada escorada, veio de cobre ou ferro e sala do altar do futuro chefe. */
	MINE("disused_mine", 6);

	private final String id;
	private final int maxSlope;

	RuinKind(String id, int maxSlope) {
		this.id = id;
		this.maxSlope = maxSlope;
	}

	public String id() {
		return id;
	}

	/** Maior diferenca de altura do terreno (blocos) aceita sob a ruina. */
	public int maxSlope() {
		return maxSlope;
	}

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static RuinKind byName(String name) {
		for (RuinKind kind : values()) {
			if (kind.serializedName().equals(name) || kind.id.equals(name)) {
				return kind;
			}
		}
		return WATCHTOWER;
	}
}
