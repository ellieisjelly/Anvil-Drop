package io.github.haykam821.anvildrop.game.map;

import java.util.Iterator;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.level.chunk.ChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;
import xyz.nucleoid.map_templates.MapTemplate;
import xyz.nucleoid.plasmid.api.game.level.generator.TemplateChunkGenerator;

public class AnvilDropMap {
	private static final BlockState CLEAR_STATE = Blocks.AIR.defaultBlockState();
	private static final BlockState ANVIL_STATE = Blocks.ANVIL.defaultBlockState();
	private static final BlockState ALTERNATE_ANVIL_STATE = Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, Direction.EAST);

	private final MapTemplate template;
	private final AnvilDropConfig config;
	private final BlockBounds platformBounds;
	private final AABB box;
	private final BlockBounds clearBounds;
	private final BlockBounds dropBounds;

	public AnvilDropMap(MapTemplate template, AnvilDropConfig config, BlockBounds platformBounds, BlockBounds clearBounds, BlockBounds dropBounds) {
		this.template = template;
		this.config = config;

		this.platformBounds = platformBounds;
		this.box = this.platformBounds.asBox().inflate(-1, -0.5, -1);

		this.clearBounds = clearBounds;
		this.dropBounds = dropBounds;
	}

	public BlockBounds getPlatformBounds() {
		return this.platformBounds;
	}

	public AABB getBox() {
		return this.box;
	}

	public void clearAnvils(ServerLevel level) {
		Iterator<BlockPos> iterator = this.clearBounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();
			if (this.config.isBreaking() && !level.isEmptyBlock(pos)) {
				level.destroyBlock(pos.atY(0), false);
			}
			level.setBlockAndUpdate(pos, CLEAR_STATE);
		}
	}

	public void dropAnvils(ServerLevel level) {
		Iterator<BlockPos> iterator = this.dropBounds.iterator();
		while (iterator.hasNext()) {
			BlockPos pos = iterator.next();
			if (level.getRandom().nextDouble() < this.config.getChance()) {
				BlockState state = level.getRandom().nextBoolean() ? ANVIL_STATE : ALTERNATE_ANVIL_STATE;
				level.setBlock(pos, state, 0);
			}
		}
	}

	public ChunkGenerator createGenerator(MinecraftServer server) {
		return new TemplateChunkGenerator(server, this.template);
	}
}