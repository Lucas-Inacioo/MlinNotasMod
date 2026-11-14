package com.mlin.notas.village.works;

import com.mlin.notas.village.Facing;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillageState;

/**
 * Celeiros de apoio e moinhos decorativos para os lotes vagos dos arredores (Vilas 2.0, fase 5; logica pura; sobras
 * das obras publicas da fase 3, categoria {@link WorkCategory#RURAL}). Sao decoracao gratuita: sem funcao, sem baus, e
 * saem sozinhos quando alguem reserva o lote ({@code releaseLot}). Cada um e desenhado num quadro local (+z = frente
 * do lote) e girado para a frente real do lote.
 *
 * <p>Referencias de desenho (escritas do zero): celeiro de tabuas com telhado de duas aguas, porta larga e sotao de feno
 * (Medieval Barn do Planet Minecraft) com curral de cerca na frente; moinho de torre de pedra com capuz de piramide e
 * quatro velas de cerca e la (Medieval Windmill do Planet Minecraft).
 */
final class RuralBuildings {
	private RuralBuildings() {
	}

	/** Quadro local do lote: x ao longo da rua, z da rua (+z) para dentro (-z, fundo). */
	private static final class Frame {
		final Blueprint bp;
		final VillageState.Rect rect;
		final int w;
		final int d;
		final int rot;
		final int h;
		final WorkContext ctx;

		Frame(WorkContext ctx, Blueprint bp, MasterPlan.Parcel p, int h) {
			this.ctx = ctx;
			this.bp = bp;
			this.rect = p.rect();
			this.w = p.along();
			this.d = p.deep();
			this.rot = VillagePlanner.rotationFor(p.front());
			this.h = h;
		}

		int[] world(int lx, int lz) {
			int[] r = VillagePlanner.rotate(lx, lz, w, d, rot);
			return new int[] {rect.x0() + r[0], rect.z0() + r[1]};
		}

		boolean free(int lx, int lz) {
			int[] p = world(lx, lz);
			return ctx.free(p[0], p[1]);
		}

		String face(Facing local) {
			return ParcelWorks.face(local.rotate(rot));
		}

		void ground(int lx, int lz, String state) {
			int[] p = world(lx, lz);
			if (ctx.free(p[0], p[1])) {
				bp.surface(p[0], h, p[1], state);
			}
		}

		void put(int lx, int ly, int lz, String state) {
			int[] p = world(lx, lz);
			if (ctx.free(p[0], p[1])) {
				bp.above(p[0], h + ly, p[1], state);
			}
		}

		void stairs(int lx, int ly, int lz, Facing local, Mats m) {
			put(lx, ly, lz, m.stairs(face(local), false));
		}
	}

	private static String log(Mats m, String axis) {
		return m.log() + "[axis=" + axis + "]";
	}

	/** Eixo do tronco no quadro local: {@code alongX} local vira x ou z do mundo conforme o giro. */
	private static String axis(Frame f, boolean localAlongX) {
		boolean swap = f.rot % 2 == 1;
		return localAlongX != swap ? "x" : "z";
	}

