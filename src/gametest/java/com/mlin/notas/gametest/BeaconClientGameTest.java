package com.mlin.notas.gametest;

import com.mlin.notas.beacon.BeaconFlight;
import com.mlin.notas.beacon.BeaconTiers;
import com.mlin.notas.client.beacon.BeaconScreenExtras;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.client.mixin.BeaconScreenAccessor;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModEffects;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Game test de cliente do sinalizador por nivel: piramide de 4 camadas de celestio, tela do sinalizador com o metal,
 * o alcance e os botoes Garimpo/Voo (screenshot), escolha do Voo e voo concedido ao jogador de sobrevivencia; depois
 * um bloco de ferro na piramide rebaixa tudo (screenshot com os extras bloqueados).
 */
@SuppressWarnings("UnstableApiUsage")
public class BeaconClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("gamerule advance_time false");
			singleplayer.getServer().runCommand("gamemode survival @a");

			BlockPos beaconPos = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos center = player.blockPosition().offset(0, 0, 7);
				BlockState celestium = ModBlocks.storage(Tier.CELESTIUM).defaultBlockState();
				for (int layer = 0; layer < 4; layer++) {
					int half = 4 - layer;
					for (int x = -half; x <= half; x++) {
						for (int z = -half; z <= half; z++) {
							level.setBlockAndUpdate(center.offset(x, layer, z), celestium);
						}
					}
				}
				BlockPos beacon = center.above(4);
				level.setBlockAndUpdate(beacon, Blocks.BEACON.defaultBlockState());
				return beacon;
			});
			waitForLevels(context, singleplayer, beaconPos);
			openBeacon(context, singleplayer, beaconPos);
			context.waitFor(mc -> BeaconScreenExtras.tier(((BeaconScreen) mc.gui.screen()).getMenu()) == Tier.CELESTIUM, 100);
			context.waitTicks(3);

			// escolhe Velocidade (primario) e Voo (extra) na tela e passa o mouse sobre o Voo
			double[] flight = context.computeOnClient(mc -> {
				BeaconScreen screen = (BeaconScreen) mc.gui.screen();
				((BeaconScreenAccessor) screen).mlinnotas$setPrimary(MobEffects.SPEED);
				for (var child : screen.children()) {
					if (child instanceof BeaconScreenExtras.ExtraButton button) {
						if (!button.active) {
							throw new IllegalStateException("extra bloqueado em piramide de celestio: " + button.extra());
						}
						if (button.extra() == BeaconTiers.Extra.FLIGHT) {
							button.onPress(null);
							int scale = mc.getWindow().getGuiScale();
							return new double[] {(button.getX() + 11) * scale, (button.getY() + 11) * scale};
						}
					}
				}
				throw new IllegalStateException("botao de Voo nao encontrado");
			});
			context.getInput().setCursorPos(flight[0], flight[1]);
			context.waitTicks(10);
			context.takeScreenshot("mlinnotas-beacon-celestium");
			// linha do metal (acima ou abaixo da janela): o mouse em cima mostra os poderes nivel III
			double[] info = context.computeOnClient(mc -> {
				AbstractContainerScreenAccessor frame = (AbstractContainerScreenAccessor) mc.gui.screen();
				int scale = mc.getWindow().getGuiScale();
				int top = frame.mlinnotas$topPos();
				int y = BeaconScreenExtras.infoY(top, top + frame.mlinnotas$imageHeight(), mc.gui.screen().height);
				return new double[] {(frame.mlinnotas$leftPos() + frame.mlinnotas$imageWidth() / 2) * scale, (y + 4) * scale};
			});
			context.getInput().setCursorPos(info[0], info[1]);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-beacon-celestium-level-three");
			boolean selected = context.computeOnClient(mc -> ModEffects.BEACON_FLIGHT.equals(((BeaconScreenAccessor) mc.gui.screen()).mlinnotas$secondary()));
			if (!selected) {
				throw new IllegalStateException("Voo deveria estar selecionado");
			}

			// paga e confirma (como o botao de confirmar da tela)
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.containerMenu.getSlot(0).set(new ItemStack(Items.IRON_INGOT));
				player.containerMenu.broadcastChanges();
			});
			context.waitTicks(5);
			context.runOnClient(mc -> {
				mc.getConnection().send(new ServerboundSetBeaconPacket(Optional.of(MobEffects.SPEED), Optional.of(ModEffects.BEACON_FLIGHT)));
				mc.player.closeContainer();
			});
			context.waitForScreen(null);
			// o sinalizador aplica a cada 80 ticks
			for (int i = 0; i < 30; i++) {
				boolean flying = singleplayer.getServer().computeOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					return player.getAbilities().mayfly && player.hasEffect(ModEffects.BEACON_FLIGHT) && BeaconFlight.granted(player);
				});
				if (flying) {
					break;
				}
				if (i == 29) {
					throw new IllegalStateException("jogador de sobrevivencia deveria poder voar perto do sinalizador de celestio");
				}
				context.waitTicks(10);
			}
			// inventario lista os efeitos ativos (Voo e Velocidade)
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-beacon-flight-effects");
			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(null);

			// um bloco de ferro rebaixa a piramide inteira: extras bloqueados
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				level.setBlockAndUpdate(beaconPos.offset(4, -4, 4), Blocks.IRON_BLOCK.defaultBlockState());
			});
			openBeacon(context, singleplayer, beaconPos);
			context.waitFor(mc -> BeaconScreenExtras.tier(((BeaconScreen) mc.gui.screen()).getMenu()) == Tier.IRON, 100);
			context.waitTicks(5);
			boolean locked = context.computeOnClient(mc -> mc.gui.screen().children().stream()
					.filter(c -> c instanceof BeaconScreenExtras.ExtraButton).allMatch(c -> !((BeaconScreenExtras.ExtraButton) c).active));
			if (!locked) {
				throw new IllegalStateException("extras deveriam ficar bloqueados com ferro na piramide");
			}
			double[] center = context.computeOnClient(mc -> {
				AbstractContainerScreenAccessor frame = (AbstractContainerScreenAccessor) mc.gui.screen();
				int scale = mc.getWindow().getGuiScale();
				return new double[] {(frame.mlinnotas$leftPos() + 167 + 1) * scale, (frame.mlinnotas$topPos() + 83) * scale};
			});
			context.getInput().setCursorPos(center[0], center[1]);
			context.waitTicks(5);
			context.takeScreenshot("mlinnotas-beacon-iron");
			context.runOnClient(mc -> mc.player.closeContainer());
			context.waitForScreen(null);
		}
	}

	private static void waitForLevels(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos beaconPos) {
		for (int i = 0; i < 40; i++) {
			int levels = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				if (player.level().getBlockEntity(beaconPos) instanceof BeaconBlockEntity beacon) {
					var menu = (net.minecraft.world.inventory.BeaconMenu) beacon.createMenu(999, player.getInventory(), player);
					return menu == null ? 0 : menu.getLevels();
				}
				return 0;
			});
			if (levels == 4) {
				return;
			}
			context.waitTicks(10);
		}
		throw new IllegalStateException("sinalizador nao ativou");
	}

	private static void openBeacon(ClientGameTestContext context, TestSingleplayerContext singleplayer, BlockPos beaconPos) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			if (player.level().getBlockEntity(beaconPos) instanceof BeaconBlockEntity beacon) {
				player.openMenu(beacon);
			}
		});
		context.waitForScreen(BeaconScreen.class);
	}
}
