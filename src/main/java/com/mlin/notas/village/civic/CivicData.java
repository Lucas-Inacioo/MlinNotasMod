package com.mlin.notas.village.civic;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estado das construcoes civicas (Vilas 2.0, fase 7), salvo a parte em {@code data/mlinnotas/civic.dat}: cartas em
 * transito e caixas de entrada do Correio, doacoes do Museu, expedicoes e retornos da Guilda, jogadores conhecidos e
 * dimensoes visitadas. Campos que faltam no arquivo viram vazios.
 */
public class CivicData extends SavedData {
	/** Carta em transito: para quem ({@code v:<vila>} / {@code p:<jogador>}), nome de quem manda e chega em {@code deliverAt}. */
	public record Parcel(String to, String toName, String fromName, long deliverAt, List<ItemStack> items) {
	}

	/** Expedicao em andamento: o aldeao (NBT inteiro), o destino, quando volta e o que traz. */
	public record Expedition(String village, String destination, long returnAt, String villagerName, CompoundTag villager, List<ItemStack> loot) {
	}

	/** Doacoes do Museu de uma vila: itens em ordem de doacao e colecoes ja premiadas. */
	public static final class Museum {
		public final List<String> donated = new ArrayList<>();
		public final Set<String> claimed = new LinkedHashSet<>();
	}

	private static final Codec<Parcel> PARCEL = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("to").forGetter(Parcel::to),
			Codec.STRING.optionalFieldOf("to_name", "?").forGetter(Parcel::toName),
			Codec.STRING.optionalFieldOf("from_name", "?").forGetter(Parcel::fromName),
			Codec.LONG.fieldOf("deliver_at").forGetter(Parcel::deliverAt),
			ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Parcel::items)
	).apply(i, Parcel::new));

	private static final Codec<Expedition> EXPEDITION = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("village").forGetter(Expedition::village),
			Codec.STRING.fieldOf("destination").forGetter(Expedition::destination),
			Codec.LONG.fieldOf("return_at").forGetter(Expedition::returnAt),
			Codec.STRING.optionalFieldOf("villager_name", "").forGetter(Expedition::villagerName),
			CompoundTag.CODEC.fieldOf("villager").forGetter(Expedition::villager),
			ItemStack.CODEC.listOf().optionalFieldOf("loot", List.of()).forGetter(Expedition::loot)
	).apply(i, Expedition::new));

	private static final Codec<Map<String, List<ItemStack>>> STACK_MAP = Codec.unboundedMap(Codec.STRING, ItemStack.CODEC.listOf());

	private static final Codec<Museum> MUSEUM = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("donated", List.of()).forGetter(m -> m.donated),
			Codec.STRING.listOf().optionalFieldOf("claimed", List.of()).forGetter(m -> List.copyOf(m.claimed))
	).apply(i, (donated, claimed) -> {
		Museum m = new Museum();
		m.donated.addAll(donated);
		m.claimed.addAll(claimed);
		return m;
	}));

	public static final Codec<CivicData> CODEC = RecordCodecBuilder.create(i -> i.group(
			PARCEL.listOf().optionalFieldOf("parcels", List.of()).forGetter(d -> d.parcels),
			STACK_MAP.optionalFieldOf("inboxes", Map.of()).forGetter(d -> d.inboxes),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("known_players", Map.of()).forGetter(d -> d.knownPlayers),
			Codec.unboundedMap(Codec.STRING, MUSEUM).optionalFieldOf("museums", Map.of()).forGetter(d -> d.museums),
			EXPEDITION.listOf().optionalFieldOf("expeditions", List.of()).forGetter(d -> d.expeditions),
			STACK_MAP.optionalFieldOf("guild_returns", Map.of()).forGetter(d -> d.guildReturns),
			Codec.STRING.listOf().optionalFieldOf("visited", List.of()).forGetter(d -> List.copyOf(d.visited))
	).apply(i, CivicData::new));

	public static final SavedDataType<CivicData> TYPE = new SavedDataType<>(MlinNotas.id("civic"), CivicData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final List<Parcel> parcels = new ArrayList<>();
	private final Map<String, List<ItemStack>> inboxes = new LinkedHashMap<>();
	private final Map<String, String> knownPlayers = new LinkedHashMap<>();
	private final Map<String, Museum> museums = new LinkedHashMap<>();
	private final List<Expedition> expeditions = new ArrayList<>();
	private final Map<String, List<ItemStack>> guildReturns = new LinkedHashMap<>();
	private final Set<String> visited = new LinkedHashSet<>();

	public CivicData() {
	}

	private CivicData(List<Parcel> parcels, Map<String, List<ItemStack>> inboxes, Map<String, String> knownPlayers, Map<String, Museum> museums,
			List<Expedition> expeditions, Map<String, List<ItemStack>> guildReturns, List<String> visited) {
		this.parcels.addAll(parcels);
		inboxes.forEach((k, v) -> this.inboxes.put(k, new ArrayList<>(v)));
		this.knownPlayers.putAll(knownPlayers);
		this.museums.putAll(museums);
		this.expeditions.addAll(expeditions);
		guildReturns.forEach((k, v) -> this.guildReturns.put(k, new ArrayList<>(v)));
		this.visited.addAll(visited);
	}

	public static CivicData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	// ---------------------------------------------------------------- correio

	public List<Parcel> parcels() {
		return parcels;
	}

	public void post(Parcel parcel) {
		parcels.add(parcel);
		setDirty();
	}

	/** Caixa de entrada (mutavel; chame {@link #touch()} depois de mexer). */
	public List<ItemStack> inbox(String key) {
		return inboxes.computeIfAbsent(key, k -> new ArrayList<>());
	}

	public Map<String, String> knownPlayers() {
		return knownPlayers;
	}

	public void touch() {
		setDirty();
	}

	// ---------------------------------------------------------------- museu

	public Museum museum(String villageKey) {
		return museums.computeIfAbsent(villageKey, k -> new Museum());
	}

	public Map<String, Museum> museums() {
		return museums;
	}

	// ---------------------------------------------------------------- guilda

	public List<Expedition> expeditions() {
		return expeditions;
	}

	public List<ItemStack> guildReturns(String villageKey) {
		return guildReturns.computeIfAbsent(villageKey, k -> new ArrayList<>());
	}

	// ---------------------------------------------------------------- dimensoes visitadas

	public Set<String> visited() {
		return visited;
	}

	public boolean markVisited(String dimension) {
		if (visited.add(dimension)) {
			setDirty();
			return true;
		}
		return false;
	}

	/** Copia solta para testes: itens de uma lista que nao sao vazios. */
	public static List<ItemStack> nonEmpty(List<ItemStack> stacks) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty()) {
				out.add(stack);
			}
		}
		return out;
	}
}
