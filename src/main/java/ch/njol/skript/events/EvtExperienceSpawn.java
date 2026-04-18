package ch.njol.skript.events;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.SkriptEventHandler;
import ch.njol.skript.events.bukkit.ExperienceSpawnEvent;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import org.bukkit.Bukkit;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.EventExecutor;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class EvtExperienceSpawn extends SkriptEvent {

	static {
		register(Skript.instance().syntaxRegistry());
	}

	public static void register(SyntaxRegistry registry) {
		registry.register(BukkitSyntaxInfos.Event.KEY,
			BukkitSyntaxInfos.Event.builder(EvtExperienceSpawn.class, "Experience Spawn")
				.addPatterns(
					BukkitSyntaxInfos.fixPattern("[e]xp[erience] [orb] spawn"),
					BukkitSyntaxInfos.fixPattern("spawn of [a[n]] [e]xp[erience] [orb]")
				)
				.addDescription("Called whenever experience is about to spawn.")
				.addExamples(
					"on xp spawn:",
					"\tworld is \"minigame_world\"",
					"\tcancel event"
				)
				.addSince("2.0")
				.addEvent(ExperienceSpawnEvent.class)
				.build()
		);
	}

	private static final List<Trigger> TRIGGERS = Collections.synchronizedList(new ArrayList<>());

	private static final AtomicBoolean REGISTERED_EXECUTORS = new AtomicBoolean();

	private static final EventExecutor EXECUTOR = (listener, event) -> {
		ExperienceSpawnEvent experienceEvent;
		if (event instanceof BlockExpEvent blockExpEvent) {
			experienceEvent = new ExperienceSpawnEvent(
				blockExpEvent.getExpToDrop(),
				blockExpEvent.getBlock().getLocation().add(0.5, 0.5, 0.5)
			);
		} else if (event instanceof EntityDeathEvent entityDeathEvent) {
			experienceEvent = new ExperienceSpawnEvent(
				entityDeathEvent.getDroppedExp(),
				entityDeathEvent.getEntity().getLocation()
			);
		} else if (event instanceof ExpBottleEvent expBottleEvent) {
			experienceEvent = new ExperienceSpawnEvent(
				expBottleEvent.getExperience(),
				expBottleEvent.getEntity().getLocation()
			);
		} else if (event instanceof PlayerFishEvent playerFishEvent) {
			if (playerFishEvent.getState() != PlayerFishEvent.State.CAUGHT_FISH)
				return;
			experienceEvent = new ExperienceSpawnEvent(
				playerFishEvent.getExpToDrop(),
				playerFishEvent.getPlayer().getLocation()
			);
		} else if (event instanceof EntitySpawnEvent entitySpawnEvent) {
			if (!(entitySpawnEvent.getEntity() instanceof ExperienceOrb orb))
				return;
			experienceEvent = new ExperienceSpawnEvent(orb.getExperience(), orb.getLocation());
		} else {
			assert false;
			return;
		}

		SkriptEventHandler.logEventStart(event);
		synchronized (TRIGGERS) {
			for (Trigger trigger : TRIGGERS) {
				SkriptEventHandler.logTriggerStart(trigger);
				trigger.execute(experienceEvent);
				SkriptEventHandler.logTriggerEnd(trigger);
			}
		}
		SkriptEventHandler.logEventEnd();

		if (experienceEvent.isCancelled())
			experienceEvent.setSpawnedXP(0);

		if (event instanceof BlockExpEvent blockExpEvent) {
			blockExpEvent.setExpToDrop(experienceEvent.getSpawnedXP());
		} else if (event instanceof EntityDeathEvent entityDeathEvent) {
			entityDeathEvent.setDroppedExp(experienceEvent.getSpawnedXP());
		} else if (event instanceof ExpBottleEvent expBottleEvent) {
			expBottleEvent.setExperience(experienceEvent.getSpawnedXP());
		} else if (event instanceof PlayerFishEvent playerFishEvent) {
			playerFishEvent.setExpToDrop(experienceEvent.getSpawnedXP());
		} else if (event instanceof EntitySpawnEvent entitySpawnEvent) {
			if (experienceEvent.isCancelled()) {
				entitySpawnEvent.setCancelled(true);
			} else if (entitySpawnEvent.getEntity() instanceof ExperienceOrb orb) {
				orb.setExperience(experienceEvent.getSpawnedXP());
			}
		}
	};

	@Override
	public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult) {
		return true;
	}

	@Override
	public boolean postLoad() {
		TRIGGERS.add(trigger);
		if (REGISTERED_EXECUTORS.compareAndSet(false, true)) {
			EventPriority priority = SkriptConfig.defaultEventPriority.value();
			//noinspection unchecked
			for (Class<? extends Event> clazz : new Class[]{BlockExpEvent.class, EntityDeathEvent.class, ExpBottleEvent.class, PlayerFishEvent.class, EntitySpawnEvent.class})
				Bukkit.getPluginManager().registerEvent(clazz, new Listener(){}, priority, EXECUTOR, Skript.getInstance(), true);
		}
		return true;
	}

	@Override
	public void unload() {
		TRIGGERS.remove(trigger);
	}

	@Override
	public boolean check(Event event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEventPrioritySupported() {
		return false;
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "experience spawn";
	}

}