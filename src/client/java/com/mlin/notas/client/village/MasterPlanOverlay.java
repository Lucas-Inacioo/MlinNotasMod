package com.mlin.notas.client.village;

import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.StreetPlanner;
import com.mlin.notas.village.VillagePayloads;
import com.mlin.notas.village.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Plano diretor no contorno da vila (so cliente): ruas na altura final (avenidas, aneis, travessas), borda de cada
 * quarteirao no plato com a cor da zona (laranja = falta terraplenagem, cinza = anel ainda fechado), a altura alvo de
 * cada quarteirao perto do jogador, lotes livres do plano e areas de jogador. Os trechos sao calculados uma vez por
 * contorno recebido.
 */
final class MasterPlanOverlay {
	static final int CENTER = 0xFFFFB347;
	static final int DISTRICT = 0xFF6FD3FF;
	static final int OUTSKIRTS = 0xFF8EE07A;
	static final int WALL = 0xFFC9A0FF;
	static final int UNPREPARED = 0xFFFF7A2E;
	static final int CLOSED = 0xFF8A8A8A;
	static final int PLAYER = 0xFFB070FF;
	private static final double RANGE = 88;

	/** Trecho de rua (celulas seguidas na mesma linha e altura). */
	private record Run(int x0, int x1, int z, int y, int kind, int ring) {
	}

	/** Lado de quarteirao (de (x0,z0) a (x1,z1) nas bordas das celulas). */
	private record Edge(double x0, double z0, double x1, double z1, int block) {
	}

	private static VillagePayloads.Overlay cached;
	private static final List<Run> RUNS = new ArrayList<>();
	private static final List<Edge> EDGES = new ArrayList<>();

	private MasterPlanOverlay() {
	}

	static int zoneColor(Zone zone) {
		if (zone == null) {
			return CLOSED;
		}
		return switch (zone) {
			case CENTER -> CENTER;
			case DISTRICT -> DISTRICT;
			case OUTSKIRTS -> OUTSKIRTS;
			case WALL -> WALL;
		};
	}

	private static void build(VillagePayloads.Overlay o, MasterPlan plan) {
		cached = o;
		RUNS.clear();
		EDGES.clear();
		int e = plan.extent();
		for (int z = -e; z <= e; z++) {
			int x = -e;
			while (x <= e) {
				if (!plan.street(x, z)) {
					x++;
					continue;
				}
				int y = plan.streetHeight(x, z);
				int kind = plan.ground(x, z) ? plan.streetKind(x, z) : -1;
				int ring = plan.ring(x, z);
				int x1 = x;
				while (x1 + 1 <= e && plan.street(x1 + 1, z) && plan.streetHeight(x1 + 1, z) == y && plan.ring(x1 + 1, z) == ring
						&& (plan.ground(x1 + 1, z) ? plan.streetKind(x1 + 1, z) : -1) == kind) {
					x1++;
				}
				RUNS.add(new Run(x, x1, z, y, kind, ring));
				x = x1 + 1;
			}
		}
		// bordas dos quarteiroes: lados de celula entre o quarteirao e o resto, juntados em linhas
		for (int z = -e; z <= e + 1; z++) {
			int start = Integer.MIN_VALUE;
			int owner = -1;
			for (int x = -e; x <= e + 1; x++) {
				MasterPlan.Block a = plan.blockAt(x, z - 1);
				MasterPlan.Block b = plan.blockAt(x, z);
				int edge = a != b ? (a != null ? a.id() : b != null ? b.id() : -1) : -1;
				if (edge != owner) {
					if (owner >= 0) {
						EDGES.add(new Edge(start, z, x, z, owner));
					}
					owner = edge;
					start = x;
				}
			}
		}
		for (int x = -e; x <= e + 1; x++) {
			int start = Integer.MIN_VALUE;
			int owner = -1;
			for (int z = -e; z <= e + 1; z++) {
				MasterPlan.Block a = plan.blockAt(x - 1, z);
				MasterPlan.Block b = plan.blockAt(x, z);
				int edge = a != b ? (a != null ? a.id() : b != null ? b.id() : -1) : -1;
				if (edge != owner) {
					if (owner >= 0) {
						EDGES.add(new Edge(x, start, x, z, owner));
					}
					owner = edge;
					start = z;
				}
			}
		}
	}

	private static int streetColor(int kind, boolean open) {
		if (!open) {
			return 0x50909090;
		}
		return switch (kind) {
			case StreetPlanner.AVENUE -> 0xA0D9A55B;
			case StreetPlanner.RING -> 0x90E0C890;
			case StreetPlanner.LANE -> 0x80C0B090;
			case StreetPlanner.LOOP -> 0x80B0A0C0;
			default -> 0x804A8CFF;
		};
	}

