import java.io.IOException;

/**
 * Textos das construcoes de producao e comida (Vilas 2.0, fase 5) para o ContentGen: projetos, o que cada nivel traz,
 * ofertas do mercado, mensagens e a descricao das necessidades que elas atendem. Chamado por ContentGen.main depois
 * do Qol2Content. Os tipos, plantas e numeros ficam em {@code village/FoodBuildings}, {@code FoodPlans},
 * {@code FoodRules}.
 */
class FoodBuildingsContent {
	static void generate() throws IOException {
		VillageContent.project("granary", "Celeiro", "Granary",
				"Galpão de feno e enxaimel: o estoque de comida da vila. Plantações, pomar, curral e padaria abastecem o baú sozinhos; "
						+ "qualquer jogador pode guardar comida nele. As plantações perto dele rendem +10% por nível. A Cidade exige um celeiro.",
				"A hay and half-timber barn: the village food stock. Farms, orchard, pen and bakery fill its chest on their own; any player can "
						+ "deposit food. Farms near it yield +10% per level. A Town needs a granary.");
		VillageContent.project("mill", "Moinho", "Mill",
				"Moinho de vento de pedra: mói o trigo do baú em farinha com rendimento melhor que a tábua de corte (5, 6 ou 7 farinhas por 4 "
						+ "trigos). Chega um moleiro.",
				"A stone windmill: grinds the wheat in its chest into flour with a better yield than the cutting board (5, 6 or 7 flour per 4 "
						+ "wheat). A miller arrives.");
		VillageContent.project("bakery", "Padaria", "Bakery",
				"Forno de tijolo que assa farinha e ingredientes do baú sozinho (pão, broa, biscoitos, bolo...); o padeiro vende pão e broa. "
						+ "Chega um padeiro.",
				"A brick oven that bakes the flour and ingredients in its chest by itself (bread, corn bread, cookies, cake...); the baker sells "
						+ "bread. A baker arrives.");
		VillageContent.project("orchard", "Pomar", "Orchard",
				"Macieiras em fileiras, arbustos de frutas e uma barraca com baú. Botão direito nas folhas colhe a fruta da estação (uma vez por "
						+ "dia por árvore). Chega um pomicultor.",
				"Rows of apple trees, berry bushes and a stall with a chest. Right-click the leaves to pick the fruit of the season (once a day "
						+ "per tree). A fruit grower arrives.");
		VillageContent.project("pen", "Curral", "Pen",
				"Cercado com abrigo de feno e cochos: vacas, ovelhas e porcos se criam sozinhos até o limite do nível (4, 6 ou 8 de cada). "
						+ "Leite e lã vão para o baú. Chega um criador.",
				"A fenced yard with a hay shelter and troughs: cows, sheep and pigs breed on their own up to the level cap (4, 6 or 8 of each). "
						+ "Milk and wool go to the chest. A herder arrives.");
		VillageContent.project("apiary", "Apiário", "Apiary",
				"Jardim de flores com colmeias e a casa do mel: favos e garrafas de mel no baú (as abelhas dormem no inverno e nunca atacam os "
						+ "moradores). Chega um apicultor.",
				"A flower garden with hives and a honey house: honeycomb and honey bottles in the chest (bees sleep in winter and never attack "
						+ "residents). A beekeeper arrives.");
		VillageContent.project("butcher", "Açougue", "Butcher",
				"Açougue de pedra com defumadores: o açougueiro vende cortes de carne e charque barato. No nível II seca a carne cozida do baú "
						+ "em charque. Chega um açougueiro.",
				"A stone butcher shop with smokers: the butcher sells cuts of meat and cheap jerky. At level II it dries the cooked meat in its "
						+ "chest into jerky. A butcher arrives.");
		VillageContent.project("winery", "Vinícola", "Winery",
				"Parreirais em latada com uvas, adega de pedra e alambique: do baú saem vinho (uvas), cerveja (trigo, nível II) e cidra (maçãs, "
						+ "nível III). Chega um vinicultor.",
				"Trellised vineyards with grapes, a stone cellar and a still: the chest turns out wine (grapes), beer (wheat, level II) and "
						+ "cider (apples, level III). A vintner arrives.");
		VillageContent.project("sawmill", "Serraria", "Sawmill",
				"Galpão aberto com pilhas de toras e uma bancada de serra: botão direito no cortador de pedras com troncos na mão dá 6 tábuas por "
						+ "tronco. O mercado vende tábuas baratas. Chega um carpinteiro.",
				"An open shed with log piles and a saw bench: right-click the stonecutter holding logs for 6 planks per log. The market sells "
						+ "cheap planks. A carpenter arrives.");

		// --- custos com tag ---
		Village4Content.text("village.mlinnotas.cost.minecraft.saplings", "Mudas de árvore (qualquer)", "Tree saplings (any)");
		Village4Content.text("village.mlinnotas.cost.minecraft.flowers", "Flores (qualquer)", "Flowers (any)");

		// --- o que cada nivel traz ---
		Village4Content.level("granary", 1, "Galpão com baú de estoque: as plantações perto rendem +10%.", "Barn with a stock chest: nearby farms yield +10%.");
		Village4Content.level("granary", 2, "Sotão de feno e segundo baú: +20% nas plantações; mais comida na necessidade.",
				"Hay loft and second chest: +20% on farms; more food for the village need.");
		Village4Content.level("granary", 3, "Base de pedra, segundo baú no térreo e abrigo lateral: +30% nas plantações.",
				"Stone base, second ground chest and a side shelter: +30% on farms.");
		Village4Content.level("mill", 1, "Moinho de dois andares: 5 farinhas por 4 trigos, até 16 trigos por minuto.",
				"Two-storey windmill: 5 flour per 4 wheat, up to 16 wheat per minute.");
		Village4Content.level("mill", 2, "Terceiro andar, anexo de depósito e segunda pedra de moer: 6 farinhas por 4 trigos, 32 por minuto.",
				"Third floor, storage annex and a second millstone: 6 flour per 4 wheat, 32 per minute.");
		Village4Content.level("mill", 3, "Velas maiores, três pedras e carroça de feno: 7 farinhas por 4 trigos, 48 por minuto.",
				"Bigger sails, three millstones and a hay cart: 7 flour per 4 wheat, 48 per minute.");
		Village4Content.level("bakery", 1, "Forno de tijolo: pão (3 farinhas = 4 pães) e broa.", "Brick oven: bread (3 flour = 4 loaves) and corn bread.");
		Village4Content.level("bakery", 2, "Andar de cima e mais um forno: biscoitos e pão de queijo; o padeiro vende pão de queijo.",
				"Upper floor and a second oven: cookies and cheese bread; the baker sells cheese bread.");
		Village4Content.level("bakery", 3, "Forno de rua com cúpula e mesas: bolo e tortas; o padeiro vende bolo.",
				"Street oven with a dome and tables: cake and pies; the baker sells cake.");
		Village4Content.level("orchard", 1, "Quatro macieiras e uma barraca: 2 maçãs por colheita.", "Four apple trees and a stall: 2 apples per picking.");
		Village4Content.level("orchard", 2, "Seis macieiras: 3 maçãs por colheita.", "Six apple trees: 3 apples per picking.");
		Village4Content.level("orchard", 3, "Prensa de cidra e caramanchão: 4 maçãs e árvores que dão fruta de novo mais cedo.",
				"Cider press and arbour: 4 apples and trees that fruit again sooner.");
		Village4Content.level("pen", 1, "Abrigo de feno: até 4 animais de cada espécie.", "Hay shelter: up to 4 animals of each kind.");
		Village4Content.level("pen", 2, "Comedouro e segundo cocho: até 6 de cada; o mercado vende leite.",
				"Feeder and a second trough: up to 6 of each; the market sells milk.");
		Village4Content.level("pen", 3, "Celeiro de fundos: até 8 de cada espécie.", "Back barn: up to 8 of each kind.");
		Village4Content.level("apiary", 1, "Quatro colmeias e a casa do mel.", "Four hives and the honey house.");
		Village4Content.level("apiary", 2, "Oito colmeias e bancada de extração: mais mel; o mercado vende favos.",
				"Eight hives and an extraction bench: more honey; the market sells honeycomb.");
		Village4Content.level("butcher", 1, "Dois defumadores: o mercado vende carne crua e charque.", "Two smokers: the market sells raw meat and jerky.");
		Village4Content.level("butcher", 2, "Andar de cura: seca a carne cozida do baú em charque e vende charque em quantidade mais barato.",
				"Curing floor: dries the cooked meat in the chest into jerky and sells jerky in bulk, cheaper.");
		Village4Content.level("winery", 1, "Duas fileiras de parreira e adega: vinho.", "Two rows of vines and a cellar: wine.");
		Village4Content.level("winery", 2, "Três fileiras e andar de degustação: também cerveja.", "Three rows and a tasting floor: beer too.");
		Village4Content.level("winery", 3, "Terraço com mesas e barris empilhados: também cidra.", "Terrace with tables and stacked barrels: cider too.");
		Village4Content.level("sawmill", 1, "Uma bancada de serra: 6 tábuas por tronco (até 16 por vez); tábuas baratas no mercado.",
				"One saw bench: 6 planks per log (up to 16 at a time); cheap planks at the market.");
		Village4Content.level("sawmill", 2, "Segunda bancada e secador de tábuas: até 32 troncos por vez; tábuas de abeto mais baratas.",
				"Second bench and a plank drying rack: up to 32 logs at a time; cheaper spruce planks.");
		Village4Content.level("sawmill", 3, "Guindaste de viga e pilhas altas: a pilha toda de uma vez; tábuas de bétula baratas.",
				"Beam crane and tall piles: the whole stack at once; cheap birch planks.");

		// --- necessidades (substitui as dicas da fase 4) ---
		Village4Content.need("food", "Comida", "Food",
				"Plantações, pomar, curral, moinho, padaria, apiário e açougue alimentam a vila; o celeiro guarda o estoque e dá bônus de colheita.",
				"Farms, orchard, pen, mill, bakery, apiary and butcher feed the village; the granary keeps the stock and boosts harvests.");
		Village4Content.need("leisure", "Lazer e fé", "Leisure & faith",
				"Praça, estalagem, biblioteca, vinícola e Prefeitura. A igreja virá depois.",
				"Plaza, inn, library, winery and town hall. The church comes later.");
		Village4Content.need("commerce", "Comércio", "Commerce", "Mercado, moinho, padaria, açougue, vinícola, serraria e um pouco da forja.",
				"Market, mill, bakery, butcher, winery, sawmill and a little from the smithy.");

		// --- mercado ---
		Village4Content.shop("mill_flour", "Farinha de trigo", "Wheat flour", "Do estoque do moinho (moinho I).", "From the mill's stock (mill I).");
		Village4Content.shop("bakery_bread", "Pão da padaria", "Bakery bread", "Fresco do forno (padaria I).", "Fresh from the oven (bakery I).");
		Village4Content.shop("bakery_broa", "Broa de milho", "Corn bread", "Assada na padaria (padaria I).", "Baked at the bakery (bakery I).");
		Village4Content.shop("bakery_cheese_bread", "Pão de queijo", "Cheese bread", "Assado na padaria (padaria II).", "Baked at the bakery (bakery II).");
		Village4Content.shop("bakery_cake", "Bolo", "Cake", "Do forno de rua (padaria III).", "From the street oven (bakery III).");
		Village4Content.shop("orchard_apples", "Maçãs do pomar", "Orchard apples", "Colhidas hoje (pomar I).", "Picked today (orchard I).");
		Village4Content.shop("pen_wool", "Lã de ovelha", "Sheep wool", "Tosquiada no curral (curral I).", "Sheared at the pen (pen I).");
		Village4Content.shop("pen_milk", "Leite", "Milk", "Tirado no curral (curral II).", "Milked at the pen (pen II).");
		Village4Content.shop("apiary_honey", "Mel", "Honey", "Em garrafas, do apiário (apiário I).", "Bottled at the apiary (apiary I).");
		Village4Content.shop("apiary_comb", "Favos de mel", "Honeycomb", "Do apiário (apiário II).", "From the apiary (apiary II).");
		Village4Content.shop("butcher_beef", "Carne bovina crua", "Raw beef", "Cortes do açougue (açougue I).", "Cuts from the butcher (butcher I).");
		Village4Content.shop("butcher_pork", "Carne de porco crua", "Raw porkchop", "Cortes do açougue (açougue I).", "Cuts from the butcher (butcher I).");
		Village4Content.shop("butcher_jerky", "Charque", "Jerky", "Curado no açougue (açougue I).", "Cured at the butcher (butcher I).");
		Village4Content.shop("butcher_jerky_bulk", "Charque em quantidade", "Jerky in bulk", "Mais barato por unidade (açougue II).",
				"Cheaper per unit (butcher II).");
		Village4Content.shop("winery_wine", "Vinho", "Wine", "Da adega (vinícola I).", "From the cellar (winery I).");
		Village4Content.shop("winery_beer", "Cerveja", "Beer", "Da cervejaria (vinícola II).", "From the brewery (winery II).");
		Village4Content.shop("winery_cider", "Cidra", "Cider", "Da prensa (vinícola III).", "From the press (winery III).");
		Village4Content.shop("winery_grape_seeds", "Sementes de uva", "Grape seeds", "Para o seu parreiral (vinícola I).", "For your own vineyard (winery I).");
		Village4Content.shop("sawmill_planks", "Tábuas de carvalho", "Oak planks", "Serradas aqui (serraria I).", "Sawn here (sawmill I).");
		Village4Content.shop("sawmill_planks_bulk", "Tábuas de abeto", "Spruce planks", "Uma pilha por esmeralda (serraria II).",
				"A stack per emerald (sawmill II).");
		Village4Content.shop("sawmill_planks_birch", "Tábuas de bétula", "Birch planks", "Uma pilha por esmeralda (serraria III).",
				"A stack per emerald (sawmill III).");

		// --- mensagens ---
		Village4Content.text("message.mlinnotas.orchard.green", "A árvore ainda está verde: volte depois", "The tree is still green: come back later");
		Village4Content.text("message.mlinnotas.orchard.picked", "Fruta colhida", "Fruit picked");
		Village4Content.text("message.mlinnotas.sawmill.sawed", "Serrados: %s troncos em %s tábuas", "Sawn: %s logs into %s planks");
	}
}
