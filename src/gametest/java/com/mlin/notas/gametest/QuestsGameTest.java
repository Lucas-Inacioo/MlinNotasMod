package com.mlin.notas.gametest;

import com.mlin.notas.quests.Quest;
import com.mlin.notas.quests.QuestBook;
import com.mlin.notas.quests.QuestData;
import com.mlin.notas.quests.QuestProgress;
import com.mlin.notas.quests.QuestReward;
import com.mlin.notas.quests.QuestServer;
import com.mlin.notas.quests.QuestTask;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;

/**
 * Game tests do livro de quests. O progresso e do mundo, entao o fluxo roda num teste so, no ambiente proprio
 * {@code mlinnotas-gametest:quests} (lote separado), com um livro de teste trocado no lugar do real e devolvido no fim.
 */
public class QuestsGameTest {
	private static final String ENV = "mlinnotas-gametest:quests";

	private static int count(ServerPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Os ids do livro de verdade existem no jogo (itens, tags, entidades, estruturas, biomas, dimensoes, conquistas). */
	@GameTest
	public void shippedBookTargetsExist(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		QuestBook book = QuestServer.loadBook(server.getResourceManager());
		helper.assertTrue(book.chapters().size() == 8 && book.quests().size() >= 50, "livro carregado: " + book.quests().size());
		helper.assertTrue(book.validate().isEmpty(), "livro valido: " + book.validate());
		Registry<Structure> structures = server.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		var biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
		List<String> missing = new ArrayList<>();
		for (Quest quest : book.quests()) {
			checkItem(missing, quest.key() + " icone", quest.icon());
			for (QuestReward reward : quest.rewards()) {
				if (reward.isItem()) {
					checkItem(missing, quest.key() + " recompensa", reward.item());
				}
			}
			for (QuestTask task : quest.tasks()) {
				String where = quest.key() + " " + task.type().id();
				Identifier id = Identifier.tryParse(task.targetId());
				if (!task.icon().isEmpty()) {
					checkItem(missing, where + " icone", task.icon());
				}
				switch (task.type()) {
					case ITEM -> {
						if (task.isTag()) {
							if (BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id)).map(s -> s.size() == 0).orElse(true)) {
								missing.add(where + ": tag vazia " + task.target());
							}
						} else {
							checkItem(missing, where, task.target());
						}
					}
					case KILL -> {
						if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
							missing.add(where + ": " + task.target());
						}
					}
					case STRUCTURE -> {
						if (task.isTag() ? structures.get(TagKey.create(Registries.STRUCTURE, id)).isEmpty() : !structures.containsKey(id)) {
							missing.add(where + ": " + task.target());
						}
					}
					case BIOME -> {
						if (task.isTag() ? biomes.get(TagKey.create(Registries.BIOME, id)).map(s -> s.size() == 0).orElse(true) : !biomes.containsKey(id)) {
							missing.add(where + ": " + task.target());
						}
					}
					case DIMENSION -> {
						if (server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) == null) {
							missing.add(where + ": " + task.target());
						}
					}
					case ADVANCEMENT -> {
						if (server.getAdvancements().get(id) == null) {
							missing.add(where + ": " + task.target());
						}
					}
					default -> {
					}
				}
			}
		}
		helper.assertTrue(missing.isEmpty(), "ids inexistentes: " + missing);
		helper.succeed();
	}

	private static void checkItem(List<String> missing, String where, String id) {
		Identifier parsed = Identifier.tryParse(id);
		if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)) {
			missing.add(where + ": " + id);
		}
	}

	/** Gera uma estrutura perto do teste e registra o inicio nos chunks (como o worldgen faria); devolve o centro de uma peca. */
	private static BlockPos fakeStructure(ServerLevel level, BlockPos near, String structureId) {
		Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder.Reference<Structure> holder = registry.get(Identifier.parse(structureId)).orElseThrow();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		ChunkPos origin = ChunkPos.containing(near);
		StructureStart start = holder.value().generate(holder, level.dimension(), level.registryAccess(), generator, generator.getBiomeSource(),
				randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED), randomState, level.getStructureTemplateManager(), level.getSeed(),
				origin, 0, level, b -> true);
		if (!start.isValid()) {
			return null;
		}
		level.getChunk(origin.x(), origin.z()).setStartForStructure(holder.value(), start);
		BoundingBox box = start.getBoundingBox();
		long reference = origin.pack();
		for (int cx = SectionPos.blockToSectionCoord(box.minX()); cx <= SectionPos.blockToSectionCoord(box.maxX()); cx++) {
			for (int cz = SectionPos.blockToSectionCoord(box.minZ()); cz <= SectionPos.blockToSectionCoord(box.maxZ()); cz++) {
				level.getChunk(cx, cz).addReferenceForStructure(holder.value(), reference);
			}
		}
		return start.getPieces().getFirst().getBoundingBox().getCenter();
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void sharedQuestFlow(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		int miningBefore = SkillServer.progress(server).level(SkillBranch.MINING);
		ServerPlayer alice = SkillsGameTest.survivalPlayer(helper, "quests-a");
		ServerPlayer bob = SkillsGameTest.survivalPlayer(helper, "quests-b");
		try {
			// livro na primeira entrada (uma vez so)
			helper.assertTrue(count(alice, ModItems.QUEST_BOOK) == 1, "alice ganhou o livro ao entrar");
			QuestServer.giveBookOnce(alice);
			helper.assertTrue(count(alice, ModItems.QUEST_BOOK) == 1, "o livro nao e dado de novo");

			// estrutura de teste: tenta uma ruina do mod, cai para a piramide do deserto se o terreno nao servir
			String structureId = "mlinnotas:old_well";
			BlockPos inside = fakeStructure(level, helper.absolutePos(new BlockPos(3, 1, 3)).offset(64, 0, 64), structureId);
			if (inside == null) {
				structureId = "minecraft:desert_pyramid";
				inside = fakeStructure(level, helper.absolutePos(new BlockPos(3, 1, 3)).offset(64, 0, 64), structureId);
			}
			helper.assertTrue(inside != null, "estrutura de teste gerada");

			QuestBook book = new QuestBook(List.of(QuestBook.parseChapter("qtest", """
					{"quests":[
					  {"id":"start","x":0,"tasks":[{"type":"checkmark"}],"rewards":[{"item":"minecraft:bread","count":3},{"xp":7}]},
					  {"id":"gather","x":1,"dependencies":["start"],"tasks":[
					    {"type":"item","target":"#minecraft:logs","count":4},
					    {"type":"kill","target":"minecraft:zombie","count":2}]},
					  {"id":"deliver","x":2,"dependencies":["start"],"tasks":[{"type":"item","target":"minecraft:coal","count":5,"consume":true}]},
					  {"id":"place","x":3,"dependencies":["start"],"tasks":[
					    {"type":"dimension","target":"minecraft:overworld"},
					    {"type":"biome","target":"#minecraft:is_overworld"},
					    {"type":"structure","target":"STRUCTURE"}]},
					  {"id":"skill","x":4,"dependencies":["start"],"tasks":[{"type":"skill","target":"mining","level":3}]},
					  {"id":"done","x":5,"dependencies":["gather","deliver","place","skill"],"rewards":[{"item":"minecraft:diamond","count":1}]}
					]}""".replace("STRUCTURE", structureId))));
			helper.assertTrue(book.validate().isEmpty(), "livro de teste valido: " + book.validate());
			QuestServer.setBook(server, book);
			QuestServer.reset(server);
			SkillServer.setLevel(server, SkillBranch.MINING, 1);
			QuestProgress progress = QuestData.get(server).progress();
			Quest start = book.quest("qtest/start");
			Quest gather = book.quest("qtest/gather");
			Quest deliver = book.quest("qtest/deliver");
			Quest place = book.quest("qtest/place");
			Quest skill = book.quest("qtest/skill");
			Quest done = book.quest("qtest/done");

			// 1) bloqueada nao anda
			alice.getInventory().add(new ItemStack(Items.OAK_LOG, 4));
			QuestServer.checkPeriodic(server);
			helper.assertTrue(progress.progress(gather, 0) == 0, "quest bloqueada nao conta itens");

			// 2) checkmark do bob completa para o mundo; cada um resgata a sua
			helper.assertTrue(QuestServer.check(bob, start), "bob marca");
			helper.assertTrue(progress.isComplete(start.key()), "start completa");
			int xpBefore = alice.totalExperience;
			helper.assertTrue(QuestServer.claim(alice, start), "alice resgata");
			helper.assertTrue(count(alice, Items.BREAD) == 3 && alice.totalExperience == xpBefore + 7, "recompensa da alice");
			helper.assertTrue(!QuestServer.claim(alice, start), "alice nao resgata duas vezes");
			helper.assertTrue(QuestServer.claim(bob, start), "bob resgata a dele");
			helper.assertTrue(count(bob, Items.BREAD) == 3, "recompensa do bob");

			// 3) ter itens (checagem periodica) + matar (evento de morte)
			QuestServer.checkPeriodic(server);
			helper.assertTrue(progress.progress(gather, 0) == 4, "troncos contados: " + progress.progress(gather, 0));
			for (int i = 0; i < 2; i++) {
				Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
				zombie.hurtServer(level, level.damageSources().playerAttack(alice), 1000.0F);
			}
			helper.assertTrue(progress.isComplete(gather.key()), "gather completa com 2 zumbis: " + progress.progress(gather, 1));

			// 4) entrega parcial e entrega que completa (so tira o que falta)
			alice.getInventory().add(new ItemStack(Items.COAL, 3));
			bob.getInventory().add(new ItemStack(Items.COAL, 10));
			helper.assertTrue(QuestServer.submit(alice, deliver), "alice entrega");
			helper.assertTrue(count(alice, Items.COAL) == 0 && progress.progress(deliver, 0) == 3, "3 entregues");
			helper.assertTrue(QuestServer.submit(bob, deliver), "bob entrega");
			helper.assertTrue(count(bob, Items.COAL) == 8 && progress.isComplete(deliver.key()), "bob pagou so 2");
			helper.assertTrue(!QuestServer.submit(bob, deliver), "quest completa nao aceita mais");

			// 5) dimensao e bioma ja batem; estrutura so quando alguem entra nela
			QuestServer.checkPeriodic(server);
			helper.assertTrue(progress.taskDone(place, 0) && progress.taskDone(place, 1), "dimensao e bioma");
			helper.assertTrue(!progress.taskDone(place, 2), "fora da estrutura");
			bob.teleportTo(inside.getX() + 0.5, inside.getY(), inside.getZ() + 0.5);
			QuestServer.checkPeriodic(server);
			helper.assertTrue(progress.isComplete(place.key()), "bob dentro de " + structureId);

			// 6) nivel da arvore de habilidades; o marco final fecha sozinho
			helper.assertTrue(!progress.isComplete(skill.key()), "Mineracao ainda em Madeira");
			SkillServer.setLevel(server, SkillBranch.MINING, 3);
			QuestServer.checkPeriodic(server);
			helper.assertTrue(progress.isComplete(skill.key()) && progress.isComplete(done.key()), "habilidade + marco final");
			helper.assertTrue(progress.view(done, alice.getStringUUID()) == QuestProgress.View.CLAIMABLE, "diamante para resgatar");

			// 7) persistencia: o SavedData volta igual
			Tag saved = QuestData.CODEC.encodeStart(NbtOps.INSTANCE, QuestData.get(server)).getOrThrow();
			QuestData loaded = QuestData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			helper.assertTrue(loaded.progress().completedList().equals(progress.completedList()), "completas salvas");
			helper.assertTrue(loaded.progress().hasClaimed(bob.getStringUUID(), start) && !loaded.progress().hasClaimed(bob.getStringUUID(), done),
					"resgates salvos por jogador");
			helper.assertTrue(!loaded.markBookGiven(alice.getStringUUID()), "quem ganhou o livro fica salvo");

			// 8) comando de operador: reset zera tudo
			QuestServer.reset(server);
			helper.assertTrue(progress.completedList().isEmpty(), "reset");
			helper.assertTrue(QuestServer.forceComplete(server, done).size() == 6, "complete puxa as dependencias");
		} finally {
			server.getPlayerList().remove(alice);
			server.getPlayerList().remove(bob);
			QuestServer.reset(server);
			SkillServer.setLevel(server, SkillBranch.MINING, miningBefore);
			QuestServer.reload(server, server.getResourceManager());
		}
		helper.succeed();
	}
}
