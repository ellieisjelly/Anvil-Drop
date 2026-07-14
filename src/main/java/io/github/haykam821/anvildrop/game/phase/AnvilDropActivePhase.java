package io.github.haykam821.anvildrop.game.phase;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.haykam821.anvildrop.game.AnvilDropConfig;
import io.github.haykam821.anvildrop.game.map.AnvilDropMap;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import xyz.nucleoid.plasmid.api.game.GameActivity;
import xyz.nucleoid.plasmid.api.game.GameCloseReason;
import xyz.nucleoid.plasmid.api.game.GameSpace;
import xyz.nucleoid.plasmid.api.game.event.GameActivityEvents;
import xyz.nucleoid.plasmid.api.game.event.GamePlayerEvents;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptor;
import xyz.nucleoid.plasmid.api.game.player.JoinAcceptorResult;
import xyz.nucleoid.plasmid.api.game.player.JoinOffer;
import xyz.nucleoid.plasmid.api.game.rule.GameRuleType;
import xyz.nucleoid.plasmid.api.util.PlayerRef;
import xyz.nucleoid.stimuli.event.EventResult;
import xyz.nucleoid.stimuli.event.player.PlayerDamageEvent;
import xyz.nucleoid.stimuli.event.player.PlayerDeathEvent;

public class AnvilDropActivePhase {
	private final ServerLevel level;
	private final GameSpace gameSpace;
	private final AnvilDropMap map;
	private final AnvilDropConfig config;
	private final Set<PlayerRef> players;
	private boolean singleplayer;
	private int ticksUntilSwitch;
	private int ticksUntilClose = -1;
	private int rounds = 0;
	private boolean anvilsDropping = false;

	public AnvilDropActivePhase(GameSpace gameSpace, ServerLevel level, AnvilDropMap map, AnvilDropConfig config, Set<PlayerRef> players) {
		this.level = level;
		this.gameSpace = gameSpace;
		this.map = map;
		this.config = config;
		this.players = players;
		this.ticksUntilSwitch = this.config.getDelay();
	}

	public static void setRules(GameActivity activity) {
		activity.deny(GameRuleType.BLOCK_DROPS);
		activity.deny(GameRuleType.CRAFTING);
		activity.deny(GameRuleType.FALL_DAMAGE);
		activity.deny(GameRuleType.HUNGER);
		activity.deny(GameRuleType.INTERACTION);
		activity.deny(GameRuleType.PORTALS);
		activity.deny(GameRuleType.PVP);
	}

	public static void open(GameSpace gameSpace, ServerLevel level, AnvilDropMap map, AnvilDropConfig config) {
		Set<PlayerRef> players = gameSpace.getPlayers().participants().stream().map(PlayerRef::of).collect(Collectors.toSet());
		AnvilDropActivePhase phase = new AnvilDropActivePhase(gameSpace, level, map, config, players);

		gameSpace.setActivity(activity -> {
			AnvilDropActivePhase.setRules(activity);

			// Listeners
			activity.listen(GameActivityEvents.ENABLE, phase::enable);
			activity.listen(GameActivityEvents.TICK, phase::tick);
			activity.listen(GamePlayerEvents.ACCEPT, phase::onAcceptPlayers);
			activity.listen(GamePlayerEvents.OFFER, JoinOffer::acceptSpectators);
			activity.listen(GamePlayerEvents.REMOVE, phase::removePlayer);
			activity.listen(PlayerDeathEvent.EVENT, phase::onPlayerDeath);
			activity.listen(PlayerDamageEvent.EVENT, phase::onPlayerDamage);
		});
	}

	private void enable() {
		this.singleplayer = this.players.size() == 1;

 		for (PlayerRef playerRef : this.players) {
			playerRef.ifOnline(this.level, player -> {
				this.updateRoundsExperienceLevel(player);
				player.setGameMode(GameType.ADVENTURE);
				AnvilDropActivePhase.spawn(this.level, this.map, player);
			});
		}

		for (ServerPlayer player : this.gameSpace.getPlayers().spectators()) {
			this.updateRoundsExperienceLevel(player);
			this.setSpectator(player);
			AnvilDropActivePhase.spawn(this.level, this.map, player);
		}
	}

	private void updateRoundsExperienceLevel(ServerPlayer player) {
		player.setExperienceLevels(this.rounds + 1);
	}

