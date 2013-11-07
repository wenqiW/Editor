// Editor.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

/** The editor state extended with methods for editor commands. */
class Editor extends Undoable<Editor> {
    private EdBuffer ed = new EdBuffer();
    private Display display;

    /** Whether the command loop should continue */
    private boolean alive = true;
    
    /** Direction for use as argument to moveCommand or deleteCommand. */
    public static final int LEFT = 1, RIGHT = 2, UP = 3, DOWN = 4,
        HOME = 5, END = 6, PAGEUP = 7, PAGEDOWN = 8;
    
    /** Amount to scroll the screen for PAGEUP and PAGEDOWN */
    private static final int SCROLL = Display.HEIGHT - 3;
    
    /** Show the buffer on a specified display */
    public void activate(Display display) {
        this.display = display;
        display.show(ed);
        ed.register(display);
        ed.initDisplay();
    }

    /** Test if the buffer is modified */
    public boolean isModified() { return ed.isModified(); }

    /** Ask for confirmation if the buffer is not clean */
    public boolean checkClean(String action) {
        if (! isModified()) return true;
        String question = 
            String.format("Buffer modified -- really %s?", action);
        return MiniBuffer.ask(display, question);
    }

    /** Load a file into the buffer */
    public void loadFile(String fname) {
        ed.loadFile(fname);
    }

    /** Command: Move the cursor in the specified direction */
    public void moveCommand(int dir) {
        int p = ed.getPoint();
        int row = ed.getRow(p);

        switch (dir) {
        case LEFT:
            if (p > 0) p--;
            break;

        case RIGHT:
            if (p < ed.length()) p++;
            break;

        case UP:
            p = ed.getPos(row-1, goalColumn());
            break;

        case DOWN:
            p = ed.getPos(row+1, goalColumn());
            break;

        case HOME:
            p = ed.getPos(row, 0);
            break;

        case END:
            p = ed.getPos(row, ed.getLineLength(row)-1);
            break;
            
        case PAGEDOWN:
            p = ed.getPos(row + SCROLL, 0);
            display.scroll(+SCROLL);
            break;

        case PAGEUP:
            p = ed.getPos(row - SCROLL, 0);
            display.scroll(-SCROLL);
            break;

        default:
            throw new Error("Bad direction");
        }

        ed.setPoint(p);
    }

    /** Scrap that records an insertion */
    public class Insertion extends Undoable.Scrap {
        /** Location of insertion */
        int pos;

        /** The text inserted. */
        Text.Immutable text;

        public Insertion(int pos, Text.Immutable text) { 
            this.pos = pos;
            this.text = text;
        } 

        public void undo() {
            ed.deleteRange(pos, text.length());
        }

        public void redo() {
            ed.insert(pos, text);
        }
    }

    /** Insertion that can be amalgamated with adjacent, similar scraps */
    public class AmalgInsertion extends Undoable.Scrap {
        /** Location of insertion */
        int pos;

        /** The text inserted by all commands amalgamated with this one */
        Text text;

        public AmalgInsertion(int pos, char ch) {
            this.pos = pos;
            this.text = new Text(ch);
        }

        public void undo() {
            ed.deleteRange(pos, text.length());
        }

        public void redo() {
            ed.insert(pos, text);
        }

        @Override
        public boolean amalgamate(Undoable.Scrap scrap) {
            try {
                AmalgInsertion other = (AmalgInsertion) scrap;

                if (text.charAt(text.length()-1) == '\n'
                    || other.pos != this.pos + this.text.length()) 
                    return false;

                text.append(other.text);
                return true;
            }
            catch (ClassCastException _) {
                return false;
            }
        }
    }

    /** Command: Insert a character */
    public Undoable.Scrap insertCommand(char ch) {
        int p = ed.getPoint();
        ed.insert(p, ch);
        ed.setPoint(p+1);
        ed.setModified();
        return new AmalgInsertion(p, ch);
    }

    /** Scrap that records a deletion */
    public class Deletion extends Undoable.Scrap {
        /** Position of the deletion */
        int pos;
        
        /** Character that was deleted */
        char deleted;

        public Deletion(int pos, char deleted) {
            this.pos = pos;
            this.deleted = deleted;
        }

        public void undo() {
            ed.insert(pos, deleted);
        }

        public void redo() {
            ed.deleteChar(pos);
        }
    }

    /** Command: Delete in a specified direction */
    public Undoable.Scrap deleteCommand(int dir) {
        int p = ed.getPoint();
        char deleted;
        
        switch (dir) {
        case LEFT:
            if (p == 0) { beep(); return null; }
            p--;
            deleted = ed.charAt(p);
            ed.deleteChar(p); 
            ed.setPoint(p);
            break;

        case RIGHT:
            if (p == ed.length()) { beep(); return null; }
            deleted = ed.charAt(p);
            ed.deleteChar(p);
            break;

        default:
            throw new Error("Bad direction");
        }

        ed.setModified();
        return new Deletion(p, deleted);
    }
    
    /** Command: Save the file */
    public void saveFileCommand() {
        String name = 
            MiniBuffer.readFilename(display, "Write file", ed.getFilename());
        if (name == null || name.length() == 0) return;
        ed.saveFile(name);
    }

    /** Prompt for a file to read into the buffer.  */
    public void replaceFileCommand() {
        if (! checkClean("overwrite")) return;
        String name = 
            MiniBuffer.readFilename(display, "Read file", ed.getFilename()); 
        if (name == null || name.length() == 0) return;

        ed.setPoint(0);
        ed.loadFile(name);
        ed.initDisplay();
        reset();
    }

    public void chooseOrigin() {
        display.chooseOrigin();
    }
    
    /** Quit, after asking about modified buffer */
    public void quit() {
        if (checkClean("quit"))
            alive = false;
    }


