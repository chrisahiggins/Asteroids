package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

/**
 * Single-file Asteroids-style arcade game.
 * Save as Asteroids.java
 *
 * Compile:
 *   javac Asteroids.java
 * Run:
 *   java Asteroids
 *
 * Uses only standard Java (Swing/AWT).
 */
public class Asteroids {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Asteroids");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            GamePanel panel = new GamePanel(800, 600);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            panel.start();
        });
    }
}

/* ---------- Utility vector class ---------- */
class Vec {
    public double x, y;

    public Vec(double x, double y) { this.x = x; this.y = y; }
    public Vec() { this(0,0); }

    public Vec copy() { return new Vec(x, y); }

    public void add(Vec v) { x += v.x; y += v.y; }
    public void sub(Vec v) { x -= v.x; y -= v.y; }
    public void mul(double s) { x *= s; y *= s; }
    public double length() { return Math.hypot(x,y); }
    public void normalize() {
        double l = length();
        if (l != 0) { x /= l; y /= l; }
    }
}

/* ---------- Game entities ---------- */
class Ship {
    Vec pos;
    Vec vel;
    double angle; // radians
    double radius = 12;
    boolean thrusting = false;
    int lives = 3;
    boolean invulnerable = false;
    int invulnTime = 0;

    public Ship(int w, int h) {
        pos = new Vec(w/2.0, h/2.0);
        vel = new Vec(0,0);
        angle = -Math.PI/2; // facing up
    }

    public void reset(int w, int h) {
        pos.x = w/2.0; pos.y = h/2.0;
        vel.x = 0; vel.y = 0;
        angle = -Math.PI/2;
        thrusting = false;
        invulnerable = true;
        invulnTime = 120; // frames
    }
}

class Bullet {
    Vec pos;
    Vec vel;
    int life; // frames

    public Bullet(Vec pos, Vec vel) {
        this.pos = pos.copy();
        this.vel = vel.copy();
        this.life = 60; // ~1 second at 60 FPS
    }

    public boolean isDead() { return life <= 0; }
}

class Asteroid {
    Vec pos;
    Vec vel;
    double radius;
    int size; // 3 = large, 2 = medium, 1 = small
    double spin;
    Polygon shape; // rough shape for visual

    public Asteroid(Vec pos, Vec vel, int size) {
        this.pos = pos.copy();
        this.vel = vel.copy();
        this.size = size;
        this.radius = 15 * size; // 45, 30, 15
        this.spin = (Math.random() - 0.5) * 0.05;
        this.shape = randomPolygon((int)radius);
    }

    private Polygon randomPolygon(int r) {
        int points = 8;
        int[] xs = new int[points];
        int[] ys = new int[points];
        for (int i = 0; i < points; i++) {
            double a = 2*Math.PI * i / points;
            double rr = r * (0.7 + Math.random()*0.6); // varied radius
            xs[i] = (int)Math.round(rr * Math.cos(a));
            ys[i] = (int)Math.round(rr * Math.sin(a));
        }
        return new Polygon(xs, ys, points);
    }
}

/* ---------- Game panel and main loop ---------- */
class GamePanel extends JPanel implements ActionListener, KeyListener {
    final int WIDTH, HEIGHT;
    javax.swing.Timer timer;
    final int FPS = 60;

    Ship ship;
    List<Bullet> bullets = Collections.synchronizedList(new ArrayList<>());
    List<Asteroid> asteroids = Collections.synchronizedList(new ArrayList<>());

    boolean left, right, up, shootPressed;
    int shootCooldown = 0;
    int thrustSoundCooldown = 0;

    Random rand = new Random();

    int score = 0;
    boolean paused = false;
    boolean gameOver = false;

    public GamePanel(int w, int h) {
        WIDTH = w; HEIGHT = h;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        ship = new Ship(WIDTH, HEIGHT);
        spawnLevel(4);
        timer = new javax.swing.Timer(1000 / FPS, this);
    }