	/** Cor da borda do quarteirao: zona (pronto), laranja (falta terraplenagem) ou cinza (anel fechado). */
	static int blockColor(MasterPlan.Block block, int open) {
		if (block.ring() > Math.min(6, open + 1)) {
			return CLOSED;
		}
		if (!block.prepared()) {
			return block.ring() > open ? (UNPREPARED & 0x00FFFFFF) | 0x90000000 : UNPREPARED;
		}
		return zoneColor(block.zone());
	}

	static void render(Minecraft mc, VillagePayloads.Overlay o, MasterPlan plan) {
		if (cached != o) {
			build(o, plan);
		}
		BlockPos c = o.center();
		Vec3 eye = mc.player.position();
		int open = o.openRings();
		double range2 = RANGE * RANGE;
		// ruas
		for (Run run : RUNS) {
			double cx = c.getX() + (run.x0 + run.x1) / 2.0;
			double cz = c.getZ() + run.z + 0.5;
			if (eye.distanceToSqr(cx, eye.y, cz) > range2) {
				continue;
			}
			int color = streetColor(run.kind, run.ring <= open);
			double y = run.y + 1.03;
			Gizmos.cuboid(new AABB(c.getX() + run.x0, y, c.getZ() + run.z, c.getX() + run.x1 + 1, y + 0.05, c.getZ() + run.z + 1), GizmoStyle.fill(color));
		}
		// bordas dos quarteiroes no plato
		for (Edge edge : EDGES) {
			MasterPlan.Block block = plan.block(edge.block);
			double mx = c.getX() + (edge.x0 + edge.x1) / 2.0;
			double mz = c.getZ() + (edge.z0 + edge.z1) / 2.0;
			if (block == null || eye.distanceToSqr(mx, eye.y, mz) > range2) {
				continue;
			}
			double y = block.target() + 1.06;
			Gizmos.line(new Vec3(c.getX() + edge.x0, y, c.getZ() + edge.z0), new Vec3(c.getX() + edge.x1, y, c.getZ() + edge.z1), blockColor(block, open),
					2.5F);
		}
		// altura alvo dos quarteiroes perto
		for (MasterPlan.Block block : plan.blocks()) {
			double bx = c.getX() + block.cx() + 0.5;
			double bz = c.getZ() + block.cz() + 0.5;
			if (block.cells() < 40 || eye.distanceToSqr(bx, eye.y, bz) > 48 * 48) {
				continue;
			}
			int color = blockColor(block, open);
			String text = Component.translatable(block.prepared() ? "gui.mlinnotas.village.plan.block_ready" : "gui.mlinnotas.village.plan.block",
					block.ring(), block.target() + 1).getString();
			Gizmos.billboardText(text, new Vec3(bx, block.target() + 2.6, bz), TextGizmo.Style.forColorAndCentered(color).withScale(1.2F));
		}
		// lotes livres do plano (reservados aparecem como obra)
		for (MasterPlan.Parcel p : plan.parcels()) {
			MasterPlan.Block block = plan.block(p.block());
			var r = p.rect();
			double px = c.getX() + (r.x0() + r.x1() + 1) / 2.0;
			double pz = c.getZ() + (r.z0() + r.z1() + 1) / 2.0;
			if (block == null || p.retired() || reserved(o, p.id()) || eye.distanceToSqr(px, eye.y, pz) > range2) {
				continue;
			}
			int color = p.player() ? PLAYER : block.ring() > open ? CLOSED : zoneColor(p.zone());
			double y = block.target() + 1.04;
			Gizmos.cuboid(new AABB(c.getX() + r.x0() + 0.15, y, c.getZ() + r.z0() + 0.15, c.getX() + r.x1() + 0.85, y + 0.04, c.getZ() + r.z1() + 0.85),
					GizmoStyle.strokeAndFill((color & 0x00FFFFFF) | 0xA0000000, 1.5F, (color & 0x00FFFFFF) | 0x18000000));
		}
		// areas de jogador
		for (var r : plan.playerAreas()) {
			double px = c.getX() + (r.x0() + r.x1() + 1) / 2.0;
			double pz = c.getZ() + (r.z0() + r.z1() + 1) / 2.0;
			if (eye.distanceToSqr(px, eye.y, pz) > range2) {
				continue;
			}
			double y = VillageOverlay.surface(mc.level, (int) Math.floor(px), (int) Math.floor(pz));
			Gizmos.cuboid(new AABB(c.getX() + r.x0(), y, c.getZ() + r.z0(), c.getX() + r.x1() + 1, y + 2.5, c.getZ() + r.z1() + 1),
					GizmoStyle.stroke(PLAYER, 2.5F));
			Gizmos.billboardText(Component.translatable("gui.mlinnotas.village.plan.player").getString(), new Vec3(px, y + 3.2, pz),
					TextGizmo.Style.forColorAndCentered(PLAYER).withScale(1.2F));
		}
	}

	private static boolean reserved(VillagePayloads.Overlay o, int parcel) {
		for (var lot : o.layout().lots()) {
			if (lot.parcel() == parcel) {
				return true;
			}
		}
		return false;
	}
}
