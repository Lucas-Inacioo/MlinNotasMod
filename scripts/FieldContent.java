import java.io.IOException;

/**
 * Textos do campo de alturas da vila inicial (Vilas 3.0 fase 1): o comando de depuracao
 * {@code /village admin field <x> <z>}. Chamado por ContentGen.main.
 */
class FieldContent {
	static void generate() throws IOException {
		text("commands.mlinnotas.village.field", "Campo em %s, %s: altura %s, %s, zona %s, peso %s%%, fechado até %s abaixo %s",
				"Field at %s, %s: height %s, %s, zone %s, weight %s%%, closed down to %s below %s");
		text("commands.mlinnotas.village.field.none", "Nenhum campo de alturas cobre %s, %s", "No height field covers %s, %s");
		text("commands.mlinnotas.village.field.gate", "(portão, rua em %s)", "(gate, street at %s)");
		text("commands.mlinnotas.village.field.pinned", "(fixo)", "(pinned)");
		kind("natural", "chão natural", "natural ground");
		kind("band", "transição", "transition");
		kind("plaza", "praça", "plaza");
		kind("block", "quarteirão", "city block");
		kind("street", "rua", "street");
		kind("gate_stone", "portão da muralha de pedra", "stone wall gate");
		kind("gate_outer", "portão da muralha externa", "outer wall gate");
		kind("free", "faixa da muralha / borda", "wall strip / edge");
		kind("water", "água", "water");
		kind("keep", "área recortada", "carved-out area");
		kind("path", "caminho do lote", "plot path");
	}

	private static void kind(String id, String pt, String en) {
		text("commands.mlinnotas.village.field.kind." + id, pt, en);
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
