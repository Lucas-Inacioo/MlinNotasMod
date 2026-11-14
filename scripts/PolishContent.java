/**
 * Textos da rodada de acabamento das Vilas 2.0 (fachadas do centro, placas das estatuas, novos detalhes) para o ContentGen.
 * Chamado por ContentGen.main depois do VillageCapacityContent.
 */
class PolishContent {
	static void generate() {
		WorksContent.text("village.mlinnotas.work.facades.1", "Fachadas: pilastras e pórticos de pedra nas casas do centro", "Facades: stone pilasters and porticos on centre houses");
		WorksContent.text("village.mlinnotas.work.townhouses.1", "Sobrados de pedra nos lotes vagos do centro", "Stone townhouses on vacant centre lots");
		WorksContent.text("village.mlinnotas.work.arcades.1", "Arcadas de pedra no comércio do centro", "Stone arcades on centre shops");
		WorksContent.text("gui.mlinnotas.village.works.cat.facades", "Fachadas", "Facades");
		WorksContent.text("gui.mlinnotas.village.works.cat.facades.tooltip", "Pilastras, sobrados e arcadas de pedra nas ruas do centro (só em blocos da vila).",
				"Stone pilasters, townhouses and arcades on the centre streets (village blocks only).");
		WorksContent.text("sign.mlinnotas.statue.header", "Em memória de", "In memory of");
		WorksContent.text("sign.mlinnotas.statue.cobalt_golem", "Golem de Cobalto", "Cobalt Golem");
		WorksContent.text("sign.mlinnotas.statue.abyssal_guardian", "Guardião Abissal", "Abyssal Guardian");
		WorksContent.text("sign.mlinnotas.statue.igneous_colossus", "Colosso Ígneo", "Igneous Colossus");
		WorksContent.text("sign.mlinnotas.statue.astral_herald", "Arauto Astral", "Astral Herald");
		WorksContent.text("sign.mlinnotas.statue.ender_dragon", "Dragão do Ender", "Ender Dragon");
		WorksContent.text("sign.mlinnotas.statue.wither", "Wither", "Wither");
		WorksContent.text("sign.mlinnotas.statue.hero", "Os heróis da vila", "The village heroes");
	}
}
