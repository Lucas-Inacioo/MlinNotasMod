package com.mlin.notas.client.village;

import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillagePayloads;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.WorldTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Contorno da vila desenhado no mundo (so cliente, com os "gizmos" do jogo no {@code END_EXTRACTION} da Fabric): raio
 * da vila, cada lote planejado na cor da situacao (livre, precisa limpar, bloqueado, construido...), ruas, postes que
 * ainda vao ser feitos e os blocos que atrapalham. Aparece por um tempo depois de fundar/subir de nivel e pelo botao
 * "Mostrar area" do quadro.
 */
public final class VillageOverlay {
	public static final int FREE = 0xFF4CE04C;
	public static final int CLEAR = 0xFFFFD23C;
	public static final int BLOCKED = 0xFFFF4A3C;
	public static final int WATER = 0xFF4A8CFF;
	public static final int STEEP = 0xFFFF8C28;
	public static final int BUILT = 0xFF7C9CC8;
	public static final int BUILDING = 0xFF3CE6E6;
	private static final int RING = 0xFFFFC23C;
	private static final int ROAD = 0x70D2B07A;
	private static final int LAMP = 0xFFFFE680;
	private static final double RANGE = 160;

	private static VillagePayloads.Overlay current;
	private static long expires;
	private static final Map<Integer, VillagePayloads.LotInfo> INFO = new HashMap<>();

	private VillageOverlay() {
	}

	public static void accept(VillagePayloads.Overlay overlay) {
		if (overlay.seconds() <= 0) {
			clear();
			return;
		}
		current = overlay;
		expires = System.currentTimeMillis() + overlay.seconds() * 1000L;
		INFO.clear();
		for (VillagePayloads.LotInfo info : overlay.lots()) {
			INFO.put(info.id(), info);
		}
	}

	public static void clear() {
		current = null;
		INFO.clear();
	}

	/** Contorno desta vila aparecendo agora? */
	public static boolean showing(String village) {
		return active() && current.village().equals(village);
	}

	public static boolean active() {
		if (current != null && System.currentTimeMillis() > expires) {
			clear();
		}
		return current != null;
	}

	public static VillagePayloads.Overlay current() {
		return active() ? current : null;
	}

	/** Situacao do lote no ultimo contorno recebido (null = sem informacao). */
	public static VillagePayloads.LotInfo info(int lotId) {
		return INFO.get(lotId);
	}

	public static int color(VillagePayloads.LotStatus status) {
		return switch (status) {
			case FREE -> FREE;
			case CLEAR -> CLEAR;
			case BLOCKED -> BLOCKED;
			case WATER -> WATER;
			case STEEP -> STEEP;
			case BUILT -> BUILT;
			case BUILDING -> BUILDING;
		};
	}

	public static Component kindName(String kind) {
		VillageProject project = VillageProject.byId(kind);
		return project == null ? Component.translatable(kind.isEmpty() ? "village.mlinnotas.project.legacy" : "village.mlinnotas.project.campfire")
				: Component.translatable("village.mlinnotas.project." + project.id());
	}

