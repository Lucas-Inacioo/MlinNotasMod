package com.mlin.notas.boss;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Os ids de animacao dos chefes nao podem cair em ids que o cliente vanilla intercepta (crash do Golem com o 63). */
class BossEventIdsTest {
	/** Ids que {@code ClientPacketListener.handleEntityEvent} trata sem chamar a entidade (21, 35, 63 = cast para Sniffer). */
	private static final int[] INTERCEPTED = {21, 35, 63};
	/** Maior id de {@code EntityEvent} vanilla em 26.3. */
	private static final int VANILLA_MAX = 72;

	@Test
	void animationIdsAvoidVanillaEvents() {
		int from = BossMob.EVENT_ANIM;
		int to = BossMob.EVENT_ANIM + 10;
		assertTrue(from > VANILLA_MAX, "base acima dos eventos vanilla");
		assertTrue(to - 1 <= Byte.MAX_VALUE, "cabe num byte");
		for (int id : INTERCEPTED) {
			assertFalse(id >= from && id < to, "id interceptado pelo cliente: " + id);
		}
	}
}
