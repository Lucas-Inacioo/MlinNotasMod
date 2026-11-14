package com.mlin.notas.village;

import com.mlin.notas.season.SeasonState;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lado servidor das construcoes de producao e comida (Vilas 2.0, fase 5; numeros em {@link FoodRules}). A cada
 * {@link #CYCLE} ticks cada obra funcionando (com trabalhador, {@link VillageBuildings#working}) faz a sua funcao sobre
 * o bau de trabalho da planta (o primeiro {@link VPart#CHEST} de {@link FoodPlans}, lido da planta e girado para o
 * lote como em {@link BuildingServer#innRoom}): o moinho mo trigo, a padaria assa, o acougue seca carne, a vinicola
 * fermenta, o apiario poe mel, o curral cria animais e da leite e la, e as plantacoes, o pomar, o curral e a padaria
 * abastecem o celeiro. Alem disso: colher fruta do pomar com o botao direito nas folhas, a bancada da serraria (6
 * tabuas por tronco no cortador de pedras) e as abelhas do apiario que nunca atacam moradores. Nada fica salvo alem dos
 * baus: o ritmo vem do tempo de jogo.
 */
public final class FoodBuildingsServer {
	/** Ticks entre duas rodadas das construcoes (1 minuto). */
	public static final int CYCLE = 1200;
	private static final Map<Long, Long> RIPE_AT = new HashMap<>();

	private FoodBuildingsServer() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CYCLE == CYCLE / 2) {
				cycle(server);
			}
		});
		UseBlockCallback.EVENT.register(FoodBuildingsServer::onUse);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof Villager && source.getEntity() instanceof Bee));
	}

	// ---------------------------------------------------------------- onde fica cada construcao no mundo

	/** Uma obra pronta e funcionando no mundo. */
	record Site(ServerLevel level, VillageState village, VillageLayout.Lot lot, int buildingLevel, Canvas canvas, int ground) {
		BlockPos pos(int x, int y, int z) {
			int[] r = VillagePlanner.rotate(x, z, canvas.w(), canvas.d(), lot.rot());
			return VillageBuilder.world(village, lot.x() + r[0], ground + y, lot.z() + r[1]);
		}

		/** Bau de trabalho: o primeiro da planta. */
		BlockPos chest() {
			List<BlockPos> all = chests();
			return all.isEmpty() ? null : all.get(0);
		}

		List<BlockPos> chests() {
			List<BlockPos> out = new ArrayList<>();
			for (var e : canvas.cells().entrySet()) {
				if (e.getValue().part() == VPart.CHEST) {
					out.add(pos(Canvas.keyX(e.getKey()), Canvas.keyY(e.getKey()), Canvas.keyZ(e.getKey())));
				}
			}
			return out;
		}

		/** Caixa do lote no mundo (a planta girada), do chao para cima. */
		AABB box(int up) {
			BlockPos a = pos(0, 0, 0);
			BlockPos b = pos(canvas.w() - 1, up, canvas.d() - 1);
			return new AABB(Math.min(a.getX(), b.getX()), a.getY(), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()) + 1, b.getY() + 1,
					Math.max(a.getZ(), b.getZ()) + 1);
		}
	}

	/** Obras funcionando do tipo (ate o nivel atual de cada uma). */
	static List<Site> sites(MinecraftServer server, VillageState village, String kind) {
		List<Site> out = new ArrayList<>();
		ServerLevel level = VillageServer.levelOf(server, village);
		VillageLayout layout = village.layout();
		if (level == null || layout == null) {
			return out;
		}
		VillageData data = VillageData.get(server);
		for (VillageBuildings.Building b : VillageBuildings.working(village)) {
			if (!b.kind().equals(kind) || b.lot() < 0) {
				continue;
			}
			VillageLayout.Lot lot = layout.lot(b.lot());
			VillageData.BuildRecord record = data.record(village.key(), b.lot());
			if (lot == null || record == null) {
				continue;
			}
			out.add(new Site(level, village, lot, b.level(), BuildingPlans.plan(lot.kind(), village.style(), lot.seed(), b.level()), record.ground));
		}
		return out;
	}

	/** Bau de trabalho da primeira obra funcionando do tipo (testes e ferramentas) ou null. */
	public static BlockPos workChest(MinecraftServer server, VillageState village, String kind) {
		List<Site> sites = sites(server, village, kind);
		return sites.isEmpty() ? null : sites.get(0).chest();
	}

	// ---------------------------------------------------------------- baus

	private static String id(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(key);
	}

	/** Contagem de itens simples (sem nome nem dano) do bau. */
	static Map<String, Integer> count(Container container) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && !stack.isDamaged()) {
				out.merge(id(stack), stack.getCount(), Integer::sum);
			}
		}
		return out;
	}

	static void remove(Container container, String id, int amount) {
		int left = amount;
		for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && !stack.isDamaged() && id(stack).equals(id)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
				container.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
			}
		}
		container.setChanged();
	}

	/** Poe no bau; o que nao couber cai no chao em cima dele. Devolve quanto coube. */
	static int insert(ServerLevel level, BlockPos chest, Container container, String id, int amount) {
		Item item = item(id);
		if (item == Items.AIR || amount <= 0) {
			return 0;
		}
		int left = amount;
		int max = item.getDefaultMaxStackSize();
		for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty() && stack.is(item) && stack.getCount() < max && !stack.isDamaged() && stack.getComponentsPatch().isEmpty()) {
				int add = Math.min(left, max - stack.getCount());
				stack.grow(add);
				left -= add;
			}
		}
		for (int i = 0; i < container.getContainerSize() && left > 0; i++) {
			if (container.getItem(i).isEmpty()) {
				int add = Math.min(left, max);
				container.setItem(i, new ItemStack(item, add));
				left -= add;
			}
		}
		container.setChanged();
		return amount - left;
	}

	/** Aplica o resultado das receitas ao bau (tira os ingredientes, poe produtos e devolucoes). */
	private static void apply(ServerLevel level, BlockPos chest, Container container, Map<String, Integer> before, Map<String, Integer> after) {
		Map<String, Integer> delta = new LinkedHashMap<>();
		before.forEach((k, v) -> delta.merge(k, -v, Integer::sum));
		after.forEach((k, v) -> delta.merge(k, v, Integer::sum));
		delta.forEach((id, n) -> {
			if (n < 0) {
				remove(container, id, -n);
			}
		});
		delta.forEach((id, n) -> {
			if (n > 0) {
				int put = insert(level, chest, container, id, n);
				if (put < n) {
					Item item = item(id);
					for (int left = n - put; left > 0; left -= item.getDefaultMaxStackSize()) {
						Containers.dropItemStack(level, chest.getX() + 0.5, chest.getY() + 1.0, chest.getZ() + 0.5,
								new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize())));
					}
				}
			}
		});
	}

	static Container containerAt(ServerLevel level, BlockPos pos) {
		if (pos == null || !level.isLoaded(pos)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof Container c ? c : null;
	}

	/** Roda receitas automaticas sobre o bau da obra; devolve o que foi produzido. */
	static Map<String, Integer> craft(Site site, List<FoodRules.Craft> crafts, int maxBatches) {
		BlockPos chest = site.chest();
		Container container = containerAt(site.level(), chest);
		if (container == null) {
			return Map.of();
		}
		Map<String, Integer> before = count(container);
		Map<String, Integer> stock = new LinkedHashMap<>(before);
		FoodRules.Result result = FoodRules.run(stock, crafts, site.buildingLevel(), maxBatches);
		if (result.batches() == 0) {
			return Map.of();
		}
		apply(site.level(), chest, container, before, stock);
		site.level().playSound(null, chest, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.4F, 1.0F);
		return result.produced();
	}

	// ---------------------------------------------------------------- ciclo

	/** Estacao atual (ordinal) ou null se as estacoes estao desligadas. */
	static Integer season() {
		var date = SeasonState.current();
		return date == null ? null : date.season().ordinal();
	}

	/** Roda uma rodada em todas as vilas (o servidor chama a cada {@link #CYCLE} ticks; testes chamam direto). */
	public static void cycle(MinecraftServer server) {
		for (VillageState village : new ArrayList<>(VillageData.get(server).villages())) {
			if (village.layout() == null) {
				continue;
			}
			cycle(server, village);
		}
	}

	public static void cycle(MinecraftServer server, VillageState village) {
		for (Site mill : sites(server, village, "mill")) {
			craft(mill, FoodRules.mill(mill.buildingLevel()), FoodRules.batches("mill", mill.buildingLevel()));
		}
		for (Site bakery : sites(server, village, "bakery")) {
			craft(bakery, FoodRules.BAKERY, FoodRules.batches("bakery", bakery.buildingLevel()));
		}
		for (Site butcher : sites(server, village, "butcher")) {
			craft(butcher, FoodRules.BUTCHER, FoodRules.batches("butcher", butcher.buildingLevel()));
		}
		for (Site winery : sites(server, village, "winery")) {
			craft(winery, FoodRules.WINERY, FoodRules.batches("winery", winery.buildingLevel()));
		}
		for (Site apiary : sites(server, village, "apiary")) {
			apiary(apiary);
		}
		for (Site pen : sites(server, village, "pen")) {
			pen(pen);
		}
		supplyGranary(server, village);
	}

	// ---------------------------------------------------------------- celeiro

	/** Celeiro de maior nivel funcionando. */
	static Site bestGranary(MinecraftServer server, VillageState village) {
		Site best = null;
		for (Site site : sites(server, village, "granary")) {
			if (best == null || site.buildingLevel() > best.buildingLevel()) {
				best = site;
			}
		}
		return best;
	}

	/** Plantacoes, pomar, curral e padaria poem comida no bau do celeiro. */
	static void supplyGranary(MinecraftServer server, VillageState village) {
		Site granary = bestGranary(server, village);
		if (granary == null) {
			return;
		}
		BlockPos chest = granary.chest();
		Container container = containerAt(granary.level(), chest);
		if (container == null) {
			return;
		}
		long day = granary.level().getServer().overworld().getOverworldClockTime() / 24000L;
		Integer season = season();
		double factor = FoodRules.seasonFactor(season);
		for (Site farm : sites(server, village, "farm")) {
			int n = Math.max(1, (int) Math.round(FoodRules.farmOutput(farm.buildingLevel(), granary.buildingLevel()) * factor));
			insert(granary.level(), chest, container, FoodRules.farmCrop(day), n);
		}
		for (Site orchard : sites(server, village, "orchard")) {
			insert(granary.level(), chest, container, "minecraft:apple", Math.max(1, (int) Math.round(FoodRules.orchardOutput(orchard.buildingLevel()) * factor)));
		}
		String[] meats = {"minecraft:beef", "minecraft:porkchop", "minecraft:mutton"};
		for (Site pen : sites(server, village, "pen")) {
			insert(granary.level(), chest, container, meats[(int) Math.floorMod(day, meats.length)], FoodRules.penOutput(pen.buildingLevel()));
		}
		for (Site bakery : sites(server, village, "bakery")) {
			Container oven = containerAt(bakery.level(), bakery.chest());
			if (oven == null) {
				continue;
			}
			int have = count(oven).getOrDefault("minecraft:bread", 0);
			int move = Math.min(have, FoodRules.bakeryTransfer(bakery.buildingLevel()));
			if (move > 0) {
				remove(oven, "minecraft:bread", move);
				insert(granary.level(), chest, container, "minecraft:bread", move);
			}
		}
	}

	/**
	 * Bonus de colheita das plantacoes perto do celeiro (+10% por nivel): o jogador colhendo uma planta num raio de
	 * {@link FoodRules#GRANARY_RADIUS} de um celeiro funcionando ganha itens a mais (a semente que replanta nao conta).
	 */
	public static void harvestBonus(ServerLevel level, BlockPos pos, List<ItemStack> drops, int seedIndex) {
		// o celeiro pode estar nos arredores (alem do raio de moradores) e a plantacao ate GRANARY_RADIUS dele
		VillageState village = VillageServer.nearest(level, pos, StreetPlanner.OUTER + 2 + FoodRules.GRANARY_RADIUS);
		if (village == null || village.layout() == null) {
			return;
		}
		int best = 0;
		for (Site granary : sites(level.getServer(), village, "granary")) {
			BlockPos c = granary.chest();
			if (c != null && Math.abs(c.getX() - pos.getX()) <= FoodRules.GRANARY_RADIUS && Math.abs(c.getZ() - pos.getZ()) <= FoodRules.GRANARY_RADIUS) {
				best = Math.max(best, granary.buildingLevel());
			}
		}
		int percent = FoodRules.granaryBonusPercent(best);
		if (percent == 0) {
			return;
		}
		for (int i = 0; i < drops.size(); i++) {
			ItemStack drop = drops.get(i);
			if (i != seedIndex && !drop.isEmpty()) {
				drop.grow(FoodRules.bonusItems(drop.getCount(), percent, level.getRandom().nextDouble()));
			}
		}
	}

	// ---------------------------------------------------------------- apiario

	private static void apiary(Site site) {
		BlockPos chest = site.chest();
		Container container = containerAt(site.level(), chest);
		if (container == null) {
			return;
		}
		int combs = FoodRules.honeycomb(site.buildingLevel(), season());
		if (combs > 0) {
			insert(site.level(), chest, container, "minecraft:honeycomb", combs);
			insert(site.level(), chest, container, "minecraft:honey_bottle", combs);
		}
		// abelhas de verdade em volta das colmeias (metade das colmeias, no maximo)
		AABB box = site.box(8).inflate(2.0);
		int bees = site.level().getEntitiesOfClass(Bee.class, box).size();
		int want = FoodRules.hives(site.buildingLevel()) / 2;
		if (bees < want) {
			BlockPos at = site.pos(site.canvas().doorX() + (bees % 2 == 0 ? -3 : 3), 2, site.canvas().d() - 5);
			EntityTypes.BEE.spawn(site.level(), at, EntitySpawnReason.MOB_SUMMONED);
		}
	}

	// ---------------------------------------------------------------- curral

	private static final List<EntityType<?>> LIVESTOCK = List.of(EntityTypes.COW, EntityTypes.SHEEP, EntityTypes.PIG);

	private static void pen(Site site) {
		AABB box = site.box(6).deflate(0.5, 0, 0.5);
		int cap = FoodRules.penCap(site.buildingLevel());
		Map<EntityType<?>, List<AgeableMob>> kinds = new LinkedHashMap<>();
		for (EntityType<?> type : LIVESTOCK) {
			kinds.put(type, new ArrayList<>());
		}
		for (Entity entity : site.level().getEntities((Entity) null, box, e -> e.isAlive() && LIVESTOCK.contains(e.getType()))) {
			if (entity instanceof AgeableMob mob) {
				kinds.get(mob.getType()).add(mob);
			}
		}
		BlockPos chest = site.chest();
		Container container = containerAt(site.level(), chest);
		for (var e : kinds.entrySet()) {
			List<AgeableMob> all = e.getValue();
			long adults = all.stream().filter(m -> !m.isBaby()).count();
			boolean none = all.isEmpty();
			if (none || FoodRules.canBreed((int) adults, all.size(), cap)) {
				int births = none ? 2 : 1;
				for (int i = 0; i < births; i++) {
					BlockPos at = site.pos(site.canvas().doorX() - 1 + i * 2, 1, site.canvas().d() - 4);
					Entity spawned = e.getKey().spawn(site.level(), at, EntitySpawnReason.BREEDING);
					if (spawned instanceof AgeableMob baby && !none) {
						baby.setAge(-24000);
					}
				}
			}
		}
		if (container != null) {
			int sheep = (int) kinds.get(EntityTypes.SHEEP).stream().filter(m -> !m.isBaby()).count();
			int cows = (int) kinds.get(EntityTypes.COW).stream().filter(m -> !m.isBaby()).count();
			if (FoodRules.woolOutput(sheep) > 0) {
				insert(site.level(), chest, container, "minecraft:white_wool", FoodRules.woolOutput(sheep));
			}
			if (FoodRules.milkOutput(cows) > 0) {
				insert(site.level(), chest, container, "minecraft:milk_bucket", FoodRules.milkOutput(cows));
			}
		}
	}

	// ---------------------------------------------------------------- interacoes

	/** Obra funcionando do tipo no lugar {@code pos}, ou null. */
	static Site siteAt(ServerLevel level, BlockPos pos, String kind) {
		VillageState village = VillageServer.planVillageAt(level, pos);
		if (village == null || village.layout() == null) {
			return null;
		}
		VillageLayout.Lot lot = village.layout().lotAt(pos.getX() - village.x(), pos.getZ() - village.z());
		if (lot == null || !lot.built() || !kind.equals(lot.kind())) {
			return null;
		}
		for (Site site : sites(level.getServer(), village, kind)) {
			if (site.lot().id() == lot.id()) {
				return site;
			}
		}
		return null;
	}

	private static InteractionResult onUse(Player player, Level world, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (state.is(BlockTags.LEAVES) && !player.isShiftKeyDown()) {
			Site orchard = siteAt(level, pos, "orchard");
			if (orchard != null) {
				return harvestTree(level, sp, orchard, pos);
			}
		} else if (state.is(Blocks.STONECUTTER) && player.getMainHandItem().is(ItemTags.LOGS) && !player.isShiftKeyDown()) {
			Site sawmill = siteAt(level, pos, "sawmill");
			if (sawmill != null) {
				return saw(level, sp, sawmill);
			}
		}
		return InteractionResult.PASS;
	}

	/** Mensagem na barra de acao (jogador de teste sem conexao nao recebe). */
	private static void message(ServerPlayer player, Component text) {
		if (player.connection != null) {
			player.sendOverlayMessage(text);
		}
	}

	/** Colhe a fruta da arvore (folha clicada): uma vez por dia de jogo por folha; a estacao traz fruta nova. */
	static InteractionResult harvestTree(ServerLevel level, ServerPlayer player, Site orchard, BlockPos pos) {
		long now = level.getGameTime();
		long ripe = RIPE_AT.getOrDefault(pos.asLong(), 0L);
		if (now < ripe) {
			message(player, Component.translatable("message.mlinnotas.orchard.green"));
			return InteractionResult.SUCCESS;
		}
		RIPE_AT.put(pos.asLong(), now + FoodRules.orchardRegrow(orchard.buildingLevel()));
		Integer season = season();
		for (String fruit : FoodRules.orchardFruit(orchard.buildingLevel(), season == null ? -1 : season)) {
			Item item = item(fruit);
			if (item != Items.AIR) {
				Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() - 0.4, pos.getZ() + 0.5, new ItemStack(item));
			}
		}
		level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 1.0F);
		message(player, Component.translatable("message.mlinnotas.orchard.picked").withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}

	/** Planks do tronco ({@code oak_log} -> {@code oak_planks}) ou null se nao ha. */
	static Item planksOf(ItemStack log) {
		String path = BuiltInRegistries.ITEM.getKey(log.getItem()).getPath();
		path = path.replace("stripped_", "");
		String wood = path.replace("_log", "").replace("_wood", "").replace("_stem", "").replace("_hyphae", "").replace("_block", "");
		Item planks = item("minecraft:" + wood + "_planks");
		return planks == Items.AIR ? null : planks;
	}

	/** Bancada da serraria: troncos na mao viram 6 tabuas cada (ate o limite do nivel por uso). */
	static InteractionResult saw(ServerLevel level, ServerPlayer player, Site sawmill) {
		ItemStack hand = player.getMainHandItem();
		Item planks = planksOf(hand);
		if (planks == null) {
			return InteractionResult.PASS;
		}
		int logs = Math.min(hand.getCount(), FoodRules.benchLimit(sawmill.buildingLevel()));
		hand.shrink(logs);
		int total = FoodRules.planks(logs);
		for (int left = total; left > 0; left -= 64) {
			ItemStack out = new ItemStack(planks, Math.min(64, left));
			if (!player.getInventory().add(out)) {
				Containers.dropItemStack(level, player.getX(), player.getY(), player.getZ(), out);
			}
		}
		level.playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 0.8F);
		message(player, Component.translatable("message.mlinnotas.sawmill.sawed", logs, total).withStyle(ChatFormatting.GREEN));
		return InteractionResult.SUCCESS;
	}
}
