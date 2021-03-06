package me.mattstudios.holovid;

import com.google.common.base.Preconditions;
import me.mattstudios.holovid.command.DownloadCommand;
import me.mattstudios.holovid.command.PlayCommand;
import me.mattstudios.holovid.command.SpawnScreenCommand;
import me.mattstudios.holovid.command.StopCommand;
import me.mattstudios.holovid.display.BufferedDisplayTask;
import me.mattstudios.holovid.display.DisplayTask;
import me.mattstudios.holovid.download.VideoDownloader;
import me.mattstudios.holovid.download.VideoProcessor;
import me.mattstudios.holovid.download.YouTubeDownloader;
import me.mattstudios.holovid.hologram.Hologram;
import me.mattstudios.holovid.listener.HologramListener;
import me.mattstudios.holovid.utils.Task;
import me.mattstudios.mf.base.CommandManager;
import me.mattstudios.mf.base.components.TypeResult;
import org.apache.commons.validator.routines.UrlValidator;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

public final class Holovid extends JavaPlugin {

    public static final int PRE_RENDER_SECONDS = 20;
    private CommandManager commandManager;
    private VideoProcessor videoProcessor;
    private VideoDownloader videoDownloader;
    private Hologram hologram;
    private DisplayTask task;

    private int displayHeight = 144;
    private int displayWidth = 256;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        displayHeight = getConfig().getInt("display-height", 144);
        displayWidth = getConfig().getInt("display-width", 256);

        // Loads the tasks util
        Task.init(this);

        commandManager = new CommandManager(this);
        videoProcessor = new VideoProcessor(this);
        videoDownloader = new YouTubeDownloader(this);

        getServer().getPluginManager().registerEvents(new HologramListener(this), this);
        registerCommands();
    }

    @Override
    public void onDisable() {
        stopTask();
    }

    /**
     * Registers anything related to commands
     * TODO should probably move this away
     */
    private void registerCommands() {
        // Registers URL parameter for commands
        commandManager.getParameterHandler().register(URL.class, argument -> {
            if (argument == null) return new TypeResult(null);
            if (!UrlValidator.getInstance().isValid(argument.toString())) return new TypeResult(argument);
            try {
                return new TypeResult(new URL(argument.toString()), argument);
            } catch (MalformedURLException e) {
                return new TypeResult(null);
            }
        });

        // Registers the videos completion
        commandManager.getCompletionHandler().register("#videos", input -> {
            final File[] files = new File(getDataFolder(), "saves").listFiles();
            if (files == null) return Collections.emptyList();
            return Arrays.asList(files).parallelStream().filter(File::isDirectory).map(File::getName).collect(Collectors.toList());
        });

        // Registers all the commands
        Arrays.asList(
                new DownloadCommand(this),
                new PlayCommand(this),
                new SpawnScreenCommand(this),
                new StopCommand(this)
        ).forEach(commandManager::register);

    }

    public void spawnHologram(final Location location) {
        // Despawn old holograms if present
        despawnHologram();

        // Lines are added when the actual images are displayed
        hologram = new Hologram(displayHeight);
        hologram.addLine();
        hologram.spawn(location);
    }

    public void despawnHologram() {
        if (hologram != null) {
            hologram.despawn();
            hologram = null;
        }
    }

    @Nullable
    public Hologram getHologram() {
        return hologram;
    }

    @Nullable
    public DisplayTask getTask() {
        return task;
    }

    public void startBufferedTask(final long startDelay, final int frames, final int height, final int fps, final boolean interlace) {
        prepareForTask(height);

        this.task = new BufferedDisplayTask(this, startDelay, true, frames, fps, interlace);
        Task.async(task);
    }

    private void prepareForTask(final int height) {
        Preconditions.checkNotNull(hologram);
        stopTask();

        if (hologram.getLines().size() < height) {
            // Expand hologram
            for (int i = hologram.getLines().size(); i < height; i++) {
                hologram.addLine();
            }
        } else {
            // Shorten it (just in case)
            final int size = hologram.getLines().size();
            for (int i = height; i < size; i++) {
                hologram.removeLine(hologram.getLines().size() - 1);
            }
        }
    }

    /**
     * Stops the display task if it is currently running.
     *
     * @return true if the task was running and has now been cancelled
     */
    public boolean stopTask() {
        if (task == null) {
            return false;
        }

        videoProcessor.stopCurrentTask();
        task.stop();
        task = null;
        return true;
    }

    public VideoProcessor getVideoProcessor() {
        return videoProcessor;
    }

    public VideoDownloader getVideoDownloader() {
        //TODO get different downloaders for different sites
        return videoDownloader;
    }

    public int getDisplayHeight() {
        return displayHeight;
    }

    public int getDisplayWidth() {
        return displayWidth;
    }

}
