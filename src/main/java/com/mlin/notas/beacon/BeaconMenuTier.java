package com.mlin.notas.beacon;

/**
 * Implementado pelo {@code BeaconMenu} vanilla (mixin): codigo do metal da piramide sincronizado como data slot extra
 * ({@link BeaconTiers#encode}), para a tela mostrar o nivel e liberar os poderes extras.
 */
public interface BeaconMenuTier {
	int mlinnotas$tierCode();
}
