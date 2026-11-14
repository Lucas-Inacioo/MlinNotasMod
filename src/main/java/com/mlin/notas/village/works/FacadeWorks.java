package com.mlin.notas.village.works;

import com.mlin.notas.village.BuildingPlans;
import com.mlin.notas.village.Canvas;
import com.mlin.notas.village.Facing;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.SizeClass;
import com.mlin.notas.village.VPart;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.Zone;

import java.util.List;
import java.util.Set;

/**
 * Fachadas do centro (logica pura, categoria {@link WorkCategory#FACADES}): tres obras publicas gratuitas que vestem a rua
 * das zonas centrais sem mexer em nada que o jogador construiu (so entra onde ha ar, e tudo o que coloca fica no registro de
 * posse da vila):
 * <ul>
 * <li>{@link #facades}: casas do centro (TOWN) ganham pilastras de pedra encostadas na fachada, portico de pedra sobre a
 * porta e soleiras com vaso de flor nas janelas;</li>
 * <li>{@link #townhouses}: lotes vagos do centro (CAPITAL) ganham um sobrado de pedra e enxaimel de fachada na rua, com
 * sacada (some quando alguem reserva o lote, como as demais obras de lote vago);</li>
 * <li>{@link #arcades}: o comercio do centro (METROPOLE) ganha uma arcada coberta de arcos de pedra na frente.</li>
 * </ul>
 * Referencias de forma: casas de cidade medieval de fachada de pedra com pilastras e cornija, sobrados estreitos de
 * enxaimel com sacada, e arcadas de praca italiana/flamenga (Planet Minecraft "medieval town house", "arcade market
 * street", "portico"); escritas do zero.
 */
public final class FacadeWorks {
	private FacadeWorks() {
	}

	/** Obra pronta no lote: tipo, nivel e onde esta (para achar a fachada). {@code x}/{@code z} = canto do lote, {@code rot} = giro da planta. */
	public record Site(String kind, int level, long seed, int x, int z, int rot, int parcel) {
	}

	/** Comercio que ganha arcada. */
	static final Set<String> COMMERCE = Set.of("market", "inn", "tavern", "bakery", "butcher", "tailor", "enchanter", "library");

	/** Direcao do mundo para onde aponta a frente (+z da planta) com o giro {@code rot}. */
	static Facing front(int rot) {
		return switch (Math.floorMod(rot, 4)) {
			case 1 -> Facing.WEST;
			case 2 -> Facing.NORTH;
			case 3 -> Facing.EAST;
			default -> Facing.SOUTH;
		};
	}

