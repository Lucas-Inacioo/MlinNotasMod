package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.List;
import java.util.Locale;

/**
 * Ponte do campo de alturas da vila inicial ({@link HeightField}, Vilas 3.0 fase 1) com o servidor: le o chao natural
 * do gerador ({@link #natural}, o unico caminho), calcula o campo da vila nova antes de o mundo gerar chunks e o de uma
 * vila antiga na primeira carga (com o que ja existe pinado), guarda em {@link VillageData} e deixa o campo da vila
 * inicial em {@link #active()} para a geracao do mundo (fase 2) ler de qualquer thread.
 */
public final class VillageFieldServer {
	/** Campo da vila inicial do servidor atual (imutavel; null = nenhum). */
	private static volatile HeightField active;
	/** Plano diretor + campo calculados no carregamento do Overworld para o lugar escolhido (vila ainda nao criada). */
	private static volatile Prepared prepared;

	private VillageFieldServer() {
	}

	/** Plano diretor da vila inicial nova, o campo e o chao da praca (o quadro vai em {@code plazaY + 1}). */
	public record Prepared(int x, int z, int plazaY, long seed, MasterPlan plan, HeightField field) {
	}

	/** Campo de alturas da vila inicial (para a geracao do mundo e quem mais precisar). Seguro em qualquer thread. */
	public static HeightField active() {
		return active;
	}

	/** Troca o campo ativo (servidor; testes que criam uma vila inicial a parte devolvem o de antes). */
	public static void setActive(HeightField field) {
		active = field;
	}

	static void clear() {
		active = null;
		prepared = null;
	}

	/** Altura minima do chao dentro da vila: um acima do nivel do mar. */
	public static int minY(ServerLevel level) {
		return level.getSeaLevel() + 1;
	}

