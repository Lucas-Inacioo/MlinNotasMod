package com.mlin.notas.quests;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Lado servidor do livro de quests: carrega {@code data/<ns>/quests/*.json} ao subir e no {@code /reload}, detecta
 * tarefas (mortes por evento; inventario, estrutura, bioma, dimensao, habilidade e conquista a cada segundo, so nas
 * quests ativas), trata entregas/checkmarks/resgates pedidos pelo cliente, anuncia quests completas e sincroniza.
 */
public final class QuestServer {
	/** Intervalo da checagem periodica (ticks). */
	public static final int CHECK_INTERVAL = 20;

	private static QuestBook book = QuestBook.EMPTY;

	private QuestServer() {
	}

	public static QuestBook book() {
		return book;
	}

	public static QuestProgress progress(MinecraftServer server) {
		return QuestData.get(server).progress();
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(QuestPayloads.Book.TYPE, QuestPayloads.Book.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(QuestPayloads.Progress.TYPE, QuestPayloads.Progress.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(QuestPayloads.Completed.TYPE, QuestPayloads.Completed.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(QuestPayloads.Action.TYPE, QuestPayloads.Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(QuestPayloads.Action.TYPE, (payload, context) -> {
			Quest quest = book.quest(payload.quest());
			if (quest == null) {
				return;
			}
			switch (payload.action()) {
				case QuestPayloads.Action.SUBMIT -> submit(context.player(), quest);
				case QuestPayloads.Action.CHECK -> check(context.player(), quest);
				case QuestPayloads.Action.CLAIM -> claim(context.player(), quest);
				default -> {
				}
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(server -> reload(server, server.getResourceManager()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				reload(server, resourceManager);
			}
		});
		ServerPlayerEvents.JOIN.register(player -> {
			giveBookOnce(player);
			syncBook(player);
			sync(player);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_INTERVAL == 0) {
				checkPeriodic(server);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof EnderDragon || !(entity.level() instanceof ServerLevel level)) {
				return; // o dragao quase nunca passa por die(): contado no ENTITY_UNLOAD
			}
			Player killer = source.getEntity() instanceof Player p ? p : entity.getLastHurtByPlayer();
			if (killer instanceof ServerPlayer player) {
				onKill(level.getServer(), player, entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof EnderDragon dragon && entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
				onKill(level.getServer(), null, dragon);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> QuestCommand.register(dispatcher));
	}

	// ---- carregamento ----

	/** Le todos os capitulos de {@code data/<ns>/quests/<capitulo>.json}. */
	public static void reload(MinecraftServer server, ResourceManager resources) {
		setBook(server, loadBook(resources));
	}

	/** Monta o livro a partir dos arquivos (capitulo com erro de JSON e ignorado e logado). */
	public static QuestBook loadBook(ResourceManager resources) {
		Map<Identifier, Resource> files = new TreeMap<>(resources.listResources("quests", id -> id.getPath().endsWith(".json")));
		List<QuestChapter> chapters = new ArrayList<>();
		for (Map.Entry<Identifier, Resource> entry : files.entrySet()) {
			String path = entry.getKey().getPath();
			String id = path.substring("quests/".length(), path.length() - ".json".length());
			try (Reader reader = entry.getValue().openAsReader()) {
				chapters.add(QuestBook.parseChapter(id, readAll(reader)));
			} catch (IOException | RuntimeException e) {
				MlinNotas.LOGGER.error("[quests] capitulo {} ignorado: {}", entry.getKey(), e.getMessage());
			}
		}
		return new QuestBook(chapters);
	}

	/** Troca o livro (reload, testes), valida, completa marcos e sincroniza com todos. */
	public static void setBook(MinecraftServer server, QuestBook value) {
		book = value;
		for (String error : value.validate()) {
			MlinNotas.LOGGER.warn("[quests] {}", error);
		}
		MlinNotas.LOGGER.info("[quests] {} capitulos, {} quests", value.chapters().size(), value.quests().size());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			syncBook(player);
		}
		changed(server, null);
	}

	private static String readAll(Reader reader) throws IOException {
		StringBuilder out = new StringBuilder();
		char[] buffer = new char[4096];
		int n;
		while ((n = reader.read(buffer)) > 0) {
			out.append(buffer, 0, n);
		}
		return out.toString();
	}

	// ---- livro na primeira entrada ----

	public static void giveBookOnce(ServerPlayer player) {
		if (QuestData.get(player.level().getServer()).markBookGiven(player.getStringUUID())) {
			give(player, new ItemStack(ModItems.QUEST_BOOK));
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// ---- deteccao ----

	/** Checagem periodica: o que da para ver sem evento (inventario, posicao, habilidades, conquistas). */
	public static void checkPeriodic(MinecraftServer server) {
		QuestProgress progress = progress(server);
		List<Quest> active = progress.active(book);
		if (active.isEmpty()) {
			return;
		}
		boolean dirty = false;
		ServerPlayer credit = null;
		for (Quest quest : active) {
			for (int i = 0; i < quest.tasks().size(); i++) {
				QuestTask task = quest.tasks().get(i);
				if (progress.taskDone(quest, i)) {
					continue;
				}
				if (task.type() == QuestTask.Type.SKILL) {
					SkillBranch branch = SkillBranch.byId(task.target());
					if (branch != null && SkillServer.progress(server).level(branch) >= task.level() && progress.finish(quest, i)) {
						dirty = true;
					}
					continue;
				}
				if (!task.isPossession() && !task.type().periodic()) {
					continue;
				}
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					if (player.isSpectator()) {
						continue;
					}
					boolean hit = task.isPossession() ? progress.raise(quest, i, count(player, task.target()))
							: meets(player, task) && progress.finish(quest, i);
					if (hit) {
						dirty = true;
						credit = credit == null ? player : credit;
					}
					if (progress.taskDone(quest, i)) {
						break;
					}
				}
			}
		}
		if (dirty) {
			changed(server, credit);
		}
	}

	/** Tarefas de sim/nao que dependem do jogador. */
	static boolean meets(ServerPlayer player, QuestTask task) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		return switch (task.type()) {
			case DIMENSION -> level.dimension().identifier().toString().equals(task.target());
			case BIOME -> matches(level.getBiome(pos), Registries.BIOME, task);
			case STRUCTURE -> level.structureManager().getStructureWithPieceAt(pos, holder -> matches(holder, Registries.STRUCTURE, task)).isValid();
			case ADVANCEMENT -> {
				AdvancementHolder advancement = level.getServer().getAdvancements().get(Identifier.parse(task.target()));
				yield advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
			}
			default -> false;
		};
	}

	private static <T> boolean matches(Holder<T> holder, ResourceKey<? extends net.minecraft.core.Registry<T>> registry, QuestTask task) {
		Identifier id = Identifier.tryParse(task.targetId());
		if (id == null) {
			return false;
		}
		return task.isTag() ? holder.is(TagKey.create(registry, id)) : holder.is(id);
	}

	/** Quanto o jogador tem do item/tag no inventario. */
	public static int count(ServerPlayer player, String target) {
		return ContainerHelper.clearOrCountMatchingItems(player.getInventory(), SkillServer.matcher(target), Integer.MAX_VALUE, true);
	}

	/** Uma morte creditada a {@code player} (null = o mundo, ex.: dragao). */
	public static void onKill(MinecraftServer server, ServerPlayer player, LivingEntity entity) {
		QuestProgress progress = progress(server);
		Holder<EntityType<?>> type = entity.getType().builtInRegistryHolder();
		boolean dirty = false;
		for (Quest quest : progress.active(book)) {
			for (int i = 0; i < quest.tasks().size(); i++) {
				QuestTask task = quest.tasks().get(i);
				if (task.type() == QuestTask.Type.KILL && matches(type, Registries.ENTITY_TYPE, task) && progress.add(quest, i, 1) > 0) {
					dirty = true;
				}
			}
		}
		if (dirty) {
			changed(server, player);
		}
	}

	// ---- acoes do jogador ----

	/** Entrega os itens das tarefas de entrega (o que o jogador tiver, ate o que falta). */
	public static boolean submit(ServerPlayer player, Quest quest) {
		MinecraftServer server = player.level().getServer();
		QuestProgress progress = progress(server);
		if (progress.status(quest) != QuestProgress.Status.ACTIVE) {
			return false;
		}
		boolean dirty = false;
		for (int i = 0; i < quest.tasks().size(); i++) {
			QuestTask task = quest.tasks().get(i);
			if (task.isPossession()) {
				dirty |= progress.raise(quest, i, count(player, task.target()));
			}
			if (!task.isDelivery() || progress.taskDone(quest, i)) {
				continue;
			}
			Predicate<ItemStack> matcher = SkillServer.matcher(task.target());
			int take = Math.min(progress.remaining(quest, i), ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher, Integer.MAX_VALUE, true));
			if (take > 0) {
				ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher, take, false);
				player.getInventory().setChanged();
				progress.add(quest, i, take);
				dirty = true;
			}
		}
		if (dirty) {
			changed(server, player);
		} else {
			notify(player, Component.translatable("message.mlinnotas.quests.nothing_to_submit").withStyle(ChatFormatting.RED));
		}
		return dirty;
	}

	/** Marca as tarefas de checkmark da quest. */
	public static boolean check(ServerPlayer player, Quest quest) {
		MinecraftServer server = player.level().getServer();
		QuestProgress progress = progress(server);
		boolean dirty = false;
		for (int i = 0; i < quest.tasks().size(); i++) {
			if (quest.tasks().get(i).type() == QuestTask.Type.CHECKMARK) {
				dirty |= progress.finish(quest, i);
			}
		}
		if (dirty) {
			changed(server, player);
		}
		return dirty;
	}

	/** Resgata a recompensa do jogador (uma vez por jogador). */
	public static boolean claim(ServerPlayer player, Quest quest) {
		MinecraftServer server = player.level().getServer();
		QuestData data = QuestData.get(server);
		if (!data.progress().claim(player.getStringUUID(), quest)) {
			return false;
		}
		data.setDirty();
		for (QuestReward reward : quest.rewards()) {
			if (reward.isItem()) {
				Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(reward.item())).orElse(null);
				if (item != null) {
					give(player, new ItemStack(item, reward.count()));
				}
			} else if (reward.xp() > 0) {
				player.giveExperiencePoints(reward.xp());
			}
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.2F);
		notify(player, Component.translatable("message.mlinnotas.quests.claimed", Component.translatable(quest.titleKey())).withStyle(ChatFormatting.GREEN));
		sync(player);
		return true;
	}

	private static void notify(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendOverlayMessage(message);
		}
	}

	// ---- mudancas, anuncio e sincronizacao ----

	/** Progresso mudou: completa o que fechou, anuncia e sincroniza com todos. */
	public static void changed(MinecraftServer server, ServerPlayer credit) {
		QuestData data = QuestData.get(server);
		data.setDirty();
		List<Quest> done = data.progress().settle(book);
		announce(server, done, credit);
		syncAll(server);
	}

	static void announce(MinecraftServer server, List<Quest> done, ServerPlayer credit) {
		if (done.isEmpty()) {
			return;
		}
		for (Quest quest : done) {
			Component title = Component.translatable(quest.titleKey());
			Component message = credit == null
					? Component.translatable("message.mlinnotas.quests.completed_world", title)
					: Component.translatable("message.mlinnotas.quests.completed", credit.getDisplayName(), title);
			server.getPlayerList().broadcastSystemMessage(message.copy().withStyle(ChatFormatting.GOLD), false);
		}
		QuestPayloads.Completed payload = new QuestPayloads.Completed(done.stream().map(Quest::key).toList());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.connection != null && ServerPlayNetworking.canSend(player, QuestPayloads.Completed.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	/** Completa a quest (e as dependencias que faltam) pelo comando de operador. */
	public static List<Quest> forceComplete(MinecraftServer server, Quest quest) {
		QuestData data = QuestData.get(server);
		List<Quest> done = new ArrayList<>(data.progress().forceComplete(book, quest));
		data.setDirty();
		done.addAll(data.progress().settle(book));
		announce(server, done, null);
		syncAll(server);
		return done;
	}

	/** Volta o livro do mundo ao inicio (progresso e resgates). */
	public static void reset(MinecraftServer server) {
		QuestData.get(server).reset();
		changed(server, null);
	}

	public static void syncBook(ServerPlayer player) {
		if (player.connection != null && ServerPlayNetworking.canSend(player, QuestPayloads.Book.TYPE)) {
			ServerPlayNetworking.send(player, QuestPayloads.Book.of(book));
		}
	}

	public static void sync(ServerPlayer player) {
		if (player.connection != null && ServerPlayNetworking.canSend(player, QuestPayloads.Progress.TYPE)) {
			ServerPlayNetworking.send(player, QuestPayloads.Progress.of(progress(player.level().getServer()), player.getStringUUID()));
		}
	}

	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player);
		}
	}
}
