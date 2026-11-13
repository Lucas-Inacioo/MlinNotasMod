package com.mlin.notas.recipes;

import com.mlin.notas.MlinNotas;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Servidor -> cliente: um lote de receitas achatadas. O cliente acumula ate receber {@code last == true}
 * e entao reconstroi o {@link RecipeIndex}. {@code first} zera o acumulador (nova sincronizacao).
 */
public record RecipeSyncPayload(List<RecipeView> recipes, boolean first, boolean last) implements CustomPacketPayload {
	public static final Type<RecipeSyncPayload> TYPE = new Type<>(MlinNotas.id("recipe_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, RecipeSyncPayload> CODEC = StreamCodec.of(RecipeSyncPayload::write, RecipeSyncPayload::read);

	/** Receitas por pacote: mantem cada pacote bem abaixo do limite de 1 MiB. */
	public static final int BATCH_SIZE = 300;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void write(FriendlyByteBuf buf, RecipeSyncPayload payload) {
		buf.writeBoolean(payload.first);
		buf.writeBoolean(payload.last);
		buf.writeVarInt(payload.recipes.size());
		for (RecipeView recipe : payload.recipes) {
			buf.writeUtf(recipe.id());
			buf.writeByte(recipe.kind().ordinal());
			buf.writeByte(recipe.width());
			buf.writeByte(recipe.height());
			buf.writeVarInt(recipe.inputs().size());
			for (List<Integer> slot : recipe.inputs()) {
				buf.writeVarInt(slot.size());
				for (int item : slot) {
					buf.writeVarInt(item);
				}
			}
			buf.writeVarInt(recipe.output());
			buf.writeVarInt(recipe.outputCount());
			buf.writeVarInt(recipe.cookTicks());
			buf.writeFloat(recipe.experience());
		}
	}

	private static RecipeSyncPayload read(FriendlyByteBuf buf) {
		boolean first = buf.readBoolean();
		boolean last = buf.readBoolean();
		int count = buf.readVarInt();
		List<RecipeView> recipes = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			String id = buf.readUtf();
			RecipeKind kind = RecipeKind.byOrdinal(buf.readByte());
			int width = buf.readByte();
			int height = buf.readByte();
			int slots = buf.readVarInt();
			List<List<Integer>> inputs = new ArrayList<>(slots);
			for (int s = 0; s < slots; s++) {
				int alternatives = buf.readVarInt();
				List<Integer> slot = new ArrayList<>(alternatives);
				for (int a = 0; a < alternatives; a++) {
					slot.add(buf.readVarInt());
				}
				inputs.add(slot);
			}
			int output = buf.readVarInt();
			int outputCount = buf.readVarInt();
			int cookTicks = buf.readVarInt();
			float experience = buf.readFloat();
			recipes.add(new RecipeView(id, kind, width, height, inputs, output, outputCount, cookTicks, experience));
		}
		return new RecipeSyncPayload(recipes, first, last);
	}

	/** Divide a lista em lotes prontos para enviar, marcando o primeiro e o ultimo. */
	public static List<RecipeSyncPayload> batches(List<RecipeView> recipes) {
		List<RecipeSyncPayload> result = new ArrayList<>();
		if (recipes.isEmpty()) {
			result.add(new RecipeSyncPayload(List.of(), true, true));
			return result;
		}
		for (int start = 0; start < recipes.size(); start += BATCH_SIZE) {
			int end = Math.min(recipes.size(), start + BATCH_SIZE);
			result.add(new RecipeSyncPayload(recipes.subList(start, end), start == 0, end == recipes.size()));
		}
		return result;
	}
}
