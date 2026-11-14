package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Lado servidor das construcoes de oficios e servicos (Vilas 2.0, fase 6): onde esta cada obra funcionando
 * ({@link #levelAt}), o bau da pedreira que enche aos poucos, o sino e a bencao da igreja, os boatos da taverna, as
 * flechas com pocao da flecharia e a feira semanal com mercadores itinerantes na praca.
 */
public final class CraftsServer {
	/** Dia da ultima bencao do altar (por jogador; persiste e passa para o corpo novo ao renascer). */
	public static final AttachmentType<Long> BLESSING_DAY = AttachmentRegistry.create(MlinNotas.id("church_blessing_day"),
			b -> b.persistent(Codec.LONG).copyOnDeath());
	/** Boatos comprados hoje, como {@code dia * 10 + contagem}. */
	public static final AttachmentType<Long> RUMORS_TODAY = AttachmentRegistry.create(MlinNotas.id("tavern_rumors"),
			b -> b.persistent(Codec.LONG).copyOnDeath());

	/** Raio (em celulas da grade da estrutura) da busca dos boatos. */
	private static final int RUMOR_RADIUS = 48;
	private static final Map<String, Long> QUARRY_LAST = new HashMap<>();
	private static final Map<String, Long> BELL_DAY = new HashMap<>();
	private static final Map<String, Long> FAIR_DAY = new HashMap<>();
	private static final List<String> FAIR_ARTIFACTS = List.of("pocket_watch", "feather_charm", "traveler_belt", "lucky_horseshoe",
			"rabbit_foot_charm", "miner_lantern", "prospector_glove", "diver_ring");

	private CraftsServer() {
	}

	private static void msg(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendOverlayMessage(message);
		}
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(CraftsPayloads.Rumor.TYPE, CraftsPayloads.Rumor.CODEC);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel) || player.isSpectator()
					|| !level.getBlockState(hit.getBlockPos()).is(Blocks.BREWING_STAND)) {
				return InteractionResult.PASS;
			}
			if (levelAt(serverLevel, hit.getBlockPos(), CraftsBuildings.CHURCH) < 1) {
				return InteractionResult.PASS;
			}
			bless(serverPlayer);
			return InteractionResult.SUCCESS;
		});
	}

	// ---------------------------------------------------------------- onde fica cada obra

	/** A obra pronta e funcionando que contem {@code pos} (null = nenhuma). */
	public static VillageBuildings.Building buildingAt(ServerLevel level, BlockPos pos) {
		VillageState village = VillageServer.planVillageAt(level, pos);
		if (village == null || village.layout() == null) {
			return null;
		}
		int x = pos.getX() - village.x();
		int z = pos.getZ() - village.z();
		for (VillageLayout.Lot lot : village.layout().lots()) {
			// varios retangulos podem cobrir a coluna (lotes antigos sem tipo): vale o da obra pronta com tipo
			if (!lot.built() || lot.kind().isEmpty() || !lot.rect().contains(x, z)) {
				continue;
			}
			for (VillageBuildings.Building b : VillageBuildings.working(village)) {
				if (b.lot() == lot.id()) {
					return b;
				}
			}
		}
		return null;
	}

	/** Nivel da obra do tipo {@code kind} funcionando onde esta {@code pos} (0 = fora ou parada). */
	public static int levelAt(ServerLevel level, BlockPos pos, String kind) {
		VillageBuildings.Building b = buildingAt(level, pos);
		return b != null && b.kind().equals(kind) ? b.level() : 0;
	}

	/** Posicoes no mundo das pecas {@code part} da planta da obra do lote (para o servidor achar o bau, o sino, o altar). */
	public static List<BlockPos> partPositions(MinecraftServer server, VillageState village, VillageLayout.Lot lot, int level, VPart part) {
		VillageData.BuildRecord record = VillageData.get(server).record(village.key(), lot.id());
		if (record == null) {
			return List.of();
		}
		Canvas canvas = BuildingPlans.plan(lot.kind(), village.style(), lot.seed(), level);
		List<BlockPos> out = new ArrayList<>();
		for (Map.Entry<Long, Canvas.Cell> e : canvas.cells().entrySet()) {
			if (e.getValue().part() != part) {
				continue;
			}
			int[] r = VillagePlanner.rotate(Canvas.keyX(e.getKey()), Canvas.keyZ(e.getKey()), canvas.w(), canvas.d(), lot.rot());
			out.add(VillageBuilder.world(village, lot.x() + r[0], record.ground + Canvas.keyY(e.getKey()), lot.z() + r[1]));
		}
		return out;
	}

	// ---------------------------------------------------------------- tick

	/** Chamado no tick do servidor depois das obras: pedreira, igreja e feira. */
	public static void tick(MinecraftServer server, VillageData data, long now) {
		if (now % 20 != 0) {
			return;
		}
		for (VillageState village : List.copyOf(data.villages())) {
			ServerLevel level = VillageServer.levelOf(server, village);
			if (level == null || village.layout() == null || !level.isLoaded(VillageServer.center(village))) {
				continue;
			}
			if (now % 100 == 0) {
				quarries(server, level, village, now);
				fair(server, level, village);
			}
			church(server, level, village, now);
		}
	}

	private static void quarries(MinecraftServer server, ServerLevel level, VillageState village, long now) {
		for (VillageBuildings.Building b : VillageBuildings.working(village)) {
			if (!b.kind().equals(CraftsBuildings.QUARRY) || b.lot() < 0) {
				continue;
			}
			String key = village.key() + "#" + b.lot();
			long last = QUARRY_LAST.getOrDefault(key, now);
			QUARRY_LAST.put(key, now);
			int due = Math.min(64, CraftsRules.quarryDue(b.level(), last, now));
			VillageLayout.Lot lot = village.layout().lot(b.lot());
			if (due <= 0 || lot == null) {
				continue;
			}
			for (BlockPos pos : partPositions(server, village, lot, b.level(), VPart.CHEST)) {
				if (!level.isLoaded(pos)) {
					continue;
				}
				BlockEntity be = level.getBlockEntity(pos);
				if (be instanceof Container chest) {
					Random random = new Random(now ^ pos.asLong());
					fillQuarry(chest, b.level(), due, random);
					be.setChanged();
				}
				break;
			}
		}
	}

	/** Poe {@code count} itens da pedreira no bau (o que nao cabe se perde: o bau cheio e problema do jogador). */
	public static void fillQuarry(Container chest, int level, int count, Random random) {
		for (int i = 0; i < count; i++) {
			insert(chest, new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(CraftsRules.quarryDrop(level, random.nextInt(100)))), 1));
		}
	}

	private static void insert(Container container, ItemStack stack) {
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
				ItemStack there = container.getItem(i);
				if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < there.getMaxStackSize()) {
					int move = Math.min(stack.getCount(), there.getMaxStackSize() - there.getCount());
					there.grow(move);
					stack.shrink(move);
				} else if (pass == 1 && there.isEmpty()) {
					container.setItem(i, stack.copyAndClear());
				}
			}
		}
	}

	// ---------------------------------------------------------------- igreja

	private static void church(MinecraftServer server, ServerLevel level, VillageState village, long now) {
		long clock = server.overworld().getOverworldClockTime();
		boolean bell = CraftsRules.duskBell(clock);
		boolean gather = CraftsRules.gathering(clock);
		if (!bell && !gather) {
			return;
		}
		long day = clock / CraftsRules.DAY;
		for (VillageBuildings.Building b : VillageBuildings.working(village)) {
			VillageLayout.Lot lot = b.lot() < 0 ? null : village.layout().lot(b.lot());
			if (!b.kind().equals(CraftsBuildings.CHURCH) || lot == null) {
				continue;
			}
			String key = village.key() + "#" + b.lot();
			if (bell && BELL_DAY.getOrDefault(key, -1L) != day) {
				BELL_DAY.put(key, day);
				for (BlockPos pos : partPositions(server, village, lot, b.level(), VPart.BELL_FLOOR)) {
					if (level.getBlockState(pos).is(Blocks.BELL)) {
						((BellBlock) Blocks.BELL).attemptToRing(level, pos, null);
					}
				}
			}
			if (gather && now % 40 == 0) {
				VillageData.BuildRecord record = VillageData.get(server).record(village.key(), lot.id());
				if (record == null) {
					continue;
				}
				int[] front = lot.front();
				BlockPos target = VillageBuilder.world(village, front[0], record.ground + 1, front[1]);
				AABB box = new AABB(target).inflate(40, 16, 40);
				for (Villager villager : level.getEntitiesOfClass(Villager.class, box)) {
					if (!villager.isSleeping() && !villager.isBaby()) {
						villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.55F, 3));
					}
				}
			}
		}
	}

	/** Bencao do altar: Regeneracao curta, uma vez por dia de jogo por jogador. */
	public static void bless(ServerPlayer player) {
		long today = player.level().getServer().overworld().getOverworldClockTime() / CraftsRules.DAY;
		long last = player.getAttachedOrElse(BLESSING_DAY, -1L);
		if (!CraftsRules.canBless(last, today)) {
			msg(player, Component.translatable("message.mlinnotas.church.already").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(BLESSING_DAY, today);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, CraftsRules.BLESSING_TICKS, 0));
		player.level().playSound(null, player.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 1.4F);
		msg(player, Component.translatable("message.mlinnotas.church.blessed").withStyle(ChatFormatting.GOLD));
	}

	// ---------------------------------------------------------------- taverna: boatos

	/** Compra de boato na taverna (oferta {@code rumor_<categoria>}). Devolve true se vendeu. */
	public static boolean buyRumor(ServerPlayer player, VillageState village, VillageShop.Offer offer) {
		Rumors.Category category = Rumors.byId(offer.target());
		ServerLevel level = player.level();
		if (category == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		long today = VillageServer.day(server);
		int tavern = VillageBuildings.level(village, CraftsBuildings.TAVERN);
		long stored = player.getAttachedOrElse(RUMORS_TODAY, 0L);
		if (!Rumors.mayBuy(stored, today, tavern)) {
			msg(player, Component.translatable("message.mlinnotas.tavern.no_more", CraftsRules.rumorsPerDay(tavern))
					.withStyle(ChatFormatting.RED));
			return false;
		}
		int price = category.price();
		int emeralds = ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), Integer.MAX_VALUE, true);
		if (emeralds < price) {
			msg(player, Component.translatable("message.mlinnotas.village.no_emeralds", price));
			return false;
		}
		int mining = SkillServer.progress(server).level(SkillBranch.MINING);
		BlockPos found = null;
		String foundId = null;
		List<String> pool = new ArrayList<>(Rumors.candidates(category, mining));
		Collections.shuffle(pool, new Random(level.getGameTime() ^ player.getUUID().getLeastSignificantBits()));
		for (String id : pool) {
			BlockPos at = locate(level, id, player.blockPosition());
			if (at != null && !RumorsData.get(server).isTold(chunkKey(at))) {
				found = at;
				foundId = id;
				break;
			}
		}
		if (found == null) {
			msg(player, Component.translatable("message.mlinnotas.tavern.nothing").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ContainerHelper.clearOrCountMatchingItems(player.getInventory(), s -> s.is(Items.EMERALD), price, false);
		player.getInventory().setChanged();
		player.setAttached(RUMORS_TODAY, Rumors.afterBuying(stored, today));
		RumorsData.get(server).tell(chunkKey(found));
		revealed(server, level, village, foundId, found, player);
		return true;
	}

	/** Anuncia o boato a todos: mensagem no chat e pacote que vira waypoint e linha do livro de quests. */
	public static void revealed(MinecraftServer server, ServerLevel level, VillageState village, String structure, BlockPos pos, ServerPlayer buyer) {
		String group = com.mlin.notas.compass.CompassCatalog.structureGroup(structure);
		Component name = Component.translatable(com.mlin.notas.compass.CompassCatalog.nameKey(com.mlin.notas.compass.CompassKind.STRUCTURE, group));
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.tavern.rumor", buyer.getDisplayName(), village.name(),
				name, pos.getX(), pos.getZ()).withStyle(ChatFormatting.GOLD), false);
		CraftsPayloads.Rumor payload = new CraftsPayloads.Rumor(group, pos.getX(), pos.getZ(), VillageServer.dimension(level), village.name());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(p, payload);
		}
		level.playSound(null, buyer.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.9F);
	}

	private static long chunkKey(BlockPos pos) {
		return (((long) (pos.getX() >> 4)) << 32) ^ ((pos.getZ() >> 4) & 0xFFFFFFFFL);
	}

	/** Estrutura mais perto de {@code origin} que ainda nao foi gerada (ninguem foi la), ou null. */
	static BlockPos locate(ServerLevel level, String id, BlockPos origin) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			return null;
		}
		var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		Holder<Structure> holder = registry.get(ResourceKey.create(Registries.STRUCTURE, identifier)).orElse(null);
		if (holder == null) {
			return null;
		}
		Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator().findNearestMapStructure(level, HolderSet.direct(holder), origin,
				RUMOR_RADIUS, true);
		return result == null ? null : result.getFirst();
	}

	// ---------------------------------------------------------------- flecharia

	/** Flecha com pocao (oferta {@code tipped_*}, alvo = id da pocao). */
	public static ItemStack tippedArrows(String potionId, int count) {
		var potion = BuiltInRegistries.POTION.get(Identifier.parse(potionId));
		if (potion.isEmpty()) {
			return new ItemStack(Items.ARROW, count);
		}
		ItemStack stack = PotionContents.createItemStack(Items.TIPPED_ARROW, potion.get());
		stack.setCount(count);
		return stack;
	}

	// ---------------------------------------------------------------- feira semanal

	private static void fair(MinecraftServer server, ServerLevel level, VillageState village) {
		int market = VillageBuildings.level(village, "market");
		long clock = server.overworld().getOverworldClockTime();
		long day = clock / CraftsRules.DAY;
		if (!CraftsRules.fairDay(day, market) || FAIR_DAY.getOrDefault(village.key(), -1L) == day || Math.floorMod(clock, CraftsRules.DAY) < 1000) {
			return;
		}
		FAIR_DAY.put(village.key(), day);
		holdFair(server, level, village, market);
	}

	/** Abre a feira agora: mercadores itinerantes na praca (testes e o dia da feira). Devolve os mercadores criados. */
	public static List<WanderingTrader> holdFair(MinecraftServer server, ServerLevel level, VillageState village, int market) {
		BlockPos center = VillageServer.center(village);
		Random random = new Random(village.seed() ^ VillageServer.day(server) * 0x9E3779B97F4A7C15L);
		int mining = SkillServer.progress(server).level(SkillBranch.MINING);
		int merchants = CraftsRules.fairMerchants(market);
		List<WanderingTrader> out = new ArrayList<>();
		for (int i = 0; i < merchants; i++) {
			BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(i == 0 ? 5 : -5, 0, 5));
			WanderingTrader trader = EntityTypes.WANDERING_TRADER.spawn(level, spot, EntitySpawnReason.MOB_SUMMONED);
			if (trader == null) {
				continue;
			}
			trader.getOffers().clear();
			trader.setCustomName(Component.translatable("entity.mlinnotas.fair_merchant"));
			trader.setDespawnDelay(11000);
			trader.setWanderTarget(center);
			if (i == 0) {
				addSeeds(trader, random);
				addMaps(trader, level, center, mining);
			}
			if (i == 1 || merchants == 1) {
				addArtifact(trader, random);
			}
			out.add(trader);
		}
		server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.fair.open", village.name()).withStyle(ChatFormatting.GOLD),
				false);
		return out;
	}

	private static void addSeeds(WanderingTrader trader, Random random) {
		var season = com.mlin.notas.season.SeasonState.current();
		List<com.mlin.notas.food.FoodCatalog.Crop> crops = new ArrayList<>();
		for (com.mlin.notas.food.FoodCatalog.Crop crop : com.mlin.notas.food.FoodCatalog.CROPS) {
			if (crop.hasSeparateSeed() && (season == null || crop.bestSeason() != season.season())) {
				crops.add(crop);
			}
		}
		Collections.shuffle(crops, random);
		for (int i = 0; i < Math.min(3, crops.size()); i++) {
			Item seed = BuiltInRegistries.ITEM.getValue(MlinNotas.id(crops.get(i).seed()));
			if (seed != Items.AIR) {
				trader.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(seed, 6), 4, 3, 0.05F));
			}
		}
	}

	private static void addMaps(WanderingTrader trader, ServerLevel level, BlockPos center, int mining) {
		String[][] maps = {{"cobalt_shrine", "mlinnotas:cobalt_shrine"}, {"abyssal_sanctum", "mlinnotas:abyssal_sanctum"}};
		for (String[] m : maps) {
			if (!com.mlin.notas.compass.CompassCatalog.unlocked(m[1], mining)) {
				continue;
			}
			TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, MlinNotas.id("on_village_maps/" + m[0]));
			BlockPos found = level.findNearestMapStructure(tag, center, 100, true);
			if (found == null) {
				continue;
			}
			ItemStack map = MapItem.create(level, found.getX(), found.getZ(), (byte) 2, true, true);
			MapItem.renderBiomePreviewMap(level, map);
			MapItemSavedData.addTargetDecoration(map, found, "+", MapDecorationTypes.RED_X);
			map.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable("village.mlinnotas.shop." + m[0].replace("cobalt_shrine", "shrine_map")
					.replace("abyssal_sanctum", "abyss_map")));
			trader.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 14), map, 1, 8, 0.05F));
		}
	}

	private static void addArtifact(WanderingTrader trader, Random random) {
		String id = FAIR_ARTIFACTS.get(random.nextInt(FAIR_ARTIFACTS.size()));
		Item item = BuiltInRegistries.ITEM.getValue(MlinNotas.id(id));
		if (item != Items.AIR) {
			trader.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 24), new ItemStack(item), 1, 12, 0.05F));
		}
	}
}
