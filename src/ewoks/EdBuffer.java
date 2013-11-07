// EdBuffer.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/** The state of an editing session. */
class EdBuffer { 
    /** The text being edited. */
    private final PlaneText text = new PlaneText();

    /** The display. */
    private Display display = null;
    
    // State components that must be preserved by undo and redo

    /** Current editing position. */
    private int point = 0;

    // State components that are not restored on undo

    /** File name for saving the text. */
    private String filename = "";

    /** Dirty flag */
    private boolean modified = false;


    /** Register a display */
    public void register(Display display) {
        this.display = display;
    }

    /** Mark the buffer as modified */
    public void setModified() { 
        modified = true;
    }
    
    /** Test whether the text is modified */
    public boolean isModified() { 
        return modified;
    }
    
    
    // Display update
    
    /** Possible value for damage. */
    public static final int CLEAN = 0, REWRITE_LINE = 1, REWRITE = 2;
    
    /** Extent that the display is out of date. */
    private int damage = CLEAN;
    
    /** Note damage to the display. */
    private void damage(boolean rewrite) {
        int newdamage = (rewrite ? REWRITE : REWRITE_LINE);
        damage = Math.max(damage, newdamage);
    }
    
    /** Update display with cursor at point */
    public void update() { update(point); }

    /** Update display with cursor at arbitrary position */
    public void update(int pos) {
        display.refresh(damage, text.getRow(pos), text.getColumn(pos));
        damage = CLEAN;
    }
    
    /** Initialise display */
    public void initDisplay() {
        damage(true);
        update();
    }


    // Accessors

    public int getPoint() { return point; }

    public void setPoint(int point) { this.point = point; }

    public String getFilename() { return filename; }


    // Delegate methods for text
    
    public char charAt(int pos) { return text.charAt(pos); }

    public int getRow(int pos) { return text.getRow(pos); }

    public int getColumn(int pos) { return text.getColumn(pos); }

    public int getPos(int row, int col) { return text.getPos(row, col); }

    public int length() { return text.length(); }

    public int getLineLength(int row) { return text.getLineLength(row); }
    
    public Text.Immutable getRange(int pos, int len) {
        return text.getRange(pos, len);
    }
    
    public void writeFile(Writer out) throws IOException {
        text.writeFile(out);
    }

    public int getNumLines() { return text.getNumLines(); }

    public void fetchLine(int n, Text buf) { text.fetchLine(n, buf); }


    // Mutator methods
    
    /** Delete a character */
    public void deleteChar(int pos) {
        char ch = text.charAt(pos);
        text.deleteChar(pos);
        damage(ch == '\n');
    }

    /** Delete a range of characters. */
    public void deleteRange(int pos, int len) {
        text.deleteRange(pos, len);
        damage(true);
    }
    
    /** Insert a character */
    public void insert(int pos, char ch) {
        text.insert(pos, ch);
        damage(ch == '\n');
    }
    
    /** Insert a string */
    public void insert(int pos, String s) {
        text.insert(pos, s);
        damage(true);
    }
    
    /** Insert an immutable text. */
    public void insert(int pos, Text.Immutable s) {
        text.insert(pos, s);
        damage(true);
    }
    
    /** Insert a Text. */
    public void insert(int pos, Text t) {
        text.insert(pos, t);
        damage(true);
    }
    
    /** Load a file into the buffer. */
    public void loadFile(String name) {
        filename = name;
        text.clear();
        
        try {
            Reader in = new FileReader(name);
            text.insertFile(0, in);
            in.close();
        }
        catch (IOException e) {
            MiniBuffer.message(display, "Couldn't read file '%s'", name);
        }
        
        modified = false;
        damage(true);
    }
    
    /** Save contents on a file */
    public void saveFile(String name) {
        filename = name;
    
        try {
            Writer out = new FileWriter(name);
            writeFile(out);
            out.close();
            modified = false;
        }
        catch (IOException e) {
            MiniBuffer.message(display, "Couldn't write '%s'", name);
        }
    }

    /** Make a Memento that records the current editing state */
    public Memento getState() { return new Memento(); }
    
    /** An immutable record of the editor state at some time.  The state that
     * is recorded consists of just the current point. */
    public class Memento {
        private final int point;
        
        private Memento() {
            this.point = EdBuffer.this.point;
        }
        
        /** Restore the state when the memento was created */
        public void restore() {
            EdBuffer.this.point = this.point;
        }
    }
}
