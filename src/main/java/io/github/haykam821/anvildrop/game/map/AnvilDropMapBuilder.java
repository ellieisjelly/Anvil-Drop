package io.github.haykam821.anvildrop.game.map;

import java.util.Iterator;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;

public class AnvilDropMapBuilder {
	private static final BlockState FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
	private static final BlockState FLOOR_OUTLINE = Blocks.NETHERITE_BLOCK.defaultBlockState();
	private static final BlockState WALL = Blocks.END_STONE_BRICK_WALL.defaultBlockState();
	private static final BlockState WALL_TOP = Blocks.BIRCH_SLAB.defaultBlockState();
	private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

	private final AnvilDropConfig config;

	public AnvilDropMapBuilder(AnvilDropConfig config) {
		this.config = config;
	}

	public AnvilDropMap create() {
		MapTemplate template = MapTemplate.createEmpty();
		AnvilDropMapConfig mapConfig = this.config.getMapConfig();

		BlockBounds bounds = BlockBounds.of(BlockPos.ZERO, new BlockPos(mapConfig.getX() + 1, this.config.getStackHeight() + 3, mapConfig.getZ() + 1));
		this.build(bounds, template, mapConfig);

		BlockBounds clearBounds = createInnerBounds(mapConfig, this.config.getStackHeight() + 1);
		BlockBounds dropBounds = createInnerBounds(mapConfig, this.config.getDropHeight() + 1);

		return new AnvilDropMap(template, config, bounds, clearBounds, dropBounds);
	}

	private BlockBounds createInnerBounds(AnvilDropMapConfig mapConfig, int y) {
		return BlockBounds.of(new BlockPos(1, y, 1), new BlockPos(mapConfig.getX(), y, mapConfig.getZ()));
	}

	private BlockState getBlockState(BlockPos pos, BlockBounds bounds, AnvilDropMapConfig mapConfig) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();

		BlockPos min = bounds.min();
		BlockPos max = bounds.max();

		boolean outline = x == min.getX() || x == max.getX() || z == min.getZ() || z == max.getZ();

		if (outline) {
			if (y == 0) {
				return FLOOR_OUTLINE;
			} else if (y == max.getY() - 1) {
				return WALL_TOP;
			} else if (y == max.getY()) {
				return BARRIER;
			} else {
				return WALL;
			}
		} else if (y == 0) {
			return FLOOR;
		}

		return null;
	}

	public void build(BlockBounds bounds, MapTemplate template, AnvilDropMapConfig mapConfig) {
		Iterator<BlockPos> iterator = bounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();

			BlockState state = this.getBlockState(pos, bounds, mapConfig);
			if (state != null) {
				template.setBlockState(pos, state);
			}
		}
	}
}