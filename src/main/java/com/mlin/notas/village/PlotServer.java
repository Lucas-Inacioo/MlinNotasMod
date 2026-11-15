package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.village.works.Mats;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lado do servidor dos terrenos do jogador (Vilas 3.0 fase 5; regras em {@link PlotPlanner}): marca o terreno no mundo
 * (estacas nos cantos e a placa "Terreno do jogador"), registra terreno numa vila que ja existe
 * ({@code /village admin plot add}), refaz o campo de alturas com o terreno pinado e nivela em volta aos poucos
 * (colunas ate o campo, nunca dentro do terreno e nunca em cima de bloco de jogador).
 */
public final class PlotServer {
	/** Quanto em volta do terreno o nivelamento olha (rampa de 1 por bloco ate um chao 10 blocos acima/abaixo cabe folgada). */
	public static final int MARGIN = 40;
	/** Colunas por tick no nivelamento. */
	static final int COLUMNS_PER_TICK = 40;
	/** Diferenca maxima entre o chao de agora e o campo que o nivelamento aceita mexer (acima disso e outra coisa). */
	static final int MAX_CHANGE = 16;

	private PlotServer() {
	}

	/** Nivelamento em andamento (memoria; o terreno com {@code terraformPending} recomeca do zero ao reiniciar o servidor). */
	private static final class Job {
		final List<Long> columns;
		int index;

		Job(List<Long> columns) {
			this.columns = columns;
		}
	}

	private static final Map<String, Job> JOBS = new HashMap<>();

	private static String jobKey(VillageState village, MasterPlan.Parcel plot) {
		return village.key() + "#" + plot.id();
	}

	// ---------------------------------------------------------------- marcas no mundo

	/** Estacas de cerca nos 4 cantos e a placa "Terreno do jogador" na frente. So poe onde ha ar (nunca sobrescreve bloco). */
	public static void mark(ServerLevel level, VillageState village, MasterPlan.Parcel plot) {
		Mats mats = Mats.of(village.style());
		BlockState fence = VanillaRemakePiece.parse(mats.fence());
		for (int[] c : plot.corners()) {
			BlockPos ground = surface(level, village, c[0], c[1]);
			if (level.getBlockState(ground).isAir() || WorldTerrain.replaceable(level.getBlockState(ground))) {
				level.setBlock(ground, fence, Block.UPDATE_ALL);
			}
		}
		int[] spot = PlotPlanner.signSpot(plot);
		BlockPos at = surface(level, village, spot[0], spot[1]);
		if (!level.getBlockState(at).isAir() && !WorldTerrain.replaceable(level.getBlockState(at))) {
			return;
		}
		BlockState sign = VanillaRemakePiece.parse(mats.sign(rotation(plot.front())));
		level.setBlock(at, sign, Block.UPDATE_ALL);
		BlockEntity entity = level.getBlockEntity(at);
		if (entity instanceof SignBlockEntity s) {
			List<Component> lines = List.of(Component.translatable("sign.mlinnotas.plot"), Component.literal(plot.along() + " x " + plot.deep()),
					Component.empty(), Component.empty());
			s.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
			s.setChanged();
			level.sendBlockUpdated(at, sign, sign, Block.UPDATE_ALL);
		}
	}

	/** Tira as estacas e a placa (terreno removido). */
	static void unmark(ServerLevel level, VillageState village, MasterPlan.Parcel plot) {
		List<int[]> spots = new ArrayList<>(plot.corners());
		spots.add(PlotPlanner.signSpot(plot));
		for (int[] c : spots) {
			BlockPos pos = surface(level, village, c[0], c[1]).below();
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof net.minecraft.world.level.block.FenceBlock || state.getBlock() instanceof net.minecraft.world.level.block.StandingSignBlock) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	/** Primeira posicao livre em cima do chao da coluna (carrega o chunk se preciso). */
	private static BlockPos surface(ServerLevel level, VillageState village, int x, int z) {
		int wx = village.x() + x;
		int wz = village.z() + z;
		int ground = WorldTerrain.scan(level, wx, wz, false)[0];
		return new BlockPos(wx, ground + 1, wz);
	}

	private static int rotation(Facing f) {
		return switch (f) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			case EAST -> 12;
		};
	}

	/** Marca todos os terrenos do plano (vila nova, logo depois dos aneis aplainados). */
	public static void markAll(ServerLevel level, VillageState village) {
		if (village.layout() == null || village.layout().master() == null) {
			return;
		}
		for (MasterPlan.Parcel p : village.layout().master().plots()) {
			mark(level, village, p);
		}
	}

