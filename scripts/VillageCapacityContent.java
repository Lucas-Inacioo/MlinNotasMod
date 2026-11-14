import java.io.IOException;

/**
 * Textos da capacidade do plano diretor (integracao das fases 5-7 das Vilas 2.0): projeto sem lugar no plano. Chamado
 * por ContentGen.main.
 */
class VillageCapacityContent {
	static void generate() throws IOException {
		text("gui.mlinnotas.village.no_room", "Sem lugar no plano %s/%s", "No room in the plan %s/%s");
		text("message.mlinnotas.village.no_room", "%s: o plano da vila não tem mais lote livre para esta obra",
				"%s: the village plan has no free lot left for this building");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
