package com.mlin.notas.quests;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confere o livro de quests que vai no jar ({@code data/mlinnotas/quests/*.json}, gerado por scripts/QuestContent.java):
 * sem erros de validacao, todos os textos em pt_br e en_us, tamanho e uma ordem jogavel.
 */
class QuestContentTest {
	private static final Path QUESTS = Path.of("src/main/resources/data/mlinnotas/quests");
	private static final Path LANG = Path.of("src/main/resources/assets/mlinnotas/lang");

	static QuestBook load() throws IOException {
		List<QuestChapter> chapters = new ArrayList<>();
		try (Stream<Path> files = Files.list(QUESTS)) {
			for (Path file : files.sorted().toList()) {
				String name = file.getFileName().toString();
				chapters.add(QuestBook.parseChapter(name.substring(0, name.length() - ".json".length()), Files.readString(file, StandardCharsets.UTF_8)));
			}
		}
		return new QuestBook(chapters);
	}

	@Test
	void shippedBookIsValid() throws IOException {
		QuestBook book = load();
		assertEquals(List.of(), book.validate());
		assertEquals(List.of("basics", "mining", "exploration", "bosses", "nether", "end", "tech", "kitchen"),
				book.chapters().stream().map(QuestChapter::id).toList());
		int total = book.quests().size();
		assertTrue(total >= 50 && total <= 130, "quests: " + total);
	}

	@Test
	void everyTextIsTranslated() throws IOException {
		QuestBook book = load();
		String pt = Files.readString(LANG.resolve("pt_br.json"), StandardCharsets.UTF_8);
		String en = Files.readString(LANG.resolve("en_us.json"), StandardCharsets.UTF_8);
		List<String> missing = new ArrayList<>();
		for (String key : book.translationKeys()) {
			if (!pt.contains("\"" + key + "\"")) {
				missing.add("pt_br: " + key);
			}
			if (!en.contains("\"" + key + "\"")) {
				missing.add("en_us: " + key);
			}
		}
		for (Quest quest : book.quests()) {
			for (QuestTask task : quest.tasks()) {
				if (task.type() == QuestTask.Type.STRUCTURE) {
					String key = "quest.mlinnotas.structure." + task.targetId().replace(':', '.');
					if (!pt.contains("\"" + key + "\"") || !en.contains("\"" + key + "\"")) {
						missing.add(key);
					}
				}
				if (task.type() == QuestTask.Type.ADVANCEMENT && task.title().isEmpty()) {
					missing.add(quest.key() + ": conquista sem titulo");
				}
			}
		}
		assertEquals(List.of(), missing);
	}

	@Test
	void onlyTheWelcomeQuestStartsOpen() throws IOException {
		QuestBook book = load();
		QuestProgress progress = new QuestProgress();
		assertEquals(List.of("basics/welcome"), progress.active(book).stream().map(Quest::key).toList());
		// completar tudo em ordem de dependencia chega ao fim: nenhuma quest fica inalcancavel
		for (Quest quest : book.quests()) {
			progress.forceComplete(book, quest);
		}
		assertEquals(book.quests().size(), progress.completedList().size());
	}
}
