import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.midlet.MIDlet;

public class BadAppleMidlet extends MIDlet {
    private PlayerCanvas canvas;
    protected void startApp() {
        if (canvas == null) {
            canvas = new PlayerCanvas(this);
            Display.getDisplay(this).setCurrent(canvas);
            canvas.play();
        }
    }
    protected void pauseApp() { if (canvas != null) canvas.setPaused(true); }
    protected void resumeApp() {}
    protected void destroyApp(boolean unconditional) { if (canvas != null) canvas.stop(); }
    void exit() { destroyApp(true); notifyDestroyed(); }
}

class PlayerCanvas extends GameCanvas implements Runnable {
    private static final int WHITE = 0x00FFFFFF;
    private static final int BLACK = 0x00000000;
    private final BadAppleMidlet midlet;
    private final int scrW, scrH;
    private final int[] rgb, mapX, mapY;
    private DataInputStream in;
    private byte[] packed;
    private int w, h, fps, frames;
    private Thread thread;
    private volatile boolean running, paused, finished;
    private String error;
    private Player music;
    private long startMs, pauseMs;

    PlayerCanvas(BadAppleMidlet midlet) {
        super(false);
        this.midlet = midlet;
        setFullScreenMode(true);
        scrW = getWidth();
        scrH = getHeight();
        rgb = new int[scrW * scrH];
        mapX = new int[scrW];
        mapY = new int[scrH];
        try { open(); } catch (IOException e) { error = e.toString(); }
    }

    private void open() throws IOException {
        InputStream raw = getClass().getResourceAsStream("/badapple.bin");
        if (raw == null) throw new IOException("no /badapple.bin in jar");
        in = new DataInputStream(raw);
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0]!='B'||magic[1]!='A'||magic[2]!='P'||magic[3]!='L') throw new IOException("bad magic");
        in.readUnsignedShort();
        w = in.readUnsignedShort();
        h = in.readUnsignedShort();
        fps = in.readUnsignedShort();
        frames = in.readInt();
        packed = new byte[(w * h) >> 3];
        boolean portrait = scrH > scrW;
        for (int x = 0; x < scrW; x++)
            mapX[x] = portrait ? (h - 1 - (x * h / scrW)) * w : x * w / scrW;
        for (int y = 0; y < scrH; y++)
            mapY[y] = portrait ? y * w / scrH : (y * h / scrH) * w;
        try {
            music = Manager.createPlayer(getClass().getResourceAsStream("/bad_apple.mid"), "audio/midi");
            music.prefetch();
        } catch (Exception e) { music = null; }
    }

    void play() { if (thread != null || error != null) return; running = true; thread = new Thread(this); thread.start(); }
    void stop() {
        running = false;
        if (music != null) { try { music.close(); } catch (Exception e) {} }
        if (in != null) { try { in.close(); } catch (Exception e) {} }
    }
    void setPaused(boolean p) {
        if (paused == p || finished || error != null) return;
        paused = p;
        if (music == null) return;
        try {
            if (p) { music.stop(); pauseMs = System.currentTimeMillis(); }
            else { music.start(); startMs += System.currentTimeMillis() - pauseMs; }
        } catch (Exception e) { music = null; }
    }

    public void run() {
        long frameMs = 1000L / fps;
        startMs = System.currentTimeMillis();
        if (music != null) { try { music.start(); } catch (Exception e) { music = null; } }
        for (int frame = 0; frame < frames && running; frame++) {
            while (paused && running) sleep(50);
            if (!running) return;
            try { in.readFully(packed); } catch (IOException e) { break; }
            render();
            repaint();
            serviceRepaints();
            long wait = startMs + frame * frameMs - System.currentTimeMillis();
            if (wait > 0) sleep(wait);
        }
        finished = true;
        repaint();
        serviceRepaints();
        thread = null;
    }

    private void render() {
        int i = 0;
        for (int y = 0; y < scrH; y++) {
            int base = mapY[y];
            for (int x = 0; x < scrW; x++) {
                int s = base + mapX[x];
                rgb[i++] = ((packed[s >> 3] & (0x80 >> (s & 7))) != 0) ? WHITE : BLACK;
            }
        }
    }

    public void paint(Graphics g) {
        g.drawRGB(rgb, 0, scrW, 0, 0, scrW, scrH, false);
        if (error != null || paused || finished) {
            String msg = error != null ? error : (finished ? "FIN: 5 - restart, any - exit" : "PAUSE");
            g.setFont(Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL));
            int tw = g.getFont().stringWidth(msg);
            g.setColor(BLACK);
            g.fillRect((scrW - tw) / 2 - 4, scrH / 2 - 10, tw + 8, 20);
            g.setColor(WHITE);
            g.drawString(msg, scrW / 2, scrH / 2, Graphics.HCENTER | Graphics.VCENTER);
        }
    }

    protected void keyPressed(int code) {
        if (error != null) { midlet.exit(); return; }
        if (finished) {
            if (getGameAction(code) == FIRE || code == KEY_NUM5) restart();
            else midlet.exit();
            return;
        }
        if (code == -6 || getGameAction(code) == FIRE || code == KEY_NUM5) setPaused(!paused);
        else if (code == -7 || code == KEY_POUND) midlet.exit();
    }

    private void restart() {
        if (music != null) { try { music.close(); } catch (Exception e) {} music = null; }
        if (in != null) { try { in.close(); } catch (Exception e) {} }
        error = null; finished = false; paused = false;
        try { open(); } catch (IOException e) { error = e.toString(); repaint(); return; }
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) {} }
}
