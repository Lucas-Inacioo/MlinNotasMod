package com.mlin.notas.village;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Onde um posto de trabalho vale dentro da vila (logica pura; Vilas 3.0). O codigo da vila usa blocos de posto (barril,
 * caldeirao, defumador, atril...) como enfeite; sem esta regra o aldeao pega o primeiro que achar e vira pescador no
 * celeiro. Regra: o posto vale para o aldeao se (a) esta numa construcao pronta cujo tipo aceita a profissao, ou (b) foi
 * colocado por um jogador (nao e bloco que a vila colocou). Enfeite da vila fora de construcao que aceite (rua, praca,
 * lote vago, obras publicas) nunca vale.
 */
public final class JobSiteRules {
	/** Todas as profissoes (o mercado serve qualquer uma). */
	public static final String ANY = "*";

	private static final Map<String, Set<String>> ACCEPTS = new HashMap<>();

	static {
		accept("smithy", "armorer", "weaponsmith", "toolsmith");
		accept("barracks", "armorer", "weaponsmith");
		accept("library", "librarian");
		accept("school", "librarian");
		accept("enchanter", "librarian");
		accept("town_hall", "librarian", "cartographer");
		accept("farm", "farmer");
		accept("granary", "farmer");
		accept("orchard", "farmer");
		accept("mill", "farmer");
		accept("bakery", "farmer");
		accept("winery", "farmer");
		accept("apiary", "farmer");
		accept("pen", "shepherd", "farmer");
		accept("butcher", "butcher");
		accept("tannery", "leatherworker");
		accept("tailor", "shepherd");
		accept("fletcher", "fletcher");
		accept("quarry", "mason");
		accept("sawmill", "mason");
		accept("church", "cleric");
		accept("explorers_guild", "cartographer");
		accept("post_office", "cartographer");
		accept("museum", "cartographer", "librarian");
		accept("market", ANY);
		accept("inn", "fisherman");
		accept("tavern", "fisherman", "butcher");
	}

	private JobSiteRules() {
	}

	private static void accept(String kind, String... professions) {
		ACCEPTS.put(kind, Set.of(professions));
	}

	/** A construcao do tipo {@code kind} aceita postos da profissao (ids sem namespace, como {@code armorer}). */
	public static boolean accepts(String kind, String profession) {
		Set<String> set = kind == null ? null : ACCEPTS.get(kind);
		return set != null && (set.contains(ANY) || set.contains(profession));
	}

	/** Alguma das profissoes que usam o posto e aceita pela construcao. */
	public static boolean accepts(String kind, Collection<String> professions) {
		for (String p : professions) {
			if (accepts(kind, p)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * O posto (que serve as {@code professions}) vale dentro da vila? {@code buildingKind} = tipo da construcao pronta que
	 * contem o bloco (null = nenhuma); {@code villageOwned} = o bloco foi colocado pela vila.
	 */
	public static boolean allowed(Collection<String> professions, String buildingKind, boolean villageOwned) {
		if (professions.isEmpty()) {
			return true;
		}
		if (accepts(buildingKind, professions)) {
			return true;
		}
		return !villageOwned;
	}

	/** Tipos de construcao que a tabela conhece (testes). */
	public static Set<String> kinds() {
		return Set.copyOf(ACCEPTS.keySet());
	}
}
