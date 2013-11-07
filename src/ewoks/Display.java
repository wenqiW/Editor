// Display.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

/** A view that keeps a terminal up to date with respect to a text. */
class Display {
    /** Dimensions of the display (fixed for now) */
    public static final int HEIGHT = 24, WIDTH = 80;

    /** Codes returned by the keyboard */
    public static final int UP = 513, DOWN = 514, RIGHT = 515, LEFT = 516,
        HOME = 517, END = 518, PAGEUP = 519, PAGEDOWN = 520,
        INS = 521, DEL = 522, F1 = 523, F2 = 524, F3 = 525, F4 = 526,
        F5 = 527, F6 = 528, F7 = 529, F8 = 530, F9 = 531, F10 = 532,
        F11 = 533, F12 = 534, CTRLHOME = 535, CTRLEND = 536, TAB = 537,
        RETURN = 538,
        UNDEFINED = -1;

    /** Number of lines in the main display */
    private static final int LINES = HEIGHT - 1;

    /** Line for showing the minibuffer */
    private static final int MINIROW = HEIGHT - 1;

    /** The editor that is shown on the display. */
    private EdBuffer editor;
    
    /** The terminal where the text is shown. */
    private Hardware terminal;
    
    /** The top line shown on the terminal */
    private int origin = 0;
    
    /** Top line from the previous time the display was refreshed */
    private int oldOrigin = 0;
    
    /** The minibuffer being displayed, or null if none */
    private MiniBuffer minibuf = null;
    
    /** A message to show on the last line */
    private String message = null;
    
    /** Current editing position */
    private int row = 0, col = 0;
   
    public Display(Hardware terminal) {
        this.terminal = terminal;
    }
    
    public void show(EdBuffer editor) {
        this.editor = editor;
    }
    

    // Delegates for keyboard methods

    /** If >= 0, a keystroke that has been pushed back 
     * to be read again later. */
    int pbkey = -1;

    /** Get a keystroke */
    public int getKey() { 
        int key;

        if (pbkey >= 0) {
            key = pbkey; pbkey = -1;
        } else {
            key = terminal.getKey(); 
        }
        return key;
    }
    
    /** Push back a keystroke to be read again later. */
    public void pushBack(int key) { pbkey = key; }

    /** Flush type-ahead */
    public void flush() { terminal.flush(); pbkey = -1; }
    
    /** Just beep */
    public void beep() { terminal.beep(); }


    // These routines rewrite parts of the display, but leave the cursor
    // where they please
    
    /** Scratch buffer for use by rewrite */
    private Text buf = new Text(WIDTH);
    
    /** Rewrite the entire screen */
    private void rewrite() {
        terminal.clear();
        
        for (int r = 0; r < LINES && origin+r < editor.getNumLines(); r++) {
            editor.fetchLine(origin+r, buf);
            if (buf.length() > 0) {
                terminal.gotoRC(r, 0);
                terminal.write(buf);
            }
        }

        rewriteMinibuf();
    }
    
    /** Rewrite just the line containing the cursor */
    private void rewriteLine() {
        terminal.gotoRC(row-origin, 0);
        terminal.clearLine();
        editor.fetchLine(row, buf);
        terminal.write(buf);
    }
    
    /** Rewrite just the minibuffer line */
    private void rewriteMinibuf() {
        terminal.gotoRC(MINIROW, 0);
        terminal.clearLine();

        if (minibuf == null) {
            String fname = editor.getFilename();
            boolean modified = editor.isModified();
            terminal.setRevVideo(true);
            terminal.write(String.format("--- EWOKS: %s%s ---",
                                         fname, (modified ? "*" : "")));
            terminal.setRevVideo(false);
            if (message != null) {
                terminal.write(' ');
                terminal.write(message);
            }
            return;
        }
        
        terminal.setRevVideo(true);
        terminal.write(minibuf.getPrompt());
        terminal.write(':');
        terminal.setRevVideo(false);
        terminal.write(' ');
        minibuf.getText(buf);
        terminal.write(buf);
    }

    /** Move the cursor to the correct place */
    private void moveCursor() {
        if (minibuf != null && minibuf.getPos() >= 0)
            terminal.gotoRC(MINIROW, 
                    minibuf.getPrompt().length() + minibuf.getPos() + 2);
        else
            terminal.gotoRC(row-origin, Math.min(col, WIDTH-1));
    }
    
    /** Update the display */
    public void refresh(int damage, int row, int col) {
        this.row = row;
        this.col = col;
        checkScroll();
        if (origin != oldOrigin)
            damage = EdBuffer.REWRITE;

        switch (damage) {
        case EdBuffer.REWRITE:
            rewrite();
            break;
        case EdBuffer.REWRITE_LINE:
            rewriteLine();
            break;
        case EdBuffer.CLEAN:
            break;
        default:
            throw new Error("Display.refresh");
        }
        
        rewriteMinibuf();
        moveCursor();
        oldOrigin = origin;
    }
    
    /** Update the minibuffer line */
    public void refreshMinibuf() {
        rewriteMinibuf();
        moveCursor();
    }

    /** Post or (with null) remove a minibuffer */
    public void setMiniBuf(MiniBuffer minibuf) {
        this.minibuf = minibuf;
        refreshMinibuf();
    }
    
    /** Post or (with null) remove a message. */
    public void setMessage(String message) {
        this.message = message;
        refreshMinibuf();
    }

    /* These routines implement the scrolling policy.  External calls of
     * chooseOrigin and scroll may set an origin that does not obey
     * the scrolling policy; this will be corrected by checkScroll next
     * time the display is refreshed. */
    
    /** Check that the origin obeys the rules, and move it if not */
    private void checkScroll() {
        if (row < origin || row >= origin + LINES)
            chooseOrigin();

        /* Ensure that the origin is within the buffer, if possible by
         * half a screen at the end */
        origin = Math.max(Math.min(origin, editor.getNumLines() - LINES/2), 0); 
    }

    /** Choose display origin to centre the cursor */
    public void chooseOrigin() {
        // This is used for Redraw
        origin = row - LINES/2;
    }
    
    /** Suggest scrolling by a specified amount */
    public void scroll(int n) {
        // This is used by PageUp and PageDown
        origin += n;
    }

    /** Interface for display hardware */
    public interface Hardware {
        /** Wait for for a keystroke and return it. */
        public int getKey();

        /** Discard any pending input */
        public void flush();

        /** Clear the whole screen */
        public void clear();

        /** Clear from the cursor to the end of the line */
        public void clearLine();

        /** Move the cursor to a specified row and column */
        public void gotoRC(int row, int col);

        /** Display a string at the cursor */
        public void write(CharSequence s);

        /** Display a character at the cursor */
        public void write(char ch);

        /** Set reverse video mode for future writes */
        public void setRevVideo(boolean rev);

        /** Ring the bell */
        public void beep();
    }
}
