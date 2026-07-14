package io.github.haykam821.anvildrop;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import io.github.haykam821.anvildrop.game.phase.AnvilDropWaitingPhase;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import xyz.nucleoid.plasmid.api.game.GameType;
import xyz.nucleoid.plasmid.api.game.GameTypes;

public class Main implements ModInitializer {
	private static final String MOD_ID = "anvildrop";

	private static final Identifier ANVIL_DROP_ID = Main.identifier("anvil_drop");
	public static final GameType<AnvilDropConfig> ANVIL_DROP_TYPE = GameTypes.register(ANVIL_DROP_ID, AnvilDropConfig.CODEC, AnvilDropWaitingPhase::open);

	@Override
	public void onInitialize() {
		return;
	}

	public static Identifier identifier(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}