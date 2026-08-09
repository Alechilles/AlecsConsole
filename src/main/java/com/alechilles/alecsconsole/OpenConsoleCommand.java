package com.alechilles.alecsconsole;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Opens Alec's Console for an authorized player. */
public final class OpenConsoleCommand extends AbstractPlayerCommand {
    public static final String PERMISSION = "alechilles.alecsconsole.command.console";

    private final ConsolePageManager manager;

    public OpenConsoleCommand(ConsolePageManager manager) {
        super("console", "Open Alec's Console");
        this.manager = manager;
        requirePermission(PERMISSION);
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world) {
        manager.openFor(playerRef);
    }
}