	/** Altura do chao (primeiro ar) no cliente. */
	static double surface(Level level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	/** Desenha o contorno (chamado no {@code END_EXTRACTION}). */
	public static void render() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null || !active()) {
			return;
		}
		VillagePayloads.Overlay o = current;
		BlockPos c = o.center();
		Vec3 eye = mc.player.position();
		if (eye.distanceToSqr(c.getX(), eye.y, c.getZ()) > RANGE * RANGE) {
			return;
		}
		Level level = mc.level;
		VillageLayout layout = o.layout();
		// raio da vila
		int r = layout.radius();
		int segments = Math.max(48, r * 4);
		Vec3 prev = null;
		for (int i = 0; i <= segments; i++) {
			double a = 2 * Math.PI * i / segments;
			double x = c.getX() + 0.5 + r * Math.cos(a);
			double z = c.getZ() + 0.5 + r * Math.sin(a);
			Vec3 p = new Vec3(x, surface(level, (int) Math.floor(x), (int) Math.floor(z)) + 0.2, z);
			if (prev != null) {
				Gizmos.line(prev, p, RING, 4.0F);
			}
			prev = p;
		}
		// ruas planejadas
		for (long k : layout.roads()) {
			int x = c.getX() + VillageLayout.colX(k);
			int z = c.getZ() + VillageLayout.colZ(k);
			if (eye.distanceToSqr(x, eye.y, z) > 96 * 96) {
				continue;
			}
			double y = surface(level, x, z);
			Gizmos.cuboid(new AABB(x + 0.05, y + 0.02, z + 0.05, x + 0.95, y + 0.06, z + 0.95), GizmoStyle.fill(ROAD));
		}
		// postes que ainda vao ser feitos
		for (VillageLayout.Lamp lamp : layout.lamps()) {
			if (lamp.built()) {
				continue;
			}
			int x = c.getX() + lamp.x();
			int z = c.getZ() + lamp.z();
			double y = surface(level, x, z);
			Gizmos.line(new Vec3(x + 0.5, y, z + 0.5), new Vec3(x + 0.5, y + 3.2, z + 0.5), LAMP, 3.0F);
			Gizmos.cuboid(new AABB(x + 0.3, y + 3.2, z + 0.3, x + 0.7, y + 3.6, z + 0.7), GizmoStyle.strokeAndFill(LAMP, 2.0F, 0x80FFE680));
		}
		// lotes
		for (VillageLayout.Lot lot : layout.lots()) {
			VillagePayloads.LotInfo info = INFO.get(lot.id());
			VillagePayloads.LotStatus status = info == null ? (lot.built() ? VillagePayloads.LotStatus.BUILT : VillagePayloads.LotStatus.FREE)
					: VillagePayloads.LotStatus.byOrdinal(info.status());
			int ground = info == null ? (int) surface(level, c.getX() + lot.x(), c.getZ() + lot.z()) - 1 : info.ground();
			drawLot(c, lot.x(), lot.z(), lot.w(), lot.d(), lot.front(), ground, color(status), status != VillagePayloads.LotStatus.BUILT,
					kindName(lot.kind()).getString() + " · " + Component.translatable("gui.mlinnotas.village.status." + status.name()
							.toLowerCase(java.util.Locale.ROOT)).getString() + (lot.preferred() ? " ★" : ""));
		}
		// blocos que atrapalham
		for (int i = 0; i < o.obstacles().size(); i++) {
			BlockPos p = BlockPos.of(o.obstacles().get(i));
			WorldTerrain.Obstacle kind = WorldTerrain.Obstacle.values()[Math.max(0, Math.min(3, o.kinds().get(i)))];
			int color = kind == WorldTerrain.Obstacle.TREE ? CLEAR : kind == WorldTerrain.Obstacle.WATER ? WATER : BLOCKED;
			Gizmos.cuboid(new AABB(p).inflate(0.02), GizmoStyle.strokeAndFill(color, 3.0F, (color & 0x00FFFFFF) | 0x40000000));
		}
	}

	/**
	 * Um lote: retangulo no chao alvo com preenchimento translucido, postes nos cantos, seta para a porta e etiqueta.
	 * x/z relativos ao centro {@code c}.
	 */
	static void drawLot(BlockPos c, int lx, int lz, int w, int d, int[] front, int ground, int color, boolean fill, String label) {
		double x0 = c.getX() + lx;
		double z0 = c.getZ() + lz;
		double x1 = x0 + w;
		double z1 = z0 + d;
		double y = ground + 1.02;
		int alpha = fill ? 0x38000000 : 0x18000000;
		Gizmos.cuboid(new AABB(x0, y, z0, x1, y + 0.08, z1), GizmoStyle.fill((color & 0x00FFFFFF) | alpha));
		Gizmos.cuboid(new AABB(x0, y, z0, x1, y + 0.08, z1), GizmoStyle.stroke(color, 3.5F)).setAlwaysOnTop();
		for (double[] p : new double[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
			Gizmos.line(new Vec3(p[0], y, p[1]), new Vec3(p[0], y + 2.5, p[1]), color, 3.0F).setAlwaysOnTop();
		}
		double cx = (x0 + x1) / 2;
		double cz = (z0 + z1) / 2;
		double fx = c.getX() + front[0] + 0.5;
		double fz = c.getZ() + front[1] + 0.5;
		Gizmos.arrow(new Vec3(cx, y + 0.3, cz), new Vec3(fx, y + 0.3, fz), color, 3.0F);
		Gizmos.billboardText(label, new Vec3(cx, y + 3.2, cz), TextGizmo.Style.forColorAndCentered(color).withScale(1.6F)).setAlwaysOnTop();
	}
}