	private void setRounds(int rounds) {
		this.rounds = rounds;
		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			this.updateRoundsExperienceLevel(player);
		}
	}

	private void tick() {
		// Decrease ticks until game end to zero
		if (this.isGameEnding()) {
			if (this.ticksUntilClose == 0) {
				this.gameSpace.close(GameCloseReason.FINISHED);
			}

			this.ticksUntilClose -= 1;
			return;
		}

		this.ticksUntilSwitch -= 1;
		if (this.ticksUntilSwitch < 0) {
			this.anvilsDropping = !this.anvilsDropping;
			this.ticksUntilSwitch = this.config.getDelay();

			if (this.anvilsDropping) {
				this.map.dropAnvils(this.level);
			} else {
				this.map.clearAnvils(this.level);
				this.setRounds(this.rounds + 1);
			}
		}

		// Eliminate players that are outside of the arena
		Iterator<PlayerRef> playerIterator = this.players.iterator();
		while (playerIterator.hasNext()) {
			PlayerRef playerRef = playerIterator.next();
			playerRef.ifOnline(this.level, player -> {
				if (!this.map.getBox().contains(player.position())) {
					this.eliminate(player, player.getY() < this.map.getBox().minY ? ".hole_in_floor" : ".out_of_bounds", false);
					playerIterator.remove();
				}
			});
		}

		// Attempt to determine a winner
		if (this.players.size() < 2) {
			if (this.players.size() == 1 && this.singleplayer) return;
			
			Component endingMessage = this.getEndingMessage();
			for (ServerPlayer player : this.gameSpace.getPlayers()) {
				player.sendSystemMessage(endingMessage, false);
			}
			this.gameSpace.getPlayers().playSound(SoundEvents.PLAYER_LEVELUP, SoundSource.UI, 1, 1);
			this.ticksUntilClose = this.config.getTicksUntilClose().sample(this.level.getRandom());
		}
	}

	private Component getEndingMessage() {
		if (this.players.size() == 1) {
			PlayerRef winnerRef = this.players.iterator().next();
			if (winnerRef.isOnline(this.level)) {
				Player winner = winnerRef.getEntity(this.level);
				return Component.translatable("text.anvildrop.win", winner.getDisplayName(), this.rounds).withStyle(ChatFormatting.GOLD);
			}
		}
		return Component.translatable("text.anvildrop.no_winners", this.rounds).withStyle(ChatFormatting.GOLD);
	}

	private boolean isGameEnding() {
		return this.ticksUntilClose >= 0;
	}

	private void setSpectator(ServerPlayer player) {
		player.setGameMode(GameType.SPECTATOR);
	}

	private JoinAcceptorResult onAcceptPlayers(JoinAcceptor acceptor) {
		return acceptor.teleport(this.level, AnvilDropActivePhase.getSpawnPos(this.map)).thenRunForEach(player -> {
			this.updateRoundsExperienceLevel(player);
			this.setSpectator(player);
		});
	}

	private void removePlayer(ServerPlayer player) {
		this.eliminate(player, true);
	}

	private void eliminate(ServerPlayer eliminatedPlayer, String suffix, boolean remove) {
		if (this.isGameEnding()) return;

		PlayerRef eliminatedRef = PlayerRef.of(eliminatedPlayer);
		if (!this.players.contains(eliminatedRef)) return;

		Component message = Component.translatable("text.anvildrop.eliminated" + suffix, eliminatedPlayer.getDisplayName()).withStyle(ChatFormatting.RED);
		for (ServerPlayer player : this.gameSpace.getPlayers()) {
			player.sendSystemMessage(message, false);
		}

		if (remove) {
			this.players.remove(eliminatedRef);
		}
		this.setSpectator(eliminatedPlayer);
	}

	private void eliminate(ServerPlayer eliminatedPlayer, boolean remove) {
		this.eliminate(eliminatedPlayer, "", remove);
	}

	private EventResult onPlayerDeath(ServerPlayer player, DamageSource source) {
		if (this.players.contains(PlayerRef.of(player))) {
			this.eliminate(player, true);
		} else {
			AnvilDropActivePhase.spawn(this.level, this.map, player);
		}
		return EventResult.DENY;
	}

	private static boolean isEliminatingDamageSource(DamageSource source) {
		return source.is(DamageTypeTags.DAMAGES_HELMET);
	}

	private EventResult onPlayerDamage(ServerPlayer player, DamageSource source, float amount) {
		if (AnvilDropActivePhase.isEliminatingDamageSource(source) && this.players.contains(PlayerRef.of(player))) {
			this.eliminate(player, ".falling_anvil", true);
		}
		return EventResult.ALLOW;
	}

	public static void spawn(ServerLevel world, AnvilDropMap map, ServerPlayer player) {
		Vec3 spawnPos = AnvilDropActivePhase.getSpawnPos(map);
		player.teleportTo(world, spawnPos.x(), spawnPos.y(), spawnPos.z(), Set.of(), 0, 0, true);
	}

	protected static Vec3 getSpawnPos(AnvilDropMap map) {
		Vec3 center = map.getPlatformBounds().center();
		return new Vec3(center.x(), map.getPlatformBounds().min().getY() + 1, center.z());
	}
}