    public void start() {
        timer.start();
        Heartbeat.start(this); // 🔊 start heartbeat
    }

    /* Spawns N large asteroids at random edges */
    private void spawnLevel(int count) {
        asteroids.clear();
        for (int i = 0; i < count; i++) {
            Vec p = randomEdgePosition();
            Vec v = new Vec((rand.nextDouble()-0.5)*1.2, (rand.nextDouble()-0.5)*1.2);
            asteroids.add(new Asteroid(p, v, 3));
        }
    }

    private Vec randomEdgePosition() {
        double x, y;
        if (rand.nextBoolean()) {
            x = rand.nextBoolean() ? 0 : WIDTH;
            y = rand.nextDouble() * HEIGHT;
        } else {
            x = rand.nextDouble() * WIDTH;
            y = rand.nextBoolean() ? 0 : HEIGHT;
        }
        return new Vec(x, y);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!paused && !gameOver) updateGame();
        repaint();
    }

    private void updateGame() {
        // Input handling: rotation & thrust
        if (left) ship.angle -= 0.08;
        if (right) ship.angle += 0.08;
        if (up) {
            double thrustPower = 0.15;
            ship.vel.x += Math.cos(ship.angle) * thrustPower;
            ship.vel.y += Math.sin(ship.angle) * thrustPower;
            ship.thrusting = true;
            if (thrustSoundCooldown <= 0) {
                Sound.playThrust();
                thrustSoundCooldown = 10; // frames between thrust sounds
            }
        } else {
            ship.thrusting = false;
        }
        if (thrustSoundCooldown > 0) thrustSoundCooldown--;

        // friction
        ship.vel.x *= 0.995;
        ship.vel.y *= 0.995;

        // move ship
        ship.pos.add(ship.vel);
        wrap(ship.pos);

        // invulnerability counter
        if (ship.invulnerable) {
            ship.invulnTime--;
            if (ship.invulnTime <= 0) ship.invulnerable = false;
        }

        // bullets
        if (shootPressed && shootCooldown <= 0) {
            shoot();
            shootCooldown = 10; // frames between shots
        }
        if (shootCooldown > 0) shootCooldown--;

        synchronized (bullets) {
            Iterator<Bullet> it = bullets.iterator();
            while (it.hasNext()) {
                Bullet b = it.next();
                b.pos.add(b.vel);
                wrap(b.pos);
                b.life--;
                if (b.isDead()) it.remove();
            }
        }

        // asteroids
        synchronized (asteroids) {
            for (Asteroid a : asteroids) {
                a.pos.add(a.vel);
                a.radius = 15 * a.size;
                // small random wobble/rotation is handled by spin when drawing
                wrap(a.pos);
            }
        }

        // collisions: bullets -> asteroids
        List<Asteroid> toAdd = new ArrayList<>();
        List<Asteroid> toRemove = new ArrayList<>();
        synchronized (bullets) {
            synchronized (asteroids) {
                Iterator<Bullet> bit = bullets.iterator();
                while (bit.hasNext()) {
                    Bullet b = bit.next();
                    Iterator<Asteroid> ait = asteroids.iterator();
                    while (ait.hasNext()) {
                        Asteroid a = ait.next();
                        double dx = b.pos.x - a.pos.x;
                        double dy = b.pos.y - a.pos.y;
                        if (dx*dx + dy*dy <= a.radius*a.radius) {
                            // hit
                            bit.remove();
                            ait.remove();
                            Sound.playExplosion(); // 🔊 add here
                            score += 100 * a.size;
                            if (a.size > 1) {
                                // split into two smaller asteroids
                                for (int k = 0; k < 2; k++) {
                                    Vec nv = new Vec((rand.nextDouble()-0.5)*2.0, (rand.nextDouble()-0.5)*2.0);
                                    Asteroid na = new Asteroid(a.pos.copy(), nv, a.size-1);
                                    toAdd.add(na);
                                }
                            }
                            break;
                        }
                    }
                }
                asteroids.addAll(toAdd);
                toAdd.clear();
            }
        }

        // collisions: ship -> asteroids
        if (!ship.invulnerable) {
            synchronized (asteroids) {
                for (Asteroid a : asteroids) {
                    double dx = ship.pos.x - a.pos.x;
                    double dy = ship.pos.y - a.pos.y;
                    if (dx*dx + dy*dy <= (ship.radius + a.radius)*(ship.radius + a.radius)) {
                        // ship hit
                        Sound.playExplosion();
                        ship.lives--;
                        if (ship.lives <= 0) {
                            gameOver = true;
                        } else {
                            ship.reset(WIDTH, HEIGHT);
                        }
                        break;
                    }
                }
            }
        }

        // if all asteroids cleared -> next level
        if (asteroids.isEmpty()) {
            spawnLevel(4 + (score/1000)); // more asteroids as score grows
            ship.reset(WIDTH, HEIGHT);
        }
    }

    private void shoot() {
        double speed = 6.5;
        Vec bVel = new Vec(Math.cos(ship.angle) * speed + ship.vel.x, Math.sin(ship.angle) * speed + ship.vel.y);
        Vec bPos = new Vec(ship.pos.x + Math.cos(ship.angle)*ship.radius*1.5, ship.pos.y + Math.sin(ship.angle)*ship.radius*1.5);
        bullets.add(new Bullet(bPos, bVel));
        Sound.playLaser(); // 🔊 add this line
    }

    private void wrap(Vec p) {
        if (p.x < 0) p.x += WIDTH;
        if (p.x >= WIDTH) p.x -= WIDTH;
        if (p.y < 0) p.y += HEIGHT;
        if (p.y >= HEIGHT) p.y -= HEIGHT;
    }

    /* ---------- Rendering ---------- */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // enable anti-aliasing
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // draw ship (with wrap copies if close to edge)
        drawShip(g2, ship.pos.x, ship.pos.y, ship.angle, ship.thrusting, ship.invulnerable);

        // draw bullets
        g2.setColor(Color.YELLOW);
        synchronized (bullets) {
            for (Bullet b : bullets) {
                g2.fillOval((int)(b.pos.x-2), (int)(b.pos.y-2), 4, 4);
                drawWrapped(g2, (gx, gy) -> g2.fillOval((int)(gx-2), (int)(gy-2), 4, 4), b.pos);
            }
        }

        // draw asteroids
        g2.setColor(Color.LIGHT_GRAY);
        synchronized (asteroids) {
            for (Asteroid a : asteroids) {
                AffineTransform at = g2.getTransform();
                g2.translate(a.pos.x, a.pos.y);
                g2.rotate(a.spin); // small rotation
                // main shape
                g2.drawPolygon(a.shape);
                // draw wrap copies
                g2.setTransform(at);
                drawWrapped(g2, (gx, gy) -> {
                    AffineTransform t2 = g2.getTransform();
                    g2.translate(gx, gy);
                    g2.rotate(a.spin);
                    g2.drawPolygon(a.shape);
                    g2.setTransform(t2);
                }, a.pos);
            }
        }

        // HUD
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 14));
        g2.drawString("Score: " + score, 10, 20);
        g2.drawString("Lives: " + ship.lives, 10, 40);
        g2.drawString("Level: " + (1 + score/1000), 10, 60);

        if (paused) {
            drawCenteredText(g2, "PAUSED - press P to continue", WIDTH/2, HEIGHT/2, 24);
        }

        if (gameOver) {
            drawCenteredText(g2, "GAME OVER - press R to restart", WIDTH/2, HEIGHT/2 - 20, 28);
            drawCenteredText(g2, "Final score: " + score, WIDTH/2, HEIGHT/2 + 20, 18);
        }

        g2.dispose();
    }

    private void drawShip(Graphics2D g2, double x, double y, double angle, boolean thrusting, boolean invuln) {
        // draw at (x,y)
        Polygon shipPoly = new Polygon(new int[]{15, -10, -6, -10}, new int[]{0, -8, 0, 8}, 4); // pointing right
        AffineTransform at = g2.getTransform();
        g2.translate(x, y);
        g2.rotate(angle);
        if (invuln) {
            // blink effect
            if ((ship.invulnTime / 6) % 2 == 0) g2.setColor(Color.WHITE);
            else g2.setColor(Color.GRAY);
        } else g2.setColor(Color.WHITE);
        g2.fill(shipPoly);
        if (thrusting) {
            // draw flame behind ship
            Polygon flame = new Polygon(new int[]{-10, -18, -10}, new int[]{-5, 0, 5}, 3);
            g2.setColor(Color.ORANGE);
            g2.fill(flame);
        }
        g2.setTransform(at);

        // wrap copies (draw near edges)
        drawWrapped(g2, (gx, gy) -> {
            AffineTransform t2 = g2.getTransform();
            g2.translate(gx, gy);
            g2.rotate(angle);
            if (invuln) {
                if ((ship.invulnTime / 6) % 2 == 0) g2.setColor(Color.WHITE);
                else g2.setColor(Color.GRAY);
            } else g2.setColor(Color.WHITE);
            g2.fill(shipPoly);
            if (thrusting) {
                Polygon flame = new Polygon(new int[]{-10, -18, -10}, new int[]{-5, 0, 5}, 3);
                g2.setColor(Color.ORANGE);
                g2.fill(flame);
            }
            g2.setTransform(t2);
        }, ship.pos);
    }

    /* Helper: draw a thing also on wrapped positions when near edges */
    private interface DrawAt {
        void draw(double gx, double gy);
    }

    private void drawWrapped(Graphics2D g2, DrawAt d, Vec pos) {
        double x = pos.x, y = pos.y;
        int margin = 40;
        if (x < margin) d.draw(x + WIDTH, y);
        if (x > WIDTH - margin) d.draw(x - WIDTH, y);
        if (y < margin) d.draw(x, y + HEIGHT);
        if (y > HEIGHT - margin) d.draw(x, y - HEIGHT);
        // corners
        if (x < margin && y < margin) d.draw(x + WIDTH, y + HEIGHT);
        if (x < margin && y > HEIGHT - margin) d.draw(x + WIDTH, y - HEIGHT);
        if (x > WIDTH - margin && y < margin) d.draw(x - WIDTH, y + HEIGHT);
        if (x > WIDTH - margin && y > HEIGHT - margin) d.draw(x - WIDTH, y - HEIGHT);
    }

    private void drawCenteredText(Graphics2D g2, String text, int x, int y, int size) {
        Font old = g2.getFont();
        g2.setFont(new Font(old.getName(), Font.BOLD, size));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        int th = fm.getAscent();
        g2.setColor(Color.WHITE);
        g2.drawString(text, x - tw/2, y + th/2);
        g2.setFont(old);
    }

    /* ---------- Input ---------- */
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT) left = true;
        if (k == KeyEvent.VK_RIGHT) right = true;
        if (k == KeyEvent.VK_UP) up = true;
        if (k == KeyEvent.VK_SPACE) shootPressed = true;
        if (k == KeyEvent.VK_P) paused = !paused;
        if (k == KeyEvent.VK_R && gameOver) {
            // restart
            score = 0;
            ship = new Ship(WIDTH, HEIGHT);
            ship.lives = 3;
            bullets.clear();
            asteroids.clear();
            spawnLevel(4);
            gameOver = false;
            Heartbeat.start(this); // 🔊 restart heartbeat
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT) left = false;
        if (k == KeyEvent.VK_RIGHT) right = false;
        if (k == KeyEvent.VK_UP) up = false;
        if (k == KeyEvent.VK_SPACE) shootPressed = false;
    }

    @Override public void keyTyped(KeyEvent e) { }
}

