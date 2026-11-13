package com.mlin.notas.gametest;

import com.mlin.notas.mixin.VillagerAccessor;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.Bounty;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Game tests das vilas que crescem. Cada teste funda uma vila longe das outras (x = 30000+), entao rodam juntos sem
 * se atrapalhar: entrega de projeto constroi a casa de verdade (aos poucos, pelo tick do servidor) sem tocar nos
 * blocos de jogador; missao completa paga; a vila sobe de nivel e muda o teto e o desconto dos aldeoes.
 */
public class VillageGameTest {
	/** O mundo do game test fica salvo entre execucoes: cada execucao usa outro lugar para nao achar a vila da anterior. */
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	/** Funda uma vila com o quadro em (x, chao, 0) e deixa os chunks em volta carregados. */
	private static VillageState found(GameTestHelper helper, int x) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - 48) >> 4; cx <= (x + 48) >> 4; cx++) {
			for (int cz = -48 >> 4; cz <= 48 >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		// terreno natural aleatorio (agua, encosta, floresta) deixaria a obra esperando "sem espaco": aplaina uma
		// planicie de grama sobre terra (conta como chao natural) para o teste nao depender da semente do lugar
		int y0 = Math.max(level.getSeaLevel() + 2, ground(level, x, 0).getY());
		for (int dx = -44; dx <= 44; dx++) {
			for (int dz = -44; dz <= 44; dz++) {
				for (int y = y0 - 4; y < y0 + 24; y++) {
					BlockState state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState()
							: y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		BlockPos board = ground(level, x, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		return VillageServer.create(level, board, false);
	}

	private static void release(GameTestHelper helper, VillageState village) {
		ServerLevel level = helper.getLevel();
		for (int cx = (village.x() - 48) >> 4; cx <= (village.x() + 48) >> 4; cx++) {
			for (int cz = -48 >> 4; cz <= 48 >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		VillageData.get(level.getServer()).remove(village.key());
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	private static AABB area(VillageState v, int radius) {
		return new AABB(v.x() - radius, v.y() - 10, v.z() - radius, v.x() + radius, v.y() + 20, v.z() + radius);
	}

	@GameTest(maxTicks = 2400, environment = "mlinnotas-gametest:villages")
	public void depositBuildsHouseOnFreeGroundOnly(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState village = found(helper, 30000 + RUN);
		// "casas de jogador": pilares de tabua em volta do quadro, que a obra nao pode tocar
		List<BlockPos> playerBlocks = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double a = Math.PI / 4 + i * Math.PI / 2; // 4 pilares nas diagonais: muitos bloqueariam todos os lotes do acampamento
			BlockPos base = ground(level, village.x() + (int) Math.round(11 * Math.cos(a)), (int) Math.round(11 * Math.sin(a)));
			for (int y = 0; y < 3; y++) {
				level.setBlock(base.above(y), Blocks.OAK_PLANKS.defaultBlockState(), 3);
				playerBlocks.add(base.above(y));
			}
		}
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.getInventory().add(new ItemStack(Items.SPRUCE_PLANKS, 64));
		player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 30));
		player.getInventory().add(new ItemStack(Items.OAK_LOG, 12));
		player.getInventory().add(new ItemStack(Items.GLASS, 6));
		player.getInventory().add(new ItemStack(Items.BED.red(), 1));
		int villagersBefore = level.getEntities(EntityTypes.VILLAGER, area(village, 50), e -> true).size();
		VillageState.Outcome outcome = VillageServer.deposit(player, village, VillageProject.HOUSE);
		if (outcome != VillageState.Outcome.COMPLETE) {
			throw new IllegalStateException("entrega completa deveria fechar o projeto, deu " + outcome);
		}
		if (count(player, Items.SPRUCE_PLANKS) != 0 || count(player, Items.OAK_PLANKS) != 30 || count(player, Items.GLASS) != 0) {
			throw new IllegalStateException("deveria tirar exatamente 64 tabuas e 6 vidros");
		}
		if (!village.queue().contains(VillageProject.HOUSE)) {
			throw new IllegalStateException("a casa deveria estar na fila de obras");
		}
		// a obra vai pelo tick do servidor: aparece o trabalho em andamento (aos poucos), depois termina
		boolean[] sawProgress = {false};
		helper.succeedWhen(() -> {
			VillageData.Job job = VillageData.get(level.getServer()).job(village.key());
			if (job != null && job.index > 0 && !job.done()) {
				sawProgress[0] = true;
			}
			if (village.built(VillageProject.HOUSE) != 1) {
				VillageData.Job j = VillageData.get(level.getServer()).job(village.key());
				throw helper.assertionException("casa ainda em construcao (obra " + (j == null ? "nenhuma" : j.index + "/" + j.total()) + ", fila " + village.queue() + ", lotes " + village.lots().size() + ")");
			}
			if (!sawProgress[0]) {
				throw new IllegalStateException("a casa deveria ser construida aos poucos, nao de uma vez");
			}
			for (BlockPos p : playerBlocks) {
				if (!level.getBlockState(p).is(Blocks.OAK_PLANKS)) {
					throw new IllegalStateException("a obra destruiu um bloco de jogador em " + p);
				}
			}
			VillageState.Rect lot = village.lots().getFirst();
			boolean bed = false;
			boolean door = false;
			for (int x = lot.x0(); x <= lot.x1(); x++) {
				for (int z = lot.z0(); z <= lot.z1(); z++) {
					for (int y = -3; y <= 12; y++) {
						var state = level.getBlockState(new BlockPos(village.x() + x, village.y() + y, village.z() + z));
						bed |= state.getBlock() instanceof BedBlock;
						door |= state.getBlock() instanceof DoorBlock;
					}
				}
			}
			if (!bed || !door) {
				throw new IllegalStateException("a casa pronta deveria ter cama e porta (cama " + bed + ", porta " + door + ")");
			}
			int villagers = level.getEntities(EntityTypes.VILLAGER, area(village, 50), e -> true).size();
			if (villagers != villagersBefore + 1) {
				throw new IllegalStateException("deveria chegar 1 aldeao, ha " + villagers);
			}
			if (village.prosperity() != VillageProject.HOUSE.prosperity()) {
				throw new IllegalStateException("prosperidade da casa nao contou: " + village.prosperity());
			}
			release(helper, village);
		});
	}

	@GameTest(maxTicks = 40, environment = "mlinnotas-gametest:villages")
	public void completedBountyPaysAndRaisesProsperity(GameTestHelper helper) {
		VillageState village = found(helper, 31000 + RUN);
		village.refreshBounties(VillageServer.day(helper.getLevel().getServer()));
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Bounty deliver = village.bounties().getFirst();
		var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(deliver.target()));
		player.getInventory().add(new ItemStack(item, deliver.count() + 5));
		int before = village.prosperity();
		if (VillageServer.bounty(player, village, 0) != VillageState.BountyOutcome.PAID) {
			throw new IllegalStateException("entrega com itens suficientes deveria pagar");
		}
		// a recompensa pode ser o mesmo item da entrega (ex. entregar diamantes e ganhar diamante): soma na sobra
		int rewardSame = deliver.rewardItem() != null && Identifier.parse(deliver.rewardItem()).equals(BuiltInRegistries.ITEM.getKey(item)) ? deliver.rewardCount() : 0;
		if (count(player, Items.EMERALD) < deliver.emeralds() || count(player, item) != 5 + rewardSame) {
			throw new IllegalStateException("pagamento errado: esmeraldas " + count(player, Items.EMERALD) + ", sobrou " + count(player, item));
		}
		if (village.prosperity() != before + deliver.prosperity()) {
			throw new IllegalStateException("prosperidade nao subiu");
		}
		// cacada: so paga depois de completa
		Bounty hunt = village.bounties().get(1);
		if (VillageServer.bounty(player, village, 1) != VillageState.BountyOutcome.NOT_READY) {
			throw new IllegalStateException("cacada incompleta nao pode pagar");
		}
		village.progress(Bounty.Kind.HUNT, hunt.target(), hunt.count());
		if (VillageServer.bounty(player, village, 1) != VillageState.BountyOutcome.PAID) {
			throw new IllegalStateException("cacada completa deveria pagar");
		}
		if (VillageServer.bounty(player, village, 1) != VillageState.BountyOutcome.INVALID) {
			throw new IllegalStateException("missao paga nao paga de novo no mesmo dia");
		}
		release(helper, village);
		helper.succeed();
	}

	@GameTest(maxTicks = 40, environment = "mlinnotas-gametest:villages")
	public void levelUpRaisesVillagerCapAndDiscount(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState village = found(helper, 32000 + RUN);
		BlockPos at = ground(level, village.x() + 3, 3);
		Villager villager = EntityTypes.VILLAGER.spawn(level, at, EntitySpawnReason.MOB_SUMMONED);
		if (villager == null) {
			throw new IllegalStateException("aldeao nao nasceu");
		}
		villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FARMER).withLevel(2));
		villager.setVillagerXp(10000);
		VillagerAccessor accessor = (VillagerAccessor) villager;
		if (village.level() != VillageLevel.CAMP || accessor.mlinnotas$shouldIncreaseLevel()) {
			throw new IllegalStateException("no Acampamento o aldeao para no nivel 2 (aprendiz)");
		}
		// a vila cresce: Aldeia libera nivel 3
		village.addBuilt(VillageProject.HOUSE);
		village.addBuilt(VillageProject.HOUSE);
		village.addBuilt(VillageProject.FARM);
		village.enqueue(VillageProject.PLAZA);
		village.addProsperity(60);
		VillageLevel up = village.finishBuild(VillageProject.PLAZA);
		if (up != VillageLevel.HAMLET) {
			throw new IllegalStateException("deveria virar Aldeia, ficou " + village.level());
		}
		VillageServer.levelUp(level.getServer(), village, up);
		if (!accessor.mlinnotas$shouldIncreaseLevel()) {
			throw new IllegalStateException("na Aldeia o aldeao nivel 2 pode subir");
		}
		villager.setVillagerData(villager.getVillagerData().withLevel(3));
		if (accessor.mlinnotas$shouldIncreaseLevel()) {
			throw new IllegalStateException("na Aldeia o teto e 3");
		}
		// Cidade: desconto de 2 esmeraldas
		village.setLevel(VillageLevel.TOWN);
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		if (villager.getOffers().isEmpty()) {
			throw new IllegalStateException("fazendeiro sem trocas");
		}
		accessor.mlinnotas$updateSpecialPrices(player);
		for (MerchantOffer offer : villager.getOffers()) {
			if (offer.getSpecialPriceDiff() > -VillageLevel.TOWN.discount()) {
				throw new IllegalStateException("troca sem o desconto da cidade: " + offer.getSpecialPriceDiff());
			}
		}
		// fora da vila: sem teto
		if (VillageServer.tradeCap(level, at.offset(500, 0, 0)) != 0) {
			throw new IllegalStateException("fora da vila nao ha teto");
		}
		villager.discard();
		release(helper, village);
		helper.succeed();
	}
}
