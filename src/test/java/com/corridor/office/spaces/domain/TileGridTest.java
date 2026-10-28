package com.corridor.office.spaces.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.corridor.office.spaces.domain.TileGrid.Tile;
import org.junit.jupiter.api.Test;

class TileGridTest {

    // 5 x 4 with outer walls and a short inner wall at (1,2) and (2,2).
    private static final String ROOMS = "CCCCC" + "C...C" + "C##.C" + "CCCCC";

    @Test
    void shouldParseAValidGrid() {
        var grid = TileGrid.parse(5, 4, ROOMS, new Tile(1, 1));

        assertThat(grid.width()).isEqualTo(5);
        assertThat(grid.height()).isEqualTo(4);
        assertThat(grid.arrival()).isEqualTo(new Tile(1, 1));
    }

    @Test
    void shouldRejectAGridWithTheWrongSize() {
        assertThatThrownBy(() -> TileGrid.parse(5, 5, ROOMS, new Tile(1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("20 tiles, expected 25");
    }

    @Test
    void shouldRejectAnUnknownTile() {
        assertThatThrownBy(() -> TileGrid.parse(5, 4, ROOMS.replace('#', 'X'), new Tile(1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'X'");
    }

    @Test
    void shouldRejectAnArrivalOnAWall() {
        assertThatThrownBy(() -> TileGrid.parse(5, 4, ROOMS, new Tile(1, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arrival");
    }

    @Test
    void shouldOnlyWalkOnFloorTiles() {
        var grid = TileGrid.parse(5, 4, ROOMS, new Tile(1, 1));

        assertThat(grid.isWalkable(new Tile(3, 2))).isTrue();
        assertThat(grid.isWalkable(new Tile(1, 2))).isFalse();
        assertThat(grid.isWalkable(new Tile(0, 0))).isFalse();
        assertThat(grid.isWalkable(new Tile(-1, 1))).isFalse();
        assertThat(grid.isWalkable(new Tile(9, 1))).isFalse();
    }

    @Test
    void shouldFindAPathAroundWalls() {
        var grid = TileGrid.parse(5, 4, ROOMS, new Tile(1, 1));

        assertThat(grid.path(new Tile(1, 1), new Tile(3, 2)))
                .containsExactly(new Tile(2, 1), new Tile(3, 1), new Tile(3, 2));
    }

    @Test
    void shouldFindNoPathToAWall() {
        var grid = TileGrid.parse(5, 4, ROOMS, new Tile(1, 1));

        assertThat(grid.path(new Tile(1, 1), new Tile(2, 2))).isEmpty();
    }
}
