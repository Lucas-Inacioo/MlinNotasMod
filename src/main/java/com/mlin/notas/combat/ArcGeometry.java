package com.mlin.notas.combat;

/**
 * Geometria dos golpes em arco, da onda de choque e da estocada, no plano horizontal (x, z) com tolerancia de
 * altura. Usa a convencao de angulo do Minecraft: yaw 0 olha para +z, 90 para -x. Logica pura.
 */
public final class ArcGeometry {
	/** Diferenca maxima de altura (pes do alvo contra pes do atacante) para entrar no arco. */
	public static final double MAX_HEIGHT_DIFFERENCE = 2.0;

	private ArcGeometry() {
	}

	/** Direcao horizontal (x, z) do olhar para um yaw em graus. */
	public static double[] look(float yawDegrees) {
		double radians = Math.toRadians(yawDegrees);
		return new double[] {-Math.sin(radians), Math.cos(radians)};
	}

	/**
	 * O alvo (centro {@code tx, tz}, meia largura {@code halfWidth}) esta no cone a frente do atacante: ate
	 * {@code radius} blocos da borda dele e ate metade de {@code arcDegrees} do olhar.
	 */
	public static boolean inArc(double ox, double oy, double oz, float yawDegrees, double tx, double ty, double tz, double halfWidth,
			double radius, double arcDegrees) {
		if (Math.abs(ty - oy) > MAX_HEIGHT_DIFFERENCE) {
			return false;
		}
		double dx = tx - ox;
		double dz = tz - oz;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance - halfWidth > radius) {
			return false;
		}
		if (distance < 1.0E-6) {
			return true;
		}
		double[] look = look(yawDegrees);
		double cos = (dx * look[0] + dz * look[1]) / distance;
		// a borda do alvo conta: alarga o angulo pelo tamanho aparente
		double widen = Math.toDegrees(Math.atan2(halfWidth, distance));
		double angle = Math.toDegrees(Math.acos(Math.clamp(cos, -1.0, 1.0)));
		return angle <= arcDegrees / 2.0 + widen;
	}

	/** O alvo esta num raio (horizontal, ate a borda) de um ponto: onda de choque. */
	public static boolean inRadius(double cx, double cy, double cz, double tx, double ty, double tz, double halfWidth, double radius) {
		if (Math.abs(ty - cy) > MAX_HEIGHT_DIFFERENCE) {
			return false;
		}
		double dx = tx - cx;
		double dz = tz - cz;
		return Math.sqrt(dx * dx + dz * dz) - halfWidth <= radius;
	}

	/**
	 * Estocada: o alvo esta atras do primeiro, na linha do olhar, ate {@code depth} blocos depois dele e a ate
	 * {@code width} blocos da linha.
	 */
	public static boolean behindInLine(double ox, double oz, float yawDegrees, double firstX, double firstZ, double tx, double tz,
			double depth, double width) {
		double[] look = look(yawDegrees);
		double along = (tx - ox) * look[0] + (tz - oz) * look[1];
		double firstAlong = (firstX - ox) * look[0] + (firstZ - oz) * look[1];
		double side = Math.abs((tx - ox) * look[1] - (tz - oz) * look[0]);
		return along > firstAlong && along - firstAlong <= depth && side <= width;
	}

	/**
	 * O atacante esta nas costas do alvo: o vetor alvo -> atacante faz mais de 110 graus com o olhar do alvo
	 * (cone de 140 graus atras).
	 */
	public static boolean behind(float targetYawDegrees, double targetX, double targetZ, double attackerX, double attackerZ) {
		double dx = attackerX - targetX;
		double dz = attackerZ - targetZ;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 1.0E-6) {
			return false;
		}
		double[] look = look(targetYawDegrees);
		double cos = (dx * look[0] + dz * look[1]) / distance;
		return cos < Math.cos(Math.toRadians(110.0));
	}
}
