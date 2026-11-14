package com.mlin.notas.grave;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraveDataTest {
	@Test
	void roundTripsAndToleratesMissingFields() {
		GraveInfo info = new GraveInfo("u1", "Ana", "minecraft:overworld", 1, -20, 2, 10, 50, 3);
		JsonElement json = GraveData.INFO_CODEC.encodeStart(JsonOps.INSTANCE, info).getOrThrow();
		assertEquals(info, GraveData.INFO_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

		// so o essencial: o resto assume padroes
		JsonElement minimal = JsonParser.parseString("{\"owner\":\"u2\",\"dimension\":\"minecraft:the_end\",\"x\":5,\"y\":60,\"z\":-7}");
		GraveInfo parsed = GraveData.INFO_CODEC.parse(JsonOps.INSTANCE, minimal).getOrThrow();
		assertEquals(new GraveInfo("u2", "?", "minecraft:the_end", 5, 60, -7, 0, 0, 1), parsed);

		// arquivo sem lista: mundo sem lapides
		GraveData empty = GraveData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();
		assertEquals(List.of(), empty.index().all());
	}
}
