package com.mlin.notas.storage;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.util.Pos;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Tablet de Rede: acesso remoto a um Terminal (ou Terminal de Crafting) da rede de armazenamento. Shift+botao direito
 * no terminal vincula (dimensao + posicao no componente {@code mlinnotas:tablet_link}); botao direito em qualquer
 * lugar, ou a tecla do tablet com ele no inventario, abre o mesmo {@link TerminalMenu} sem limite de distancia na
 * mesma dimensao. Enquanto o menu remoto esta aberto, tickets temporarios ({@link #TICKET}, expiram sozinhos em 3 s se
 * nao forem renovados) seguram carregadas as chunks do terminal e da rede ({@link TabletChunks}).
 */
public final class NetworkTablet {
	public static final DataComponentType<TabletLink> LINK = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			MlinNotas.id("tablet_link"), DataComponentType.<TabletLink>builder()
					.persistent(TabletLink.CODEC)
					.networkSynchronized(TabletLink.STREAM_CODEC)
					.build());

	/** Carrega (sem simular) e expira em 60 ticks se o menu nao renovar; nunca e salvo. */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, MlinNotas.id("network_tablet"),
			new TicketType(60L, TicketType.FLAG_LOADING));

	public static final Item ITEM = ModItems.register("network_tablet", NetworkTabletItem::new, new Item.Properties().stacksTo(1));

	/** Cliente -> servidor: tecla do tablet; abre o primeiro tablet vinculado do inventario. */
	public record OpenPayload() implements CustomPacketPayload {
		public static final OpenPayload INSTANCE = new OpenPayload();
		public static final Type<OpenPayload> TYPE = new Type<>(MlinNotas.id("open_network_tablet"));
		public static final StreamCodec<ByteBuf, OpenPayload> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private NetworkTablet() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(OpenPayload.TYPE, OpenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenPayload.TYPE, (payload, context) -> openFromInventory(context.player()));
	}

	/** Vincula {@code stack} ao terminal em {@code pos} (servidor). */
	public static boolean link(ServerPlayer player, ItemStack stack, BlockPos pos) {
		BlockState state = player.level().getBlockState(pos);
		if (!(state.getBlock() instanceof StorageTerminalBlock terminal)) {
			return false;
		}
		stack.set(LINK, new TabletLink(GlobalPos.of(player.level().dimension(), pos.immutable()), terminal.crafting()));
		message(player, Component.translatable("message.mlinnotas.network_tablet.linked", state.getBlock().getName(),
				pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	/** Tecla do tablet: mao principal, segunda mao e depois o resto do inventario (vinculado tem preferencia). */
	public static void openFromInventory(ServerPlayer player) {
		ItemStack found = ItemStack.EMPTY;
		for (ItemStack stack : candidates(player.getInventory())) {
			if (stack.is(ITEM)) {
				if (stack.has(LINK)) {
					found = stack;
					break;
				}
				if (found.isEmpty()) {
					found = stack;
				}
			}
		}
		if (!found.isEmpty()) {
			open(player, found);
		}
	}

	private static List<ItemStack> candidates(Inventory inventory) {
		List<ItemStack> out = new ArrayList<>();
		out.add(inventory.player.getMainHandItem());
		out.add(inventory.player.getOffhandItem());
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			out.add(inventory.getItem(i));
		}
		return out;
	}

	/**
	 * Abre de longe o terminal vinculado a {@code stack}. Sem vinculo, outra dimensao ou terminal sumido: mensagem (e, no
	 * ultimo caso, o vinculo e apagado). Devolve se abriu.
	 */
	public static boolean open(ServerPlayer player, ItemStack stack) {
		TabletLink link = stack.get(LINK);
		if (link == null) {
			message(player, Component.translatable("message.mlinnotas.network_tablet.unlinked").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		if (!player.level().dimension().equals(link.terminal().dimension())) {
			message(player, Component.translatable("message.mlinnotas.network_tablet.other_dimension").withStyle(ChatFormatting.RED));
			return false;
		}
		ServerLevel level = player.level();
		BlockPos pos = link.terminal().pos();
		if (!level.isInWorldBounds(pos)) {
			stack.remove(LINK);
			return false;
		}
		// segura a chunk ja antes de olhar o bloco (o getBlockState carrega na hora; o ticket mantem enquanto abre)
		level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.containing(pos), TabletChunks.RADIUS);
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof StorageTerminalBlock terminal)) {
			stack.remove(LINK);
			message(player, Component.translatable("message.mlinnotas.network_tablet.lost", pos.getX(), pos.getY(), pos.getZ())
					.withStyle(ChatFormatting.RED));
			return false;
		}
		if (terminal.crafting() != link.crafting()) {
			stack.set(LINK, new TabletLink(link.terminal(), terminal.crafting()));
		}
		StorageTerminalBlock.open(player, pos, terminal.crafting(), true);
		return true;
	}

	/** O jogador tem no inventario um tablet vinculado a {@code pos} na dimensao em que esta? */
	public static boolean hasTabletFor(Player player, BlockPos pos) {
		GlobalPos target = GlobalPos.of(player.level().dimension(), pos);
		for (ItemStack stack : candidates(player.getInventory())) {
			TabletLink link = stack.is(ITEM) ? stack.get(LINK) : null;
			if (link != null && link.terminal().equals(target)) {
				return true;
			}
		}
		return false;
	}

	/** Renova os tickets das chunks do terminal e da rede (chamado a cada sincronizacao do menu remoto). */
	static void keepLoaded(ServerLevel level, BlockPos terminal, NetworkGraph.Result graph, Set<Long> ticketed) {
		List<Pos> nodes = new ArrayList<>(graph.units());
		nodes.addAll(graph.containers());
		nodes.addAll(graph.terminals());
		for (long chunk : TabletChunks.plan(new Pos(terminal.getX(), terminal.getY(), terminal.getZ()), nodes)) {
			level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(TabletChunks.unpackX(chunk), TabletChunks.unpackZ(chunk)),
					TabletChunks.RADIUS);
			ticketed.add(chunk);
		}
	}

	/** Solta os tickets quando o menu remoto fecha. */
	static void release(ServerLevel level, Set<Long> ticketed) {
		for (Iterator<Long> it = ticketed.iterator(); it.hasNext(); ) {
			long chunk = it.next();
			level.getChunkSource().removeTicketWithRadius(TICKET, new ChunkPos(TabletChunks.unpackX(chunk), TabletChunks.unpackZ(chunk)),
					TabletChunks.RADIUS);
			it.remove();
		}
	}

	private static void message(ServerPlayer player, Component text) {
		if (player.connection != null) {
			player.sendOverlayMessage(text);
		}
	}
}
