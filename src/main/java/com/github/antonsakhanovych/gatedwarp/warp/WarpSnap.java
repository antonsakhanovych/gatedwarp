package com.github.antonsakhanovych.gatedwarp.warp;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Picks the warp a map click should snap to. */
public final class WarpSnap {
	private WarpSnap() {
	}

	/**
	 * Finds the nearest warp in {@code dimension} whose horizontal distance to ({@code x}, {@code z})
	 * is at most {@code radius}. Y is ignored on purpose, map clicks carry no meaningful height.
	 * Ties are broken by name so the result is deterministic.
	 */
	public static Optional<Warp> nearest(Collection<Warp> warps, String dimension, double x, double z, double radius) {
		double radiusSq = radius * radius;
		return warps.stream()
			.filter(warp -> warp.dimension().equals(dimension))
			.filter(warp -> horizontalDistanceSq(warp, x, z) <= radiusSq)
			.min(Comparator
				.comparingDouble((Warp warp) -> horizontalDistanceSq(warp, x, z))
				.thenComparing(Warp::name));
	}

	private static double horizontalDistanceSq(Warp warp, double x, double z) {
		double dx = warp.x() - x;
		double dz = warp.z() - z;
		return dx * dx + dz * dz;
	}
}