/* ---------- Retro sound synthesizer ---------- */
class Sound {
    private static final float SAMPLE_RATE = 44100f;

    public static void playThump() {
        // Deep short square wave thump
        //byte[] buf = squareWave(90, 100, 0.8, 1.0);
        byte[] buf = squareWave(70, 120, 0.9, 1.2); // lower pitch, longer fade
        playBuffer(buf);
    }

    private static void playBuffer(byte[] buf) {
        new Thread(() -> {
            try {
                javax.sound.sampled.AudioFormat af =
                        new javax.sound.sampled.AudioFormat(SAMPLE_RATE, 8, 1, true, false);
                javax.sound.sampled.SourceDataLine sdl =
                        javax.sound.sampled.AudioSystem.getSourceDataLine(af);
                sdl.open(af);
                sdl.start();
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.stop();
                sdl.close();
            } catch (Exception e) {
                // ignore sound issues
            }
        }).start();
    }

    /* ---------- Tone generation ---------- */
    private static byte[] squareWave(int hz, int msecs, double vol, double fade) {
        int len = (int)(SAMPLE_RATE * msecs / 1000);
        byte[] buf = new byte[len];
        int period = (int)(SAMPLE_RATE / hz);
        for (int i = 0; i < len; i++) {
            double envelope = 1.0 - fade * i / len; // linear fade
            double value = (i % period < period / 2 ? 1 : -1) * 127 * vol * envelope;
            buf[i] = (byte)value;
        }
        return buf;
    }

