package me.mattstudios.holovid.display;

import me.mattstudios.holovid.Holovid;
import net.minecraft.server.v1_16_R1.ChatBaseComponent;
import net.minecraft.server.v1_16_R1.ChatComponentText;
import net.minecraft.server.v1_16_R1.IChatBaseComponent;

import java.util.concurrent.ArrayBlockingQueue;

public final class BufferedDisplayTask extends DisplayTask {

    private final ArrayBlockingQueue<int[][]> frames;
    private final int bufferCapacity;
    private final long startDelay;
    private final int max;

    public BufferedDisplayTask(final Holovid plugin, final long startDelay, final boolean repeat, final int max, final int fps, final boolean interlace) {
        super(plugin, repeat, fps, interlace);
        this.startDelay = startDelay;
        this.max = max;

        // Buffer a few seconds of video beforehand
        this.bufferCapacity = Holovid.PRE_RENDER_SECONDS * fps;
        this.frames = new ArrayBlockingQueue<>(bufferCapacity);
    }

    @Override
    protected void prerun() throws InterruptedException {
        // Give the processing a headstart
        if (startDelay > 0) {
            Thread.sleep(startDelay);
        }
    }

    @Override
    public int getMaxFrames() {
        return max;
    }

    @Override
    protected IChatBaseComponent[] getCurrentFrame() throws InterruptedException {
        // Block until the frame is processed
        final int[][] frame = frames.take();

        // Convert to json component
        final IChatBaseComponent[] frameText = new IChatBaseComponent[interlace ? frame.length / 2 : frame.length];
        if (interlace){
            for (int y = oddFrame ? 1 : 0; y < frame.length; y += 2){
                frameText[y / 2] = dataToComponent(frame[y]);
            }
        } else {
            for (int i = 0; i < frame.length; i++) {
                frameText[i] = dataToComponent(frame[i]);
            }
        }
        return frameText;
    }

    public ArrayBlockingQueue<int[][]> getFrameQueue() {
        return frames;
    }

    public boolean isQueueFull() {
        return frames.size() >= bufferCapacity;
    }

    private IChatBaseComponent dataToComponent(final int[] row) {
        final ChatBaseComponent component = new ChatComponentText("");
        ChatComponentText lastComponent = null;
        int lastRgb = 0xFFFFFF;
        for (int rgb : row) {
            rgb &= 0x00FFFFFF;
            lastComponent = appendComponent(component, rgb, lastRgb, lastComponent);
            lastRgb = rgb;
        }
        return component;
    }
}
