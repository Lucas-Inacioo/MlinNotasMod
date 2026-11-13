package com.mlin.notas.client.quests;

import com.mlin.notas.quests.Quest;
import com.mlin.notas.quests.QuestTask;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillTree;
import com.mlin.notas.tier.Tier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/** Textos e icones das quests e tarefas na tela do livro. */
public final class QuestTexts {
	private QuestTexts() {
	}

	public static ItemStack stack(String id) {
		Identifier parsed = Identifier.tryParse(id);
		Item item = parsed == null ? null : BuiltInRegistries.ITEM.getOptional(parsed).orElse(null);
		return new ItemStack(item == null ? Items.BARRIER : item);
	}

	/** Primeiro item da tag (ou barreira). */
	private static ItemStack firstInTag(String tagId) {
		Identifier id = Identifier.tryParse(tagId);
		if (id != null) {
			for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) {
				return new ItemStack(holder.value());
			}
		}
		return new ItemStack(Items.BARRIER);
	}

	private static EntityType<?> entity(String id) {
		Identifier parsed = Identifier.tryParse(id);
		return parsed == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(parsed).orElse(null);
	}

	private static String langPath(String id) {
		return id.replace(':', '.').replace('/', '.');
	}

	/** Nome legivel a partir do caminho do id ({@code ruined_watchtower} -> {@code Ruined watchtower}). */
	private static String pretty(String id) {
		String path = id.substring(id.indexOf(':') + 1);
		path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
		return path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1);
	}

	public static ItemStack taskIcon(QuestTask task) {
		if (!task.icon().isEmpty()) {
			return stack(task.icon());
		}
		return switch (task.type()) {
			case ITEM -> task.isTag() ? firstInTag(task.targetId()) : stack(task.target());
			case KILL -> {
				EntityType<?> type = task.isTag() ? null : entity(task.target());
				yield type == null ? new ItemStack(Items.IRON_SWORD)
						: SpawnEggItem.byId(type).map(h -> new ItemStack(h.value())).orElse(new ItemStack(Items.IRON_SWORD));
			}
			case STRUCTURE -> new ItemStack(Items.FILLED_MAP);
			case BIOME -> new ItemStack(Items.GRASS_BLOCK);
			case DIMENSION -> switch (task.target()) {
				case "minecraft:the_nether" -> new ItemStack(Items.NETHERRACK);
				case "minecraft:the_end" -> new ItemStack(Items.END_STONE);
				default -> new ItemStack(Items.GRASS_BLOCK);
			};
			case SKILL -> {
				SkillBranch branch = SkillBranch.byId(task.target());
				if (branch == SkillBranch.MINING) {
					yield stack(SkillTree.pickaxe(tier(task.level())));
				}
				yield stack(branch == null ? "minecraft:book" : branch.icon());
			}
			case ADVANCEMENT -> new ItemStack(Items.KNOWLEDGE_BOOK);
			case CHECKMARK -> new ItemStack(Items.WRITABLE_BOOK);
		};
	}

	private static Tier tier(int level) {
		Tier[] tiers = Tier.values();
		return tiers[Math.max(0, Math.min(tiers.length - 1, level - 1))];
	}

	/** Nome do alvo da tarefa. */
	public static Component targetName(QuestTask task) {
		String id = task.targetId();
		return switch (task.type()) {
			case ITEM -> task.isTag() ? Component.translatableWithFallback("tag.item." + langPath(id), "#" + id) : stack(id).getHoverName();
			case KILL -> {
				EntityType<?> type = task.isTag() ? null : entity(id);
				yield type == null ? Component.translatableWithFallback("tag.entity_type." + langPath(id), "#" + id) : type.getDescription();
			}
			case STRUCTURE -> Component.translatableWithFallback("quest.mlinnotas.structure." + langPath(id), pretty(id));
			case BIOME -> task.isTag() ? Component.translatableWithFallback("quest.mlinnotas.biome_tag." + langPath(id), pretty(id))
					: Component.translatableWithFallback("biome." + langPath(id), pretty(id));
			case DIMENSION -> Component.translatableWithFallback("quest.mlinnotas.dimension." + langPath(id), pretty(id));
			case SKILL -> skillName(task);
			case ADVANCEMENT -> Component.literal(pretty(id));
			case CHECKMARK -> Component.empty();
		};
	}

	private static Component skillName(QuestTask task) {
		SkillBranch branch = SkillBranch.byId(task.target());
		if (branch == null) {
			return Component.literal(task.target());
		}
		Component name = Component.translatable("skill.mlinnotas.branch." + branch.id());
		if (branch == SkillBranch.MINING) {
			return Component.translatable("skill.mlinnotas.node.mining", name, Component.translatable("skill.mlinnotas.tier." + tier(task.level()).id()));
		}
		return Component.translatable("skill.mlinnotas.node.side", name, Component.translatable("enchantment.level." + task.level()));
	}

	/** Texto da tarefa ("Tenha 16x Tronco", "Visite: Poco Antigo"...). */
	public static Component taskText(QuestTask task) {
		if (!task.title().isEmpty()) {
			return Component.translatable(task.title());
		}
		String key = switch (task.type()) {
			case ITEM -> task.consume() ? "gui.mlinnotas.quests.task.deliver" : "gui.mlinnotas.quests.task.have";
			case KILL -> "gui.mlinnotas.quests.task.kill";
			case STRUCTURE -> "gui.mlinnotas.quests.task.structure";
			case BIOME -> "gui.mlinnotas.quests.task.biome";
			case DIMENSION -> "gui.mlinnotas.quests.task.dimension";
			case SKILL -> "gui.mlinnotas.quests.task.skill";
			case ADVANCEMENT -> "gui.mlinnotas.quests.task.advancement";
			case CHECKMARK -> "gui.mlinnotas.quests.task.checkmark";
		};
		if (task.type() == QuestTask.Type.ITEM || task.type() == QuestTask.Type.KILL) {
			return Component.translatable(key, task.count(), targetName(task));
		}
		return Component.translatable(key, targetName(task));
	}

	public static Component title(Quest quest) {
		return Component.translatable(quest.titleKey());
	}
}
