package com.github.antonsakhanovych.gatedwarp.warp;

import java.util.UUID;

/**
 * An immutable warp point. The dimension is kept as its identifier string (for example
 * {@code minecraft:overworld}) and only resolved to a world when a teleport happens.
 */
public record Warp(
	String name,
	String dimension,
	double x,
	double y,
	double z,
	float yaw,
	float pitch,
	String description,
	UUID creator,
	long createdAt
) {
	public Warp withName(String newName) {
		return new Warp(newName, dimension, x, y, z, yaw, pitch, description, creator, createdAt);
	}
}
