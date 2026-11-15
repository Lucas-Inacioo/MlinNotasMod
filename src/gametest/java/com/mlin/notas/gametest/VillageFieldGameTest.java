package com.mlin.notas.gametest;

import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.StartingVillage;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageFieldServer;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.WorldTerrain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;

/**
 * Campo de alturas da vila inicial (Vilas 3.0 fase 1, lote {@code mlinnotas-gametest:village_field}): uma vila inicial
 * criada num lugar novo do mundo de teste guarda o campo, o plano ja tem o anel rural e e igual ao campo, os aneis do
 * Acampamento ficam na altura do campo, o campo volta igual do arquivo, uma vila "antiga" sem campo calcula o seu com
 * o que ja existe pinado e o comando {@code /village admin field} responde a altura.
 */
public class VillageFieldGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 6_000_000;

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:village_field")
	public void starterVillageStoresItsHeightField(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		LevelData.RespawnData spawn = level.getRespawnData();
		HeightField previous = VillageFieldServer.active();
		VillageData data = VillageData.get(server);
		VillageState village = StartingVillage.create(level, new BlockPos(RUN, 0, -RUN / 3));
		try {
			MasterPlan plan = village.layout().master();
			check(plan.rural(), "o plano da vila nova ja vem com o anel rural");
			HeightField field = data.field(village.key());
			check(field != null, "campo guardado");
			check(field == VillageFieldServer.active(), "campo ativo para a geracao");
			check(field.centerX() == village.x() && field.centerZ() == village.z(), "campo centrado no quadro");
			check(field.plazaY() == village.y() - 1, "praca embaixo do quadro: " + field.plazaY() + " / " + village.y());
			int e = plan.extent();
			for (int x = -e; x <= e; x++) {
				for (int z = -e; z <= e; z++) {
					int h = plan.plannedHeight(x, z);
					if (h != Integer.MIN_VALUE && h != field.heightRel(x, z)) {
						throw new IllegalStateException("plano " + h + " != campo " + field.heightRel(x, z) + " em " + x + "," + z);
					}
				}
			}
			for (HeightField.Gate g : field.gates()) {
				for (int x = g.x0(); x <= g.x1(); x++) {
					for (int z = g.z0(); z <= g.z1(); z++) {
						check(!field.kindRel(x, z).gate() || field.heightRel(x, z) == g.level(), "portao plano");
					}
				}
			}
			check(field.gates().size() >= 8, "portoes nas duas muralhas: " + field.gates().size());
			// os aneis do Acampamento foram aplainados inteiros (coluna de chunk descarregado faria a obra parar no meio)
			for (int r = 1; r <= 2; r++) {
				check(village.ring(r).columns() > 100 && village.ring(r).ready() == village.ring(r).columns(),
						"anel " + r + " aplainado inteiro: " + village.ring(r).ready() + "/" + village.ring(r).columns());
			}
			// e ficaram na altura do campo (fora das obras)
			WorldTerrain terrain = new WorldTerrain(level, VillageServer.center(village), true);
			int checked = 0;
			int match = 0;
			for (int x = -30; x <= 30; x += 3) {
				for (int z = -30; z <= 30; z += 3) {
					MasterPlan.Block b = plan.blockAt(x, z);
					if (b == null || b.ring() > 2 || village.layout().lotAt(x, z) != null || !terrain.free(x, z)) {
						continue;
					}
					checked++;
					if (terrain.ground(x, z) == field.heightRel(x, z)) {
						match++;
					}
				}
			}
			check(checked > 20 && match >= checked * 9 / 10, "aneis 1-2 na altura do campo: " + match + "/" + checked);
			check(!level.getBlockState(VillageServer.center(village).below()).isAir(), "quadro apoiado na praca");
			// ida e volta pelo arquivo
			Tag tag = VillageData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
			VillageData back = VillageData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
			check(field.sameAs(back.field(village.key())), "campo volta igual do villages.dat");
			// dados antigos: sem campo, a vila calcula o seu com o que ja existe pinado (os aneis prontos ficam)
			data.removeField(village.key());
			HeightField old = VillageFieldServer.ensureField(level, village);
			check(old != null && old != field, "campo recalculado");
			for (int x = -30; x <= 30; x++) {
				for (int z = -30; z <= 30; z++) {
					MasterPlan.Block b = plan.blockAt(x, z);
					if (b != null && b.prepared() && old.heightRel(x, z) != b.target()) {
						throw new IllegalStateException("plato pronto mudou em " + x + "," + z);
					}
				}
			}
			check(data.field(village.key()) == old, "campo recalculado guardado");
			// comando de depuracao
			int answer;
			try {
				answer = server.getCommands().getDispatcher().execute("village admin field " + (village.x() + 3) + " " + (village.z() + 3),
						server.createCommandSourceStack().withSuppressedOutput());
			} catch (CommandSyntaxException ex) {
				throw new IllegalStateException(ex);
			}
			check(answer == old.height(village.x() + 3, village.z() + 3), "comando responde a altura");
		} finally {
			data.remove(village.key());
			level.setRespawnData(spawn);
			VillageFieldServer.setActive(previous);
		}
		helper.succeed();
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new IllegalStateException(message);
		}
	}
}
