package com.mlin.notas.village.civic;

import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lado servidor do Museu: o pedestal recebe uma doacao de cada item das colecoes liberadas pelo nivel (botao direito com o
 * item na mao), a doacao aparece numa moldura da parede (as molduras ja vem prontas na planta; preenchidas na ordem das
 * doacoes) e cada colecao completa paga recompensa e prosperidade uma vez. Mao vazia mostra o andamento.
 */
public final class MuseumServer {
	private MuseumServer() {
	}

	public static Item item(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(identifier);
	}

	public static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		VillageState village = com.mlin.notas.village.VillageServer.planVillageAt(level, pos);
		CivicSites.Site site = null;
		if (village != null) {
			for (CivicSites.Site s : CivicSites.working(server, village, CivicBuildings.MUSEUM)) {
				if (s.lot().rect().contains(pos.getX() - village.x(), pos.getZ() - village.z())) {
					site = s;
				}
			}
		}
		if (site == null) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.civic.museum.inactive").withStyle(ChatFormatting.RED));
			return;
		}
		CivicData data = CivicData.get(server);
		CivicData.Museum museum = data.museum(village.key());
		ItemStack held = player.getMainHandItem();
		String id = held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
		if (!held.isEmpty() && MuseumSets.wanted(id, site.level())) {
			if (museum.donated.contains(id)) {
				player.sendOverlayMessage(Component.translatable("message.mlinnotas.civic.museum.have", held.getHoverName()).withStyle(ChatFormatting.YELLOW));
				return;
			}
			donate(player, level, village, site, museum, held, id);
			return;
		}
		syncFrames(level, site, museum);
		status(player, museum, site.level());
		if (!held.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.civic.museum.not_wanted", held.getHoverName()).withStyle(ChatFormatting.YELLOW));
		}
	}

	private static void donate(ServerPlayer player, ServerLevel level, VillageState village, CivicSites.Site site, CivicData.Museum museum,
			ItemStack held, String id) {
		MinecraftServer server = level.getServer();
		Component name = held.getHoverName();
		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}
		museum.donated.add(id);
		level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.museum.donated", name).withStyle(ChatFormatting.GREEN));
		syncFrames(level, site, museum);
		Set<String> donated = new HashSet<>(museum.donated);
		for (MuseumSets.MuseumSet set : MuseumSets.newlyComplete(donated, site.level(), museum.claimed)) {
			museum.claimed.add(set.id());
			for (MuseumSets.Reward reward : set.rewards()) {
				ItemStack stack = new ItemStack(item(reward.item()), reward.count());
				if (!stack.isEmpty()) {
					player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.civic.museum.complete", player.getDisplayName(),
					Component.translatable("museum.mlinnotas.set." + set.id()), village.name(), set.prosperity()).withStyle(ChatFormatting.GOLD), false);
			VillageLevel up = village.addProsperity(set.prosperity());
			if (up != null) {
				VillageServer.levelUp(server, village, up);
			}
		}
		CivicData.get(server).touch();
		VillageData.get(server).changed();
	}

	/** Chat: cada colecao liberada com o andamento. */
	public static void status(ServerPlayer player, CivicData.Museum museum, int level) {
		Set<String> donated = new HashSet<>(museum.donated);
		player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.museum.head", museum.donated.size()).withStyle(ChatFormatting.GOLD));
		for (MuseumSets.MuseumSet set : MuseumSets.available(level)) {
			boolean done = museum.claimed.contains(set.id());
			player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.museum.line", Component.translatable("museum.mlinnotas.set." + set.id()),
					set.have(donated), set.items().size()).withStyle(done ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		}
		int locked = (int) MuseumSets.all().stream().filter(s -> s.minLevel() > level).count();
		if (locked > 0) {
			player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.museum.locked", locked).withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** Molduras da planta, na ordem; a i-esima mostra a i-esima doacao. Cria as que faltam e acerta o item. */
	public static void syncFrames(ServerLevel level, CivicSites.Site site, CivicData.Museum museum) {
		List<int[]> marks = new ArrayList<>(site.canvas().marks("frame"));
		marks.sort((a, b) -> a[1] != b[1] ? Integer.compare(a[1], b[1]) : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[2], b[2]));
		for (int i = 0; i < Math.min(marks.size(), museum.donated.size()); i++) {
			int[] m = marks.get(i);
			BlockPos pos = site.world(m);
			Direction facing = site.facing(m);
			if (facing == null || !level.isLoaded(pos)) {
				continue;
			}
			ItemStack shown = new ItemStack(item(museum.donated.get(i)));
			if (shown.isEmpty()) {
				continue;
			}
			ItemFrame frame = null;
			for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, new AABB(pos))) {
				if (f.getPos().equals(pos)) {
					frame = f;
				}
			}
			if (frame == null) {
				frame = new ItemFrame(level, pos, facing);
				frame.setPermanentlyInvulnerable(true);
				frame.setItem(shown, false);
				level.addFreshEntity(frame);
			} else if (frame.getItem().isEmpty()) {
				frame.setItem(shown, false);
			}
		}
	}

	/** Molduras (entidades) com item dentro da construcao: testes e quadro de status. */
	public static int framesShown(ServerLevel level, CivicSites.Site site) {
		int n = 0;
		for (int[] m : site.canvas().marks("frame")) {
			BlockPos pos = site.world(m);
			for (ItemFrame f : level.getEntitiesOfClass(ItemFrame.class, new AABB(pos))) {
				if (f.getPos().equals(pos) && !f.getItem().isEmpty()) {
					n++;
				}
			}
		}
		return n;
	}
}
