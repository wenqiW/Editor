// Terminal.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** A simulation of a simple VDU using Swing. */
class Terminal implements Display.Hardware {
    private static final int WIDTH = Display.WIDTH;
    private static final int HEIGHT = Display.HEIGHT;
    
    /** Title for the window */
    private String banner;

    /** The source for keystrokes. */
    private InputQueue queue = new InputQueue();
    
    /** If non-null, the screen on which the display is shown. */
    private Screen screen;

    /** The characters displayed on the screen. */
    private char text[][] = new char[HEIGHT][WIDTH];
    
    /** Flags to say if characters on the screen are in reverse video */
    private boolean rev[][] = new boolean[HEIGHT][WIDTH];
    
    /** The current location of the cursor. */
    private int curs_col = 0, curs_line = 0;
    
    /** Whether new characters are written in reverse video. */
    private boolean rev_video = false;
    
    /** If non-null, a file used to record keystrokes for later replay */
    private PrintWriter dribble;
    
    /** Create a terminal, simulated by a GUI window. 
     * If the environment "DRIBBLE" is set, then it is taken 
     * as a file name on which to record keystrokes. */
    public Terminal(String banner) {
        this.banner = banner;
        clear();
        
        // Secretly write a dribble file
        String name = System.getenv("DRIBBLE");
        if (name != null) {
            try {
                dribble = new PrintWriter(new FileWriter(name));
            }
            catch (IOException e) {
                throw new Error("Couldn't open dribble file");
            }
        }
    }