    private static byte[] noise(int msecs, double vol, double fade) {
        int len = (int)(SAMPLE_RATE * msecs / 1000);
        byte[] buf = new byte[len];
        java.util.Random r = new java.util.Random();
        for (int i = 0; i < len; i++) {
            double envelope = 1.0 - fade * i / len;
            buf[i] = (byte)((r.nextDouble() * 2 - 1) * 127 * vol * envelope);
        }
        return buf;
    }

    /* ---------- Specific sound effects ---------- */
    public static void playLaser() {
        // high square wave blip
        byte[] buf = squareWave(880, 90, 0.8, 0.8);
        playBuffer(buf);
    }

    public static void playThrust() {
        // lower square wave with fade
        byte[] buf = squareWave(180, 120, 0.5, 1.0);
        playBuffer(buf);
    }

    public static void playExplosion() {
        // white noise burst + quick fade
        byte[] buf = noise(400, 0.9, 1.2);
        playBuffer(buf);
    }
}

class Heartbeat {
    private static volatile boolean running = false;
    private static Thread loopThread;

    // Start the looping heartbeat sound thread
    public static void start(GamePanel panel) {
        if (running) return;
        running = true;
        loopThread = new Thread(() -> {
            double interval = 900; // starting interval (ms between beats)
            double minInterval = 200; // fastest heartbeat
            double adjustSpeed = 0.05; // how fast it eases toward target interval

            try {
                while (running) {
                    if (panel.paused || panel.gameOver) {
                        Thread.sleep(150);
                        continue;
                    }

                    // compute target interval based on asteroid count
                    int count = panel.asteroids.size();
                    double target = 900; // default slow beat
                    if (count < 12) target = 750;
                    if (count < 8)  target = 600;
                    if (count < 5)  target = 450;
                    if (count < 3)  target = 300;
                    if (count <= 1) target = 200;

                    // smooth approach toward target interval
                    interval += (target - interval) * adjustSpeed;

                    // play the "thump" sound
                    Sound.playThump();

                    // wait for current interval
                    Thread.sleep((long) interval);
                }
            } catch (InterruptedException e) {
                // stop thread gracefully
            }
        });
        loopThread.setDaemon(true);
        loopThread.start();
    }

    // Stop the heartbeat
    public static void stop() {
        running = false;
        if (loopThread != null) loopThread.interrupt();
    }
}