	/**
	 * Celeiro de apoio: 9x6 de tabuas e troncos no fundo do lote, telhado de duas aguas com cumeeira ao longo da rua,
	 * porta larga aberta na frente com feno e barris, e um curral de cerca na frente com cocho e fardos.
	 */
	static void barn(WorkContext ctx, Blueprint bp, MasterPlan.Parcel p, int h) {
		Mats m = ctx.mats();
		Frame f = new Frame(ctx, bp, p, h);
		int cx = f.w / 2;
		int x0 = cx - 4;
		int x1 = cx + 4;
		int z0 = 1;
		int z1 = 6;
		for (int x = 1; x <= f.w - 2; x++) {
			for (int z = 1; z <= f.d - 2; z++) {
				f.ground(x, z, m.lawn());
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				f.ground(x, z, x == x0 || x == x1 || z == z0 || z == z1 ? m.cobble() : m.planks());
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge) {
					continue;
				}
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				boolean post = corner || (x - x0) % 4 == 0 && (z == z0 || z == z1);
				for (int y = 1; y <= 4; y++) {
					boolean frontOpening = z == z1 && Math.abs(x - cx) <= 1 && y <= 3;
					if (frontOpening) {
						continue;
					}
					boolean window = y == 2 && !post && (z == z0 || (x == x0 || x == x1) && z % 2 == 0 && z != z1);
					String block = y == 4 ? log(m, axis(f, z == z0 || z == z1)) : post ? log(m, "y") : window ? "minecraft:glass_pane" : m.planks();
					f.put(x, y, z, block);
				}
			}
		}
		// porta larga: batente de tronco e viga em cima
		for (int dx : new int[] {-2, 2}) {
			for (int y = 1; y <= 3; y++) {
				f.put(cx + dx, y, z1, log(m, "y"));
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			f.put(cx + dx, 4, z1, log(m, axis(f, true)));
		}
		// telhado: cumeeira ao longo de x, degraus para os dois lados, oitoes de tabua
		for (int i = 0; ; i++) {
			int lo = z0 - 1 + i;
			int hi = z1 + 1 - i;
			int y = 4 + i;
			if (lo > hi) {
				break;
			}
			for (int x = x0 - 1; x <= x1 + 1; x++) {
				if (lo == hi) {
					f.put(x, y, lo, m.slabBottom());
				} else {
					f.stairs(x, y, lo, Facing.NORTH, m);
					f.stairs(x, y, hi, Facing.SOUTH, m);
				}
			}
			if (i >= 1) {
				for (int z = lo + 1; z <= hi - 1; z++) {
					f.put(x0, y, z, m.planks());
					f.put(x1, y, z, m.planks());
				}
			}
			if (lo == hi) {
				break;
			}
		}
		// janela do sotao no oitao da frente
		f.put(cx, 5, z1, "minecraft:hay_block");
		f.put(cx, 6, z1, m.planks());
		// interior: feno, barris e uma lanterna
		for (int x = x0 + 1; x <= x0 + 3; x++) {
			f.put(x, 1, z0 + 1, "minecraft:hay_block");
			f.put(x, 2, z0 + 1, "minecraft:hay_block");
		}
		f.put(x1 - 1, 1, z0 + 1, "minecraft:barrel[facing=up]");
		f.put(x1 - 2, 1, z0 + 1, "minecraft:barrel[facing=up]");
		f.put(cx, 1, z0 + 2, "minecraft:hay_block");
		f.put(cx, 2, z0 + 2, Mats.lantern(false));
		// curral na frente: cerca em volta, cocho e fardos
		for (int x = 1; x <= f.w - 2; x++) {
			for (int z = z1 + 2; z <= f.d - 2; z++) {
				boolean edge = x == 1 || x == f.w - 2 || z == z1 + 2 || z == f.d - 2;
				if (edge) {
					boolean gate = z == f.d - 2 && x == cx;
					f.put(x, 1, z, gate ? m.fence().replace("_fence", "_fence_gate") + "[facing=" + f.face(Facing.SOUTH) + ",open=true]" : m.fence());
				}
			}
		}
		if (f.d - 2 >= z1 + 4) {
			f.put(x1 - 1, 1, z1 + 3, "minecraft:water_cauldron[level=3]");
			f.put(x0 + 1, 1, z1 + 3, "minecraft:hay_block");
			f.put(x0 + 2, 1, z1 + 3, "minecraft:hay_block");
		}
	}