    // Command execution protocol
    
    /** Goal column for vertical motion. */
    private int goal = -1, prevgoal;
    
    /** Execute a command, wrapping it in actions common to all commands */
    public Undoable.Scrap obey(Undoable.Action<Editor> cmd) {
        prevgoal = goal; goal = -1;
        display.setMessage(null);
        EdBuffer.Memento before = ed.getState();
        Undoable.Scrap scrap = cmd.execute(this);
        EdBuffer.Memento after = ed.getState();
        ed.update();
        return wrapChange(before, scrap, after);
    }
    
    /** The desired column for the cursor after an UP or DOWN motion */
    private int goalColumn() {  
        /* Successive UP and DOWN commands share the same goal column,
         * but other commands cause it to be reset to the current column */
        if (goal < 0) {
            int p = ed.getPoint();
            goal = (prevgoal >= 0 ? prevgoal : ed.getColumn(p));
        }
        
        return goal;
    }

    /** Beep */
    public void beep() { display.beep(); }

    /** Read keystrokes and execute commands */
    public void commandLoop() {
        activate(display);

        while (alive) {
            int key = display.getKey();
            Keymap.Command<Editor> cmd = keymap.find(key);
            if (cmd != null) 
                cmd.command(this);
            else
                beep();
        }
    }

    /** Main program for the entire Ewoks application. */
    public static void main(String args[]) {
        if (args.length > 1) {
            System.err.println("Usage: ewoks [file]");
            System.exit(2);
        }

        Terminal terminal = new Terminal("EWOKS");
        terminal.activate();
        Editor app = new Editor();
        Display display = new Display(terminal);
        app.activate(display);
        if (args.length > 0) app.loadFile(args[0]);
        app.commandLoop();
        System.exit(0);
    }

    private Undoable.Scrap wrapChange(EdBuffer.Memento before,
                Undoable.Scrap change, EdBuffer.Memento after) {
        if (change == null)
            return null;
        else
            return new EditorScrap(before, change, after);
    }
    
    private class EditorScrap extends Undoable.Scrap {
        private EdBuffer.Memento before;
        private Undoable.Scrap change;
        private EdBuffer.Memento after;
        
        public EditorScrap(EdBuffer.Memento before, 
                Undoable.Scrap change, EdBuffer.Memento after) {
            this.before = before;
            this.change = change;
            this.after = after;
        }
        
        public void undo() {
            change.undo(); before.restore();
        }
            
        public void redo() {
            change.redo(); after.restore();
        }
            
        
        public boolean amalgamate(EditorScrap other) {
            if (! change.amalgamate(other.change)) return false;
            after = other.after;
            return true;
        }

        public boolean amalgamate(Undoable.Scrap other) {
            return amalgamate((EditorScrap) other);
        }
    }


    /** Keymap for editor commands */
    private static Keymap<Editor> keymap = new Keymap<Editor>();

    /** Editor action that inserts a certain character. */
    private static Keymap.Command<Editor> insertAction(char ch) {
        return editorAction("insertCommand", ch);
    }

    /** Editor action that moves in a certain direction. */
    private static Keymap.Command<Editor> moveAction(int dir) {
        return editorAction("moveCommand", dir);
    }

    /** Editor action that deletes in a certain direction. */
    private static Keymap.Command<Editor> deleteAction(int dir) {
        return editorAction("deleteCommand", dir);
    }
    
    /** Editor action that calls a specified method */
    private static Keymap.Command<Editor>
                editorAction(String name, Object... args) {
        return Undoable.reflectAction(Editor.class, name, args);
    }
    
    static {
        for (char ch = 32; ch < 128; ch++)
            keymap.register((int) ch, insertAction(ch));

        keymap.register(Display.RETURN, insertAction('\n'));
        keymap.register(Display.RIGHT, moveAction(Editor.RIGHT));
        keymap.register(Display.LEFT, moveAction(Editor.LEFT));
        keymap.register(Display.UP, moveAction(Editor.UP));
        keymap.register(Display.DOWN, moveAction(Editor.DOWN));
        keymap.register(Display.HOME, moveAction(Editor.HOME));
        keymap.register(Display.END, moveAction(Editor.END));
        keymap.register(Display.PAGEUP, moveAction(Editor.PAGEUP));
        keymap.register(Display.PAGEDOWN, moveAction(Editor.PAGEDOWN));
        keymap.register(Keymap.ctrl('?'), deleteAction(Editor.LEFT));
        keymap.register(Display.DEL, deleteAction(Editor.RIGHT));
        keymap.register(Keymap.ctrl('A'), moveAction(Editor.HOME));
        keymap.register(Keymap.ctrl('B'), moveAction(Editor.LEFT));
        keymap.register(Keymap.ctrl('D'), deleteAction(Editor.RIGHT));
        keymap.register(Keymap.ctrl('E'), moveAction(Editor.END));
        keymap.register(Keymap.ctrl('F'), moveAction(Editor.RIGHT));
        keymap.register(Keymap.ctrl('G'), editorAction("beep"));
        keymap.register(Keymap.ctrl('L'), editorAction("chooseOrigin"));
        keymap.register(Keymap.ctrl('N'), moveAction(Editor.DOWN));
        keymap.register(Keymap.ctrl('P'), moveAction(Editor.UP));
        keymap.register(Keymap.ctrl('Q'), editorAction("quit"));
        keymap.register(Keymap.ctrl('R'), editorAction("replaceFileCommand"));
        keymap.register(Keymap.ctrl('W'), editorAction("saveFileCommand"));
        keymap.register(Keymap.ctrl('Y'), editorAction("redo"));
        keymap.register(Keymap.ctrl('Z'), editorAction("undo"));
    }
}
