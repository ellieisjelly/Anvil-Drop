package io.github.haykam821.anvildrop.game.phase;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import io.github.haykam821.anvildrop.game.map.AnvilDropMap;
import io.github.haykam821.anvildrop.game.map.AnvilDropMapBuilder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.plasmid.api.game.GameOpenContext;
import xyz.nucleoid.plasmid.api.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.api.game.GameResult;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.common.GameWaitingLobby;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class AnvilDropWaitingPhase {
	private final GameSpace gameSpace;
	private final ServerLevel level;
	private final AnvilDropMap map;
	private final AnvilDropConfig config;

	public AnvilDropWaitingPhase(GameSpace gameSpace, ServerLevel level, AnvilDropMap map, AnvilDropConfig config) {
		this.gameSpace = gameSpace;
		this.level = level;
		this.map = map;
		this.config = config;
	}

	public static GameOpenProcedure open(GameOpenContext<AnvilDropConfig> context) {
		AnvilDropMapBuilder mapBuilder = new AnvilDropMapBuilder(context.config());
		AnvilDropMap map = mapBuilder.create();

		RuntimeLevelConfig levelConfig = new RuntimeLevelConfig()
			.setGenerator(map.createGenerator(context.server()));

		return context.openWithLevel(levelConfig, (game, level) -> {
			AnvilDropWaitingPhase phase = new AnvilDropWaitingPhase(game.getGameSpace(), level, map, context.config());

			GameWaitingLobby.addTo(game, context.config().getPlayerConfig());
			AnvilDropActivePhase.setRules(game);

			// Listeners
			game.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			game.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			game.listen(GamePlayerEvents.OFFER, JoinOffer::accept);
			game.listen(GameActivityEvents.REQUEST_START, phase::requestStart);
		});
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.level, AnvilDropActivePhase.getSpawnPos(this.map)).thenRunForEach(player -> {
			player.setGameMode(GameType.ADVENTURE);
		});
	}

	private GameResult requestStart() {
		AnvilDropActivePhase.open(this.gameSpace, this.level, this.map, this.config);
		return GameResult.ok();
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		AnvilDropActivePhase.spawn(this.level, this.map, player);
		return EventResult.DENY;
	}
}