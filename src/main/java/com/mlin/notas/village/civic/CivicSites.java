package com.mlin.notas.village.civic;

import com.mlin.notas.village.BuildingPlans;
import com.mlin.notas.village.Canvas;
import com.mlin.notas.village.Facing;
import com.mlin.notas.village.VillageBuilder;
import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Onde cada ponto da planta de uma construcao funcionando caiu no mundo (ponte entre {@link Canvas#mark} e as funcoes):
 * a planta do lote e refeita pela mesma semente, girada como na obra e posta na altura gravada na construcao.
 */
public final class CivicSites {
	/** Uma construcao funcionando: lote, nivel e a planta do nivel. */
	public record Site(VillageState village, VillageLayout.Lot lot, int level, Canvas canvas, int ground) {
		/** Posicao no mundo do ponto {@code {x, y, z, dir}} da planta. */
		public BlockPos world(int[] point) {
			int[] r = VillagePlanner.rotate(point[0], point[2], canvas.w(), canvas.d(), lot.rot());
			return VillageBuilder.world(village, lot.x() + r[0], ground + point[1], lot.z() + r[1]);
		}

		/** Direcao do ponto no mundo (null se o ponto nao tem). */
		public Direction facing(int[] point) {
			if (point[3] < 0) {
				return null;
			}
			Facing f = Facing.values()[point[3]].rotate(lot.rot());
			return switch (f) {
				case NORTH -> Direction.NORTH;
				case SOUTH -> Direction.SOUTH;
				case EAST -> Direction.EAST;
				case WEST -> Direction.WEST;
			};
		}

		/** Todos os pontos com este nome, no mundo. */
		public List<BlockPos> marks(String name) {
			List<BlockPos> out = new ArrayList<>();
			for (int[] m : canvas.marks(name)) {
				out.add(world(m));
			}
			return out;
		}
	}

	private static final Map<String, Canvas> CACHE = new ConcurrentHashMap<>();

	private CivicSites() {
	}

	private static Canvas canvas(VillageState village, VillageLayout.Lot lot, int level) {
		String key = lot.kind() + "|" + village.style() + "|" + lot.seed() + "|" + level;
		return CACHE.computeIfAbsent(key, k -> BuildingPlans.plan(lot.kind(), village.style(), lot.seed(), level));
	}

	/** Construcoes do tipo que estao funcionando na vila (com lote e registro de obra). */
	public static List<Site> working(MinecraftServer server, VillageState village, String kind) {
		List<Site> out = new ArrayList<>();
		VillageLayout layout = village.layout();
		if (layout == null) {
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
			out.add(new Site(village, lot, b.level(), canvas(village, lot, b.level()), record.ground));
		}
		return out;
	}

	/** Todas as construcoes do tipo ja prontas (funcionando ou paradas). */
	public static List<Site> built(MinecraftServer server, VillageState village, String kind) {
		List<Site> out = new ArrayList<>();
		VillageLayout layout = village.layout();
		if (layout == null) {
			return out;
		}
		VillageData data = VillageData.get(server);
		for (VillageBuildings.Building b : VillageBuildings.all(village)) {
			if (!b.kind().equals(kind) || b.lot() < 0) {
				continue;
			}
			VillageLayout.Lot lot = layout.lot(b.lot());
			VillageData.BuildRecord record = data.record(village.key(), b.lot());
			if (lot == null || record == null) {
				continue;
			}
			out.add(new Site(village, lot, b.level(), canvas(village, lot, b.level()), record.ground));
		}
		return out;
	}

	/** A vila (da dimensao do nivel) cujo centro esta mais perto de {@code pos} dentro de raio + margem; null se nenhuma. */
	public static VillageState villageAt(MinecraftServer server, ServerLevel level, BlockPos pos, int margin) {
		String dim = VillageServer.dimension(level);
		VillageState best = null;
		double bestD = Double.MAX_VALUE;
		for (VillageState v : VillageData.get(server).villages()) {
			if (!v.dimension().equals(dim)) {
				continue;
			}
			double d = Math.sqrt(v.distanceSq(pos.getX(), pos.getZ()));
			if (d <= v.level().radius() + margin && d < bestD) {
				best = v;
				bestD = d;
			}
		}
		return best;
	}

	/** Local do tipo em {@code pos}: a construcao pronta cujo retangulo do lote contem a posicao (ou null). */
	public static Site siteAt(MinecraftServer server, VillageState village, String kind, BlockPos pos) {
		for (Site site : built(server, village, kind)) {
			if (site.lot().rect().contains(pos.getX() - village.x(), pos.getZ() - village.z())) {
				return site;
			}
		}
		return null;
	}
}
