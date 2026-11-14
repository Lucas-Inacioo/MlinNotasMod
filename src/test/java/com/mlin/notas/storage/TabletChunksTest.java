package com.mlin.notas.storage;

import com.mlin.notas.util.Pos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabletChunksTest {
	@Test
	void packMatchesVanillaChunkPos() {
		for (int[] c : new int[][] {{0, 0}, {-1, 5}, {100, -3}, {-1875, -60000}}) {
			long packed = TabletChunks.pack(c[0], c[1]);
			assertEquals(ChunkPos.pack(c[0], c[1]), packed);
			assertEquals(c[0], TabletChunks.unpackX(packed));
			assertEquals(c[1], TabletChunks.unpackZ(packed));
		}
		assertEquals(TabletChunks.pack(-1, -1), TabletChunks.chunkOf(new Pos(-1, 64, -16)));
	}

	@Test
	void terminalChunkComesFirstWithoutDuplicates() {
		Set<Long> plan = TabletChunks.plan(new Pos(5, 64, 5), List.of(new Pos(7, 64, 5), new Pos(20, 64, 5), new Pos(21, 60, 6)));
		assertEquals(List.of(TabletChunks.pack(0, 0), TabletChunks.pack(1, 0)), new ArrayList<>(plan));
	}

	@Test
	void plansAreCapped() {
		List<Pos> nodes = new ArrayList<>();
		for (int i = 0; i < 200; i++) {
			nodes.add(new Pos(i * 16, 64, 0));
		}
		Set<Long> plan = TabletChunks.plan(new Pos(-100, 64, -100), nodes);
		assertEquals(TabletChunks.MAX_CHUNKS, plan.size());
		assertTrue(plan.contains(TabletChunks.chunkOf(new Pos(-100, 64, -100))));
	}
}
