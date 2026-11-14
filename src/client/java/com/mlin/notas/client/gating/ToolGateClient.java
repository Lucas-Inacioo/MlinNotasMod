package com.mlin.notas.client.gating;

import com.mlin.notas.client.skills.SkillClient;
import com.mlin.notas.gating.ToolGate;
import com.mlin.notas.gating.ToolLocks;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Cliente da trava de ferramentas: le o nivel de Mineracao do progresso sincronizado (para o cliente nem comecar a
 * quebrar/atacar) e poe a linha "Requer Mineracao: X" na dica de toda ferramenta/arma com nivel (vermelha se travada,
 * cinza se liberada; madeira nao mostra).
 */
public final class ToolGateClient {
	private ToolGateClient() {
	}

	public static void init() {
		ToolLocks.setClientMiningLevel(() -> SkillClient.progress().level(SkillBranch.MINING));
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			Tier tier = ToolLocks.tier(stack);
			if (tier == null || tier == Tier.WOOD) {
				return;
			}
			boolean unlocked = ToolGate.allowed(tier, SkillClient.progress().level(SkillBranch.MINING));
			lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.mlinnotas.tool_tier", SkillServer.tierName(tier))
					.withStyle(unlocked ? ChatFormatting.GRAY : ChatFormatting.RED));
		});
	}
}
