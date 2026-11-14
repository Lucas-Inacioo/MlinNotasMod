package com.mlin.notas.village;

/**
 * Acabamento das construcoes novas das fases 5-7 (comida, oficios, civicas), ligado por {@link BuildingPlans#plan} so para
 * elas (as casas e o resto continuam com o desenho de sempre): venezianas nas janelas do terreo, floreiras de tabuas com
 * flores sob cada janela, aba de cascalho em volta das paredes e uma lucarna de sobrancelha na agua da frente do telhado.
 * Referencias: casas de enxaimel de vila medieval com floreiras e lucarnas das galerias do Planet Minecraft
 * ("Medieval House Tutorial", "Cottage Details"); escrito do zero. Usa uma flag por thread para o {@code body} saber
 * que esta desenhando uma construcao nova sem mudar a assinatura dele.
 */
final class BodyDressing {
	private static final ThreadLocal<Boolean> ON = ThreadLocal.withInitial(() -> false);

	private BodyDressing() {
	}

	static boolean on() {
		return ON.get();
	}

	static <T> T with(java.util.function.Supplier<T> plan) {
		boolean before = ON.get();
		ON.set(true);
		try {
			return plan.get();
		} finally {
			ON.set(before);
		}
	}

	/** Floreira sob a janela (x, y, z) que olha para {@code out}: tabuas encostadas na parede e uma flor na frente do vidro. */
	static void flowerBox(Canvas c, int x, int y, int z, Facing out, int seed) {
		int bx = x + out.dx;
		int bz = z + out.dz;
		if (bx < 0 || bz < 0 || bx >= c.w() || bz >= c.d() || y < 2) {
			return;
		}
		if (c.get(bx, y - 1, bz) == null && c.get(bx, y, bz) == null) {
			c.set(bx, y - 1, bz, VPart.PLANKS);
			c.set(bx, y, bz, VPart.FLOWER_POT);
		}
	}

	/** Aba de cascalho em volta das paredes (so onde nao ha nada ainda). */
	static void apron(Canvas c, int x0, int z0, int x1, int z1) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				boolean ring = x < x0 || x > x1 || z < z0 || z > z1;
				if (ring && x >= 0 && z >= 0 && x < c.w() && z < c.d()) {
					c.setIfAbsent(x, 0, z, VPart.GRAVEL);
				}
			}
		}
	}

	/** Lucarna de sobrancelha na agua da frente do telhado (cumeeira ao longo de x): janela e um degrau de telhado por cima. */
	static void dormer(Canvas c, VillageStyle style, int x0, int x1, int z1, int top) {
		if (style.flatRoof() || x1 - x0 < 8) {
			return;
		}
		int mid = (x0 + x1) / 2;
		for (int xd : new int[] {mid - 3, mid + 3}) {
			if (xd <= x0 + 1 || xd >= x1 - 1) {
				continue;
			}
			// a agua da frente na segunda camada: degrau em (xd, top+1, z1); troca por vidraca e poe a sobrancelha em cima
			if (c.part(xd, top + 1, z1) == VPart.ROOF && c.part(xd, top + 2, z1) == null) {
				c.set(xd, top + 1, z1, VPart.WINDOW, Facing.EAST);
				c.set(xd, top + 2, z1, VPart.ROOF, Facing.NORTH);
			}
		}
	}
}
