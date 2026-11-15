package com.mlin.notas.village;

import com.mlin.notas.village.works.VillageWorksData;
import com.mlin.notas.village.works.WBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Aldeoes da vila (Vilas 3.0): so pegam posto de trabalho onde ele vale ({@link JobSiteRules}; o filtro entra na busca
 * de postos pelo {@code PoiJobSiteMixin}), todo morador tem uma cama de casa dentro da area, ficam na vila de dia e voltam
 * para a cama ao anoitecer ({@link VillagerCurfew}). {@link #sweep} roda a cada 5 s por vila (DefenseServer).
 */
public final class VillageVillagers {
	private static final Map<PoiType, Set<String>> PROFESSIONS = new ConcurrentHashMap<>();
	private static final Map<VillageData.BuildRecord, Set<Long>> RECORD_SETS = new WeakHashMap<>();

	private VillageVillagers() {
	}

	// ---------------------------------------------------------------- postos de trabalho

	/** Profissoes (ids sem namespace) que usam o tipo de POI como posto. */
	static Set<String> professionsFor(Holder<PoiType> type) {
		return PROFESSIONS.computeIfAbsent(type.value(), k -> {
			Set<String> out = new HashSet<>();
			BuiltInRegistries.VILLAGER_PROFESSION.listElements().forEach(p -> {
				if (!p.is(VillagerProfession.NONE) && !p.is(VillagerProfession.NITWIT) && p.value().heldJobSite().test(type)) {
					out.add(p.key().identifier().getPath());
				}
			});
			return Set.copyOf(out);
		});
	}

	/** O posto de trabalho em {@code pos} pode ser usado por aldeao? (true fora de vila fundada e para bloco que nao e posto) */
	public static boolean jobSiteAllowed(ServerLevel level, BlockPos pos) {
		VillageState village = VillageServer.planVillageAt(level, pos);
		if (village == null || village.layout() == null) {
			return true;
		}
		Optional<Holder<PoiType>> type = level.getPoiManager().getType(pos);
		if (type.isEmpty()) {
			return true;
		}
		Set<String> professions = professionsFor(type.get());
		if (professions.isEmpty()) {
			return true;
		}
		VillageLayout layout = village.layout();
		VillageLayout.Lot lot = layout.lotAt(pos.getX() - village.x(), pos.getZ() - village.z());
		String kind = lot != null && lot.built() ? lot.kind() : null;
		if (JobSiteRules.accepts(kind, professions)) {
			return true;
		}
		return !villageOwned(level, village, lot, pos);
	}

	/** O bloco em {@code pos} foi colocado pela vila (registro da obra do lote ou das obras publicas). */
	static boolean villageOwned(ServerLevel level, VillageState village, VillageLayout.Lot lot, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
		if (lot != null && lot.built()) {
			VillageData.BuildRecord record = VillageData.get(level.getServer()).record(village.key(), lot.id());
			if (record != null && recordSet(record).contains(pos.asLong()) && recordedAs(record, pos.asLong(), id)) {
				return true;
			}
		}
		VillageWorksData.State works = VillageWorksData.get(level.getServer()).peek(village.key());
		return works != null && works.owned.owns(WBlock.above(pos.getX() - village.x(), pos.getY(), pos.getZ() - village.z(), "").key(), id);
	}

	private static synchronized Set<Long> recordSet(VillageData.BuildRecord record) {
		return RECORD_SETS.computeIfAbsent(record, r -> {
			Set<Long> set = new HashSet<>();
			for (long p : r.positions) {
				set.add(p);
			}
			return set;
		});
	}

	private static boolean recordedAs(VillageData.BuildRecord record, long pos, String id) {
		for (int i = 0; i < record.size(); i++) {
			if (record.positions[i] == pos && record.block(i).equals(id)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- varredura periodica

	/** Area da vila mais folga, para achar os aldeoes (inclui quem se afastou). */
	private static AABB box(ServerLevel level, VillageState village, int margin) {
		double r = VillageServer.planHalf(village) + margin;
		return new AABB(village.x() - r, level.getMinY(), village.z() - r, village.x() + r, level.getMaxY(), village.z() + r);
	}

	/** Postos invalidos, camas e toque de recolher dos aldeoes da vila (a cada 5 s). */
	public static void sweep(ServerLevel level, VillageState village) {
		sweep(level, village, level.getOverworldClockTime());
	}

	/** Como {@link #sweep(ServerLevel, VillageState)} com a hora dada (testes). */
	public static void sweep(ServerLevel level, VillageState village, long dayTime) {
		if (village.layout() == null) {
			return;
		}
		int half = VillageServer.planHalf(village);
		for (Villager v : villagers(level, village)) {
			if (!v.isAlive()) {
				continue;
			}
			clearInvalidSites(level, v);
			ensureHome(level, village, v, half);
			curfew(level, village, v, half, dayTime);
		}
	}

	/** Posto de trabalho de enfeite: quem nunca negociou larga a profissao (o vanilla zera sem posto); quem negociou fica. */
	public static boolean clearInvalidSites(ServerLevel level, Villager v) {
		boolean changed = false;
		var brain = v.getBrain();
		GlobalPos potential = brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).orElse(null);
		if (potential != null && potential.dimension() == level.dimension() && !jobSiteAllowed(level, potential.pos())) {
			brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
			level.getPoiManager().release(potential.pos());
			changed = true;
		}
		GlobalPos job = brain.getMemory(MemoryModuleType.JOB_SITE).orElse(null);
		if (job != null && job.dimension() == level.dimension() && !jobSiteAllowed(level, job.pos())
				&& v.getVillagerXp() == 0 && v.getVillagerData().level() <= 1) {
			brain.eraseMemory(MemoryModuleType.JOB_SITE);
			level.getPoiManager().release(job.pos());
			changed = true;
		}
		return changed;
	}

	private static boolean isBed(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(BlockTags.VILLAGERS_CAN_SLEEP_ON_BED);
	}

	/** O aldeao tem cama (HOME) dentro da area? Se nao, pega a cama livre mais perto dentro da area. */
	public static boolean ensureHome(ServerLevel level, VillageState village, Villager v, int half) {
		var brain = v.getBrain();
		GlobalPos home = brain.getMemory(MemoryModuleType.HOME).orElse(null);
		if (home != null && home.dimension() == level.dimension()
				&& VillagerCurfew.homeValid(home.pos().getX() - village.x(), home.pos().getZ() - village.z(), half) && isBed(level, home.pos())) {
			return true;
		}
		PoiManager poi = level.getPoiManager();
		int radius = Math.min(256, half * 2 + 16);
		Optional<BlockPos> bed = poi.findAllClosestFirstWithType(p -> p.is(PoiTypes.HOME), pos -> {
			BlockState s = level.getBlockState(pos);
			return VillagerCurfew.homeValid(pos.getX() - village.x(), pos.getZ() - village.z(), half)
					&& s.is(BlockTags.VILLAGERS_CAN_SLEEP_ON_BED) && !s.getValue(BedBlock.OCCUPIED);
		}, v.blockPosition(), radius, PoiManager.Occupancy.HAS_SPACE).map(pair -> pair.getSecond()).findFirst();
		if (bed.isEmpty()) {
			return false;
		}
		BlockPos target = bed.get();
		if (poi.take(p -> p.is(PoiTypes.HOME), (t, pp) -> pp.equals(target), target, 1).isEmpty()) {
			return false;
		}
		if (home != null) {
			poi.release(home.pos());
		}
		brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), target));
		return true;
	}

	/** Toque de recolher: volta para dentro de dia, vai para a cama ao anoitecer (nao mexe em quem dorme, negocia ou esta preso). */
	public static VillagerCurfew.Decision curfew(ServerLevel level, VillageState village, Villager v, int half, long dayTime) {
		if (v.isSleeping() || v.isLeashed() || v.getTradingPlayer() != null) {
			return VillagerCurfew.Decision.NONE;
		}
		int rx = v.getBlockX() - village.x();
		int rz = v.getBlockZ() - village.z();
		GlobalPos home = v.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
		int[] bed = home == null || home.dimension() != level.dimension() ? null
				: new int[] {home.pos().getX() - village.x(), home.pos().getZ() - village.z()};
		int[] shelter = null;
		if (village.layout().master() != null) {
			shelter = RuralRules.nearestShelter(com.mlin.notas.village.civic.DefenseServer.shelters(village), rx, rz);
		}
		VillagerCurfew.Decision d = VillagerCurfew.decide(dayTime, rx, rz, half, bed, shelter);
		if (d.action() == VillagerCurfew.Action.NONE) {
			return d;
		}
		int x = village.x() + d.tx();
		int z = village.z() + d.tz();
		BlockPos to;
		if (d.action() == VillagerCurfew.Action.BED && home != null) {
			to = home.pos();
		} else if (d.action() == VillagerCurfew.Action.SHELTER && shelter != null) {
			to = new BlockPos(x, shelter[2] + 1, z);
		} else {
			if (!level.isLoaded(new BlockPos(x, village.y(), z))) {
				return VillagerCurfew.Decision.NONE;
			}
			to = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		}
		v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 0.6F, 2));
		return d;
	}

	/** Aldeoes que {@link #sweep} olha: os da area da vila mais uma folga de 96 blocos. */
	public static List<Villager> villagers(ServerLevel level, VillageState village) {
		return level.getEntitiesOfClass(Villager.class, box(level, village, 96));
	}
}