	private static String face(Facing f) {
		return f.name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Planta do lote com o que a fachada precisa saber: linha da porta, extremos da frente e altura das paredes. */
	private record Front(Canvas canvas, int doorX, int doorZ, int x0, int x1, int top) {
	}

	private static Front front(Site site, com.mlin.notas.village.VillageStyle style) {
		Canvas c = BuildingPlans.plan(site.kind(), style, site.seed(), site.level());
		int doorX = -1;
		int doorZ = -1;
		for (var e : c.cells().entrySet()) {
			if (e.getValue().part() == VPart.DOOR && Canvas.keyY(e.getKey()) == 1) {
				doorX = Canvas.keyX(e.getKey());
				doorZ = Canvas.keyZ(e.getKey());
				break;
			}
		}
		if (doorX < 0) {
			return null;
		}
		int x0 = doorX;
		int x1 = doorX;
		for (int x = doorX; x >= 0 && isWall(c.part(x, 1, doorZ)); x--) {
			x0 = x;
		}
		for (int x = doorX; x < c.w() && isWall(c.part(x, 1, doorZ)); x++) {
			x1 = x;
		}
		int top = 1;
		for (int y = 1; y <= 12; y++) {
			VPart p = c.part(x0, y, doorZ);
			if (p == null || p == VPart.ROOF || p == VPart.ROOF_SLAB || p == VPart.ROOF_TOP || p == VPart.ROOF_BLOCK) {
				break;
			}
			top = y;
		}
		return new Front(c, doorX, doorZ, x0, x1, top + 1);
	}

	private static boolean isWall(VPart p) {
		return p != null && p != VPart.AIR && p != VPart.FLOWER && p != VPart.PLANT && p != VPart.PATH && p != VPart.SOIL;
	}

	/** Coluna do mundo de uma celula da planta (x, z) do lote. */
	private static int[] world(Site site, Canvas c, int x, int z) {
		int[] r = VillagePlanner.rotate(x, z, c.w(), c.d(), site.rot());
		return new int[] {site.x() + r[0], site.z() + r[1]};
	}

	// ---------------------------------------------------------------- fachadas das casas do centro

	public static Blueprint facades(WorkContext ctx, List<Site> sites) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		for (Site site : sites) {
			MasterPlan.Parcel parcel = site.parcel() < 0 ? null : plan.parcel(site.parcel());
			if (!site.kind().equals("house") || parcel == null || parcel.zone() != Zone.CENTER) {
				continue;
			}
			Front f = front(site, ctx.style());
			if (f == null) {
				continue;
			}
			Canvas c = f.canvas();
			int h = plan.block(parcel.block()).target();
			int zOut = f.doorZ() + 1;
			// pilastras nas pontas e dos dois lados do portico, do soco ate logo abaixo do beiral
			int[] cols = {f.x0(), f.x1(), f.doorX() - 2, f.doorX() + 2};
			for (int x : cols) {
				if (x < f.x0() || x > f.x1()) {
					continue;
				}
				int[] w = world(site, c, x, zOut);
				if (!ctx.free(w[0], w[1])) {
					continue;
				}
				bp.above(w[0], h + 1, w[1], m.bricks());
				for (int y = 2; y <= f.top() - 2; y++) {
					bp.above(w[0], h + y, w[1], m.wall());
				}
				bp.above(w[0], h + f.top() - 1, w[1], m.bricks());
			}
			// portico: colunas dos dois lados da porta, verga de pedra e laje
			for (int dx : new int[] {-1, 1}) {
				int[] w = world(site, c, f.doorX() + dx, zOut);
				if (ctx.free(w[0], w[1])) {
					bp.above(w[0], h + 1, w[1], m.bricks());
					bp.above(w[0], h + 2, w[1], m.wall());
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				int[] w = world(site, c, f.doorX() + dx, zOut);
				bp.above(w[0], h + 3, w[1], m.bricks());
			}
			if (f.top() > 4) {
				for (int dx = -1; dx <= 1; dx++) {
					int[] w = world(site, c, f.doorX() + dx, zOut);
					bp.above(w[0], h + 4, w[1], slabTop(m));
				}
			}
			// soleira e vaso de flor sob cada janela da frente
			for (var e : c.cells().entrySet()) {
				if (e.getValue().part() != VPart.WINDOW || Canvas.keyZ(e.getKey()) != f.doorZ()) {
					continue;
				}
				int x = Canvas.keyX(e.getKey());
				int y = Canvas.keyY(e.getKey());
				int[] w = world(site, c, x, zOut);
				if (!ctx.free(w[0], w[1])) {
					continue;
				}
				bp.above(w[0], h + y - 1, w[1], m.brickSlab());
				bp.above(w[0], h + y, w[1], ctx.roll(w[0], w[1], 41, 2) == 0 ? "minecraft:potted_poppy" : "minecraft:potted_cornflower");
			}
		}
		return bp;
	}

	private static String slabTop(Mats m) {
		return m.brickSlab().replace("bottom", "top");
	}

	// ---------------------------------------------------------------- arcadas do comercio

	public static Blueprint arcades(WorkContext ctx, List<Site> sites) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		Mats m = ctx.mats();
		for (Site site : sites) {
			MasterPlan.Parcel parcel = site.parcel() < 0 ? null : plan.parcel(site.parcel());
			if (!COMMERCE.contains(site.kind()) || parcel == null || parcel.zone() != Zone.CENTER) {
				continue;
			}
			Front f = front(site, ctx.style());
			if (f == null) {
				continue;
			}
			Canvas c = f.canvas();
			int zPillar = f.doorZ() + 3;
			if (zPillar > c.d() - 2) {
				continue;
			}
			int h = plan.block(parcel.block()).target();
			// pilares a 3 de distancia (o vao do meio, da porta, tem 3 de largura); os da frente do bosque da porta ficam livres
			int[] xs = {f.doorX() - 5, f.doorX() - 2, f.doorX() + 2, f.doorX() + 5};
			boolean any = false;
			for (int x : xs) {
				if (x < f.x0() || x > f.x1()) {
					continue;
				}
				int[] w = world(site, c, x, zPillar);
				if (!ctx.free(w[0], w[1])) {
					continue;
				}
				any = true;
				bp.surface(w[0], h, w[1], m.bricks());
				bp.above(w[0], h + 1, w[1], m.bricks());
				bp.above(w[0], h + 2, w[1], m.wall());
				bp.above(w[0], h + 3, w[1], m.bricks());
			}
			if (!any) {
				continue;
			}
			// arcos entre os pilares: degraus de cabeca para baixo nos vaos de 2 e laje no vao de 3
			for (int x = f.x0(); x <= f.x1(); x++) {
				int[] w = world(site, c, x, zPillar);
				boolean pillar = false;
				for (int px : xs) {
					pillar |= px == x;
				}
				if (pillar) {
					continue;
				}
				int left = nearest(xs, x, -1);
				int right = nearest(xs, x, 1);
				if (left == Integer.MIN_VALUE && right == Integer.MAX_VALUE) {
					continue;
				}
				// cada celula de arco encosta num pilar: o degrau de cabeca para baixo "nasce" dele
				int[] wl = world(site, c, x - 1, zPillar);
				int[] wr = world(site, c, x + 1, zPillar);
				boolean nextToLeft = x - 1 == left;
				boolean nextToRight = x + 1 == right;
				if (nextToLeft && nextToRight) {
					bp.above(w[0], h + 3, w[1], slabTop(m));
				} else if (nextToLeft || nextToRight) {
					int[] p = nextToLeft ? wl : wr;
					Facing toward = Facing.toward(w[0] - p[0], w[1] - p[1]);
					bp.above(w[0], h + 3, w[1], m.stoneStairs(face(toward), true));
				} else {
					bp.above(w[0], h + 3, w[1], slabTop(m));
				}
				if (nextToLeft) {
					bp.above(w[0], h + 2, w[1], Mats.lantern(true));
				}
			}
			// cobertura do passeio: laje de pedra em cima de tudo, do beiral ate os pilares
			for (int x = f.x0(); x <= f.x1(); x++) {
				for (int z = f.doorZ() + 1; z <= zPillar; z++) {
					int[] w = world(site, c, x, z);
					if (!ctx.blocked().test(w[0], w[1])) {
						bp.above(w[0], h + 4, w[1], m.brickSlab());
					}
				}
			}
			// calcada de pedra no passeio
			for (int x = f.x0(); x <= f.x1(); x++) {
				for (int z = f.doorZ() + 1; z <= zPillar; z++) {
					int[] w = world(site, c, x, z);
					if (!ctx.blocked().test(w[0], w[1]) && Math.floorMod(x + z, 2) == 0) {
						bp.surface(w[0], h, w[1], m.smooth());
					}
				}
			}
		}
		return bp;
	}

