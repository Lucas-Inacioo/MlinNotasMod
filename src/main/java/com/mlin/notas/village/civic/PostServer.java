package com.mlin.notas.village.civic;

import com.mlin.notas.village.VillageBuildings;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado servidor do Correio: a caixa de correio abre um bau com tres fileiras de entrada (o que chegou para a vila ou
 * para o jogador) e uma ou duas de saida. Agachado + botao direito troca o destino; ao fechar, o que esta na saida vira
 * uma carta para o destino e chega no dia seguinte de jogo.
 */
public final class PostServer {
	/** Um destino: o Correio de outra vila sua ou outro jogador. */
	public record Destination(boolean village, String key, String name) {
	}

	private static final Map<UUID, String> SELECTED = new HashMap<>();

	private PostServer() {
	}

	public static void clear() {
		SELECTED.clear();
	}

	public static long now(MinecraftServer server) {
		return server.overworld().getOverworldClockTime();
	}

	/** Destinos para quem posta na vila {@code from}: Correios funcionando de outras vilas e os outros jogadores conhecidos. */
	public static List<Destination> destinations(MinecraftServer server, VillageState from, UUID self) {
		List<Destination> out = new ArrayList<>();
		List<VillageState> villages = new ArrayList<>(VillageData.get(server).villages());
		villages.sort(Comparator.comparing(VillageState::name));
		for (VillageState v : villages) {
			if (!v.key().equals(from.key()) && !CivicSites.working(server, v, CivicBuildings.POST_OFFICE).isEmpty()) {
				out.add(new Destination(true, PostRules.villageInbox(v.key()), v.name()));
			}
		}
		List<Map.Entry<String, String>> players = new ArrayList<>(CivicData.get(server).knownPlayers().entrySet());
		players.sort(Map.Entry.comparingByValue());
		for (Map.Entry<String, String> p : players) {
			if (!p.getKey().equals(self.toString())) {
				out.add(new Destination(false, PostRules.playerInbox(p.getKey()), p.getValue()));
			}
		}
		return out;
	}

	public static Destination selected(List<Destination> list, UUID player) {
		String key = SELECTED.get(player);
		for (Destination d : list) {
			if (d.key().equals(key)) {
				return d;
			}
		}
		return list.isEmpty() ? null : list.get(0);
	}

