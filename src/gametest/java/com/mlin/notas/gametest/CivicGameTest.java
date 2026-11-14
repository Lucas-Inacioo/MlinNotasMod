package com.mlin.notas.gametest;

import com.mlin.notas.grave.GraveContents;
import com.mlin.notas.grave.GraveData;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.village.BuildingTypes;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.civic.CivicBuildings;
import com.mlin.notas.village.civic.CivicData;
import com.mlin.notas.village.civic.CivicRules;
import com.mlin.notas.village.civic.CivicSites;
import com.mlin.notas.village.civic.DefenseServer;
import com.mlin.notas.village.civic.ExpeditionRules;
import com.mlin.notas.village.civic.GuildServer;
import com.mlin.notas.village.civic.MuseumServer;
import com.mlin.notas.village.civic.MuseumSets;
import com.mlin.notas.village.civic.PostServer;
import com.mlin.notas.village.civic.VillageGuard;
import com.mlin.notas.waystone.WaystoneCost;
import com.mlin.notas.waystone.WaystoneData;
import com.mlin.notas.waystone.WaystoneInfo;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Game tests das construcoes civicas (Vilas 2.0, fase 7; lote {@code mlinnotas-gametest:civic}): todas as obras saem do
 * plano com os blocos certos e funcionam; Correio entrega a carta no dia seguinte, Escola faz filhote crescer, Torre
 * atira, Quartel gera os guardas por nivel, Farol aplica o efeito dentro do raio, Museu enche molduras e paga a colecao,
 * Guilda manda o aldeao e ele volta com o equipamento gasto, pedras de vila custam metade, Refugio e Seguro de morte e
 * {@code civic.dat} sem campos carrega vazio.
 */