	private static int nearest(int[] xs, int x, int dir) {
		int best = dir < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
		for (int p : xs) {
			if (dir < 0 && p < x && p > best) {
				best = p;
			}
			if (dir > 0 && p > x && p < best) {
				best = p;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- sobrados dos lotes vagos do centro

	/** Moldura de um lote: u ao longo da frente, v da rua para dentro. */
	private record Frame(VillageState.Rect r, Facing front) {
		int[] cell(int u, int v) {
			return switch (front) {
				case SOUTH -> new int[] {r.x0() + u, r.z1() - v};
				case NORTH -> new int[] {r.x0() + u, r.z0() + v};
				case EAST -> new int[] {r.x1() - v, r.z0() + u};
				case WEST -> new int[] {r.x0() + v, r.z0() + u};
			};
		}

		int along() {
			return front.alongX() ? r.z1() - r.z0() + 1 : r.x1() - r.x0() + 1;
		}

		int deep() {
			return front.alongX() ? r.x1() - r.x0() + 1 : r.z1() - r.z0() + 1;
		}

		/** Direcao do mundo do aumento de u. */
		Facing uDir() {
			int[] a = cell(0, 0);
			int[] b = cell(1, 0);
			return Facing.toward(b[0] - a[0], b[1] - a[1]);
		}
	}

	public static Blueprint townhouses(WorkContext ctx) {
		Blueprint bp = new Blueprint();
		MasterPlan plan = ctx.plan();
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.zone() != Zone.CENTER || p.size() == SizeClass.T || p.size() == SizeClass.W || p.size() == SizeClass.WL || !ParcelWorks.vacant(ctx, p)) {
				continue;
			}
			MasterPlan.Block b = plan.block(p.block());
			Frame fr = new Frame(p.rect(), p.front());
			int width = Math.min(fr.along() - 2, 9);
			if (width % 2 == 0) {
				width--;
			}
			int depth = Math.min(fr.deep() - 4, 7);
			if (width < 5 || depth < 4) {
				continue;
			}
			bp.needRing(b.ring());
			townhouse(ctx, bp, fr, b.target(), width, depth, ctx.roll(p.rect().x0(), p.rect().z0(), 43, 3));
		}
		return bp;
	}

	/** Sobrado de {@code width} x {@code depth}: ground floor in stone, upper in half-timber, front gable, balcony. */
	static void townhouse(WorkContext ctx, Blueprint bp, Frame fr, int h, int width, int depth, int variant) {
		Mats m = ctx.mats();
		int u0 = (fr.along() - width) / 2;
		int v0 = 2;
		int v1 = v0 + depth - 1;
		int mid = u0 + width / 2;
		Facing out = fr.front;
		Facing uDir = fr.uDir();
		String outName = face(out);
		String plaster = m.plaster();
		String beamAxis = uDir.alongX() ? "x" : "z";
		String depthAxis = uDir.alongX() ? "z" : "x";
		// piso e soco
		for (int u = u0; u < u0 + width; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean edge = u == u0 || u == u0 + width - 1 || v == v0 || v == v1;
				int[] w = fr.cell(u, v);
				if (!ctx.free(w[0], w[1])) {
					return;
				}
			}
		}
		for (int u = u0; u < u0 + width; u++) {
			for (int v = v0; v <= v1; v++) {
				int[] w = fr.cell(u, v);
				boolean edge = u == u0 || u == u0 + width - 1 || v == v0 || v == v1;
				bp.surface(w[0], h, w[1], edge ? m.smooth() : m.planks());
			}
		}
		// paredes: andar de baixo de pedra com cantoneiras, andar de cima de enxaimel, vigamento entre os dois
		for (int u = u0; u < u0 + width; u++) {
			for (int v = v0; v <= v1; v++) {
				boolean edge = u == u0 || u == u0 + width - 1 || v == v0 || v == v1;
				boolean corner = (u == u0 || u == u0 + width - 1) && (v == v0 || v == v1);
				int[] w = fr.cell(u, v);
				if (!edge) {
					continue;
				}
				for (int y = 1; y <= 3; y++) {
					bp.above(w[0], h + y, w[1], corner ? m.accent() : y == 1 ? m.bricks() : Math.floorMod(u * 5 + v * 3 + y, 7) == 0 ? "minecraft:mossy_stone_bricks" : m.bricks());
				}
				// viga do piso de cima
				String beamAxisHere = (v == v0 || v == v1) ? beamAxis : depthAxis;
				bp.above(w[0], h + 4, w[1], m.log() + "[axis=" + (corner ? "y" : beamAxisHere) + "]");
				for (int y = 5; y <= 7; y++) {
					boolean post = corner || (u - u0) % 3 == 0 && (v == v0 || v == v1) || (v - v0) % 3 == 0 && (u == u0 || u == u0 + width - 1);
					bp.above(w[0], h + y, w[1], post ? m.log() : plaster);
				}
				bp.above(w[0], h + 8, w[1], m.log() + "[axis=" + (corner ? "y" : (v == v0 || v == v1) ? beamAxis : depthAxis) + "]");
			}
		}
		// piso de cima (laje de tabuas) com um vao para a escada de mao
		for (int u = u0 + 1; u < u0 + width - 1; u++) {
			for (int v = v0 + 1; v < v1; v++) {
				int[] w = fr.cell(u, v);
				boolean ladder = u == u0 + 1 && v == v1 - 1;
				if (!ladder) {
					bp.above(w[0], h + 4, w[1], m.planks());
				}
			}
		}
		int[] lad = fr.cell(u0 + 1, v1 - 1);
		for (int y = 1; y <= 4; y++) {
			bp.above(lad[0], h + y, lad[1], "minecraft:ladder[facing=" + outName + "]");
		}
		// porta no meio, janelas dos lados, janelas e balcao em cima
		String door = "minecraft:" + m.wood() + "_door[facing=" + outName + ",half=lower,hinge=left,open=false]";
		int[] dw = fr.cell(mid, v0);
		bp.above(dw[0], h + 1, dw[1], door);
		bp.above(dw[0], h + 2, dw[1], door.replace("half=lower", "half=upper"));
		for (int du : new int[] {-2, 2}) {
			int[] w = fr.cell(mid + du, v0);
			bp.above(w[0], h + 2, w[1], "minecraft:glass_pane");
			int[] sill = fr.cell(mid + du, v0 - 1);
			if (ctx.free(sill[0], sill[1])) {
				bp.above(sill[0], h + 1, sill[1], m.brickSlab());
				bp.above(sill[0], h + 2, sill[1], variant == 0 ? "minecraft:potted_poppy" : "minecraft:potted_azure_bluet");
			}
		}
		for (int du : new int[] {-2, 0, 2}) {
			int[] w = fr.cell(mid + du, v0);
			bp.above(w[0], h + 6, w[1], "minecraft:glass_pane");
			if (du == 0) {
				bp.above(w[0], h + 5, w[1], door);
				bp.above(w[0], h + 6, w[1], door.replace("half=lower", "half=upper"));
			}
		}
		// laterais
		for (int v : new int[] {v0 + 2, v1 - 2}) {
			for (int side : new int[] {u0, u0 + width - 1}) {
				if (v > v0 && v < v1) {
					int[] w = fr.cell(side, v);
					bp.above(w[0], h + 2, w[1], "minecraft:glass_pane");
					bp.above(w[0], h + 6, w[1], "minecraft:glass_pane");
				}
			}
		}
		// sacada em balanço na frente do andar de cima: laje, grade de cerca e arrimo
		for (int du = -1; du <= 1; du++) {
			int[] w = fr.cell(mid + du, v0 - 1);
			bp.above(w[0], h + 4, w[1], m.slabBottom());
			bp.above(w[0], h + 5, w[1], m.fence());
		}
		// pilares de apoio da sacada
		for (int du : new int[] {-1, 1}) {
			int[] w = fr.cell(mid + du, v0 - 1);
			bp.above(w[0], h + 1, w[1], m.wall());
			bp.above(w[0], h + 2, w[1], m.wall());
			bp.above(w[0], h + 3, w[1], m.wall());
		}
		// telhado de duas aguas com a empena para a rua: cumeeira ao longo de v
		for (int i = 0; ; i++) {
			int y = 8 + i;
			int ua = u0 - 1 + i;
			int ub = u0 + width - i;
			if (ua > ub) {
				break;
			}
			for (int v = v0 - 1; v <= v1 + 1; v++) {
				if (ua == ub) {
					int[] w = fr.cell(ua, v);
					bp.above(w[0], h + y, w[1], m.slabBottom());
				} else {
					int[] wa = fr.cell(ua, v);
					int[] wb = fr.cell(ub, v);
					bp.above(wa[0], h + y, wa[1], m.stairs(face(uDir.opposite()), false));
					bp.above(wb[0], h + y, wb[1], m.stairs(face(uDir), false));
				}
			}
			// empenas: triangulo de reboco nas duas pontas
			if (i >= 1) {
				for (int u = ua + 1; u < ub; u++) {
					int[] wf = fr.cell(u, v0);
					int[] wk = fr.cell(u, v1);
					bp.above(wf[0], h + y, wf[1], plaster);
					bp.above(wk[0], h + y, wk[1], plaster);
				}
			}
		}
		int[] rw = fr.cell(mid, v0);
		bp.above(rw[0], h + 10, rw[1], "minecraft:glass_pane");
		// chamine na lateral do fundo
		int[] ch = fr.cell(u0 + width - 1, v1 - 1);
		for (int y = 4; y <= 12; y++) {
			bp.above(ch[0], h + y, ch[1], m.bricks());
		}
		bp.above(ch[0], h + 13, ch[1], "minecraft:campfire[lit=true,signal_fire=false]");
		// lanterna sobre a porta e estante no fundo
		int[] lamp = fr.cell(mid + 1, v0 + 1);
		bp.above(lamp[0], h + 1, lamp[1], "minecraft:barrel");
		bp.above(lamp[0], h + 2, lamp[1], Mats.lantern(false));
		for (int u = u0 + 2; u < u0 + width - 1; u++) {
			int[] w = fr.cell(u, v1 - 1);
			bp.above(w[0], h + 1, w[1], "minecraft:bookshelf");
		}
	}
}
