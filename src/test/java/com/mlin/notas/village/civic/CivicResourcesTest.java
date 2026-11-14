package com.mlin.notas.village.civic;

import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.VillageProject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Confere os recursos das construcoes civicas: nome e descricao de cada projeto e o que cada nivel traz (pt_br e en_us),
 * mensagens, colecoes do Museu, destinos da Guilda, e estado/modelo/item/loot/texturas dos tres blocos de balcao e a pele
 * do guarda. Se falhar: rode {@code java scripts/ContentGen.java src/main/resources} e {@code scripts/CivicTexGen.java}.
 */
class CivicResourcesTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/mlinnotas");
	private static final Path DATA = Path.of("src/main/resources/data/mlinnotas");

	@Test
	void everyProjectLevelMessageAndNameHasBothLanguages() throws IOException {
		String pt = Files.readString(ASSETS.resolve("lang/pt_br.json"), StandardCharsets.UTF_8);
		String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8);
		List<String> keys = new ArrayList<>();
		for (VillageProject project : CivicBuildings.PROJECTS) {
			keys.add("village.mlinnotas.project." + project.id());
			keys.add("village.mlinnotas.project." + project.id() + ".desc");
			for (int level = 1; level <= BuildingTypes.maxLevel(project.id()); level++) {
				keys.add("village.mlinnotas.building." + project.id() + ".level." + level);
			}
		}
		for (MuseumSets.MuseumSet set : MuseumSets.all()) {
			keys.add("museum.mlinnotas.set." + set.id());
		}
		for (ExpeditionRules.Destination d : ExpeditionRules.Destination.values()) {
			keys.add("guild.mlinnotas.dest." + d.id());
		}
		for (String block : List.of("mailbox", "museum_pedestal", "guild_board")) {
			keys.add("block.mlinnotas." + block);
		}
		keys.addAll(List.of("entity.mlinnotas.village_guard", "container.mlinnotas.mailbox", "container.mlinnotas.mailbox.none", "container.mlinnotas.guild",
				"container.mlinnotas.guild.idle", "message.mlinnotas.civic.post.inactive", "message.mlinnotas.civic.post.no_destination",
				"message.mlinnotas.civic.post.dest_village", "message.mlinnotas.civic.post.dest_player", "message.mlinnotas.civic.post.sent",
				"message.mlinnotas.civic.post.arrived", "message.mlinnotas.civic.museum.inactive", "message.mlinnotas.civic.museum.have",
				"message.mlinnotas.civic.museum.not_wanted", "message.mlinnotas.civic.museum.donated", "message.mlinnotas.civic.museum.complete",
				"message.mlinnotas.civic.museum.head", "message.mlinnotas.civic.museum.line", "message.mlinnotas.civic.museum.locked",
				"message.mlinnotas.civic.guild.inactive", "message.mlinnotas.civic.guild.dest", "message.mlinnotas.civic.guild.full",
				"message.mlinnotas.civic.guild.no_villager", "message.mlinnotas.civic.guild.departed", "message.mlinnotas.civic.guild.returned",
				"message.mlinnotas.civic.tower.raid", "message.mlinnotas.civic.insurance", "message.mlinnotas.civic.insurance_respawn"));
		List<String> missing = new ArrayList<>();
		for (String key : keys) {
			if (!pt.contains("\"" + key + "\"")) {
				missing.add("pt_br " + key);
			}
			if (!en.contains("\"" + key + "\"")) {
				missing.add("en_us " + key);
			}
		}
		assertEquals(List.of(), missing);
	}

	@Test
	void everyCounterBlockHasStateModelItemLootAndTextures() {
		List<String> missing = new ArrayList<>();
		for (String block : List.of("mailbox", "museum_pedestal", "guild_board")) {
			List<Path> files = new ArrayList<>(List.of(ASSETS.resolve("blockstates/" + block + ".json"), ASSETS.resolve("models/block/" + block + ".json"),
					ASSETS.resolve("items/" + block + ".json"), DATA.resolve("loot_table/blocks/" + block + ".json")));
			for (String face : List.of("front", "side", "top")) {
				files.add(ASSETS.resolve("textures/block/" + block + "_" + face + ".png"));
			}
			for (Path p : files) {
				if (!Files.exists(p)) {
					missing.add(p.toString());
				}
			}
		}
		if (!Files.exists(ASSETS.resolve("textures/entity/village_guard.png"))) {
			missing.add("textures/entity/village_guard.png");
		}
		assertEquals(List.of(), missing);
	}
}