	/**
	 * Moinho de vento decorativo: torre de pedra 5x5 com porta na frente e janelas, capuz de piramide de degraus e as
	 * quatro velas (cerca + la) no alto da fachada, cercadinho de grama e feno ao lado.
	 */
	static void windmill(WorkContext ctx, Blueprint bp, MasterPlan.Parcel p, int h) {
		Mats m = ctx.mats();
		Frame f = new Frame(ctx, bp, p, h);
		int cx = f.w / 2;
		int x0 = cx - 2;
		int x1 = cx + 2;
		int z0 = 2;
		int z1 = 6;
		for (int x = 1; x <= f.w - 2; x++) {
			for (int z = 1; z <= f.d - 2; z++) {
				f.ground(x, z, m.lawn());
			}
		}
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				f.ground(x, z, x == x0 || x == x1 || z == z0 || z == z1 ? m.cobble() : m.planks());
			}
		}
		int top = 8;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
				if (!edge) {
					continue;
				}
				boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
				for (int y = 1; y <= top; y++) {
					boolean door = z == z1 && x == cx && y <= 2;
					if (door) {
						continue;
					}
					boolean window = !corner && (y == 3 || y == 6) && (x == cx || z == (z0 + z1) / 2) && !(z == z1 && x == cx);
					String block = corner ? m.bricks() : y == top ? log(m, axis(f, z == z0 || z == z1)) : window ? "minecraft:glass_pane"
							: (x * 7 + y * 13 + z * 5) % 5 == 0 ? m.bricks() : m.cobble();
					f.put(x, y, z, block);
				}
			}
		}
		String doorState = "minecraft:" + m.wood() + "_door[facing=" + f.face(Facing.SOUTH) + ",half=%s,hinge=left,open=false]";
		f.put(cx, 1, z1, String.format(doorState, "lower"));
		f.put(cx, 2, z1, String.format(doorState, "upper"));
		f.put(cx - 1, 1, z1 + 1, Mats.lantern(false));
		f.ground(cx - 1, z1 + 1, m.cobble());
		// capuz de piramide: aneis de degraus e miolo cheio
		int lx = x0 - 1;
		int hx = x1 + 1;
		int lz = z0 - 1;
		int hz = z1 + 1;
		for (int i = 0; lx <= hx && lz <= hz; i++, lx++, hx--, lz++, hz--) {
			int y = top + i;
			if (lx == hx || lz == hz) {
				for (int x = lx; x <= hx; x++) {
					for (int z = lz; z <= hz; z++) {
						f.put(x, y, z, m.slabBottom());
					}
				}
				break;
			}
			for (int x = lx; x <= hx; x++) {
				for (int z = lz; z <= hz; z++) {
					if (z == lz) {
						f.stairs(x, y, z, Facing.NORTH, m);
					} else if (z == hz) {
						f.stairs(x, y, z, Facing.SOUTH, m);
					} else if (x == lx) {
						f.stairs(x, y, z, Facing.WEST, m);
					} else if (x == hx) {
						f.stairs(x, y, z, Facing.EAST, m);
					} else {
						f.put(x, y, z, m.planks());
					}
				}
			}
		}
		// velas na fachada: cubo de tronco e quatro bracos de cerca com pas de la
		int hub = 6;
		int sz = z1 + 2;
		f.put(cx, hub, z1 + 1, log(m, axis(f, false)));
		f.put(cx, hub, sz, log(m, axis(f, false)));
		String white = "minecraft:white_wool";
		String red = "minecraft:red_wool";
		for (int k = 1; k <= 4; k++) {
			f.put(cx + k, hub, sz, m.fence());
			f.put(cx - k, hub, sz, m.fence());
			f.put(cx, hub + k, sz, m.fence());
			f.put(cx, hub - k, sz, m.fence());
		}
		for (int k = 2; k <= 4; k++) {
			String cloth = k % 2 == 0 ? white : red;
			f.put(cx + k, hub + 1, sz, cloth);
			f.put(cx - 1, hub + k, sz, cloth);
			f.put(cx - k, hub - 1, sz, cloth);
			f.put(cx + 1, hub - k, sz, cloth);
		}
		// caminho de cascalho ate a porta e feno ao lado
		for (int z = z1 + 1; z <= f.d - 2; z++) {
			f.ground(cx, z, m.gravel());
		}
		f.put(x1 + 1, 1, z1 + 1, "minecraft:hay_block");
		f.put(x1 + 2, 1, z1 + 1, "minecraft:hay_block");
		f.put(x1 + 1, 2, z1 + 1, "minecraft:hay_block");
	}
}
