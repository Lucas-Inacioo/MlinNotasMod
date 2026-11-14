package com.mlin.notas.village.works;

import com.mlin.notas.village.Facing;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.SizeClass;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.Zone;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Obras publicas em cima dos lotes e das sobras do plano diretor (logica pura): estacas com placa "Lote livre" nos lotes
 * ainda vagos de um anel novo, jardins nos lotes minusculos, hortas, pomares e pastos cercados nos lotes vagos dos
 * arredores e a decoracao de rua (arvores, bancos, barris, barracas) nas sobras de quarteirao fora de qualquer lote.
 * Tudo que fica dentro de lote vago e da vila e sai quando alguem reserva o lote ({@code releaseLot} no servidor).
 *
 * <p>Referencias de forma: hortas cercadas e pomares de vila medieval (Planet Minecraft "medieval farm", "village
 * orchard"), bancos e barracas de feira de rua; escrito do zero.
 */
public final class ParcelWorks {
	private ParcelWorks() {
	}

	/** Lote vago do anel: nao reservado, nao de jogador, nao aposentado, anel pronto. */
	static boolean vacant(WorkContext ctx, MasterPlan.Parcel p) {
		MasterPlan.Block b = ctx.plan().block(p.block());
		return !p.retired() && !p.player() && b != null && b.prepared() && b.ring() <= ctx.openRings() && !ctx.reservedParcels().contains(p.id());
	}

	static String face(Facing f) {
		return f.name().toLowerCase(java.util.Locale.ROOT);
	}

	static int rotation(Facing f) {
		return switch (f) {
			case SOUTH -> 0;
			case WEST -> 4;
			case NORTH -> 8;
			case EAST -> 12;
		};
	}

	// ---------------------------------------------------------------- estacas e placa

	/** "Lote livre": estacas de cerca nos quatro cantos e uma placa na frente de cada lote vago do anel. */
	public static Blueprint stakes(WorkContext ctx, int ring) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		for (MasterPlan.Parcel p : plan.parcels()) {
			MasterPlan.Block b = plan.block(p.block());
			if (b == null || b.ring() != ring || !vacant(ctx, p)) {
				continue;
			}
			bp.needRing(ring);
			int h = b.target();
			VillageState.Rect r = p.rect();
			for (int[] c : p.corners()) {
				if (ctx.free(c[0], c[1])) {
					bp.above(c[0], h + 1, c[1], m.fence());
				}
			}
			// placa a 1 do canto da frente (esquerdo de quem olha da rua), dentro da faixa da frente
			int sx;
			int sz;
			switch (p.front()) {
				case SOUTH -> {
					sx = r.x0() + 1;
					sz = r.z1();
				}
				case NORTH -> {
					sx = r.x1() - 1;
					sz = r.z0();
				}
				case EAST -> {
					sx = r.x1();
					sz = r.z1() - 1;
				}
				default -> {
					sx = r.x0();
					sz = r.z0() + 1;
				}
			}
			if (p.along() >= 5 && ctx.free(sx, sz)) {
				bp.add(WBlock.sign(sx, h + 1, sz, m.sign(rotation(p.front())), "tr:sign.mlinnotas.free_lot", p.along() + " x " + p.deep(), "", ""));
			}
		}
		return bp;
	}

	// ---------------------------------------------------------------- jardins

	/** Lotes minusculos viram jardim: grama, canteiros nas bordas, arvore, banco virado para a rua. */
	public static Blueprint gardens(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.size() != SizeClass.T || !vacant(ctx, p)) {
				continue;
			}
			MasterPlan.Block b = plan.block(p.block());
			bp.needRing(b.ring());
			int h = b.target();
			VillageState.Rect r = p.rect();
			int cx = (r.x0() + r.x1()) / 2;
			int cz = (r.z0() + r.z1()) / 2;
			for (int x = r.x0(); x <= r.x1(); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					if (!ctx.free(x, z)) {
						continue;
					}
					boolean edge = x == r.x0() || x == r.x1() || z == r.z0() || z == r.z1();
					boolean frontMid = isFrontMiddle(p, x, z);
					bp.surface(x, h, z, frontMid || (x == cx || z == cz) && !edge ? m.gravel() : m.lawn());
					if (edge && !frontMid) {
						bp.above(x, h + 1, z, ctx.roll(x, z, 21, 3) == 0 ? "minecraft:cornflower" : ctx.roll(x, z, 22, 2) == 0 ? "minecraft:azure_bluet"
								: "minecraft:poppy");
					}
				}
			}
			if (ctx.free(cx, cz)) {
				StreetWorks.tree(bp, m, cx, cz, h, ctx.roll(cx, cz, 4, 3));
			}
			// banco no fundo, virado para a rua
			int[] back = backMiddle(p);
			if (ctx.free(back[0], back[1]) && !(back[0] == cx && back[1] == cz)) {
				bp.above(back[0], h + 1, back[1], m.stairs(face(p.front().opposite()), false));
			}
		}
		return bp;
	}

	static boolean isFrontMiddle(MasterPlan.Parcel p, int x, int z) {
		VillageState.Rect r = p.rect();
		int mx = (r.x0() + r.x1()) / 2;
		int mz = (r.z0() + r.z1()) / 2;
		return switch (p.front()) {
			case SOUTH -> z >= r.z1() - 1 && x == mx;
			case NORTH -> z <= r.z0() + 1 && x == mx;
			case EAST -> x >= r.x1() - 1 && z == mz;
			case WEST -> x <= r.x0() + 1 && z == mz;
		};
	}

	static int[] backMiddle(MasterPlan.Parcel p) {
		VillageState.Rect r = p.rect();
		int mx = (r.x0() + r.x1()) / 2;
		int mz = (r.z0() + r.z1()) / 2;
		return switch (p.front()) {
			case SOUTH -> new int[] {mx, r.z0() + 1};
			case NORTH -> new int[] {mx, r.z1() - 1};
			case EAST -> new int[] {r.x0() + 1, mz};
			case WEST -> new int[] {r.x1() - 1, mz};
		};
	}

	// ---------------------------------------------------------------- arredores: horta, pomar, pasto

	/** Lotes vagos dos arredores (medios para cima): horta cercada, pomar ou pasto cercado, por sorteio estavel. */
	public static Blueprint rural(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.zone() != Zone.OUTSKIRTS || p.size() == SizeClass.T || p.size() == SizeClass.S || !vacant(ctx, p) || p.along() < 11 || p.deep() < 9) {
				continue;
			}
			MasterPlan.Block b = plan.block(p.block());
			bp.needRing(b.ring());
			int kind = ctx.roll(p.rect().x0(), p.rect().z0(), 31, 5);
			switch (kind) {
				case 0 -> plot(ctx, bp, p, b.target());
				case 1 -> orchard(ctx, bp, p, b.target());
				case 3 -> RuralBuildings.barn(ctx, bp, p, b.target());
				case 4 -> RuralBuildings.windmill(ctx, bp, p, b.target());
				default -> pasture(ctx, bp, p, b.target());
			}
		}
		return bp;
	}

	/** Cerca em volta do retangulo (1 de folga), com portao aberto no meio da frente. */
	static void fence(WorkContext ctx, Blueprint bp, MasterPlan.Parcel p, int h) {
		Mats m = ctx.mats();
		VillageState.Rect r = p.rect();
		for (int x = r.x0() + 1; x <= r.x1() - 1; x++) {
			for (int z = r.z0() + 1; z <= r.z1() - 1; z++) {
				boolean edge = x == r.x0() + 1 || x == r.x1() - 1 || z == r.z0() + 1 || z == r.z1() - 1;
				if (!edge || !ctx.free(x, z)) {
					continue;
				}
				if (frontGate(p, x, z)) {
					bp.above(x, h + 1, z, gate(m, p.front()));
					continue;
				}
				bp.above(x, h + 1, z, m.fence());
			}
		}
	}

	static String gate(Mats m, Facing front) {
		String wood = m.fence().replace("_fence", "_fence_gate");
		return wood + "[facing=" + face(front) + ",open=true]";
	}

	static boolean frontGate(MasterPlan.Parcel p, int x, int z) {
		VillageState.Rect r = p.rect();
		int mx = (r.x0() + r.x1()) / 2;
		int mz = (r.z0() + r.z1()) / 2;
		return switch (p.front()) {
			case SOUTH -> z == r.z1() - 1 && x == mx;
			case NORTH -> z == r.z0() + 1 && x == mx;
			case EAST -> x == r.x1() - 1 && z == mz;
			case WEST -> x == r.x0() + 1 && z == mz;
		};
	}

	/** Horta: canteiros de terra arada em faixas com um canal de agua entre cada duas, plantas maduras. */
	static void plot(WorkContext ctx, Blueprint bp, MasterPlan.Parcel p, int h) {
		Mats m = ctx.mats();
		VillageState.Rect r = p.rect();
		fence(ctx, bp, p, h);
		boolean alongX = !p.front().alongX();
		String[] crops = {"minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]", "minecraft:beetroots[age=3]"};
		int row = 0;
		for (int x = r.x0() + 2; x <= r.x1() - 2; x++) {
			for (int z = r.z0() + 2; z <= r.z1() - 2; z++) {
				if (!ctx.free(x, z)) {
					continue;
				}
				int line = alongX ? z - (r.z0() + 2) : x - (r.x0() + 2);
				if (line % 3 == 2) {
					bp.surface(x, h, z, "minecraft:water");
					continue;
				}
				bp.surface(x, h, z, "minecraft:farmland[moisture=7]");
				bp.above(x, h + 1, z, crops[Math.floorMod(line / 3 + ctx.roll(r.x0(), r.z0(), 33, 4), 4)]);
				row++;
			}
		}
	}

	/** Pomar: arvores em grade de 4 com grama e flores no chao, cerca em volta. */
	static void orchard(WorkContext ctx, Blueprint bp, MasterPlan.Parcel p, int h) {
		Mats m = ctx.mats();
		VillageState.Rect r = p.rect();
		fence(ctx, bp, p, h);
		for (int x = r.x0() + 2; x <= r.x1() - 2; x++) {
			for (int z = r.z0() + 2; z <= r.z1() - 2; z++) {
				if (!ctx.free(x, z)) {
					continue;
				}
				bp.surface(x, h, z, m.lawn());
				if ((x - r.x0() - 3) % 4 == 0 && (z - r.z0() - 3) % 4 == 0 && x <= r.x1() - 3 && z <= r.z1() - 3) {
					StreetWorks.tree(bp, m, x, z, h, ctx.roll(x, z, 6, 3));
				} else if (ctx.roll(x, z, 7, 5) == 0) {
					bp.above(x, h + 1, z, ctx.roll(x, z, 8, 2) == 0 ? "minecraft:dandelion" : "minecraft:short_grass");
				}
			}
		}
	}

	/** Pasto: cerca, feno e um bebedouro (caldeirao com agua). */
	static void pasture(WorkContext ctx, Blueprint bp, MasterPlan.Parcel p, int h) {
		Mats m = ctx.mats();
		VillageState.Rect r = p.rect();
		fence(ctx, bp, p, h);
		for (int x = r.x0() + 2; x <= r.x1() - 2; x++) {
			for (int z = r.z0() + 2; z <= r.z1() - 2; z++) {
				if (ctx.free(x, z)) {
					bp.surface(x, h, z, m.lawn());
				}
			}
		}
		int cx = (r.x0() + r.x1()) / 2;
		int cz = (r.z0() + r.z1()) / 2;
		int bx = r.x0() + 3;
		int bz = r.z0() + 3;
		if (ctx.free(bx, bz) && ctx.free(bx + 1, bz)) {
			bp.above(bx, h + 1, bz, "minecraft:hay_block");
			bp.above(bx + 1, h + 1, bz, "minecraft:hay_block");
			bp.above(bx, h + 2, bz, "minecraft:hay_block");
		}
		if (ctx.free(cx, cz)) {
			bp.above(cx, h + 1, cz, "minecraft:water_cauldron[level=3]");
		}
	}

	// ---------------------------------------------------------------- rua: arvores e decoracao

	/** Sobras de quarteirao (sem lote) encostadas numa rua de chao, em ordem estavel, prontas para receber coisa. */
	static List<int[]> sidewalk(WorkContext ctx, int maxStreetDist) {
		MasterPlan plan = ctx.plan();
		int e = plan.extent();
		List<int[]> out = new ArrayList<>();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (!ctx.yard(x, z) || !ctx.ready(x, z) || !ctx.free(x, z) || ctx.insideLot(x, z) || ctx.h(x, z) == Integer.MIN_VALUE) {
					continue;
				}
				int near = nearestStreet(plan, x, z, maxStreetDist);
				if (near > 0) {
					out.add(new int[] {x, z, near});
				}
			}
		}
		out.sort(Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		return out;
	}

	/** Distancia (1..max) ate a rua de chao mais perto andando nas 4 direcoes; 0 se nao tem. */
	static int nearestStreet(MasterPlan plan, int x, int z, int max) {
		for (int k = 1; k <= max; k++) {
			for (Facing f : Facing.values()) {
				if (plan.ground(x + f.dx * k, z + f.dz * k)) {
					return k;
				}
			}
		}
		return 0;
	}

	/** Facing da rua mais perto (para virar banco e barraca para ela). */
	static Facing towardStreet(MasterPlan plan, int x, int z) {
		for (int k = 1; k <= 4; k++) {
			for (Facing f : Facing.values()) {
				if (plan.ground(x + f.dx * k, z + f.dz * k)) {
					return f;
				}
			}
		}
		return Facing.SOUTH;
	}

	/** Arvores de rua (do bioma) nas sobras ao lado da rua, a pelo menos 9 blocos uma da outra. */
	public static Blueprint trees(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		Mats m = ctx.mats();
		List<int[]> picked = new ArrayList<>();
		for (int[] c : sidewalk(ctx, 2)) {
			if (c[2] != 1 && c[2] != 2) {
				continue;
			}
			boolean far = true;
			for (int[] p : picked) {
				int dx = p[0] - c[0];
				int dz = p[1] - c[1];
				if (dx * dx + dz * dz < 9 * 9) {
					far = false;
					break;
				}
			}
			// a copa (raio 2) nao pode encostar em lote nem em outro bloco de fora
			if (!far || !roomForTree(ctx, c[0], c[1]) || ctx.roll(c[0], c[1], 41, 3) != 0) {
				continue;
			}
			picked.add(c);
			bp.needRing(ctx.plan().ring(c[0], c[1]));
			int h = ctx.h(c[0], c[1]);
			StreetWorks.tree(bp, m, c[0], c[1], h, ctx.roll(c[0], c[1], 3, 3));
			for (Facing f : Facing.values()) {
				if (ctx.free(c[0] + f.dx, c[1] + f.dz) && ctx.yard(c[0] + f.dx, c[1] + f.dz) && ctx.h(c[0] + f.dx, c[1] + f.dz) == h
						&& ctx.roll(c[0] + f.dx, c[1] + f.dz, 43, 2) == 0) {
					bp.above(c[0] + f.dx, h + 1, c[1] + f.dz, "minecraft:poppy");
				}
			}
		}
		return bp;
	}

	static boolean roomForTree(WorkContext ctx, int x, int z) {
		int h = ctx.h(x, z);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (ctx.insideLot(x + dx, z + dz) || !ctx.free(x + dx, z + dz) || ctx.plan().code(x + dx, z + dz) == MasterPlan.OUT) {
					return false;
				}
				MasterPlan.Parcel q = ctx.plan().parcelAt(x + dx, z + dz);
				int hh = ctx.h(x + dx, z + dz);
				if (q != null || hh == Integer.MIN_VALUE || hh > h + 1) {
					return false;
				}
			}
		}
		return ctx.h(x, z) != Integer.MIN_VALUE && h != Integer.MIN_VALUE;
	}

	/** Bancos, barris e barracas de feira nas sobras ao lado da rua, bem espacados. */
	public static Blueprint decor(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		Mats m = ctx.mats();
		MasterPlan plan = ctx.plan();
		List<int[]> picked = new ArrayList<>();
		for (int[] c : sidewalk(ctx, 1)) {
			if (c[2] != 1) {
				continue;
			}
			int x = c[0];
			int z = c[1];
			boolean far = true;
			for (int[] p : picked) {
				int dx = p[0] - x;
				int dz = p[1] - z;
				if (dx * dx + dz * dz < 7 * 7) {
					far = false;
					break;
				}
			}
			if (!far || ctx.roll(x, z, 51, 3) != 0 || plan.parcelAt(x, z) != null) {
				continue;
			}
			int h = ctx.h(x, z);
			Facing toward = towardStreet(plan, x, z);
			int kind = ctx.roll(x, z, 52, 3);
			bp.needRing(plan.ring(x, z));
			if (kind == 0) {
				bp.above(x, h + 1, z, m.stairs(face(toward.opposite()), false));
			} else if (kind == 1) {
				bp.above(x, h + 1, z, "minecraft:barrel[facing=up]");
				if (ctx.free(x - toward.dz, z + toward.dx) && ctx.yard(x - toward.dz, z + toward.dx) && ctx.h(x - toward.dz, z + toward.dx) == h) {
					bp.above(x - toward.dz, h + 1, z + toward.dx, "minecraft:barrel[facing=up]");
					bp.above(x, h + 2, z, "minecraft:barrel[facing=up]");
				}
			} else {
				// barraca: balcao de laje e duas hastes com toldo de manta
				int sx = -toward.dz;
				int sz = toward.dx;
				boolean room = true;
				for (int k = -1; k <= 1; k++) {
					room &= ctx.free(x + sx * k, z + sz * k) && ctx.yard(x + sx * k, z + sz * k) && ctx.h(x + sx * k, z + sz * k) == h;
				}
				if (!room) {
					continue;
				}
				for (int k = -1; k <= 1; k++) {
					bp.above(x + sx * k, h + 1, z + sz * k, k == 0 ? m.slabBottom() : m.fence());
					if (k != 0) {
						bp.above(x + sx * k, h + 2, z + sz * k, m.fence());
					}
					bp.above(x + sx * k, h + 3, z + sz * k, ctx.roll(x, z, 53, 2) == 0 ? "minecraft:red_carpet" : "minecraft:white_carpet");
				}
				bp.above(x, h + 2, z, "minecraft:potted_poppy");
			}
			picked.add(c);
		}
		return bp;
	}
}
