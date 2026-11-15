package com.mlin.notas.village;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Campo de alturas da vila inicial (Vilas 3.0, logica pura, imutavel): a altura alvo do chao em cada coluna da area da
 * vila e da faixa de transicao em volta, calculada UMA vez por {@link HeightFieldBuilder} e lida por todo o resto
 * (geracao do mundo, terraplenagem, muralhas, lotes do jogador).
 *
 * <h2>Modelo</h2>
 * Grade quadrada centrada no quadro ({@link #centerX()}, {@link #centerZ()}), meia-largura {@link #extent()} (170 =
 * borda do anel rural 114 + faixa de transicao {@link HeightFieldBuilder#BAND} 56). Cada coluna tem:
 * <ul>
 * <li>{@link #heightRel altura}: Y do bloco do topo do chao (mesma convencao de {@link MasterPlan#plannedHeight}: o
 * bloco de grama; a praca fica em {@link #plazaY()}, o quadro em cima dela);</li>
 * <li>{@link #kindRel tipo} ({@link Kind}): o que o plano diretor poe ali (quarteirao, rua, portao, faixa das muralhas,
 * agua, area recortada...), {@link Kind#BAND} na transicao e {@link Kind#NATURAL} fora dela;</li>
 * <li>{@link #weightRel peso} de mistura, 1 dentro do plano diretor caindo suave (smoothstep pela distancia ate o plano)
 * ate 0 na borda da faixa (fora = 0): quanto a geracao deve puxar o terreno para a altura do campo. A altura da faixa ja
 * e a mistura (natural + (vila - natural) * curva), entao puxar com peso 1 na faixa tambem da certo;</li>
 * <li>{@link #depthRel profundidade}: quantos blocos abaixo da superficie a geracao deve manter fechados (sem caverna nem
 * agua): {@link HeightFieldBuilder#DEPTH_INSIDE} dentro, caindo com o peso, 0 em agua e areas recortadas;</li>
 * <li>pino ({@link #pinnedRel}): a altura veio de um pino ({@link FieldPins}: obra pronta, anel aplainado, area de
 * jogador, lote do jogador) e e respeitada exatamente.</li>
 * </ul>
 *
 * <h2>Garantias (testadas em {@code HeightFieldTest})</h2>
 * Quarteiroes sao platos (mesma altura em todas as celulas) e vizinhos diferem no maximo {@link MasterPlan#MAX_STEP};
 * rua com rampa de no maximo 1 por bloco e a no maximo 1 dos platos encostados; cada portao (avenida cruzando a muralha
 * de pedra ou a externa) e plano por {@link HeightFieldBuilder#GATE_HALF} blocos de cada lado da linha da muralha; pinos
 * exatos com rampa de no maximo 1 ate eles; na transicao, degrau de no maximo 1 onde o natural permite (curva suave
 * sem terracos) e a borda da faixa igual ao natural.
 *
 * <h2>Uso pelas fases seguintes</h2>
 * Consultas em coordenadas do mundo ({@link #height}, {@link #weight}, {@link #depth}, {@link #contains}) sao O(1)
 * sobre arrays e podem ser chamadas de qualquer thread (o objeto nunca muda depois de construido). A geracao (fase 2)
 * pega o campo ativo em {@code VillageFieldServer.active()}; muralhas (fase 4) usam {@link #gates()},
 * {@link #gateLevel} e {@link #wallPoints}; terraplenagem (fase 3) le {@link #heightRel} em vez de
 * {@link MasterPlan#plannedHeight}.
 *
 * <h2>Formato salvo</h2>
 * Cabecalho de inteiros + alturas em bytes (por linha: diferenca para a celula anterior, em pares varint
 * diferenca/repeticoes) + tipos em pares varint tipo/repeticoes ({@link #encodeHeights}, {@link #encodeKinds}). Peso e
 * profundidade nao sao salvos: saem dos tipos (distancia ate o plano) ao carregar.
 */
public final class HeightField {
	/** Versao do formato salvo. */
	public static final int FORMAT = 1;

	/** O que a coluna e no plano (ordinal salvo: so acrescente no fim). */
	public enum Kind {
		/** Fora da faixa de transicao: chao natural, a vila nao mexe. */
		NATURAL,
		/** Faixa de transicao: mistura do chao da vila com o natural. */
		BAND,
		PLAZA,
		/** Quarteirao (plato). */
		BLOCK,
		/** Rua de chao (rampa de no maximo 1). */
		STREET,
		/** Rua de portao da muralha de pedra (plana). */
		GATE_STONE,
		/** Rua de portao da muralha externa (plana). */
		GATE_OUTER,
		/** Faixa da muralha externa e borda do anel rural (rampa de no maximo 1). */
		FREE,
		/** Agua funda do plano (lago, ponte): altura = fundo natural. */
		WATER,
		/** Area recortada sem pino (construcao de jogador, area protegida): chao natural, a vila nao mexe. */
		KEEP,
		/** Caminho do lote do jogador ate a rua (rampa de no maximo 1). */
		PATH;

		private static final Kind[] VALUES = values();

		public static Kind byOrdinal(int i) {
			return i >= 0 && i < VALUES.length ? VALUES[i] : NATURAL;
		}

		/** Dentro do plano diretor (peso 1). */
		public boolean inside() {
			return this != NATURAL && this != BAND;
		}

		public boolean gate() {
			return this == GATE_STONE || this == GATE_OUTER;
		}

		/** Rua andavel (rua, portao, caminho). */
		public boolean walkway() {
			return this == STREET || gate() || this == PATH;
		}
	}

	/** Qual muralha. */
	public enum Wall {
		/** Muralha de pedra, octogono de meia-largura {@link VillagePlanner#WALL_HALF}. */
		STONE,
		/** Muralha externa na faixa {@link MasterPlan#WALL_BAND} (linha em {@link RuralRing#WALL}). */
		OUTER
	}

	/**
	 * Portao: retangulo (coordenadas relativas) das celulas de avenida planas em volta da linha da muralha, altura do
	 * chao delas ({@code level}; o arco tem que deixar livre de {@code level + 1} ate {@code level + 4}) e o centro.
	 */
	public record Gate(Wall wall, int x0, int z0, int x1, int z1, int level, int cx, int cz) {
	}

	/**
	 * Ponto da linha de uma muralha: celula (relativa), chao nela, chao {@link #WALL_PROBE} blocos para dentro e para fora
	 * (pela normal do lado do octogono) e se e portao.
	 */
	public record WallPoint(int x, int z, int ground, int inner, int outer, boolean gate) {
	}

	/** Distancia (em blocos) da linha da muralha onde {@link WallPoint#inner}/{@link WallPoint#outer} sao lidos. */
	public static final int WALL_PROBE = 2;

	private static final int PINNED_BIT = 0x10;
	private static final int KIND_MASK = 0x0F;

	private final int centerX;
	private final int centerZ;
	private final int extent;
	private final int size;
	private final int planExtent;
	private final int band;
	private final int plazaY;
	private final int minY;
	private final short[] heights;
	private final byte[] flags;
	private final byte[] weights;
	private final byte[] depths;
	private final List<Gate> gates;

	/**
	 * Monta o campo a partir dos arrays (indice {@code (x + extent) * size + (z + extent)}); peso e profundidade sao
	 * calculados aqui pelos tipos. Os arrays passam a ser do campo (nao mexa depois).
	 */
	HeightField(int centerX, int centerZ, int extent, int planExtent, int band, int plazaY, int minY, short[] heights, byte[] flags) {
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.extent = extent;
		this.size = 2 * extent + 1;
		this.planExtent = planExtent;
		this.band = band;
		this.plazaY = plazaY;
		this.minY = minY;
		if (heights.length != size * size || flags.length != size * size) {
			throw new IllegalArgumentException("tamanho do campo errado: " + heights.length + " / " + flags.length + " para " + size);
		}
		this.heights = heights;
		this.flags = flags;
		this.weights = new byte[size * size];
		this.depths = new byte[size * size];
		boolean[] inside = new boolean[size * size];
		for (int i = 0; i < inside.length; i++) {
			inside[i] = Kind.byOrdinal(flags[i] & KIND_MASK).inside();
		}
		double[] dist = HeightFieldBuilder.distance(inside, extent, null);
		for (int i = 0; i < inside.length; i++) {
			double b = HeightFieldBuilder.blend(dist[i], band);
			Kind kind = Kind.byOrdinal(flags[i] & KIND_MASK);
			weights[i] = (byte) Math.round(b * 255);
			int depth = kind == Kind.WATER || kind == Kind.KEEP ? 0 : (int) Math.round(HeightFieldBuilder.DEPTH_INSIDE * b);
			depths[i] = (byte) depth;
		}
		this.gates = Collections.unmodifiableList(findGates());
	}

	// ---------------------------------------------------------------- dados

	public int centerX() {
		return centerX;
	}

	public int centerZ() {
		return centerZ;
	}

	/** Meia-largura da grade do campo (plano + transicao). */
	public int extent() {
		return extent;
	}

	/** Meia-largura do plano diretor usado (114 com o anel rural). Plano maior que isso = campo velho, recalcular. */
	public int planExtent() {
		return planExtent;
	}

	/** Largura da faixa de transicao. */
	public int band() {
		return band;
	}

	public int plazaY() {
		return plazaY;
	}

	/** Altura minima dentro da vila (um acima do nivel do mar). */
	public int minY() {
		return minY;
	}

	// ---------------------------------------------------------------- consultas relativas ao centro

	public boolean inRel(int x, int z) {
		return Math.abs(x) <= extent && Math.abs(z) <= extent;
	}

	private int idx(int x, int z) {
		return (x + extent) * size + (z + extent);
	}

	/** Altura alvo (Y do topo do chao); {@link Integer#MIN_VALUE} fora da grade. */
	public int heightRel(int x, int z) {
		return inRel(x, z) ? heights[idx(x, z)] : Integer.MIN_VALUE;
	}

	public Kind kindRel(int x, int z) {
		return inRel(x, z) ? Kind.byOrdinal(flags[idx(x, z)] & KIND_MASK) : Kind.NATURAL;
	}

	public boolean pinnedRel(int x, int z) {
		return inRel(x, z) && (flags[idx(x, z)] & PINNED_BIT) != 0;
	}

	/** Peso da vila (0..1). */
	public float weightRel(int x, int z) {
		return inRel(x, z) ? (weights[idx(x, z)] & 0xFF) / 255.0F : 0.0F;
	}

	/** Blocos abaixo da superficie que a geracao mantem fechados (sem caverna nem agua). */
	public int depthRel(int x, int z) {
		return inRel(x, z) ? depths[idx(x, z)] : 0;
	}

	/** Altura da rua do portao na celula, ou {@link Integer#MIN_VALUE} se nao e portao. */
	public int gateLevelRel(int x, int z) {
		return kindRel(x, z).gate() ? heightRel(x, z) : Integer.MIN_VALUE;
	}

	// ---------------------------------------------------------------- consultas em coordenadas do mundo

	public boolean contains(int wx, int wz) {
		return inRel(wx - centerX, wz - centerZ);
	}

	/** Altura alvo na coluna do mundo; {@link Integer#MIN_VALUE} fora do campo (use o chao natural). */
	public int height(int wx, int wz) {
		return heightRel(wx - centerX, wz - centerZ);
	}

	public Kind kind(int wx, int wz) {
		return kindRel(wx - centerX, wz - centerZ);
	}

	/** Peso da vila na coluna do mundo (0 fora do campo). */
	public float weight(int wx, int wz) {
		return weightRel(wx - centerX, wz - centerZ);
	}

	public int depth(int wx, int wz) {
		return depthRel(wx - centerX, wz - centerZ);
	}

	public int gateLevel(int wx, int wz) {
		return gateLevelRel(wx - centerX, wz - centerZ);
	}

	// ---------------------------------------------------------------- muralhas e portoes

	/** Portoes (avenidas atravessando as muralhas). */
	public List<Gate> gates() {
		return gates;
	}

	private List<Gate> findGates() {
		List<Gate> out = new ArrayList<>();
		boolean[] seen = new boolean[size * size];
		for (int start = 0; start < seen.length; start++) {
			Kind kind = Kind.byOrdinal(flags[start] & KIND_MASK);
			if (!kind.gate() || seen[start]) {
				continue;
			}
			int x0 = Integer.MAX_VALUE;
			int z0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE;
			int z1 = Integer.MIN_VALUE;
			long sx = 0;
			long sz = 0;
			int n = 0;
			java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
			queue.add(start);
			seen[start] = true;
			while (!queue.isEmpty()) {
				int i = queue.poll();
				int x = i / size - extent;
				int z = i % size - extent;
				x0 = Math.min(x0, x);
				z0 = Math.min(z0, z);
				x1 = Math.max(x1, x);
				z1 = Math.max(z1, z);
				sx += x;
				sz += z;
				n++;
				for (Facing f : Facing.values()) {
					int nx = x + f.dx;
					int nz = z + f.dz;
					if (!inRel(nx, nz)) {
						continue;
					}
					int j = idx(nx, nz);
					if (!seen[j] && Kind.byOrdinal(flags[j] & KIND_MASK) == kind) {
						seen[j] = true;
						queue.add(j);
					}
				}
			}
			out.add(new Gate(kind == Kind.GATE_STONE ? Wall.STONE : Wall.OUTER, x0, z0, x1, z1, heights[start], (int) Math.round((double) sx / n),
					(int) Math.round((double) sz / n)));
		}
		return out;
	}

	/** A celula (relativa) esta na linha da muralha. */
	public static boolean onWallLine(Wall wall, int x, int z) {
		return wall == Wall.STONE ? VillagePlanner.wallCell(x, z) : RuralRing.wallLine(x, z);
	}

	/** Octogono da linha da muralha (distancia com sinal, negativa dentro). */
	static StreetPlanner.Octagon wallOctagon(Wall wall) {
		return StreetPlanner.Octagon.of(wall == Wall.STONE ? VillagePlanner.WALL_HALF : RuralRing.WALL, null);
	}

	/**
	 * Pontos da linha da muralha com o chao dos dois lados. Muralha externa so existe com o anel rural no plano
	 * ({@link #planExtent()} >= {@link RuralRing#EXTENT}).
	 */
	public List<WallPoint> wallPoints(Wall wall) {
		List<WallPoint> out = new ArrayList<>();
		if (wall == Wall.OUTER && planExtent < RuralRing.EXTENT) {
			return out;
		}
		StreetPlanner.Octagon oct = wallOctagon(wall);
		int reach = wall == Wall.STONE ? VillagePlanner.WALL_HALF + 1 : RuralRing.WALL + 1;
		for (int x = -reach; x <= reach; x++) {
			for (int z = -reach; z <= reach; z++) {
				if (!onWallLine(wall, x, z)) {
					continue;
				}
				int[] n = StreetPlanner.SIDES[oct.side(x, z)];
				int sx = n[0];
				int sz = n[1];
				int ix = x - sx * WALL_PROBE;
				int iz = z - sz * WALL_PROBE;
				int ox = x + sx * WALL_PROBE;
				int oz = z + sz * WALL_PROBE;
				out.add(new WallPoint(x, z, heightRel(x, z), heightRel(ix, iz), heightRel(ox, oz), kindRel(x, z).gate()));
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- formato salvo

	/** Cabecalho: formato, centro, meia-largura, plano, faixa, praca, minimo. */
	public int[] header() {
		return new int[] {FORMAT, centerX, centerZ, extent, planExtent, band, plazaY, minY};
	}

	/** Alturas: por linha (x fixo, z crescendo) diferenca para a anterior (a 1a da linha: para a 1a da linha de cima). */
	public byte[] encodeHeights() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int[] deltas = new int[heights.length];
		int prevRowStart = plazaY;
		for (int x = 0; x < size; x++) {
			int prev = prevRowStart;
			for (int z = 0; z < size; z++) {
				int h = heights[x * size + z];
				deltas[x * size + z] = h - prev;
				prev = h;
				if (z == 0) {
					prevRowStart = h;
				}
			}
		}
		runs(out, deltas);
		return out.toByteArray();
	}

	public byte[] encodeKinds() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int[] values = new int[flags.length];
		for (int i = 0; i < flags.length; i++) {
			values[i] = flags[i] & 0xFF;
		}
		runs(out, values);
		return out.toByteArray();
	}

	private static void runs(ByteArrayOutputStream out, int[] values) {
		int i = 0;
		while (i < values.length) {
			int v = values[i];
			int j = i;
			while (j < values.length && values[j] == v) {
				j++;
			}
			varint(out, (v << 1) ^ (v >> 31));
			varint(out, j - i - 1);
			i = j;
		}
	}

	private static void varint(ByteArrayOutputStream out, int v) {
		while ((v & ~0x7F) != 0) {
			out.write((v & 0x7F) | 0x80);
			v >>>= 7;
		}
		out.write(v);
	}

	private static int[] unruns(byte[] data, int length) {
		int[] out = new int[length];
		int[] pos = {0};
		int k = 0;
		while (pos[0] < data.length && k < length) {
			int zig = readVarint(data, pos);
			int v = (zig >>> 1) ^ -(zig & 1);
			int n = readVarint(data, pos) + 1;
			for (int r = 0; r < n && k < length; r++) {
				out[k++] = v;
			}
		}
		if (k < length) {
			throw new IllegalArgumentException("campo de alturas truncado: " + k + " de " + length);
		}
		return out;
	}

	private static int readVarint(byte[] data, int[] pos) {
		int v = 0;
		int shift = 0;
		while (pos[0] < data.length) {
			int b = data[pos[0]++] & 0xFF;
			v |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return v;
			}
			shift += 7;
		}
		throw new IllegalArgumentException("varint truncado");
	}

	/** Remonta o campo salvo; null se o formato nao for conhecido (o servidor recalcula). */
	public static HeightField decode(int[] header, byte[] heightData, byte[] kindData) {
		if (header.length < 8 || header[0] != FORMAT) {
			return null;
		}
		int extent = header[3];
		int size = 2 * extent + 1;
		int[] deltas = unruns(heightData, size * size);
		int[] kinds = unruns(kindData, size * size);
		short[] heights = new short[size * size];
		byte[] flags = new byte[size * size];
		int prevRowStart = header[6];
		for (int x = 0; x < size; x++) {
			int prev = prevRowStart;
			for (int z = 0; z < size; z++) {
				int h = prev + deltas[x * size + z];
				heights[x * size + z] = (short) h;
				prev = h;
				if (z == 0) {
					prevRowStart = h;
				}
				flags[x * size + z] = (byte) kinds[x * size + z];
			}
		}
		return new HeightField(header[1], header[2], extent, header[4], header[5], header[6], header[7], heights, flags);
	}

	/** Mesmo conteudo (alturas, tipos e cabecalho). */
	public boolean sameAs(HeightField o) {
		return o != null && java.util.Arrays.equals(header(), o.header()) && java.util.Arrays.equals(heights, o.heights)
				&& java.util.Arrays.equals(flags, o.flags);
	}

	static int pinnedBit() {
		return PINNED_BIT;
	}
}
