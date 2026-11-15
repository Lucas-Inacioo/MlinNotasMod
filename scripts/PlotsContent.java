import java.io.IOException;

/**
 * Textos dos terrenos do jogador (Vilas 3.0 fase 5): placa, contorno, comando {@code /village admin plot}. Chamado por
 * ContentGen.main.
 */
class PlotsContent {
	static void generate() throws IOException {
		t("sign.mlinnotas.plot", "Terreno do jogador", "Player plot");
		t("gui.mlinnotas.village.plan.plot", "Terreno do jogador", "Player plot");
		t("commands.mlinnotas.village.plot.no_village", "Não há vila inicial.", "There is no starting village.");
		t("commands.mlinnotas.village.plot.added", "Terreno #%s registrado de %s, %s a %s, %s (chão em y=%s); a vila vai nivelar em volta aos poucos.",
				"Plot #%s registered from %s, %s to %s, %s (floor at y=%s); the village will level the surroundings over time.");
		t("commands.mlinnotas.village.plot.outside_field", "Atenção: o terreno está fora da área do campo de alturas da vila; ele foi registrado, mas não há nivelamento nem caminho.",
				"Warning: the plot lies outside the village height field; it was registered, but there is no levelling or path.");
		t("commands.mlinnotas.village.plot.removed", "Terreno #%s removido.", "Plot #%s removed.");
		t("commands.mlinnotas.village.plot.unknown", "Não existe terreno #%s.", "There is no plot #%s.");
		t("commands.mlinnotas.village.plot.none", "Nenhum terreno do jogador.", "No player plots.");
		t("commands.mlinnotas.village.plot.entry", "#%s: %s, %s a %s, %s, chão y=%s %s", "#%s: %s, %s to %s, %s, floor y=%s %s");
		t("commands.mlinnotas.village.plot.fail.too_small", "O terreno é pequeno demais (mínimo 4x4).", "The plot is too small (minimum 4x4).");
		t("commands.mlinnotas.village.plot.fail.street", "O terreno cobre uma rua ou a praça da vila em %s, %s; ajuste o retângulo.",
				"The plot covers a village street or the plaza at %s, %s; adjust the rectangle.");
		t("commands.mlinnotas.village.plot.fail.lot", "O terreno bate numa obra ou lote reservado da vila em %s, %s.",
				"The plot overlaps a village building or reserved lot at %s, %s.");
		t("commands.mlinnotas.village.plot.fail.plot", "Já existe um terreno do jogador em %s, %s.", "A player plot already exists at %s, %s.");
		t("gui.mlinnotas.village.plots.title", "Terrenos do jogador", "Player plots");
		t("gui.mlinnotas.village.plots.entry", "Terreno %s,%s · %s", "Plot %s,%s · %s");
		t("gui.mlinnotas.village.plots.house", "conta como casa", "counts as a house");
		t("gui.mlinnotas.village.plots.empty", "livre", "free");
	}

	static void t(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
