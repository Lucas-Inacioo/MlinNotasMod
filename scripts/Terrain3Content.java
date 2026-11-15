import java.io.IOException;

/**
 * Textos da terraplenagem pelo campo e das muralhas (Vilas 3.0 fases 3 e 4): o comando de operador
 * {@code /village admin rebuild_walls}. Chamado por ContentGen.main.
 */
class Terrain3Content {
	static void generate() throws IOException {
		text("commands.mlinnotas.village.rebuild_walls", "Muralhas de %s refeitas nas alturas do campo: %s blocos tirados, %s colunas de portão acertadas, %s blocos colocados, %s obras de muralha reabertas",
				"Walls of %s rebuilt at field heights: %s blocks removed, %s gate columns levelled, %s blocks placed, %s wall works reopened");
		text("commands.mlinnotas.village.rebuild_walls.none", "Nenhuma vila inicial com muralha pronta para refazer", "No starting village with a finished wall to rebuild");
	}

	static void text(String key, String pt, String en) {
		ContentGen.PT.put(key, pt);
		ContentGen.EN.put(key, en);
	}
}