	/**
	 * Chao natural em volta de ({@code cx}, {@code cz}) lido do gerador, sem gerar chunk: Y do topo solido (sem agua) de
	 * 4 em 4 blocos, interpolado. Unico caminho de leitura do natural para o campo: a fase 2, que mistura o terreno com o
	 * campo, liga aqui o desvio para ler o terreno sem a mistura.
	 */
	public static HeightFieldBuilder.Natural natural(ServerLevel level, int cx, int cz) {
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState random = level.getChunkSource().randomState();
		// Vilas 3.0 fase 2: o gerador ja mistura o terreno com o campo ativo; aqui le o terreno cru (desvio por thread)
		return com.mlin.notas.village.gen.VillageGen.raw(() -> HeightFieldBuilder.coarse(
				(x, z) -> generator.getBaseHeight(cx + x, cz + z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1, HeightFieldBuilder.EXTENT,
				HeightFieldBuilder.SAMPLE_STEP));
	}

	/**
	 * Vila inicial nova com centro em (x, z): chao da praca, semente (a mesma que {@link VillageServer#create} vai dar
	 * com o quadro em {@code plazaY + 1}), plano diretor ja com o anel rural e o campo.
 * Fica guardado para {@link StartingVillage} usar quando criar a vila.
	 */
	static Prepared prepareStarter(ServerLevel level, int x, int z) {
		Prepared p = prepared;
		if (p != null && p.x == x && p.z == z) {
			return p;
		}
		long start = System.nanoTime();
		HeightFieldBuilder.Natural natural = natural(level, x, z);
		int minY = minY(level);
		int plazaY = HeightFieldBuilder.plazaHeight(natural, minY);
		long seed = VillageServer.seedFor(level, new BlockPos(x, plazaY + 1, z));
		HeightFieldBuilder.Result result = HeightFieldBuilder.forNewVillage(seed, x, z, plazaY, natural, minY, List.of(), FieldPins.none());
		p = new Prepared(x, z, plazaY, seed, result.plan(), result.field());
		prepared = p;
		MlinNotas.LOGGER.info("[{}] campo de alturas da vila inicial em {}, {} (praca y={}) calculado em {} ms", MlinNotas.MOD_ID, x, z, plazaY,
				(System.nanoTime() - start) / 1_000_000);
		return p;
	}

	/**
	 * Vila inicial que ja existe (dados antigos sem campo, ou campo menor que o plano porque o plano ganhou o anel rural
	 * depois): calcula o campo com o que ja existe pinado e guarda. Devolve o campo (ou null sem plano diretor).
	 */
	public static HeightField ensureField(ServerLevel level, VillageState village) {
		if (!village.starter() || village.layout() == null || village.layout().master() == null) {
			return null;
		}
		VillageData data = VillageData.get(level.getServer());
		MasterPlan plan = village.layout().master();
		HeightField field = data.field(village.key());
		if (field != null && field.planExtent() >= plan.extent() && field.centerX() == village.x() && field.centerZ() == village.z()) {
			active = field;
			return field;
		}
		long start = System.nanoTime();
		FieldPins pins = pins(level, village, plan);
		field = HeightFieldBuilder.forPlan(plan, village.x(), village.z(), natural(level, village.x(), village.z()), minY(level), pins);
		data.putField(village.key(), field);
		active = field;
		MlinNotas.LOGGER.info("[{}] campo de alturas da vila {} calculado em {} ms ({} colunas pinadas)", MlinNotas.MOD_ID, village.name(),
				(System.nanoTime() - start) / 1_000_000, pins.size());
		return field;
	}

	/**
	 * O que uma vila antiga ja tem e o campo nao pode mudar: quarteiroes aplainados (alvo do plato), ruas dos aneis
	 * prontos (menos a rua dos portoes, que o campo deixa plana), lotes com obra (altura do plano, ou o chao de agora) e
	 * areas recortadas / lotes de jogador (o chao embaixo das construcoes de agora).
	 */
	static FieldPins pins(ServerLevel level, VillageState village, MasterPlan plan) {
		FieldPins pins = new FieldPins();
		int e = plan.extent();
		boolean[] ringDone = new boolean[MasterPlan.RINGS + 1];
		for (int r = 1; r <= MasterPlan.RINGS; r++) {
			ringDone[r] = village.ring(r).done();
		}
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				int code = plan.code(x, z);
				MasterPlan.Block block = plan.blockAt(x, z);
				if (block != null) {
					if (block.prepared()) {
						pins.pin(x, z, block.target());
					}
				} else if (plan.ground(x, z)) {
					int ring = plan.ring(x, z);
					if (ring >= 1 && ringDone[ring] && HeightFieldBuilder.gateWall(plan, x, z) == null) {
						pins.pin(x, z, plan.streetHeight(x, z));
					}
				} else if (code == MasterPlan.CARVED) {
					pins.pin(x, z, groundUnder(level, village.x() + x, village.z() + z));
				}
			}
		}
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.player() && !p.retired()) {
				pinWorld(level, village, pins, p.rect());
			}
		}
		for (VillageLayout.Lot lot : village.layout().lots()) {
			if (!lot.built()) {
				continue;
			}
			VillageState.Rect r = lot.rect();
			for (int x = r.x0(); x <= r.x1(); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					int h = plan.plannedHeight(x, z);
					pins.pin(x, z, h != Integer.MIN_VALUE ? h : groundUnder(level, village.x() + x, village.z() + z));
				}
			}
		}
		return pins;
	}

	private static void pinWorld(ServerLevel level, VillageState village, FieldPins pins, VillageState.Rect r) {
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				pins.pin(x, z, groundUnder(level, village.x() + x, village.z() + z));
			}
		}
	}

	/**
	 * Chao natural embaixo do que estiver construido na coluna: desce do topo passando por construcoes (ate 32 blocos)
	 * ate o primeiro bloco de chao natural. Coluna so de construcao: o topo.
	 */
	static int groundUnder(ServerLevel level, int wx, int wz) {
		level.getChunk(wx >> 4, wz >> 4);
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(wx, top, wz);
		for (int i = 0; i < 32 && pos.getY() > level.getMinY(); i++) {
			BlockState state = level.getBlockState(pos);
			if (WorldTerrain.naturalGround(state)) {
				return pos.getY();
			}
			pos.move(0, -1, 0);
		}
		return top;
	}

	// ---------------------------------------------------------------- comando

	/** {@code /village admin field <x> <z>}: altura alvo, tipo, zona, peso e profundidade da coluna. */
	static int describe(CommandContext<CommandSourceStack> ctx) {
		int x = IntegerArgumentType.getInteger(ctx, "x");
		int z = IntegerArgumentType.getInteger(ctx, "z");
		HeightField field = active;
		VillageData data = VillageData.get(ctx.getSource().getServer());
		VillageState owner = null;
		for (VillageState v : data.villages()) {
			HeightField f = data.field(v.key());
			if (f != null && f.contains(x, z)) {
				field = f;
				owner = v;
				break;
			}
		}
		if (field == null || !field.contains(x, z)) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.village.field.none", x, z));
			return 0;
		}
		HeightField f = field;
		HeightField.Kind kind = f.kind(x, z);
		String zone = "-";
		if (owner != null && owner.layout() != null && owner.layout().master() != null) {
			MasterPlan.Block block = owner.layout().master().blockAt(x - owner.x(), z - owner.z());
			if (block != null && block.zone() != null) {
				zone = block.zone().id();
			}
		}
		int gate = f.gateLevel(x, z);
		Component kindName = Component.translatable("commands.mlinnotas.village.field.kind." + kind.name().toLowerCase(Locale.ROOT));
		Component extra = gate != Integer.MIN_VALUE ? Component.translatable("commands.mlinnotas.village.field.gate", gate)
				: f.pinnedRel(x - f.centerX(), z - f.centerZ()) ? Component.translatable("commands.mlinnotas.village.field.pinned") : Component.empty();
		String zoneId = zone;
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.field", x, z, f.height(x, z), kindName, zoneId,
				Math.round(f.weight(x, z) * 100), f.depth(x, z), extra), false);
		return f.height(x, z);
	}
}