	// ---------------------------------------------------------------- registrar

	/** Resultado de {@link #add}. */
	public record Outcome(MasterPlan.Parcel plot, PlotPlanner.Problem problem, int x, int z, boolean fieldCovers) {
		public boolean ok() {
			return plot != null;
		}
	}

	/**
	 * Registra o terreno (coordenadas do mundo, cantos em qualquer ordem; {@code floorY} = Y do bloco do chao, como o campo)
	 * numa vila que ja existe: tira do plano os lotes livres que ele cobre, pina o terreno e recalcula o campo de alturas e
	 * marca o terreno para nivelar em volta.
	 */
	public static Outcome add(ServerLevel level, VillageState village, int x1, int z1, int x2, int z2, int floorY) {
		VillageLayout layout = VillagePlanning.ensureLayout(level, village);
		MasterPlan plan = layout.master();
		VillageState.Rect rect = new VillageState.Rect(Math.min(x1, x2) - village.x(), Math.min(z1, z2) - village.z(), Math.max(x1, x2) - village.x(),
				Math.max(z1, z2) - village.z());
		Set<Integer> reserved = new HashSet<>();
		List<VillageState.Rect> lotRects = new ArrayList<>();
		for (VillageLayout.Lot lot : layout.lots()) {
			if (lot.parcel() >= 0) {
				reserved.add(lot.parcel());
			}
			lotRects.add(lot.rect());
		}
		PlotPlanner.Added added = PlotPlanner.add(plan, rect, floorY, reserved, lotRects);
		if (added.plot() == null) {
			return new Outcome(null, added.problem(), village.x() + added.x(), village.z() + added.z(), false);
		}
		for (MasterPlan.Parcel p : added.retired()) {
			VillageTerrain.removeStakes(level, village, plan, p);
			com.mlin.notas.village.works.VillageWorks.releaseArea(level, village, p.rect());
		}
		MasterPlan.Parcel plot = added.plot();
		plot.setTerraformPending(true);
		recompute(level, village);
		HeightField field = VillageData.get(level.getServer()).field(village.key());
		boolean covers = field != null && field.contains(village.x() + rect.x0(), village.z() + rect.z0())
				&& field.contains(village.x() + rect.x1(), village.z() + rect.z1());
		mark(level, village, plot);
		VillageData.get(level.getServer()).changed();
		VillageServer.sync(level.getServer(), village);
		return new Outcome(plot, null, 0, 0, covers);
	}

	/** Tira o terreno (a vila volta a poder usar o lugar so depois de um novo plano; o campo e refeito sem o pino). */
	public static boolean remove(ServerLevel level, VillageState village, int parcelId) {
		VillageLayout layout = village.layout();
		if (layout == null || layout.master() == null) {
			return false;
		}
		MasterPlan.Parcel plot = layout.master().parcel(parcelId);
		if (plot == null || !plot.plot() || plot.retired()) {
			return false;
		}
		unmark(level, village, plot);
		PlotPlanner.remove(layout.master(), parcelId);
		JOBS.remove(jobKey(village, plot));
		if (village.starter()) {
			recompute(level, village);
		}
		VillageData.get(level.getServer()).changed();
		VillageServer.sync(level.getServer(), village);
		return true;
	}

	/** Campo de alturas refeito com o que a vila tem agora pinado (inclusive os terrenos). */
	static void recompute(ServerLevel level, VillageState village) {
		if (!village.starter()) {
			return;
		}
		VillageData data = VillageData.get(level.getServer());
		data.removeField(village.key());
		VillageFieldServer.ensureField(level, village);
	}

	// ---------------------------------------------------------------- nivelar em volta

	/** Colunas em volta do terreno (fora dele), as mais perto primeiro, onde o campo manda (nao e chao natural de fora). */
	static List<Long> columnsAround(VillageState village, MasterPlan plan, HeightField field, MasterPlan.Parcel plot) {
		VillageState.Rect r = plot.rect();
		List<Long> out = new ArrayList<>();
		for (int x = r.x0() - MARGIN; x <= r.x1() + MARGIN; x++) {
			for (int z = r.z0() - MARGIN; z <= r.z1() + MARGIN; z++) {
				int wx = village.x() + x;
				int wz = village.z() + z;
				if (!field.contains(wx, wz) || plan.insidePlot(x, z) || field.kind(wx, wz) == HeightField.Kind.NATURAL) {
					continue;
				}
				out.add(VillageLayout.col(x, z));
			}
		}
		out.sort(Comparator.comparingInt(c -> {
			int dx = Math.max(0, Math.max(r.x0() - VillageLayout.colX(c), VillageLayout.colX(c) - r.x1()));
			int dz = Math.max(0, Math.max(r.z0() - VillageLayout.colZ(c), VillageLayout.colZ(c) - r.z1()));
			return dx * dx + dz * dz;
		}));
		return out;
	}