    public void activate() {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                screen = new Screen();

                final JFrame gui = new JFrame(banner);
                gui.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                gui.getContentPane().add(screen);

                gui.addWindowListener(new WindowAdapter() {
                    public void windowClosing(WindowEvent e) {
                        queue.enqueue(Keymap.ctrl('Q'));
                    }

                    public void windowOpened(WindowEvent e) {
                        // Must delay this, as it interferes with 
                        // setLocationByPlatform
                        gui.setResizable(false);
                    }
                });

                gui.setLocationByPlatform(true);
                gui.pack();
                gui.setVisible(true);
                screen.requestFocus();
            }
        });
    }

    /** Wait for for a keystroke and return it. */
    public int getKey() {
        int key;

        if (screen != null) screen.repaint();
        key = queue.getKey();
        if (dribble != null) {
            dribble.println(key);
            dribble.flush();
        }
        return key;
    }

    /** Discard any pending input */
    public void flush() {
        queue.flush();
    }

    /** Clear the whole screen */
    public void clear() { 
        curs_col = 0; curs_line = 0; rev_video = false;
        for (int i = 0; i < HEIGHT; i++) {
            for (int j = 0; j < WIDTH; j++) {
                text[i][j] = ' '; rev[i][j] = false;
            }
        } 
        if (screen != null) screen.repaint();
    }

    /** Clear from the cursor to the end of the line */
    public void clearLine() { 
        for (int j = curs_col; j < WIDTH; j++) {
            text[curs_line][j] = ' '; rev[curs_line][j] = false;
        }
        if (screen != null) screen.repaint();
    }

    /** Move the cursor to a specified row and column */
    public void gotoRC(int row, int col) { 
        if (row < 0 || row >= HEIGHT || col < 0 || col >= WIDTH) return;
        curs_line = row; curs_col = col;
        if (screen != null) screen.repaint();
    }
    
    /** Store a character at the cursor */
    private void putChar(char ch) {
        text[curs_line][curs_col] = ch;
        rev[curs_line][curs_col] = rev_video;
        if (curs_col < WIDTH-1) curs_col++;
    }
    
    /** Display a string at the cursor */
    public void write(CharSequence s) { 
        for (int i = 0; i < s.length(); i++)
            putChar(s.charAt(i));
    }

    /** Display a character at the cursor */
    public void write(char ch) { 
        putChar(ch);
        if (screen != null) screen.repaint();
    }

    /** Set reverse video mode for future writes */
    public void setRevVideo(boolean rev) { 
        rev_video = rev;
    }

    /** Ring the bell */
    public void beep() {
        java.awt.Toolkit.getDefaultToolkit().beep();

        // beep doesn't work under Gnome, so try this instead.
        // Assumes a shell script for 'beep', such as
        // aplay $HOME/lib/ping.wav >/dev/null 2>&1 &
        /*
        Runtime rt = Runtime.getRuntime();
        if (rt != null)
            try { rt.exec("beep"); } catch (IOException e) { }
        */

        // If all else fails ...
        System.out.println("BEEP!");
    }
    
    /** Write the screen contents on a file.  This is used to save the
     * results of a test. */
    public void writeScreen(PrintWriter out) {
        for (int i = 0; i < HEIGHT; i++) {
            for (int j = 0; j < WIDTH; j++) {
                if (curs_line == i && curs_col == j) out.print('#');
                out.print(text[i][j]);
            }
            out.println();
        }
        out.flush();
    }

    /** A Swing component that simulates the screen and captures keystrokes. */
    private class Screen extends JPanel {
        // This definition avoids a compiler warning.
        private static final long serialVersionUID = 1L;

        /** Dimension for a character.  We assume the font is monospaced. */
        private final int char_width, char_height, char_ascent;
        
        /** Border between the text and the edge of the screen */
        private final int border = 5;
        
        public Screen() {
            // Load the font as a resource.  Don't look ...
            Font font = new Font("Monospaced", Font.PLAIN, 15);
            setFont(font);

            FontMetrics fm = getFontMetrics(font);
            char_width = fm.getMaxAdvance();
            char_height = fm.getHeight();
            char_ascent = fm.getAscent();

            setPreferredSize(new Dimension(
                    Terminal.WIDTH * char_width + 2 * border, 
                    Terminal.HEIGHT * char_height + 2 * border));
            setFocusTraversalKeysEnabled(false);
            
            // These colours give that authentic, antique feel.
            //setBackground(Color.black);
            //setForeground(Color.green);

            setBackground(Color.white);
            setForeground(Color.black);
        }

        /* For simplicity, we avoid creating a UI delegate, and just implement
         * paintComponent and processComponentKeyEvent directly. */ 

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            Color fg = getForeground(), bg = getBackground();

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                                RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(fg);
            for (int i = 0; i < Terminal.HEIGHT; i++) {
                int y = i * char_height + border;
                for (int j = 0; j < Terminal.WIDTH; j++) {
                    int x = j * char_width + border;
                    if (rev[i][j] || i == curs_line && j == curs_col) {
                        g.fillRect(x, y, char_width, char_height);
                        g.setColor(bg);
                        g.drawChars(text[i], j, 1, x, y+char_ascent);
                        g.setColor(fg);
                    }
                    else {
                        if (text[i][j] == ' ') continue;
                        g.drawChars(text[i], j, 1, x, y+char_ascent);
                    }
                }
            }
        }

        /** Grab all key events for the component */
        @Override
        public void processComponentKeyEvent(KeyEvent ev) {
            if (ev.getID() == KeyEvent.KEY_PRESSED)
                keyPressed(ev);
            ev.consume();
        }

        private void keyPressed(KeyEvent ev) {
            int ch = Display.UNDEFINED;

            switch (ev.getKeyCode()) {
            case KeyEvent.VK_CONTROL:
            case KeyEvent.VK_SHIFT:
            case KeyEvent.VK_ALT:       return;
            case KeyEvent.VK_UP:        ch = Display.UP; break;
            case KeyEvent.VK_DOWN:      ch = Display.DOWN; break;
            case KeyEvent.VK_LEFT:      ch = Display.LEFT; break;
            case KeyEvent.VK_RIGHT:     ch = Display.RIGHT; break;
            case KeyEvent.VK_PAGE_UP:   ch = Display.PAGEUP; break;
            case KeyEvent.VK_PAGE_DOWN: ch = Display.PAGEDOWN; break;
            case KeyEvent.VK_INSERT:    ch = Display.INS; break;
            case KeyEvent.VK_DELETE:    ch = Display.DEL; break;
            case KeyEvent.VK_F1:        ch = Display.F1; break;
            case KeyEvent.VK_F2:        ch = Display.F2; break;
            case KeyEvent.VK_F3:        ch = Display.F3; break;
            case KeyEvent.VK_F4:        ch = Display.F4; break;
            case KeyEvent.VK_F5:        ch = Display.F5; break;
            case KeyEvent.VK_F6:        ch = Display.F6; break;
            case KeyEvent.VK_F7:        ch = Display.F7; break;
            case KeyEvent.VK_F8:        ch = Display.F8; break;
            case KeyEvent.VK_F9:        ch = Display.F9; break;
            case KeyEvent.VK_F10:       ch = Display.F10; break;
            case KeyEvent.VK_F11:       ch = Display.F11; break;
            case KeyEvent.VK_F12:       ch = Display.F12; break;
            case KeyEvent.VK_BACK_SPACE: ch = 0x7f; break;
            case KeyEvent.VK_TAB:       ch = Display.TAB; break;
            case KeyEvent.VK_ENTER:     ch = Display.RETURN; break;

            case KeyEvent.VK_HOME:   
                ch = ((ev.getModifiers() & KeyEvent.CTRL_MASK) != 0
                        ? Display.CTRLHOME : Display.HOME); 
                break;

            case KeyEvent.VK_END:   
                ch = ((ev.getModifiers() & KeyEvent.CTRL_MASK) != 0
                        ? Display.CTRLEND : Display.END); 
                break;

            default:
                char ch1 = ev.getKeyChar();
                if (ch1 != KeyEvent.CHAR_UNDEFINED) 
                    ch = (int) ch1;
            }

            queue.enqueue(ch);
        }
    }
    
    /** A small queue of characters that have been typed on the keyboard
     * but not consumed by the editor.
     * This class uses the concurrent features of Java to provide
     * necessary synchronization and prevent unwelcome
     * interference between the GUI update thread, which is pushing
     * characters into the buffer as they are typed, and the main application
     * thread, which is taking them out and consuming them.
     * In particular, when there are no characters waiting, a call to
     * getKey() will be suspended until a key is pressed. */
    private static class InputQueue {
        private static final int QMAX = 10;

        /* The queue contains queue[head..head+len), where 
         * the subscripts are taken modulo QMAX. */
        private int queue[] = new int[QMAX];
        private int head = 0, len = 0;  

        /** Add a keycode to the input queue */
        public synchronized void enqueue(int ch) {
            // Just toss the character if the queue is full
            if (len >= QMAX) return;
            queue[(head+len)%QMAX] = ch;
            len++;
            notify();
        }

        /** Wait for a keystroke to be available, then return it. */
        public synchronized int getKey() {
            // Wait for the queue to be non-empty
            try {
                while (len == 0) wait();
            }
            catch (InterruptedException e) {
                throw new Error("Unexpected interrupt");
            }

            int ch = queue[head];
            head = (head+1)%QMAX; len--;
            return ch;
        }

        /** Discard pending input. */
        public synchronized void flush() {
            head = len = 0;
        }
    }
}
