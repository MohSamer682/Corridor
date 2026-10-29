package com.corridor.office.spaces;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A floor as a grid of one-character tiles, row by row: {@code C} outer wall, {@code #} wall, {@code G} glass,
 * {@code F} furniture, {@code .} floor. Only floor tiles can be walked on.
 */
public final class TileGrid {

    private static final String KNOWN_TILES = "C#GF.";
    private static final char FLOOR = '.';
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public record Tile(int x, int y) {}

    private final int width;
    private final int height;
    private final String tiles;
    private final Tile arrival;

    private TileGrid(int width, int height, String tiles, Tile arrival) {
        this.width = width;
        this.height = height;
        this.tiles = tiles;
        this.arrival = arrival;
    }

    public static TileGrid parse(int width, int height, String tiles, Tile arrival) {
        if (tiles.length() != width * height) {
            throw new IllegalArgumentException(
                    "Grid has %d tiles, expected %d".formatted(tiles.length(), width * height));
        }
        tiles.chars().filter(tile -> KNOWN_TILES.indexOf(tile) < 0).findFirst().ifPresent(tile -> {
            throw new IllegalArgumentException("Unknown tile '%c'".formatted((char) tile));
        });
        var grid = new TileGrid(width, height, tiles, arrival);
        if (!grid.isWalkable(arrival)) {
            throw new IllegalArgumentException("The arrival tile %s is not walkable".formatted(arrival));
        }
        return grid;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Tile arrival() {
        return arrival;
    }

    public boolean isWalkable(Tile tile) {
        return contains(tile) && tiles.charAt(index(tile)) == FLOOR;
    }

    /** Shortest walk from {@code from} to {@code to}, excluding {@code from}; empty when {@code to} can't be reached. */
    public List<Tile> path(Tile from, Tile to) {
        return path(from, to, Set.of());
    }

    /** Like {@link #path(Tile, Tile)}, but walks around {@code blocked} tiles other than the target. */
    public List<Tile> path(Tile from, Tile to, Set<Tile> blocked) {
        if (!isWalkable(to)) {
            return List.of();
        }
        var cameFrom = new int[width * height];
        Arrays.fill(cameFrom, -1);
        cameFrom[index(from)] = index(from);
        var queue = new ArrayDeque<Tile>(List.of(from));
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (current.equals(to)) {
                return trace(cameFrom, from, to);
            }
            for (var step : STEPS) {
                var next = new Tile(current.x() + step[0], current.y() + step[1]);
                var open = next.equals(to) || !blocked.contains(next);
                if (isWalkable(next) && open && cameFrom[index(next)] < 0) {
                    cameFrom[index(next)] = index(current);
                    queue.add(next);
                }
            }
        }
        return List.of();
    }

    /** The walkable tile nearest to {@code start} that isn't taken, or empty if the floor is full. */
    public Optional<Tile> nearestFree(Tile start, Set<Tile> taken) {
        var seen = new boolean[width * height];
        var queue = new ArrayDeque<Tile>();
        if (contains(start)) {
            seen[index(start)] = true;
            queue.add(start);
        }
        while (!queue.isEmpty()) {
            var current = queue.poll();
            if (isWalkable(current) && !taken.contains(current)) {
                return Optional.of(current);
            }
            for (var step : STEPS) {
                var next = new Tile(current.x() + step[0], current.y() + step[1]);
                if (contains(next) && !seen[index(next)] && tiles.charAt(index(next)) != 'C') {
                    seen[index(next)] = true;
                    queue.add(next);
                }
            }
        }
        return Optional.empty();
    }

    private List<Tile> trace(int[] cameFrom, Tile from, Tile to) {
        var path = new ArrayList<Tile>();
        for (var at = index(to); at != index(from); at = cameFrom[at]) {
            path.add(new Tile(at % width, at / width));
        }
        Collections.reverse(path);
        return path;
    }

    private boolean contains(Tile tile) {
        return tile.x() >= 0 && tile.y() >= 0 && tile.x() < width && tile.y() < height;
    }

    private int index(Tile tile) {
        return tile.y() * width + tile.x();
    }
}
