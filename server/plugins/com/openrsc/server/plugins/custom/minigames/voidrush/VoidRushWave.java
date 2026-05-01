package com.openrsc.server.plugins.custom.minigames.voidrush;

import com.openrsc.server.model.Point;

public final class VoidRushWave {
	public enum Direction {
		NORTH_TO_SOUTH,
		SOUTH_TO_NORTH,
		EAST_TO_WEST,
		WEST_TO_EAST
	}

	private final Direction direction;
	private final int gapStart;
	private final int gapSize;
	private final int delayTicks;
	private final int strideTiles;
	private int previousLine;
	private int line;

	public VoidRushWave(Direction direction, int gapStart, int gapSize, int delayTicks, int strideTiles) {
		this.direction = direction;
		this.gapStart = gapStart;
		this.gapSize = gapSize;
		this.delayTicks = delayTicks;
		this.strideTiles = Math.max(1, strideTiles);
		this.line = firstLine(direction);
		this.previousLine = line;
	}

	public Direction getDirection() {
		return direction;
	}

	public int getGapStart() {
		return gapStart;
	}

	public int getGapEnd() {
		return gapStart + gapSize - 1;
	}

	public int getGapSize() {
		return gapSize;
	}

	public int getDelayTicks() {
		return delayTicks;
	}

	public int getStrideTiles() {
		return strideTiles;
	}

	public int getLine() {
		return line;
	}

	public int getPreviousLine() {
		return previousLine;
	}

	public boolean isHorizontal() {
		return direction == Direction.NORTH_TO_SOUTH || direction == Direction.SOUTH_TO_NORTH;
	}

	public void advance() {
		previousLine = line;
		line += step(direction) * strideTiles;
	}

	public boolean isPastArena() {
		return getStep() > 0 ? getSweptLineMin() > getLineMax() : getSweptLineMax() < getLineMin();
	}

	public boolean hasActiveLines() {
		return getSweptLineMax() >= getLineMin() && getSweptLineMin() <= getLineMax();
	}

	public boolean isDangerous(Point point) {
		if (point == null || !hasActiveLines()) {
			return false;
		}
		int lineCoordinate = getLineCoordinate(point);
		return lineCoordinate >= getSweptLineMin()
			&& lineCoordinate <= getSweptLineMax()
			&& !isInGap(getPerpendicularCoordinate(point));
	}

	public boolean isInGap(int perpendicular) {
		return perpendicular >= gapStart && perpendicular <= getGapEnd();
	}

	public int getPerpendicularMin() {
		return isHorizontal() ? VoidRushConfig.ARENA_MIN_X + 1 : VoidRushConfig.ARENA_MIN_Y + 1;
	}

	public int getPerpendicularMax() {
		return isHorizontal() ? VoidRushConfig.ARENA_MAX_X - 1 : VoidRushConfig.ARENA_MAX_Y - 1;
	}

	public int getLineMin() {
		return isHorizontal() ? VoidRushConfig.ARENA_MIN_Y + 1 : VoidRushConfig.ARENA_MIN_X + 1;
	}

	public int getLineMax() {
		return isHorizontal() ? VoidRushConfig.ARENA_MAX_Y - 1 : VoidRushConfig.ARENA_MAX_X - 1;
	}

	public int getSweptLineMin() {
		int startLine = previousLine == line ? line : previousLine + getStep();
		return Math.min(startLine, line);
	}

	public int getSweptLineMax() {
		int startLine = previousLine == line ? line : previousLine + getStep();
		return Math.max(startLine, line);
	}

	public int getLineCoordinate(Point point) {
		return isHorizontal() ? point.getY() : point.getX();
	}

	public int getPerpendicularCoordinate(Point point) {
		return isHorizontal() ? point.getX() : point.getY();
	}

	public int distanceToGap(int perpendicular) {
		if (isInGap(perpendicular)) {
			return 0;
		}
		if (perpendicular < gapStart) {
			return gapStart - perpendicular;
		}
		return perpendicular - getGapEnd();
	}

	public Point pointAt(int perpendicular) {
		return pointAt(line, perpendicular);
	}

	public Point pointAt(int line, int perpendicular) {
		if (isHorizontal()) {
			return Point.location(perpendicular, line);
		}
		return Point.location(line, perpendicular);
	}

	private int getStep() {
		return step(direction);
	}

	private static int firstLine(Direction direction) {
		switch (direction) {
			case NORTH_TO_SOUTH:
				return VoidRushConfig.ARENA_MIN_Y + 1;
			case SOUTH_TO_NORTH:
				return VoidRushConfig.ARENA_MAX_Y - 1;
			case EAST_TO_WEST:
				return VoidRushConfig.ARENA_MAX_X - 1;
			case WEST_TO_EAST:
			default:
				return VoidRushConfig.ARENA_MIN_X + 1;
		}
	}

	private static int step(Direction direction) {
		switch (direction) {
			case SOUTH_TO_NORTH:
			case EAST_TO_WEST:
				return -1;
			case NORTH_TO_SOUTH:
			case WEST_TO_EAST:
			default:
				return 1;
		}
	}
}