	/** Botao direito na caixa de correio. */
	public static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		VillageState village = com.mlin.notas.village.VillageServer.planVillageAt(level, pos);
		CivicSites.Site site = village == null ? null : workingAt(server, village, pos);
		if (site == null) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.civic.post.inactive").withStyle(ChatFormatting.RED));
			return;
		}
		List<Destination> list = destinations(server, village, player.getUUID());
		Destination current = selected(list, player.getUUID());
		if (player.isShiftKeyDown()) {
			if (list.isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.mlinnotas.civic.post.no_destination").withStyle(ChatFormatting.YELLOW));
				return;
			}
			int index = Math.max(0, list.indexOf(current));
			Destination next = list.get(PostRules.cycle(index, list.size(), 1));
			SELECTED.put(player.getUUID(), next.key());
			player.sendOverlayMessage(Component.translatable(next.village() ? "message.mlinnotas.civic.post.dest_village" : "message.mlinnotas.civic.post.dest_player",
					next.name()).withStyle(ChatFormatting.GREEN));
			return;
		}
		open(player, level, village, site, current);
	}

	private static CivicSites.Site workingAt(MinecraftServer server, VillageState village, BlockPos pos) {
		for (CivicSites.Site site : CivicSites.working(server, village, CivicBuildings.POST_OFFICE)) {
			if (site.lot().rect().contains(pos.getX() - village.x(), pos.getZ() - village.z())) {
				return site;
			}
		}
		return null;
	}

	/** Abre o menu de correio (o destino pode ser null: so a caixa de entrada). */
	public static void open(ServerPlayer player, ServerLevel level, VillageState village, CivicSites.Site site, Destination destination) {
		MailContainer container = new MailContainer(level.getServer(), village, player, site.level(), destination);
		int rows = PostRules.menuRows(site.level());
		MenuType<?> type = rows == 4 ? MenuType.GENERIC_9x4 : MenuType.GENERIC_9x5;
		Component title = destination == null ? Component.translatable("container.mlinnotas.mailbox.none")
				: Component.translatable("container.mlinnotas.mailbox", destination.name());
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new MailMenu(type, id, inventory, container, rows), title));
	}

	/** Entrada (27 encaixes) mais saida (9 por nivel). */
	static final class MailContainer extends SimpleContainer {
		final MinecraftServer server;
		final VillageState village;
		final ServerPlayer owner;
		final int level;
		final Destination destination;

		MailContainer(MinecraftServer server, VillageState village, ServerPlayer owner, int level, Destination destination) {
			super(PostRules.INBOX_SLOTS + PostRules.outboxSlots(level));
			this.server = server;
			this.village = village;
			this.owner = owner;
			this.level = level;
			this.destination = destination;
			CivicData data = CivicData.get(server);
			int slot = 0;
			for (String key : List.of(PostRules.playerInbox(owner.getStringUUID()), PostRules.villageInbox(village.key()))) {
				List<ItemStack> inbox = data.inbox(key);
				while (slot < PostRules.INBOX_SLOTS && !inbox.isEmpty()) {
					setItem(slot++, inbox.remove(0));
				}
			}
			data.touch();
		}

		/** O que sobrou na entrada volta para a caixa da vila; a saida vira carta (ou volta ao jogador sem destino). */
		void finish(Player player) {
			CivicData data = CivicData.get(server);
			for (int i = 0; i < PostRules.INBOX_SLOTS; i++) {
				ItemStack stack = removeItemNoUpdate(i);
				if (!stack.isEmpty()) {
					data.inbox(PostRules.villageInbox(village.key())).add(stack);
				}
			}
			List<ItemStack> out = new ArrayList<>();
			for (int i = PostRules.INBOX_SLOTS; i < getContainerSize(); i++) {
				ItemStack stack = removeItemNoUpdate(i);
				if (!stack.isEmpty()) {
					out.add(stack);
				}
			}
			if (!out.isEmpty()) {
				if (destination == null) {
					for (ItemStack stack : out) {
						player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
					}
					player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.post.no_destination").withStyle(ChatFormatting.YELLOW));
				} else {
					data.post(new CivicData.Parcel(destination.key(), destination.name(), player.getName().getString(),
							PostRules.deliveryTick(now(server), level), out));
					player.sendSystemMessage(Component.translatable("message.mlinnotas.civic.post.sent", count(out), destination.name())
							.withStyle(ChatFormatting.GREEN));
				}
			}
			data.touch();
		}

		private static int count(List<ItemStack> stacks) {
			int n = 0;
			for (ItemStack stack : stacks) {
				n += stack.getCount();
			}
			return n;
		}
	}

	/** Bau normal; ao fechar entrega a saida e guarda a entrada. */
	static final class MailMenu extends ChestMenu {
		private final MailContainer mail;

		MailMenu(MenuType<?> type, int id, Inventory inventory, MailContainer container, int rows) {
			super(type, id, inventory, (Container) container, rows);
			this.mail = container;
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (!player.level().isClientSide()) {
				mail.finish(player);
			}
		}
	}

	// ---------------------------------------------------------------- entrega

	/** Entrega as cartas cujo dia chegou e avisa quem esta na vila. */
	public static void tick(MinecraftServer server) {
		CivicData data = CivicData.get(server);
		if (data.parcels().isEmpty()) {
			return;
		}
		long now = now(server);
		boolean changed = false;
		Iterator<CivicData.Parcel> it = data.parcels().iterator();
		while (it.hasNext()) {
			CivicData.Parcel parcel = it.next();
			if (parcel.deliverAt() > now) {
				continue;
			}
			it.remove();
			data.inbox(parcel.to()).addAll(CivicData.nonEmpty(parcel.items()));
			changed = true;
			notifyArrival(server, parcel);
		}
		if (changed) {
			data.touch();
		}
	}

	private static void notifyArrival(MinecraftServer server, CivicData.Parcel parcel) {
		Component message = Component.translatable("message.mlinnotas.civic.post.arrived", parcel.fromName()).withStyle(ChatFormatting.GOLD);
		if (parcel.to().startsWith("p:")) {
			ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(parcel.to().substring(2)));
			if (player != null) {
				player.sendSystemMessage(message);
			}
			return;
		}
		String key = parcel.to().substring(2);
		for (VillageState v : VillageData.get(server).villages()) {
			if (v.key().equals(key)) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					if (CivicSites.villageAt(server, p.level(), p.blockPosition(), 0) == v) {
						p.sendSystemMessage(message);
					}
				}
			}
		}
	}

	/** Nivel do Correio funcionando na vila (0 = nenhum). */
	public static int level(VillageState village) {
		return VillageBuildings.level(village, CivicBuildings.POST_OFFICE);
	}
}
