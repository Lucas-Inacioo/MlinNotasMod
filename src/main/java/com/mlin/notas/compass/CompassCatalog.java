package com.mlin.notas.compass;

import com.mlin.notas.tier.Tier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Lista das bussolas (logica pura): agrupa variantes de estruturas vanilla numa entrada so (vilas, portais em ruinas,
 * naufragios...), esconde covis de chefe e a Cidadela ate o mundo chegar no nivel de Mineracao anterior ao do chefe
 * (para a bussola nao pular a progressao), filtra pela busca e calcula distancia/direcao.
 */
public final class CompassCatalog {
	/** Prefixos de estruturas vanilla com variantes: {@code minecraft:village_plains} -> {@code minecraft:village}. */
	private static final String[] VANILLA_GROUPS = {"village", "ruined_portal", "abandoned_camp", "ocean_ruin", "shipwreck", "mineshaft"};

	/** Estrutura -> tier de Mineracao que o mundo precisa ter (o anterior ao que o chefe do covil libera). */
	private static final Map<String, Tier> GATES = new LinkedHashMap<>();

	static {
		GATES.put("mlinnotas:cobalt_shrine", Tier.GOLD);       // Golem de Cobalto -> Cobalto
		GATES.put("mlinnotas:abyssal_sanctum", Tier.DIAMOND);  // Guardiao das Profundezas -> Netherite
		GATES.put("mlinnotas:colossus_forge", Tier.NETHERITE); // Colosso Igneo -> Infernita
		GATES.put("mlinnotas:end_citadel", Tier.ENDITE);       // Arauto Astral (cofre da Cidadela) -> Astralita
	}

	private CompassCatalog() {
	}

	/** Entrada da lista de estruturas que representa {@code structureId}. */
	public static String structureGroup(String structureId) {
		int colon = structureId.indexOf(':');
		if (colon < 0 || !structureId.substring(0, colon).equals("minecraft")) {
			return structureId;
		}
		String path = structureId.substring(colon + 1);
		for (String group : VANILLA_GROUPS) {
			if (path.equals(group) || path.startsWith(group + "_")) {
				return "minecraft:" + group;
			}
		}
		return structureId;
	}

	/** Tier de Mineracao que o mundo precisa ter para a entrada aparecer (null = sempre aparece). */
	public static Tier requiredTier(String entryId) {
		return GATES.get(entryId);
	}

	public static boolean unlocked(String entryId, int miningLevel) {
		Tier tier = GATES.get(entryId);
		return tier == null || miningLevel >= tier.level();
	}

	/** Entradas distintas (ordenadas por id) das estruturas possiveis na dimensao, sem as travadas. */
	public static List<String> structureEntries(Collection<String> structureIds, int miningLevel) {
		TreeSet<String> out = new TreeSet<>();
		for (String id : structureIds) {
			String group = structureGroup(id);
			if (unlocked(group, miningLevel)) {
				out.add(group);
			}
		}
		return new ArrayList<>(out);
	}

	/** Estruturas da dimensao que pertencem a entrada {@code entryId}. */
	public static List<String> structuresOf(String entryId, Collection<String> structureIds) {
		List<String> out = new ArrayList<>();
		for (String id : structureIds) {
			if (structureGroup(id).equals(entryId)) {
				out.add(id);
			}
		}
		return out;
	}

	/**
	 * Filtra pela busca: texto comum casa com o nome mostrado ou com o id (sem acento, sem diferenciar maiusculas);
	 * {@code @ns} filtra pelo namespace (ex.: {@code @mlinnotas}). Ordena pelo nome mostrado.
	 */
	public static List<String> filter(List<String> ids, String query, Function<String, String> displayName) {
		String q = fold(query == null ? "" : query.trim());
		String namespace = null;
		if (q.startsWith("@")) {
			int space = q.indexOf(' ');
			namespace = space < 0 ? q.substring(1) : q.substring(1, space);
			q = space < 0 ? "" : q.substring(space + 1).trim();
		}
		List<String> out = new ArrayList<>();
		for (String id : ids) {
			int colon = id.indexOf(':');
			String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
			if (namespace != null && !ns.startsWith(namespace)) {
				continue;
			}
			if (q.isEmpty() || fold(displayName.apply(id)).contains(q) || fold(id).contains(q)) {
				out.add(id);
			}
		}
		out.sort((a, b) -> {
			int c = fold(displayName.apply(a)).compareTo(fold(displayName.apply(b)));
			return c != 0 ? c : a.compareTo(b);
		});
		return out;
	}

	/** Minusculas sem acento, para "pantano" achar "Pântano". */
	public static String fold(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
	}

	/** Nome legivel para um id sem traducao: {@code minecraft:trail_ruins} -> "Trail Ruins". */
	public static String prettyName(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		path = path.substring(path.lastIndexOf('/') + 1);
		StringBuilder out = new StringBuilder();
		for (String word : path.split("_")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	/** Chave de traducao do nome da entrada (biomas usam a chave vanilla {@code biome.ns.path}). */
	public static String nameKey(CompassKind kind, String id) {
		int colon = id.indexOf(':');
		String ns = colon < 0 ? "minecraft" : id.substring(0, colon);
		String path = id.substring(colon + 1).replace('/', '.');
		return kind == CompassKind.BIOME ? "biome." + ns + "." + path : "compass.mlinnotas.structure." + ns + "." + path;
	}

	/** Distancia horizontal arredondada em blocos. */
	public static int horizontalDistance(double fromX, double fromZ, double toX, double toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
	}

	/**
	 * Direcao em 8 pontos ("n", "ne", "e", "se", "s", "sw", "w", "nw"; "here" em cima do alvo) de quem esta em
	 * (fromX, fromZ) para o alvo. No Minecraft o norte e -Z e o leste e +X.
	 */
	public static String direction(double fromX, double fromZ, double toX, double toZ) {
		double dx = toX - fromX;
		double dz = toZ - fromZ;
		if (dx * dx + dz * dz < 4.0) {
			return "here";
		}
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = norte, 90 = leste
		int sector = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
		return new String[] {"n", "ne", "e", "se", "s", "sw", "w", "nw"}[sector];
	}
}