	/** Uma coluna ate o campo; devolve quantos blocos solidos colocou. */
	static int column(ServerLevel level, VillageState village, MasterPlan plan, HeightField field, int x, int z, VillagePalette palette, boolean force) {
		int wx = village.x() + x;
		int wz = village.z() + z;
		BlockPos at = new BlockPos(wx, 0, wz);
		if (!force && !level.isLoaded(at)) {
			return -1;
		}
		int target = field.height(wx, wz);
		int[] s = WorldTerrain.scan(level, wx, wz, true);
		if (s[1] == 0 && s[2] == 0) {
			// bloco de jogador (ou de outra estrutura) na coluna: nunca mexe
			return 0;
		}
		if (Math.abs(target - s[0]) > MAX_CHANGE) {
			return 0;
		}
		boolean street = plan.ground(x, z);
		List<Placement> placements = TerrainWork.column(x, z, s[0], s[2], target, street, (nx, nz) -> {
			int wnx = village.x() + nx;
			int wnz = village.z() + nz;
			return field.contains(wnx, wnz) ? field.height(wnx, wnz) : s[0];
		}, false);
		int solids = 0;
		for (Placement p : placements) {
			BlockPos w = VillageBuilder.world(village, p.x(), p.y(), p.z());
			if (VillageBuilder.place(level, palette, w, p, x * 31 + z, true)) {
				solids++;
			}
		}
		return solids;
	}

	/** Nivela tudo agora (testes, comando com {@code instant}): carrega chunks e vai ate o fim. */
	public static int terraformNow(ServerLevel level, VillageState village, MasterPlan.Parcel plot) {
		VillageData data = VillageData.get(level.getServer());
		HeightField field = data.field(village.key());
		MasterPlan plan = village.layout() == null ? null : village.layout().master();
		if (field == null || plan == null) {
			return 0;
		}
		VillagePalette palette = VillagePalette.of(village.style());
		int solids = 0;
		for (long col : columnsAround(village, plan, field, plot)) {
			solids += Math.max(0, column(level, village, plan, field, VillageLayout.colX(col), VillageLayout.colZ(col), palette, true));
		}
		plot.setTerraformPending(false);
		JOBS.remove(jobKey(village, plot));
		data.changed();
		return solids;
	}

	/** Avanca o nivelamento dos terrenos pendentes (chamado todo tick pelo servidor de vilas). */
	public static void tick(MinecraftServer server, VillageData data, long now) {
		for (VillageState village : data.villages()) {
			if (village.layout() == null || village.layout().master() == null) {
				continue;
			}
			MasterPlan plan = village.layout().master();
			for (MasterPlan.Parcel plot : plan.plots()) {
				if (!plot.terraformPending()) {
					continue;
				}
				ServerLevel level = VillageServer.levelOf(server, village);
				HeightField field = data.field(village.key());
				if (level == null || field == null || !level.isLoaded(VillageServer.center(village))) {
					continue;
				}
				Job job = JOBS.computeIfAbsent(jobKey(village, plot), k -> new Job(columnsAround(village, plan, field, plot)));
				VillagePalette palette = VillagePalette.of(village.style());
				int looked = 0;
				while (job.index < job.columns.size() && looked < COLUMNS_PER_TICK) {
					long col = job.columns.get(job.index);
					int r = column(level, village, plan, field, VillageLayout.colX(col), VillageLayout.colZ(col), palette, false);
					if (r < 0) {
						break;
					}
					job.index++;
					looked++;
				}
				if (job.index >= job.columns.size()) {
					plot.setTerraformPending(false);
					JOBS.remove(jobKey(village, plot));
					data.changed();
					MlinNotas.LOGGER.info("[{}] terreno {} de {} nivelado em volta", MlinNotas.MOD_ID, plot.id(), village.name());
					VillageServer.sync(server, village);
				}
			}
		}
	}

	/** Estado em memoria apagado (servidor parado). */
	static void clear() {
		JOBS.clear();
	}
}