public class CivicGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 500 + 5_100_000;
	private static final int R = 80;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Chao plano em volta de (x, 0), quadro no meio, vila no nivel dado com os aneis aplainados e as obras prontas. */
	private static VillageState village(GameTestHelper helper, int x, VillageLevel lvl, VillageProject... projects) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 6, ground(level, x, 0).getY()) + 4;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 6; y < y0 + 40; y++) {
					BlockState state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		BlockPos board = ground(level, x, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState village = VillageServer.create(level, board, false);
		VillagePlanning.ensureLayout(level, village);
		village.setLevel(lvl);
		for (VillageProject p : projects) {
			village.enqueue(p);
		}
		// fila antes do plano: as obras pagas reservam lote antes dos outros oficios (VillageLayout.first)
		VillagePlanning.grow(level, village);
		VillageTerrain.prepareOpenRings(level, village, false);
		VillageServer.completeAll(level.getServer(), village);
		for (VillageProject p : projects) {
			int want = (int) java.util.Arrays.stream(projects).filter(q -> q == p).count();
			if (village.built(p) < want) {
				throw new IllegalStateException(p + " nao ficou pronto (" + village.built(p) + " de " + want + ")");
			}
		}
		return village;
	}

	private static void release(GameTestHelper helper, VillageState village) {
		ServerLevel level = helper.getLevel();
		for (ServerPlayer p : PLAYERS) {
			level.getServer().getPlayerList().remove(p);
		}
		PLAYERS.clear();
		for (int cx = (village.x() - R) >> 4; cx <= (village.x() + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		VillageData.get(level.getServer()).remove(village.key());
	}

	private static VillageProject[] houses(int n, VillageProject... more) {
		List<VillageProject> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			out.add(VillageProject.HOUSE);
		}
		out.addAll(List.of(more));
		return out.toArray(new VillageProject[0]);
	}

	private static CivicSites.Site site(MinecraftServer server, VillageState v, String kind) {
		List<CivicSites.Site> sites = CivicSites.working(server, v, kind);
		if (sites.isEmpty()) {
			throw new IllegalStateException(kind + " nao esta funcionando (idle? " + VillageBuildings.idle(v) + ")");
		}
		return sites.get(0);
	}

	private static void setLevel(VillageState v, String kind, int level) {
		VillageLayout.Lot lot = v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst().orElseThrow();
		v.buildingsState().setLevel(lot.id(), level);
	}

	private static final List<ServerPlayer> PLAYERS = new ArrayList<>();

	@SuppressWarnings("removal")
	private static ServerPlayer player(GameTestHelper helper, ServerLevel level, BlockPos near) {
		ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		PLAYERS.add(p);
		p.teleportTo(level, near.getX() + 0.5, near.getY() + 1, near.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true);
		return p;
	}

	private static void fail(String message) {
		throw new IllegalStateException(message);
	}

	// ---------------------------------------------------------------- obras

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void everyCivicBuildingIsBuiltWithItsFunctionBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, RUN, VillageLevel.CAPITAL, houses(5, VillageProject.POST_OFFICE, VillageProject.SCHOOL, VillageProject.WATCHTOWER,
				VillageProject.BARRACKS, VillageProject.VILLAGE_WAYSTONE, VillageProject.BEACON, VillageProject.MUSEUM, VillageProject.EXPLORERS_GUILD));
		MinecraftServer server = level.getServer();
		for (String kind : CivicBuildings.IDS) {
			if (VillageBuildings.level(v, kind) < 1) {
				fail(kind + " nao esta funcionando");
			}
		}
		expectBlock(level, site(server, v, CivicBuildings.POST_OFFICE).marks("mailbox"), ModBlocks.MAILBOX, "caixa de correio");
		expectBlock(level, site(server, v, CivicBuildings.MUSEUM).marks("pedestal"), ModBlocks.MUSEUM_PEDESTAL, "pedestal");
		expectBlock(level, site(server, v, CivicBuildings.GUILD).marks("board"), ModBlocks.GUILD_BOARD, "quadro da guilda");
		expectBlock(level, site(server, v, CivicBuildings.BEACON).marks("beacon"), Blocks.BEACON, "sinalizador");
		expectBlock(level, site(server, v, CivicBuildings.WATCHTOWER).marks("bell"), Blocks.BELL, "sino da torre");
		CivicSites.Site stone = site(server, v, CivicBuildings.WAYSTONE);
		expectBlock(level, stone.marks("waystone"), ModBlocks.WAYSTONE, "pedra de teleporte");
		BlockPos lower = stone.marks("waystone").get(0);
		if (!level.getBlockState(lower.above()).is(ModBlocks.WAYSTONE)) {
			fail("a pedra de vila nao tem as duas metades");
		}
		if (WaystoneData.get(server).registry().get(VillageServer.dimension(level), lower.getX(), lower.getY(), lower.getZ()).isEmpty()) {
			fail("a pedra de vila nao nasceu ativada");
		}
		// a piramide do farol I e de ferro, sob o sinalizador
		BlockPos beacon = site(server, v, CivicBuildings.BEACON).marks("beacon").get(0);
		if (!level.getBlockState(beacon.below()).is(Blocks.IRON_BLOCK)) {
			fail("farol I deveria ter piramide de ferro: " + level.getBlockState(beacon.below()));
		}
		// a Prefeitura agora tem sino de verdade? (a planta I-III usa o lampiao no lanternim; o sino entra no turreto)
		release(helper, v);
		helper.succeed();
	}

	private static void expectBlock(ServerLevel level, List<BlockPos> marks, net.minecraft.world.level.block.Block block, String what) {
		if (marks.isEmpty()) {
			fail(what + ": a planta nao marcou o ponto");
		}
		for (BlockPos pos : marks) {
			if (!level.getBlockState(pos).is(block)) {
				fail(what + " ausente em " + pos + ": " + level.getBlockState(pos));
			}
		}
	}

	// ---------------------------------------------------------------- correio

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void mailGoesToAnotherVillagesPostOfficeAndArrivesNextDay(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		CivicData.get(server).parcels().clear();
		VillageState a = village(helper, RUN + 400, VillageLevel.TOWN, houses(2, VillageProject.POST_OFFICE));
		VillageState b = village(helper, RUN + 1000, VillageLevel.TOWN, houses(2, VillageProject.POST_OFFICE));
		CivicSites.Site siteA = site(server, a, CivicBuildings.POST_OFFICE);
		CivicSites.Site siteB = site(server, b, CivicBuildings.POST_OFFICE);
		ServerPlayer sender = player(helper, level, siteA.marks("mailbox").get(0));
		List<PostServer.Destination> list = PostServer.destinations(server, a, sender.getUUID());
		Optional<PostServer.Destination> toB = list.stream().filter(d -> d.village() && d.name().equals(b.name())).findFirst();
		if (toB.isEmpty()) {
			fail("o Correio da outra vila deveria ser um destino: " + list);
		}
		PostServer.open(sender, level, a, siteA, toB.get());
		AbstractContainerMenu menu = sender.containerMenu;
		menu.getSlot(27).set(new ItemStack(Items.DIAMOND, 5));
		menu.getSlot(28).set(new ItemStack(Items.BREAD, 3));
		sender.closeContainer();
		CivicData data = CivicData.get(server);
		if (data.parcels().size() != 1 || data.parcels().get(0).deliverAt() <= PostServer.now(server)) {
			fail("a carta deveria estar em transito para o dia seguinte: " + data.parcels());
		}
		long when = data.parcels().get(0).deliverAt();
		if (when % 12_000L != 0) {
			fail("entrega fora da virada do dia: " + when);
		}
		PostServer.tick(server);
		if (data.parcels().size() != 1) {
			fail("a carta nao pode chegar antes do dia seguinte");
		}
		// passa o dia: marca a carta como vencida
		CivicData.Parcel parcel = data.parcels().remove(0);
		data.parcels().add(new CivicData.Parcel(parcel.to(), parcel.toName(), parcel.fromName(), 0L, parcel.items()));
		PostServer.tick(server);
		if (!data.parcels().isEmpty() || data.inbox(parcel.to()).isEmpty()) {
			fail("a carta deveria ter chegado na caixa de entrada da outra vila");
		}
		ServerPlayer receiver = player(helper, level, siteB.marks("mailbox").get(0));
		PostServer.open(receiver, level, b, siteB, null);
		AbstractContainerMenu inbox = receiver.containerMenu;
		int diamonds = 0;
		int bread = 0;
		for (int i = 0; i < 27; i++) {
			ItemStack s = inbox.getSlot(i).getItem();
			diamonds += s.is(Items.DIAMOND) ? s.getCount() : 0;
			bread += s.is(Items.BREAD) ? s.getCount() : 0;
		}
		if (diamonds != 5 || bread != 3) {
			fail("a caixa de entrada deveria ter 5 diamantes e 3 paes: " + diamonds + "/" + bread);
		}
		receiver.closeContainer();
		release(helper, a);
		release(helper, b);
		helper.succeed();
	}

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void mailToAPlayerLandsInThatPlayersInbox(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState a = village(helper, RUN + 1600, VillageLevel.TOWN, houses(2, VillageProject.POST_OFFICE));
		CivicSites.Site siteA = site(server, a, CivicBuildings.POST_OFFICE);
		ServerPlayer sender = player(helper, level, siteA.marks("mailbox").get(0));
		String friend = "00000000-0000-0000-0000-0000000000aa";
		CivicData data = CivicData.get(server);
		data.knownPlayers().put(friend, "Amiga");
		PostServer.Destination toFriend = PostServer.destinations(server, a, sender.getUUID()).stream().filter(d -> !d.village() && d.name().equals("Amiga"))
				.findFirst().orElseThrow(() -> new IllegalStateException("o outro jogador deveria ser um destino"));
		PostServer.open(sender, level, a, siteA, toFriend);
		sender.containerMenu.getSlot(27).set(new ItemStack(Items.EMERALD, 9));
		sender.closeContainer();
		CivicData.Parcel p = data.parcels().remove(data.parcels().size() - 1);
		data.parcels().add(new CivicData.Parcel(p.to(), p.toName(), p.fromName(), 0L, p.items()));
		PostServer.tick(server);
		if (data.inbox("p:" + friend).stream().mapToInt(ItemStack::getCount).sum() != 9) {
			fail("a caixa do jogador deveria ter 9 esmeraldas: " + data.inbox("p:" + friend));
		}
		data.inbox("p:" + friend).clear();
		data.knownPlayers().remove(friend);
		release(helper, a);
		helper.succeed();
	}

	// ---------------------------------------------------------------- escola

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void schoolMakesBabiesGrowFasterAndNovicesEarnExperience(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, RUN + 2200, VillageLevel.TOWN, houses(2, VillageProject.SCHOOL));
		BlockPos at = VillageServer.center(v).offset(4, 1, 4);
		Villager baby = EntityTypes.VILLAGER.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
		baby.setAge(-24_000);
		DefenseServer.school(level, v, 20);
		if (baby.getAge() != -24_000 + 20) {
			fail("escola I: +20 tiques de idade por segundo, veio " + baby.getAge());
		}
		setLevel(v, CivicBuildings.SCHOOL, 2);
		baby.setAge(-24_000);
		DefenseServer.school(level, v, 20);
		if (baby.getAge() != -24_000 + 40) {
			fail("escola II: +40, veio " + baby.getAge());
		}
		Villager novice = EntityTypes.VILLAGER.spawn(level, at.east(), EntitySpawnReason.MOB_SUMMONED);
		novice.setVillagerXp(0);
		DefenseServer.school(level, v, 1200);
		if (novice.getVillagerXp() != 1) {
			fail("escola II: novato ganha 1 ponto de experiencia, veio " + novice.getVillagerXp());
		}
		baby.discard();
		novice.discard();
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- torre

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void towerLevelThreeArchersShootNearbyMonsters(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState v = village(helper, RUN + 2800, VillageLevel.TOWN, VillageProject.WATCHTOWER);
		setLevel(v, CivicBuildings.WATCHTOWER, 3);
		CivicSites.Site tower = site(server, v, CivicBuildings.WATCHTOWER);
		if (tower.marks("archer").size() != 4) {
			fail("a torre III precisa de 4 postos de arqueiro");
		}
		// espera as chunks recem-forcadas virarem acessiveis a busca por entidades (senao o zumbi que acabou de nascer nao e visto)
		helper.runAfterDelay(5, () -> {
		BlockPos post = tower.marks("archer").get(0);
		// zumbi parado a 8 blocos do posto, no chao, com ceu aberto e a vista do posto (a torre fica na beira do plano: o
		// lado de fora pode ser a muralha externa ou outra obra)
		BlockPos zombiePos = null;
		net.minecraft.world.phys.Vec3 eye = net.minecraft.world.phys.Vec3.atCenterOf(post).add(0, 0.6, 0);
		for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
			BlockPos g = ground(level, post.getX() + d[0], post.getZ() + d[1]);
			net.minecraft.world.phys.Vec3 head = net.minecraft.world.phys.Vec3.atBottomCenterOf(g).add(0, 1.7, 0);
			if (zombiePos == null && level.clip(new net.minecraft.world.level.ClipContext(eye, head, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getType()
					== net.minecraft.world.phys.HitResult.Type.MISS) {
				zombiePos = g;
			}
		}
		if (zombiePos == null) {
			fail("nenhum chao a vista do posto de arqueiro a 8 blocos");
		}
		Zombie zombie = EntityTypes.ZOMBIE.spawn(level, zombiePos, EntitySpawnReason.MOB_SUMMONED);
		zombie.setNoAi(true);
		zombie.setHealth(zombie.getMaxHealth());
		int arrows = 0;
		for (int t = 0; t < 8 && arrows == 0; t++) {
			DefenseServer.tower(server, level, v, 100 + 20L * t);
			arrows = level.getEntitiesOfClass(Arrow.class, new AABB(post).inflate(30)).size();
		}
		if (arrows == 0 && zombie.getHealth() >= zombie.getMaxHealth()) {
			fail("nenhum arqueiro atirou no zumbi a 8 blocos");
		}
		// torre II nao tem arqueiros
		for (Arrow a : level.getEntitiesOfClass(Arrow.class, new AABB(post).inflate(40))) {
			a.discard();
		}
		setLevel(v, CivicBuildings.WATCHTOWER, 2);
		DefenseServer.tower(server, level, v, 500);
		if (!level.getEntitiesOfClass(Arrow.class, new AABB(post).inflate(40)).isEmpty()) {
			fail("a torre II nao tem arqueiros");
		}
		zombie.discard();
		release(helper, v);
		helper.succeed();
		});
	}

	// ---------------------------------------------------------------- quartel

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void barracksSpawnsTwoFourSixArmoredGuards(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState v = village(helper, RUN + 3400, VillageLevel.CAPITAL, houses(3, VillageProject.BARRACKS));
		AABB box = new AABB(VillageServer.center(v)).inflate(90, 60, 90);
		DefenseServer.guards(server, level, v);
		List<VillageGuard> guards = level.getEntitiesOfClass(VillageGuard.class, box);
		if (guards.size() != 2) {
			fail("quartel I: 2 guardas, vieram " + guards.size());
		}
		if (!guards.get(0).getItemBySlot(EquipmentSlot.HEAD).is(Items.CHAINMAIL_HELMET)) {
			fail("guarda do quartel I deveria usar malha: " + guards.get(0).getItemBySlot(EquipmentSlot.HEAD));
		}
		setLevel(v, CivicBuildings.BARRACKS, 2);
		DefenseServer.guards(server, level, v);
		guards = level.getEntitiesOfClass(VillageGuard.class, box);
		if (guards.size() != 4 || !guards.get(0).getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)) {
			fail("quartel II: 4 guardas de ferro, vieram " + guards.size());
		}
		setLevel(v, CivicBuildings.BARRACKS, 3);
		DefenseServer.guards(server, level, v);
		guards = level.getEntitiesOfClass(VillageGuard.class, box);
		if (guards.size() != 6 || !guards.get(0).getMainHandItem().is(Items.DIAMOND_SWORD)) {
			fail("quartel III: 6 guardas de diamante, vieram " + guards.size());
		}
		// o guarda nunca some e ataca monstro perto
		if (guards.get(0).removeWhenFarAway(1000.0) || guards.get(0).getMaxHealth() < 40) {
			fail("guarda precisa ser persistente e forte");
		}
		setLevel(v, CivicBuildings.BARRACKS, 1);
		DefenseServer.guards(server, level, v);
		if (level.getEntitiesOfClass(VillageGuard.class, box).size() != 2) {
			fail("ao baixar o nivel sobram 2 guardas");
		}
		for (VillageGuard g : level.getEntitiesOfClass(VillageGuard.class, box)) {
			g.discard();
		}
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- farol

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void communityBeaconBuffsEveryoneInsideTheWallOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState v = village(helper, RUN + 4000, VillageLevel.CAPITAL, VillageProject.BEACON);
		BlockPos center = VillageServer.center(v);
		ServerPlayer inside = player(helper, level, center.offset(30, 0, 0));
		ServerPlayer far = player(helper, level, center.offset(300, 0, 0));
		DefenseServer.beacon(server, level, v);
		if (!inside.hasEffect(MobEffects.SPEED) || inside.hasEffect(MobEffects.HASTE)) {
			fail("farol I: so Velocidade dentro da muralha");
		}
		if (far.hasEffect(MobEffects.SPEED)) {
			fail("quem esta longe nao recebe o efeito");
		}
		setLevel(v, CivicBuildings.BEACON, 3);
		DefenseServer.beacon(server, level, v);
		if (!inside.hasEffect(MobEffects.HASTE) || !inside.hasEffect(MobEffects.RESISTANCE)) {
			fail("farol III: Pressa e Resistencia tambem");
		}
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- museu

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void museumDonationsFillFramesAndPayCompletedCollections(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState v = village(helper, RUN + 4600, VillageLevel.CAPITAL, houses(2, VillageProject.MUSEUM));
		CivicSites.Site museum = site(server, v, CivicBuildings.MUSEUM);
		BlockPos pedestal = museum.marks("pedestal").get(0);
		ServerPlayer player = player(helper, level, pedestal);
		int prosperity = v.prosperity();
		// uma peca fora das colecoes e um trofeu de chefe (so no nivel III) nao entram
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 3));
		MuseumServer.use(player, level, pedestal);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(MuseumServer.item("mlinnotas:cobalt_heart"), 1));
		MuseumServer.use(player, level, pedestal);
		CivicData.Museum state = CivicData.get(server).museum(v.key());
		if (!state.donated.isEmpty()) {
			fail("nada disso deveria ser aceito no Museu I: " + state.donated);
		}
		for (String id : MuseumSets.byId("gems").orElseThrow().items()) {
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(MuseumServer.item(id), 2));
			MuseumServer.use(player, level, pedestal);
		}
		if (state.donated.size() != 7) {
			fail("7 gemas doadas: " + state.donated);
		}
		if (player.getMainHandItem().getCount() != 1) {
			fail("a doacao leva so 1 do item");
		}
		if (!state.claimed.contains("gems")) {
			fail("colecao completa deveria ter sido premiada");
		}
		if (v.prosperity() <= prosperity) {
			fail("a colecao completa soma prosperidade");
		}
		int emeralds = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			emeralds += s.is(Items.EMERALD) ? s.getCount() : 0;
		}
		if (emeralds < 16) {
			fail("a recompensa traz esmeraldas: " + emeralds);
		}
		int shown = MuseumServer.framesShown(level, museum);
		if (shown != Math.min(7, museum.canvas().marks("frame").size())) {
			fail("molduras com doacao: " + shown);
		}
		// doar de novo o mesmo item nao vale
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.AMETHYST_SHARD, 2));
		MuseumServer.use(player, level, pedestal);
		if (state.donated.size() != 7) {
			fail("doacao repetida entrou");
		}
		// melhorando para III os trofeus passam a valer
		setLevel(v, CivicBuildings.MUSEUM, 3);
		CivicSites.Site upgraded = site(server, v, CivicBuildings.MUSEUM);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(MuseumServer.item("mlinnotas:cobalt_heart"), 1));
		MuseumServer.use(player, level, upgraded.marks("pedestal").get(0));
		if (!state.donated.contains("mlinnotas:cobalt_heart")) {
			fail("museu III aceita trofeu de chefe");
		}
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- guilda

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void guildSendsAVillagerAndBringsBackLootAndWornGear(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		CivicData.get(server).expeditions().clear();
		VillageState v = village(helper, RUN + 5200, VillageLevel.TOWN, houses(2, VillageProject.EXPLORERS_GUILD));
		CivicSites.Site guild = site(server, v, CivicBuildings.GUILD);
		BlockPos board = guild.marks("board").get(0);
		Villager explorer = EntityTypes.VILLAGER.spawn(level, board.south(2), EntitySpawnReason.MOB_SUMMONED);
		explorer.setCustomName(net.minecraft.network.chat.Component.literal("Marco"));
		for (Villager other : level.getEntitiesOfClass(Villager.class, new AABB(board).inflate(80))) {
			if (other != explorer) {
				other.discard();
			}
		}
		ServerPlayer player = player(helper, level, board.south(3));
		// sem destino armado, fechar o quadro devolve o equipamento
		GuildServer.open(player, v, guild);
		player.containerMenu.getSlot(0).set(new ItemStack(Items.IRON_PICKAXE));
		player.closeContainer();
		if (!CivicData.get(server).expeditions().isEmpty() || !explorer.isAlive()) {
			fail("sem destino armado nao parte ninguem");
		}
		if (!player.getInventory().contains(s -> s.is(Items.IRON_PICKAXE))) {
			fail("o equipamento deveria voltar ao jogador");
		}
		// com destino: parte levando a picareta
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		boolean departed = GuildServer.depart(player, v, guild, ExpeditionRules.Destination.MINE, new ArrayList<>(List.of(pickaxe, new ItemStack(Items.BREAD, 5))));
		if (!departed || CivicData.get(server).expeditions().size() != 1 || explorer.isAlive()) {
			fail("o aldeao deveria ter partido");
		}
		CivicData.Expedition e = CivicData.get(server).expeditions().get(0);
		if (e.returnAt() <= PostServer.now(server) || e.returnAt() > PostServer.now(server) + 3 * 24_000L + 1) {
			fail("volta em 1 a 3 dias: " + (e.returnAt() - PostServer.now(server)));
		}
		// a Guilda I so manda um por vez
		Villager other = EntityTypes.VILLAGER.spawn(level, board.south(2), EntitySpawnReason.MOB_SUMMONED);
		if (GuildServer.depart(player, v, guild, ExpeditionRules.Destination.FOREST, new ArrayList<>())) {
			fail("a Guilda I so tem 1 explorador por vez");
		}
		other.discard();
		// passa o tempo: ele volta, com a picareta gasta e o que achou
		CivicData data = CivicData.get(server);
		CivicData.Expedition due = data.expeditions().remove(0);
		data.expeditions().add(new CivicData.Expedition(due.village(), due.destination(), 0L, due.villagerName(), due.villager(), due.loot()));
		GuildServer.tick(server);
		if (!data.expeditions().isEmpty()) {
			fail("a expedicao deveria ter acabado");
		}
		List<Villager> back = level.getEntitiesOfClass(Villager.class, new AABB(board).inflate(20), x -> x.hasCustomName());
		if (back.size() != 1) {
			fail("o explorador deveria reaparecer junto do quadro: " + back.size());
		}
		List<ItemStack> returns = data.guildReturns(v.key());
		ItemStack worn = returns.stream().filter(s -> s.is(Items.IRON_PICKAXE)).findFirst().orElseThrow(() -> new IllegalStateException("a picareta voltou?"));
		if (worn.getDamageValue() <= 0) {
			fail("o equipamento deveria voltar gasto");
		}
		int miningLevel = com.mlin.notas.skills.SkillServer.progress(server).level(com.mlin.notas.skills.SkillBranch.MINING);
		for (ItemStack s : returns) {
			String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
			for (ExpeditionRules.Entry entry : ExpeditionRules.table(ExpeditionRules.Destination.MINE)) {
				if (entry.item().equals(id) && entry.oreTier() > Math.min(miningLevel, 3)) {
					fail("minerio acima do nivel de Mineracao do mundo voltou: " + id);
				}
			}
		}
		returns.clear();
		back.get(0).discard();
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- pedras de vila

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void villageWaystonesAreMarkedAndTravelBetweenThemCostsHalf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState a = village(helper, RUN + 5800, VillageLevel.VILLAGE, VillageProject.VILLAGE_WAYSTONE);
		VillageState b = village(helper, RUN + 6400, VillageLevel.VILLAGE, VillageProject.VILLAGE_WAYSTONE);
		DefenseServer.syncWaystones(server, true);
		BlockPos pa = site(server, a, CivicBuildings.WAYSTONE).marks("waystone").get(0);
		BlockPos pb = site(server, b, CivicBuildings.WAYSTONE).marks("waystone").get(0);
		WaystoneInfo ia = new WaystoneInfo(VillageServer.dimension(level), pa.getX(), pa.getY(), pa.getZ(), "A");
		WaystoneInfo ib = new WaystoneInfo(VillageServer.dimension(level), pb.getX(), pb.getY(), pb.getZ(), "B");
		if (!WaystoneCost.villageKeys().contains(ia.key()) || !WaystoneCost.villageKeys().contains(ib.key())) {
			fail("as pedras de vila deveriam estar na lista: " + WaystoneCost.villageKeys());
		}
		int full = WaystoneCost.levelsForDistance(ia.horizontalDistance(ib));
		if (WaystoneCost.levels(ia, ib) != CivicRules.halfCost(full)) {
			fail("entre pedras de vila custa metade: " + WaystoneCost.levels(ia, ib) + " de " + full);
		}
		WaystoneInfo wild = new WaystoneInfo(VillageServer.dimension(level), pb.getX() + 4000, pb.getY(), pb.getZ(), "W");
		if (WaystoneCost.levels(ia, wild) != WaystoneCost.levelsForDistance(ia.horizontalDistance(wild))) {
			fail("pedra que nao e de vila: preco cheio");
		}
		release(helper, a);
		release(helper, b);
		DefenseServer.syncWaystones(server, true);
		helper.succeed();
	}

	// ---------------------------------------------------------------- refugio

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void refugeStopsNaturalMonsterSpawnsInsideBuildings(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState v = village(helper, RUN + 7000, VillageLevel.TOWN, houses(2, VillageProject.SCHOOL));
		CivicSites.Site school = site(server, v, CivicBuildings.SCHOOL);
		BlockPos inside = school.world(new int[] {school.canvas().w() / 2, 1, 3, -1});
		// sem muralha completa: nasce
		Zombie free = EntityTypes.ZOMBIE.spawn(level, inside, EntitySpawnReason.NATURAL);
		if (free == null || free.isRemoved()) {
			fail("sem muralha completa o monstro nasce");
		}
		free.discard();
		v.addBuilt(VillageProject.WALL);
		Zombie blocked = EntityTypes.ZOMBIE.spawn(level, inside, EntitySpawnReason.NATURAL);
		if (blocked != null && !blocked.isRemoved()) {
			fail("dentro da construcao com muralha completa o monstro natural nao nasce");
		}
		Zombie egg = EntityTypes.ZOMBIE.spawn(level, inside, EntitySpawnReason.SPAWN_ITEM_USE);
		if (egg == null || egg.isRemoved()) {
			fail("ovo de spawn continua funcionando dentro da vila");
		}
		egg.discard();
		BlockPos street = VillageServer.center(v).offset(1, 1, 1);
		Zombie outside = EntityTypes.ZOMBIE.spawn(level, street, EntitySpawnReason.NATURAL);
		if (outside == null || outside.isRemoved()) {
			fail("fora das construcoes o monstro nasce normal");
		}
		outside.discard();
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- seguro de morte

	@GameTest(maxTicks = 400, environment = "mlinnotas-gametest:civic")
	public void deathInsuranceRespawnsAtTheInnAndKeepsTheItemsInAGrave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState v = village(helper, RUN + 7600, VillageLevel.TOWN, houses(2, VillageProject.INN));
		ServerPlayer player = player(helper, level, VillageServer.center(v).offset(10, 0, 10));
		GraveContents contents = new GraveContents();
		contents.set(0, new ItemStack(Items.DIAMOND_SWORD));
		contents.set(5, new ItemStack(Items.COOKED_BEEF, 12));
		// Estalagem I: sem seguro, a lapide normal
		if (DefenseServer.insure(player, level, player.blockPosition(), contents) || contents.isEmpty()) {
			fail("sem Estalagem II nao ha seguro");
		}
		setLevel(v, "inn", 2);
		BlockPos room = com.mlin.notas.village.BuildingServer.innRoom(server, v);
		if (room == null) {
			fail("estalagem II sem quarto");
		}
		int graves = GraveData.get(server).index().all().size();
		if (!DefenseServer.insure(player, level, player.blockPosition(), contents)) {
			fail("com a Estalagem II a morte perto da vila e segurada");
		}
		if (!contents.isEmpty()) {
			fail("os itens devem ter ido para a lapide do achados e perdidos");
		}
		if (GraveData.get(server).index().all().size() != graves + 1) {
			fail("deveria existir uma lapide nova");
		}
		var grave = GraveData.get(server).index().all().get(GraveData.get(server).index().all().size() - 1);
		if (Math.abs(grave.x() - room.getX()) > 12 || Math.abs(grave.z() - room.getZ()) > 12) {
			fail("a lapide fica ao lado da estalagem, nao no lugar da morte: " + grave);
		}
		if (player.getRespawnConfig() == null || !player.getRespawnConfig().respawnData().pos().equals(room)) {
			fail("o ponto de renascer deveria ser o quarto da estalagem");
		}
		// longe da vila: sem seguro
		ServerPlayer far = player(helper, level, VillageServer.center(v).offset(600, 0, 0));
		GraveContents more = new GraveContents();
		more.set(0, new ItemStack(Items.IRON_SWORD));
		if (DefenseServer.insure(far, level, far.blockPosition(), more) || more.isEmpty()) {
			fail("morte longe da vila nao tem seguro");
		}
		release(helper, v);
		helper.succeed();
	}

	// ---------------------------------------------------------------- dados

	@GameTest(maxTicks = 40, environment = "mlinnotas-gametest:civic")
	public void civicDataLoadsFromEmptyAndRoundTrips(GameTestHelper helper) {
		CivicData empty = CivicData.CODEC.parse(NbtOps.INSTANCE, new net.minecraft.nbt.CompoundTag()).getOrThrow();
		if (!empty.parcels().isEmpty() || !empty.expeditions().isEmpty() || !empty.visited().isEmpty()) {
			fail("civic.dat sem campos deveria carregar vazio");
		}
		CivicData data = new CivicData();
		data.post(new CivicData.Parcel("v:X", "X", "Y", 100L, List.of(new ItemStack(Items.DIAMOND, 3))));
		data.museum("v:X").donated.add("minecraft:diamond");
		data.museum("v:X").claimed.add("gems");
		data.markVisited("minecraft:the_nether");
		data.inbox("p:abc").add(new ItemStack(Items.BREAD, 7));
		var tag = CivicData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		CivicData back = CivicData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		if (back.parcels().size() != 1 || back.parcels().get(0).items().get(0).getCount() != 3 || !back.museum("v:X").claimed.contains("gems")
				|| !back.visited().contains("minecraft:the_nether") || back.inbox("p:abc").get(0).getCount() != 7) {
			fail("civic.dat nao voltou igual");
		}
		helper.succeed();
	}

	@GameTest(maxTicks = 40, environment = "mlinnotas-gametest:civic")
	public void everyItemOfTheCollectionsAndExpeditionTablesExists(GameTestHelper helper) {
		Set<String> ids = new HashSet<>();
		MuseumSets.all().forEach(s -> {
			ids.addAll(s.items());
			s.rewards().forEach(r -> ids.add(r.item()));
		});
		for (ExpeditionRules.Destination d : ExpeditionRules.Destination.values()) {
			ExpeditionRules.table(d).forEach(e -> ids.add(e.item()));
		}
		for (int slot = 0; slot < 4; slot++) {
			for (int level = 1; level <= 3; level++) {
				ids.add(CivicRules.guardArmor(level, slot));
			}
		}
		for (int level = 1; level <= 3; level++) {
			ids.add(CivicRules.guardWeapon(level));
		}
		List<String> missing = new ArrayList<>();
		for (String id : ids) {
			Identifier identifier = Identifier.tryParse(id);
			if (identifier == null || BuiltInRegistries.ITEM.getValue(identifier) == Items.AIR) {
				missing.add(id);
			}
		}
		for (String effect : List.of("minecraft:speed", "minecraft:haste", "minecraft:resistance", "minecraft:strength", "minecraft:regeneration")) {
			if (BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(effect)).isEmpty()) {
				missing.add(effect);
			}
		}
		if (!missing.isEmpty()) {
			fail("ids que nao existem: " + missing);
		}
		if (ModEntities.VILLAGE_GUARD == null || BuildingTypes.of(CivicBuildings.GUILD) == null) {
			fail("registro incompleto");
		}
		helper.succeed();
	}
}
