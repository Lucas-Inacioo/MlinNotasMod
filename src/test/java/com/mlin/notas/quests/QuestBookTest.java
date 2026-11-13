package com.mlin.notas.quests;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestBookTest {
	static QuestChapter chapter(String id, String json) {
		return QuestBook.parseChapter(id, json);
	}

	static boolean has(List<String> errors, String fragment) {
		return errors.stream().anyMatch(e -> e.contains(fragment));
	}

	@Test
	void parsesChapterAndResolvesDependencies() {
		QuestChapter c = chapter("basics", """
				{"icon":"minecraft:book","order":3,"quests":[
				  {"id":"a","x":0,"y":0,"tasks":[{"type":"checkmark"}],"rewards":[{"xp":10}]},
				  {"id":"b","x":1,"y":0.5,"dependencies":["a","other/c"],"tasks":[
				    {"type":"item","target":"#minecraft:logs","count":16},
				    {"type":"item","target":"minecraft:coal","count":4,"consume":true},
				    {"type":"skill","target":"mining","level":4}],
				   "rewards":[{"item":"minecraft:bread","count":8}]}
				]}""");
		assertEquals("basics", c.id());
		assertEquals(3, c.order());
		Quest b = c.quests().get(1);
		assertEquals("basics/b", b.key());
		assertEquals(List.of("basics/a", "other/c"), b.dependencies());
		assertEquals(0.5, b.y());
		assertTrue(b.tasks().get(0).isTag());
		assertEquals("minecraft:logs", b.tasks().get(0).targetId());
		assertTrue(b.tasks().get(1).isDelivery());
		assertEquals(1, b.tasks().get(2).goal(), "habilidade e sim/nao");
		assertEquals(4, b.tasks().get(2).level());
		assertEquals("quest.mlinnotas.basics.b", b.titleKey());
		assertEquals("quest.mlinnotas.basics.b.desc", b.descriptionKey());
		assertEquals("quest.mlinnotas.chapter.basics", c.titleKey());
	}

	@Test
	void jsonRoundTripKeepsEverything() {
		QuestChapter c = chapter("x", """
				{"icon":"minecraft:stone","order":1,"quests":[
				  {"id":"a","icon":"minecraft:dirt","x":2,"y":3,"tasks":[{"type":"kill","target":"minecraft:zombie","count":5}]},
				  {"id":"b","x":3,"y":3,"dependencies":["a","y/z"],"tasks":[{"type":"structure","target":"mlinnotas:old_well"}],
				   "rewards":[{"item":"minecraft:bread","count":2},{"xp":5}]}
				]}""");
		QuestChapter again = QuestBook.parseChapter("x", QuestBook.toJson(c));
		assertEquals(c, again);
	}

	@Test
	void unknownTaskTypeFailsToParse() {
		assertThrows(IllegalArgumentException.class, () -> chapter("x", """
				{"quests":[{"id":"a","tasks":[{"type":"fly"}]}]}"""));
	}

	@Test
	void validBookHasNoErrors() {
		QuestBook book = new QuestBook(List.of(
				chapter("a", """
						{"quests":[{"id":"one","x":0,"y":0,"tasks":[{"type":"checkmark"}]},
						           {"id":"two","x":1,"y":0,"dependencies":["one"],"tasks":[{"type":"dimension","target":"minecraft:the_nether"}]}]}"""),
				chapter("b", """
						{"order":1,"quests":[{"id":"three","dependencies":["a/two"],"tasks":[{"type":"biome","target":"#minecraft:is_jungle"}]}]}""")));
		assertEquals(List.of(), book.validate());
		assertEquals(3, book.quests().size());
		assertNotNull(book.quest("b/three"));
		assertEquals(List.of("a/two"), book.dependents("a/one").stream().map(Quest::key).toList());
	}

	@Test
	void reportsMissingDependencyAndSelfDependency() {
		QuestBook book = new QuestBook(List.of(chapter("a", """
				{"quests":[{"id":"one","x":0,"dependencies":["ghost","one"]}]}""")));
		List<String> errors = book.validate();
		assertTrue(has(errors, "inexistente: a/ghost"), errors.toString());
		assertTrue(has(errors, "depende de si mesma"), errors.toString());
	}

	@Test
	void reportsCycles() {
		QuestBook book = new QuestBook(List.of(chapter("a", """
				{"quests":[{"id":"one","x":0,"dependencies":["three"]},
				           {"id":"two","x":1,"dependencies":["one"]},
				           {"id":"three","x":2,"dependencies":["two"]},
				           {"id":"free","x":3}]}""")));
		List<String> errors = book.validate();
		assertEquals(1, errors.size(), errors.toString());
		assertTrue(has(errors, "ciclo"), errors.toString());
	}

	@Test
	void reportsCrossChapterCycle() {
		QuestBook book = new QuestBook(List.of(
				chapter("a", """
						{"quests":[{"id":"one","dependencies":["b/two"]}]}"""),
				chapter("b", """
						{"quests":[{"id":"two","dependencies":["a/one"]}]}""")));
		assertTrue(has(book.validate(), "ciclo"));
	}

	@Test
	void reportsBadTasksRewardsAndPositions() {
		QuestBook book = new QuestBook(List.of(chapter("a", """
				{"quests":[
				  {"id":"one","x":0,"y":0,"tasks":[
				    {"type":"item","target":"stone","count":1},
				    {"type":"kill","target":"minecraft:zombie","count":0},
				    {"type":"biome","target":"minecraft:desert","consume":true},
				    {"type":"skill","target":"cooking","level":1},
				    {"type":"skill","target":"combat","level":9},
				    {"type":"dimension","target":"#minecraft:any"}],
				   "rewards":[{"count":1},{"item":"minecraft:bread","xp":3}]},
				  {"id":"two","x":0,"y":0}
				]}""")));
		List<String> errors = book.validate();
		assertTrue(has(errors, "alvo invalido 'stone'"), errors.toString());
		assertTrue(has(errors, "contagem"), errors.toString());
		assertTrue(has(errors, "consume so vale para item"), errors.toString());
		assertTrue(has(errors, "ramo desconhecido"), errors.toString());
		assertTrue(has(errors, "nivel fora"), errors.toString());
		assertTrue(has(errors, "nao aceita tag"), errors.toString());
		assertTrue(has(errors, "recompensa precisa de item ou xp"), errors.toString());
		assertTrue(has(errors, "mesma posicao"), errors.toString());
	}

	@Test
	void chaptersAreSortedByOrderThenId() {
		QuestBook book = new QuestBook(List.of(
				chapter("z", "{\"order\":0,\"quests\":[]}"),
				chapter("b", "{\"order\":5,\"quests\":[]}"),
				chapter("a", "{\"order\":5,\"quests\":[]}")));
		assertEquals(List.of("z", "a", "b"), book.chapters().stream().map(QuestChapter::id).toList());
	}
}
