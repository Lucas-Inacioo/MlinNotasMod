package com.mlin.notas.quests;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestProgressTest {
	private static final QuestBook BOOK = new QuestBook(List.of(QuestBook.parseChapter("c", """
			{"quests":[
			  {"id":"start","x":0,"tasks":[{"type":"checkmark"}],"rewards":[{"xp":10}]},
			  {"id":"logs","x":1,"dependencies":["start"],"tasks":[
			    {"type":"item","target":"#minecraft:logs","count":16},
			    {"type":"kill","target":"minecraft:zombie","count":3}],
			   "rewards":[{"item":"minecraft:bread","count":4}]},
			  {"id":"coal","x":2,"dependencies":["start"],"tasks":[{"type":"item","target":"minecraft:coal","count":10,"consume":true}]},
			  {"id":"milestone","x":3,"dependencies":["logs","coal"],"rewards":[{"xp":100}]},
			  {"id":"after","x":4,"dependencies":["milestone"]},
			  {"id":"far","x":5,"dependencies":["far2"],"tasks":[{"type":"checkmark"}]},
			  {"id":"far2","x":6,"dependencies":["coal"],"tasks":[{"type":"checkmark"}]}
			]}""")));

	private static Quest q(String id) {
		return BOOK.quest("c/" + id);
	}

	@Test
	void statusFollowsDependencies() {
		QuestProgress p = new QuestProgress();
		assertEquals(QuestProgress.Status.ACTIVE, p.status(q("start")));
		assertEquals(QuestProgress.Status.LOCKED, p.status(q("logs")));
		assertEquals(List.of(q("start")), p.active(BOOK));
	}

	@Test
	void lockedQuestsDoNotProgress() {
		QuestProgress p = new QuestProgress();
		assertEquals(0, p.add(q("logs"), 1, 1));
		assertFalse(p.raise(q("logs"), 0, 16));
		assertEquals(0, p.progress(q("logs"), 0));
	}

	@Test
	void addIsCappedAndSettleCompletes() {
		QuestProgress p = new QuestProgress();
		assertTrue(p.finish(q("start"), 0));
		assertEquals(List.of(q("start")), p.settle(BOOK));
		assertTrue(p.isComplete("c/start"));

		Quest logs = q("logs");
		assertEquals(2, p.add(logs, 1, 2));
		assertEquals(1, p.add(logs, 1, 5), "so falta 1");
		assertEquals(0, p.add(logs, 1, 1), "ja cheio");
		assertTrue(p.raise(logs, 0, 10));
		assertFalse(p.raise(logs, 0, 4), "ter menos depois nao volta o progresso");
		assertEquals(10, p.progress(logs, 0));
		assertEquals(0.5 * 0 + (10.0 + 3) / 19, p.fraction(logs), 1e-9);
		assertEquals(List.of(), p.settle(BOOK), "ainda faltam troncos");
		assertTrue(p.raise(logs, 0, 99));
		assertEquals(16, p.progress(logs, 0));
		assertEquals(List.of(logs), p.settle(BOOK));
		assertEquals(1.0, p.fraction(logs));
	}

	@Test
	void milestonesCompleteInChain() {
		QuestProgress p = new QuestProgress();
		p.finish(q("start"), 0);
		p.settle(BOOK);
		p.raise(q("logs"), 0, 16);
		p.add(q("logs"), 1, 3);
		p.add(q("coal"), 0, 10);
		List<Quest> done = p.settle(BOOK);
		assertEquals(List.of("c/logs", "c/coal", "c/milestone", "c/after"), done.stream().map(Quest::key).toList(),
				"marco sem tarefas completa assim que libera, e libera o proximo");
	}

	@Test
	void rewardsAreClaimedOncePerPlayer() {
		QuestProgress p = new QuestProgress();
		Quest start = q("start");
		assertEquals(QuestProgress.View.ACTIVE, p.view(start, "alice"));
		assertFalse(p.claim("alice", start), "nao completa");
		p.finish(start, 0);
		p.settle(BOOK);
		assertEquals(QuestProgress.View.CLAIMABLE, p.view(start, "alice"));
		assertTrue(p.claim("alice", start));
		assertFalse(p.claim("alice", start), "uma vez so");
		assertEquals(QuestProgress.View.DONE, p.view(start, "alice"));
		assertEquals(QuestProgress.View.CLAIMABLE, p.view(start, "bob"), "bob ainda tem a dele");
		assertTrue(p.claim("bob", start));
		QuestChapter chapter = BOOK.chapter("c");
		assertEquals(0, p.claimableCount(chapter, "bob"));
	}

	@Test
	void questWithoutRewardsIsDoneNotClaimable() {
		QuestProgress p = new QuestProgress();
		p.forceComplete(BOOK, q("coal"));
		assertEquals(QuestProgress.View.DONE, p.view(q("coal"), "alice"));
		assertFalse(p.claim("alice", q("coal")));
	}

	@Test
	void forceCompleteTakesMissingDependencies() {
		QuestProgress p = new QuestProgress();
		List<Quest> done = p.forceComplete(BOOK, q("far"));
		assertEquals(List.of("c/start", "c/coal", "c/far2", "c/far"), done.stream().map(Quest::key).toList());
		assertEquals(4, p.completedCount(BOOK.chapter("c")));
	}

	@Test
	void exportRoundTripAndPlayerView() {
		QuestProgress p = new QuestProgress();
		p.finish(q("start"), 0);
		p.settle(BOOK);
		p.add(q("logs"), 1, 2);
		p.claim("alice", q("start"));
		QuestProgress copy = QuestProgress.of(p.completedList(), p.taskMap(), p.claimedMap());
		assertEquals(2, copy.progress(q("logs"), 1));
		assertTrue(copy.hasClaimed("alice", q("start")));
		QuestProgress bobView = p.forPlayer("bob");
		assertTrue(bobView.isComplete("c/start"));
		assertEquals(Map.of(), bobView.claimedMap(), "o cliente do bob nao recebe os resgates da alice");
		p.clear();
		assertEquals(List.of(), p.completedList());
	}
}
