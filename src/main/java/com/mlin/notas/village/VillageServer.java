package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.waystone.WaystoneNames;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Lado servidor das vilas: quadro de missoes (abrir, entregar projetos, missoes do dia, mercado), fila de obras e
 * construcao aos poucos, contagem de cacadas/exploracoes, subida de nivel (golens, mensagem) e os efeitos nos aldeoes
 * (teto de nivel de profissao e desconto, aplicados pelo mixin de {@code Villager}).
 */
public final class VillageServer {
	/** Mundo novo nasce num acampamento (vila inicial). Desligue na criacao do mundo para nascer no spawn vanilla. */
	public static final GameRule<Boolean> STARTING_VILLAGE = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC)
			.buildAndRegister(MlinNotas.id("starting_village"));
	/** O jogador ja foi levado ao acampamento na primeira entrada. */
	public static final AttachmentType<Boolean> GREETED = AttachmentRegistry.create(MlinNotas.id("village_greeted"),
			b -> b.persistent(Codec.BOOL));

	/** Quadro a ate esta distancia do centro de uma vila pertence a ela. */
	public static final int JOIN_RADIUS = 48;
	public static final double USE_RANGE = 8.0;
	/** Aldeoes ate esta distancia alem do raio da vila contam como moradores. */
	public static final int RESIDENT_MARGIN = 16;

	private static final Map<String, Long> RETRY_AT = new HashMap<>();
	private static final Set<String> WAITING = new java.util.HashSet<>();
	private static final Map<UUID, BlockPos> VIEWING = new HashMap<>();
	private static final TagKey<Structure> RUIN_TAG = TagKey.create(Registries.STRUCTURE, MlinNotas.id("on_village_maps/ruins"));
	private static final TagKey<Structure> SHRINE_TAG = TagKey.create(Registries.STRUCTURE, MlinNotas.id("on_village_maps/cobalt_shrine"));
	private static final TagKey<Structure> OUTPOST_TAG = TagKey.create(Registries.STRUCTURE, MlinNotas.id("village_bounty/outpost"));

	private VillageServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(VillagePayloads.View.TYPE, VillagePayloads.View.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(VillagePayloads.Action.TYPE, VillagePayloads.Action.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VillagePayloads.Overlay.TYPE, VillagePayloads.Overlay.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(VillagePayloads.Action.TYPE, (payload, context) -> action(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(VillageServer::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(VillageServer::onDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RETRY_AT.clear();
			VillageTerrain.clearCache();
			WAITING.clear();
			VIEWING.clear();
		});
		ServerPlayerEvents.JOIN.register(StartingVillage::greet);
		VillageBoardFeature.init();
		com.mlin.notas.village.works.VillageWorks.init();
		StartingVillage.init();
		com.mlin.notas.village.civic.CivicServer.init();
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> VillageCommand.register(dispatcher));
	}

	public static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	public static BlockPos center(VillageState village) {
		return new BlockPos(village.x(), village.y(), village.z());
	}

	public static ServerLevel levelOf(MinecraftServer server, VillageState village) {
		Identifier id = Identifier.tryParse(village.dimension());
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	// ---------------------------------------------------------------- vilas

	/** Vila cujo centro fica a ate {@code radius} de {@code pos} (a mais perto), ou null. */
	public static VillageState nearest(ServerLevel level, BlockPos pos, int radius) {
		String dim = dimension(level);
		VillageState best = null;
		long bestD = (long) radius * radius;
		for (VillageState village : VillageData.get(level.getServer()).villages()) {
			if (!village.dimension().equals(dim)) {
				continue;
			}
			long d = village.distanceSq(pos.getX(), pos.getZ());
			if (d <= bestD) {
				bestD = d;
				best = village;
			}
		}
		return best;
	}

	/** Vila onde {@code pos} mora (dentro do raio do nivel + margem). */
	public static VillageState villageAt(ServerLevel level, BlockPos pos) {
		String dim = dimension(level);
		for (VillageState village : VillageData.get(level.getServer()).villages()) {
			if (village.dimension().equals(dim)) {
				long r = village.level().radius() + RESIDENT_MARGIN;
				if (village.distanceSq(pos.getX(), pos.getZ()) <= r * r && Math.abs(pos.getY() - village.y()) < 64) {
					return village;
				}
			}
		}
		return null;
	}

	/** Teto de nivel de profissao dos aldeoes em {@code pos} (fora de vila fundada: {@link VillageLevel#WILD_TRADE_CAP}). */
	public static int tradeCap(ServerLevel level, BlockPos pos) {
		VillageState village = villageAt(level, pos);
		return village == null ? VillageLevel.WILD_TRADE_CAP : village.level().tradeCap();
	}

	/** Esmeraldas de desconto nas trocas com aldeoes em {@code pos}. */
	public static int discount(ServerLevel level, BlockPos pos) {
		VillageState village = villageAt(level, pos);
		return village == null ? 0 : village.level().discount();
	}

	/** Cria a vila com centro no quadro {@code board} (estilo pelo bioma; vila vanilla ja comeca como Aldeia). */
	public static VillageState create(ServerLevel level, BlockPos board, boolean starter) {
		long seed = level.getSeed() ^ board.asLong() * 0x5DEECE66DL;
		String biome = level.getBiome(board).unwrapKey().map(k -> k.identifier().toString()).orElse(null);
		VillageState village = new VillageState(dimension(level), board.getX(), board.getY(), board.getZ(), seed, VillageStyle.forBiome(biome),
				WaystoneNames.generate(seed));
		village.setStarter(starter);
		if (!starter && level.structureManager().getStructureWithPieceAt(board, StructureTags.VILLAGE).isValid()) {
			// vila do worldgen: ja tem casas e moradores
			village.setLevel(VillageLevel.HAMLET);
			village.addProsperity(VillageLevel.HAMLET.prosperity());
		}
		village.refreshBounties(day(level.getServer()));
		VillageData.get(level.getServer()).add(village);
		return village;
	}

	public static long day(MinecraftServer server) {
		return server.overworld().getOverworldClockTime() / 24000L;
	}

	// ---------------------------------------------------------------- quadro

	/** Botao direito no quadro: acha (ou funda) a vila e abre a tela. */
	public static void use(ServerPlayer player, ServerLevel level, BlockPos board) {
		VillageState village = nearest(level, board, JOIN_RADIUS);
		if (village == null) {
			village = create(level, board, false);
			player.sendSystemMessage(Component.translatable("message.mlinnotas.village.founded", village.name()).withStyle(ChatFormatting.GOLD));
			VillagePlanning.ensureLayout(level, village);
			VillagePlanning.showNearby(level, village, VillagePlanning.AUTO_SECONDS, Component.translatable("message.mlinnotas.village.area_shown",
					VillagePlanning.AUTO_SECONDS).withStyle(ChatFormatting.AQUA));
		}
		if (village.refreshBounties(day(level.getServer()))) {
			VillageData.get(level.getServer()).changed();
		}
		VillagePlanning.ensureLayout(level, village);
		VillageLevel up = VillageTerrain.refresh(level, village);
		if (up != null) {
			levelUp(level.getServer(), village, up);
		}
		BuildingServer.count(level, village);
		VIEWING.put(player.getUUID(), board);
		send(player, village, board, true);
	}

	private static void send(ServerPlayer player, VillageState village, BlockPos board, boolean open) {
		if (player.connection == null || !ServerPlayNetworking.canSend(player, VillagePayloads.View.TYPE)) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		VillageData.Job job = VillageData.get(server).job(village.key());
		ServerPlayNetworking.send(player, new VillagePayloads.View(village, board, job == null ? "" : job.project.id(),
				job == null ? 0 : job.index, job == null ? 0 : job.total(), WAITING.contains(village.key()), day(server), open));
	}

	/** Atualiza a tela de quem esta olhando um quadro desta vila. */
	public static void sync(MinecraftServer server, VillageState village) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			BlockPos board = VIEWING.get(player.getUUID());
			if (board != null && dimension(player.level()).equals(village.dimension()) && village.distanceSq(board.getX(), board.getZ())
					<= (long) JOIN_RADIUS * JOIN_RADIUS) {
				send(player, village, board, false);
			}
		}
	}

	private static void action(ServerPlayer player, VillagePayloads.Action payload) {
		ServerLevel level = player.level();
		BlockPos board = payload.board();
		double range = switch (payload.action()) {
			case "place", "pick", "overlay" -> VillagePlanning.SHOW_RANGE;
			default -> USE_RANGE;
		};
		if (!player.blockPosition().closerThan(board, range) || !(level.getBlockState(board).getBlock() instanceof VillageBoardBlock)) {
			return;
		}
		VillageState village = nearest(level, board, JOIN_RADIUS);
		if (village == null) {
			return;
		}
		switch (payload.action()) {
			case "deposit" -> {
				VillageProject project = VillageProject.byId(payload.arg());
				if (project != null) {
					deposit(player, village, project);
				}
			}
			case "bounty" -> {
				try {
					bounty(player, village, Integer.parseInt(payload.arg()));
				} catch (NumberFormatException ignored) {
					// pedido invalido
				}
			}
			case "buy" -> buy(player, village, payload.arg());
			case "terrain" -> {
				try {
					VillageTerrain.deposit(player, village, Integer.parseInt(payload.arg()));
				} catch (NumberFormatException ignored) {
					// pedido invalido
				}
			}
			case "overlay" -> VillagePlanning.show(player, village, "off".equals(payload.arg()) ? 0 : VillagePlanning.TOGGLE_SECONDS);
			case "pick" -> VillagePlanning.pick(player, village, payload.arg());
			case "place" -> VillagePlanning.place(player, village, payload.arg());
			case "works" -> com.mlin.notas.village.works.VillageWorks.action(player, village, payload.arg());
			case "cancel" -> {
				try {
					VillagePlanning.cancel(player, village, board, Integer.parseInt(payload.arg()));
				} catch (NumberFormatException ignored) {
					// pedido invalido
				}
			}
			case "demolish" -> {
				try {
					VillagePlanning.demolish(player, village, board, Integer.parseInt(payload.arg()));
				} catch (NumberFormatException ignored) {
					// pedido invalido
				}
			}
			case "upgrade", "cancel_upgrade" -> {
				try {
					int lot = Integer.parseInt(payload.arg());
					if (payload.action().equals("upgrade")) {
						BuildingServer.deposit(player, village, lot);
					} else {
						BuildingServer.cancel(player, village, board, lot);
					}
				} catch (NumberFormatException ignored) {
					// pedido invalido
				}
			}
			default -> {
			}
		}
	}

	public static Component projectName(VillageProject project) {
		return Component.translatable("village.mlinnotas.project." + project.id());
	}

	public static Component levelName(VillageLevel level) {
		return Component.translatable("village.mlinnotas.level." + level.id());
	}

	private static void notify(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendOverlayMessage(message);
		}
	}

	/** Entrega para o projeto o que o jogador tiver do que falta. Completou: entra na fila de obras. */
	public static VillageState.Outcome deposit(ServerPlayer player, VillageState village, VillageProject project) {
		MinecraftServer server = player.level().getServer();
		Map<String, Integer> available = new LinkedHashMap<>();
		for (VillageCost cost : project.costs()) {
			available.put(cost.id(), ContainerHelper.clearOrCountMatchingItems(player.getInventory(), SkillServer.matcher(cost.id()), Integer.MAX_VALUE, true));
		}
		VillageState.Deposit result = village.deposit(project, available);
		if (result.accepted()) {
			Map<String, Integer> items = new LinkedHashMap<>();
			result.taken().forEach((id, count) -> VillagePlanning.take(player, id, count).forEach((item, n) -> items.merge(item, n, Integer::sum)));
			village.recordPaid(project, items, result.outcome() == VillageState.Outcome.COMPLETE);
			player.getInventory().setChanged();
			VillageData.get(server).changed();
		}
		Component name = projectName(project);
		switch (result.outcome()) {
			case COMPLETE -> {
				server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.project_funded", player.getDisplayName(),
						name, village.name()).withStyle(ChatFormatting.GOLD), false);
				player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
				RETRY_AT.remove(village.key());
			}
			case PARTIAL -> notify(player, Component.translatable("message.mlinnotas.village.deposited", name));
			case NOTHING -> notify(player, Component.translatable("message.mlinnotas.village.nothing", name));
			case LOCKED -> notify(player, Component.translatable("message.mlinnotas.village.locked", name, levelName(project.minLevel())));
			case MAXED -> notify(player, Component.translatable("message.mlinnotas.village.maxed", name));
		}
		sync(server, village);
		return result.outcome();
	}

	private static Item item(String id) {
		Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
		return item == null ? Items.AIR : item;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			net.minecraft.world.level.block.Block.popResource(player.level(), player.blockPosition(), stack);
		}
	}

	/** Entregar (itens) ou receber (cacada/exploracao pronta) a missao {@code index}. */
	public static VillageState.BountyOutcome bounty(ServerPlayer player, VillageState village, int index) {
		MinecraftServer server = player.level().getServer();
		if (village.refreshBounties(day(server))) {
			VillageData.get(server).changed();
		}
		if (index < 0 || index >= village.bounties().size()) {
			return VillageState.BountyOutcome.INVALID;
		}
		Bounty bounty = village.bounties().get(index);
		int available = 0;
		if (bounty.kind() == Bounty.Kind.DELIVER) {
			available = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), SkillServer.matcher(bounty.target()), Integer.MAX_VALUE, true);
		}
		VillageState.BountyResult result = village.claim(index, available);
		if (result.taken() > 0) {
			ContainerHelper.clearOrCountMatchingItems(player.getInventory(), SkillServer.matcher(bounty.target()), result.taken(), false);
			player.getInventory().setChanged();
		}
		switch (result.outcome()) {
			case PAID -> {
				give(player, new ItemStack(Items.EMERALD, bounty.emeralds()));
				Item reward = item(bounty.rewardItem());
				if (reward != Items.AIR) {
					give(player, new ItemStack(reward, bounty.rewardCount()));
				}
				VillageLevel up = village.addProsperity(bounty.prosperity());
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
				server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.bounty_done", player.getDisplayName(),
						village.name(), bounty.prosperity()).withStyle(ChatFormatting.GREEN), false);
				if (up != null) {
					levelUp(server, village, up);
				}
			}
			case PARTIAL -> notify(player, Component.translatable("message.mlinnotas.village.bounty_partial", result.taken()));
			case NOT_READY -> notify(player, Component.translatable("message.mlinnotas.village.bounty_not_ready"));
			case INVALID -> {
			}
		}
		VillageData.get(server).changed();
		sync(server, village);
		return result.outcome();
	}

	/** Compra no mercado da vila (esmeraldas do inventario). Devolve true se comprou. */
	public static boolean buy(ServerPlayer player, VillageState village, String offerId) {
		VillageShop.Offer offer = VillageShop.byId(offerId);
		if (offer == null || village.level().ordinal() < offer.level().ordinal()) {
			return false;
		}
		if (!VillageShop.available(offer, village)) {
			String req = VillageShop.requirement(offer);
			notify(player, Component.translatable("message.mlinnotas.village.needs_building", BuildingServer.kindName(req.substring(0, req.indexOf(':'))),
					BuildingServer.levelNumeral(VillageLevel.requiredLevel(req))));
			return false;
		}
		if (offer.kind() == VillageShop.Kind.REPAIR) {
			// conserto na forja: esmeraldas + material, desconto pelo nivel da forja
			return BuildingServer.forgeRepair(player, village);
		}
		ServerLevel level = player.level();
		int price = offer.price();
		ItemStack hand = player.getMainHandItem();
		if (offer.kind() == VillageShop.Kind.LODGING && BuildingServer.innRoom(level.getServer(), village) == null) {
			BuildingServer.lodge(player, village);
			return false;
		}
		int emeralds = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), Integer.MAX_VALUE, true);
		if (emeralds < price) {
			notify(player, Component.translatable("message.mlinnotas.village.no_emeralds", price));
			return false;
		}
		ItemStack result = ItemStack.EMPTY;
		Random random = new Random(level.getGameTime() ^ player.getUUID().getLeastSignificantBits());
		switch (offer.kind()) {
			case ITEM -> result = new ItemStack(item(offer.target()), offer.count());
			case MAP -> {
				result = explorationMap(level, player, offer);
				if (result.isEmpty()) {
					notify(player, Component.translatable("message.mlinnotas.village.map_none"));
					return false;
				}
			}
			case BOOK, RARE_BOOK -> result = book(level, random, offer.kind() == VillageShop.Kind.RARE_BOOK);
			case REPAIR -> hand.setDamageValue(0);
			case LODGING -> BuildingServer.lodge(player, village);
		}
		ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), price, false);
		player.getInventory().setChanged();
		if (!result.isEmpty()) {
			give(player, result);
		}
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
		notify(player, Component.translatable("message.mlinnotas.village.bought", Component.translatable("village.mlinnotas.shop." + offer.id()), price));
		return true;
	}

	private static ItemStack explorationMap(ServerLevel level, ServerPlayer player, VillageShop.Offer offer) {
		if (level.dimension() != Level.OVERWORLD) {
			return ItemStack.EMPTY;
		}
		TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, Identifier.parse(offer.target()));
		BlockPos found = level.findNearestMapStructure(tag, player.blockPosition(), 100, true);
		if (found == null) {
			return ItemStack.EMPTY;
		}
		ItemStack map = MapItem.create(level, found.getX(), found.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, found, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("village.mlinnotas.shop." + offer.id()));
		return map;
	}

	private static ItemStack book(ServerLevel level, Random random, boolean rare) {
		Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		List<Holder<Enchantment>> options = new ArrayList<>();
		registry.get(rare ? EnchantmentTags.TREASURE : EnchantmentTags.IN_ENCHANTING_TABLE)
				.ifPresent(set -> set.stream().filter(h -> !h.is(EnchantmentTags.CURSE)).forEach(options::add));
		if (options.isEmpty()) {
			return new ItemStack(Items.BOOK);
		}
		Holder<Enchantment> pick = options.get(random.nextInt(options.size()));
		int max = pick.value().getMaxLevel();
		int lvl = rare ? max : 1 + random.nextInt(Math.min(3, max));
		// teto de encantamento do mundo (Mineracao da arvore de habilidades), como na mesa e na bigorna
		lvl = Math.max(1, Math.min(lvl, com.mlin.notas.enchanting.EnchantRules.cappedLevel(max,
				com.mlin.notas.skills.SkillServer.progress(level.getServer()).level(com.mlin.notas.skills.SkillBranch.MINING))));
		return EnchantmentHelper.createBook(new EnchantmentInstance(pick, lvl));
	}

	// ---------------------------------------------------------------- nivel

	/** A vila subiu de nivel: anuncio, festa, golens de guarda. */
	public static void levelUp(MinecraftServer server, VillageState village, VillageLevel level) {
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.level_up", village.name(), levelName(level))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		ServerLevel world = levelOf(server, village);
		if (world == null) {
			return;
		}
		VillagePlanning.grow(world, village);
		VillagePlanning.showNearby(world, village, VillagePlanning.AUTO_SECONDS, Component.translatable("message.mlinnotas.village.area_grown",
				VillagePlanning.AUTO_SECONDS).withStyle(ChatFormatting.AQUA));
		BlockPos center = center(village);
		world.playSound(null, center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.NEUTRAL, 2.0F, 1.0F);
		world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, center.getX() + 0.5, center.getY() + 2, center.getZ() + 0.5, 80, 1.5, 1.5, 1.5, 0.4);
		for (int i = 0; i < level.golems(); i++) {
			BlockPos spot = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(3 - 6 * i, 0, 3));
			VillageBuilder.spawnGolem(world, spot);
		}
		VillageData.get(server).changed();
	}

	// ---------------------------------------------------------------- tick: obras, dia, exploracao

	private static void tick(MinecraftServer server) {
		VillageData data = VillageData.get(server);
		long now = server.getTickCount();
		for (VillageData.Job job : List.copyOf(data.jobs())) {
			VillageState village = data.village(job.village);
			ServerLevel level = village == null ? null : levelOf(server, village);
			if (level == null) {
				data.endJob(job.village);
				continue;
			}
			if (job.paused()) {
				// obra parada (plato mexido): confere de novo de tempos em tempos e continua quando o chao voltar
				if (now % VillageTerrain.PAUSE_RECHECK == 0 && VillageTerrain.plateauOk(level, village, VillageLayout.colX(job.pausedAt),
						VillageLayout.colZ(job.pausedAt), job.ground)) {
					job.markVerified(job.pausedAt);
					job.pausedAt = Long.MIN_VALUE;
					data.changed();
					server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.resumed", village.name(),
							projectName(job.project)).withStyle(ChatFormatting.GREEN), false);
				}
				continue;
			}
			boolean finished = VillageBuilder.step(level, village, job, VillagePalette.of(village.style(), job.variant()));
			data.changed();
			if (job.paused()) {
				int px = VillageLayout.colX(job.pausedAt);
				int pz = VillageLayout.colZ(job.pausedAt);
				server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.paused", village.name(),
						projectName(job.project), village.x() + px, job.ground, village.z() + pz).withStyle(ChatFormatting.RED), false);
				sync(server, village);
				continue;
			}
			if (finished) {
				finish(server, level, village, job);
			} else if (now % 20 == 0) {
				sync(server, village);
			}
		}
		VillageTerrain.tick(server, data, now);
		BuildingServer.tick(server, data, now);
		if (now % 20 == 0) {
			for (VillageState village : List.copyOf(data.villages())) {
				if (!village.queue().isEmpty() && data.job(village.key()) == null && now >= RETRY_AT.getOrDefault(village.key(), 0L)) {
					startNext(server, village, now);
				}
			}
		}
		if (now % 100 == 0) {
			long day = day(server);
			for (VillageState village : data.villages()) {
				if (village.layout() == null || village.layout().master() == null) {
					ServerLevel world = levelOf(server, village);
					if (world != null && world.isLoaded(center(village))) {
						VillagePlanning.ensureLayout(world, village);
					}
				}
				if (village.refreshBounties(day)) {
					data.changed();
					sync(server, village);
				}
			}
		}
		if (now % 40 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				exploreCheck(player, data);
			}
		}
	}

	/** Tira as estacas da terraplenagem dos cantos do lote do plano diretor onde a obra vai. */
	static void removeStakes(ServerLevel level, VillageState village, int lotId) {
		VillageLayout layout = village.layout();
		if (layout == null || layout.master() == null || lotId < 0 || layout.lot(lotId) == null) {
			return;
		}
		MasterPlan.Parcel parcel = layout.master().parcel(layout.lot(lotId).parcel());
		com.mlin.notas.village.works.VillageWorks.releaseLot(level, village, lotId);
		if (parcel != null) {
			VillageTerrain.removeStakes(level, village, layout.master(), parcel);
		}
	}

	/** Planeja a obra da frente da fila; sem lugar livre, tenta de novo em 10 s. */
	public static VillageData.Job startNext(MinecraftServer server, VillageState village, long now) {
		ServerLevel level = levelOf(server, village);
		BlockPos center = center(village);
		if (level == null || !level.isLoaded(center)) {
			return null;
		}
		VillagePlanning.ensureLayout(level, village);
		VillageProject project = village.queue().getFirst();
		long seed = village.seed() ^ (project.ordinal() * 7919L) ^ ((village.built(project) + village.lots().size() + 1) * 104729L);
		VillagePlanner.Planned planned = VillagePlanner.plan(project, village, new WorldTerrain(level, center), seed);
		VillageData data = VillageData.get(server);
		if (planned == null || planned.placements().isEmpty()) {
			if (WAITING.add(village.key())) {
				sync(server, village);
			}
			RETRY_AT.put(village.key(), now + 200);
			return null;
		}
		WAITING.remove(village.key());
		if (planned.lot() != null) {
			village.addLot(planned.lot().rect());
		}
		if (project == VillageProject.WALL) {
			com.mlin.notas.village.works.VillageWorks.beforeWall(level, village);
		}
		VillageBuilder.clearSite(level, village, planned, false);
		removeStakes(level, village, planned.lotId());
		VillageData.Job job = VillageData.Job.create(village.key(), planned);
		data.startJob(job);
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.building", village.name(),
				projectName(project)).withStyle(ChatFormatting.YELLOW), false);
		sync(server, village);
		return job;
	}

	private static void finish(MinecraftServer server, ServerLevel level, VillageState village, VillageData.Job job) {
		if (job.upgradeTo > 0) {
			BuildingServer.finishUpgrade(server, level, village, job);
			return;
		}
		VillageData data = VillageData.get(server);
		data.endJob(village.key());
		VillageBuilder.spawnResidents(level, village, job);
		VillageLayout.Lot lot = job.lot >= 0 && village.layout() != null ? village.layout().lot(job.lot) : null;
		if (lot != null) {
			lot.setBuilt(true);
			lot.setPreferred(false);
			lot.setPaid(village.queuedPaid(job.project));
			data.putRecord(village.key(), lot.id(), job.buildRecord());
		}
		VillageLevel up = village.finishBuild(job.project);
		data.changed();
		BlockPos center = center(village);
		level.playSound(null, center, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5F, 1.0F);
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.built", village.name(),
				projectName(job.project), job.project.prosperity()).withStyle(ChatFormatting.GREEN), false);
		if (up != null) {
			levelUp(server, village, up);
		}
		sync(server, village);
	}

	/** Termina na hora toda obra pendente da vila (testes, comandos). */
	public static void completeAll(MinecraftServer server, VillageState village) {
		ServerLevel level = levelOf(server, village);
		VillageData data = VillageData.get(server);
		for (int guard = 0; guard < 32 && level != null; guard++) {
			VillageData.Job job = data.job(village.key());
			if (job == null) {
				if (village.queue().isEmpty()) {
					return;
				}
				RETRY_AT.remove(village.key());
				job = startNext(server, village, server.getTickCount());
				if (job == null) {
					// sem lugar: tira da fila para nao travar
					village.dropQueued(village.queue().getFirst());
					continue;
				}
			}
			VillageBuilder.runJob(level, village, job, false);
			finish(server, level, village, job);
		}
	}

	/** Tira a vila da espera por lugar (fila mudou). */
	public static void clearWaiting(VillageState village) {
		WAITING.remove(village.key());
		RETRY_AT.remove(village.key());
	}

	public static boolean waiting(VillageState village) {
		return WAITING.contains(village.key());
	}

	// ---------------------------------------------------------------- cacadas e exploracao

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
		progressAll(player, Bounty.Kind.HUNT, id);
	}

	private static void progressAll(ServerPlayer player, Bounty.Kind kind, String target) {
		MinecraftServer server = player.level().getServer();
		VillageData data = VillageData.get(server);
		for (VillageState village : data.villages()) {
			List<Integer> ready = village.progress(kind, target, 1);
			boolean open = village.bounties().stream().anyMatch(b -> b.kind() == kind && b.target().equals(target));
			if (open) {
				data.changed();
				sync(server, village);
			}
			if (!ready.isEmpty()) {
				server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.village.bounty_ready", village.name())
						.withStyle(ChatFormatting.AQUA), false);
			}
		}
	}

	private static void exploreCheck(ServerPlayer player, VillageData data) {
		Set<String> open = new java.util.HashSet<>();
		for (VillageState village : data.villages()) {
			for (int i = 0; i < village.bounties().size(); i++) {
				Bounty b = village.bounties().get(i);
				if (b.kind() == Bounty.Kind.EXPLORE && !village.bountyDone(i) && village.bountyProgress(i) < b.count()) {
					open.add(b.target());
				}
			}
		}
		for (String key : open) {
			if (explored(player, key)) {
				progressAll(player, Bounty.Kind.EXPLORE, key);
			}
		}
	}

	/** O jogador esta no lugar da missao de exploracao {@code key}. */
	public static boolean explored(ServerPlayer player, String key) {
		ServerLevel level = player.level();
		BlockPos pos = player.blockPosition();
		String dim = dimension(level);
		switch (key) {
			case "nether" -> {
				return dim.equals("minecraft:the_nether");
			}
			case "end" -> {
				return dim.equals("minecraft:the_end");
			}
			case "ruin", "outpost", "shrine" -> {
				if (level.dimension() != Level.OVERWORLD) {
					return false;
				}
				TagKey<Structure> tag = key.equals("ruin") ? RUIN_TAG : key.equals("shrine") ? SHRINE_TAG : OUTPOST_TAG;
				return level.structureManager().getStructureWithPieceAt(pos, tag).isValid();
			}
			default -> {
			}
		}
		Holder<Biome> biome = level.getBiome(pos);
		String id = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("");
		return switch (key) {
			case "desert" -> id.contains("desert");
			case "jungle" -> biome.is(BiomeTags.IS_JUNGLE);
			case "snowy" -> id.contains("snow") || id.contains("frozen") || id.contains("ice_spikes");
			case "badlands" -> biome.is(BiomeTags.IS_BADLANDS);
			case "mushroom" -> id.equals("minecraft:mushroom_fields");
			case "cherry" -> id.equals("minecraft:cherry_grove");
			case "swamp" -> id.contains("swamp");
			case "deep_dark" -> id.equals("minecraft:deep_dark");
			default -> false;
		};
	}

}
