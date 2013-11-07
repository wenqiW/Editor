// MiniBuffer.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.io.File;
import java.io.FilenameFilter;

/** A small editing area that appears on the bottom line of the screen.
 * Clients can call commandLoop() to allow the minibuffer 
 * contents to be edited, or they can use the other methods to control
 * the minibuffer contents from their own command loop. */
class MiniBuffer {
    private String prompt;
    private Text text = new Text(100);
    private Display display;
    private Completer completer = null;
    private int pos = 0;
    
    public static final int 
        /** Value returned by getStatus: editing still in progress. */
        NORMAL = 0, 
        /** Value returned by getStatus: editing finished normally. */
        DONE = 1,
        /** Value returned by getStatus: editing aborted. */
        ABORT = 2;

    private int status = NORMAL;
        
    /** Construct a minibuffer with a specified prompt string and initial
     * contents, for posting on a specified display.  The minibuffer 
     * does not appear until it is passed to Display.setMiniBuf(). */
    public MiniBuffer(Display display, String prompt, String deflt) {
        this.display = display;
        this.prompt = prompt;
        if (deflt != null) text.insert(0, deflt);
    }
    
    /** Get the status: ABORT if the editing was aborted with Ctrl-G. */
    public int getStatus() { return status; }
    
    /** Get the prompt string */
    public String getPrompt() { return prompt; }
    
    public void setCompleter(Completer completer) {
        this.completer = completer;
    }
    
    public void setVisible(boolean visible) {
        display.setMiniBuf(visible ? this : null);
    }
    
    /** Set the cursor position.
     * Either the cursor should be in the range [0..length], or it should by
     * -1 to indicate that no cursor should be displayed. */
    public void setPos(int pos) { 
        assert pos == -1 || 0 <= pos && pos <= length();
        this.pos = pos; 
    } 
    
    /** Get the cursor position */
    public int getPos() { return pos; }
    
    public int length() { return text.length(); }

    public void getText(Text buf) {
        buf.clear();
        buf.insert(0, text);
    }

    public String toString() { return text.toString(); }
    
    public void clear() { text.clear(); if (pos >= 0) pos = 0; }
    
    public void append(char ch) {
        if (pos >= 0 && pos == text.length()) pos++;
        text.insert(text.length(), ch);
    }
    
    public void append(String s) {
        if (pos >= 0 && pos == text.length()) pos += s.length();
        text.insert(text.length(), s);
    }
    
    public void deleteLast() {
        assert (text.length() > 0);
        if (pos >= 0 && pos == text.length()) pos--;
        text.deleteLast();
    }
    
    /** A function for TAB completion */
    public interface Completer {
        /** expand a partial input as far as possible; return null if no
         * unambiguous expansion exists. */
        public String expand(String partial);
    }
    
    // Editing commands
    public void insertChar(char ch) { text.insert(pos, ch); pos++; }
    public void moveRight() { if (pos < length()) pos++; }
    public void moveLeft() { if (pos > 0) pos--; }
    public void deleteRight() { if (pos < length()) text.deleteChar(pos); }
    public void deleteLeft() { if (pos > 0) text.deleteChar(--pos); }
    public void moveHome() { pos = 0; }
    public void moveEnd() { pos = length(); }
    public void accept() { status = DONE; }
    public void abort() { display.beep(); status = ABORT; } 
    
    public void complete() {
        if (completer == null) { display.beep(); return; }
        String s = completer.expand(text.toString());
        if (s == null) { display.beep(); return; }
        text.clear(); text.insert(0, s); pos = length();
    }
    
    private static Keymap.Command<MiniBuffer> 
                reflectCommand(String name, Object... args) {
        return Keymap.reflectCommand(MiniBuffer.class, name, args);
    }
    
    private static Keymap<MiniBuffer> keymap = new Keymap<MiniBuffer>();

    static {
        for (char ch = 32; ch < 128; ch++)
            keymap.register(ch, reflectCommand("insertChar", ch));
        
        keymap.register(Display.RIGHT, reflectCommand("moveRight"));
        keymap.register(Display.LEFT, reflectCommand("moveLeft"));
        keymap.register(Display.DEL, reflectCommand("deleteRight"));
        keymap.register(Display.HOME, reflectCommand("moveHome"));
        keymap.register(Display.END, reflectCommand("moveEnd"));
        keymap.register(Keymap.ctrl('A'), reflectCommand("moveHome"));
        keymap.register(Keymap.ctrl('B'), reflectCommand("moveLeft"));
        keymap.register(Keymap.ctrl('D'), reflectCommand("deleteRight"));
        keymap.register(Keymap.ctrl('E'), reflectCommand("moveEnd"));
        keymap.register(Keymap.ctrl('F'), reflectCommand("moveRight"));
        keymap.register(Keymap.ctrl('G'), reflectCommand("abort"));
        keymap.register(Keymap.ctrl('?'), reflectCommand("deleteLeft"));
        keymap.register(Display.RETURN, reflectCommand("accept"));
        keymap.register(Display.TAB, reflectCommand("complete"));
    }
    
    public void commandLoop() {
        setVisible(true);

        while (status == NORMAL) {
            display.refreshMinibuf();
            int k = display.getKey();
            Keymap.Command<MiniBuffer> cmd = keymap.find(k);
            if (cmd != null)
                cmd.command(this);
            else
                display.beep();
        }
        
        setVisible(false);
    }
    
    // Utility functions

    /** Use the minibuffer to prompt for a string */
    public static String readString(Display display, String prompt, 
            String deflt) {
        return readString(display, prompt, deflt, null);
    }
    
    public static String readFilename(Display display, String prompt, 
            String deflt) {
        return readString(display, prompt, deflt, new FilenameCompleter());
    }
    
    public static String readString(Display display, String prompt, 
            String deflt, Completer completer) {
        MiniBuffer mini = new MiniBuffer(display, prompt, deflt);
        mini.setCompleter(completer);
        mini.commandLoop();
        if (mini.getStatus() == ABORT) return null;
        return mini.toString();
    }
    
    /** Completion function for filenames. */
    private static class FilenameCompleter implements Completer {
        public String expand(String partial) {
            /* This messy file name stuff doesn't belong here, but I didn't
             * want to make another source file for it. */
            File init = new File(partial);
            File dir; 
            final String prefix;

            // Treat a trailing slash specially
            if (partial.endsWith(File.separator)) {
                dir = init;
                prefix = "";
            } else {
                dir = init.getParentFile();
                prefix = init.getName();
            }

            // Find the directory to search
            File searchDir =
                (dir == null ? new File(System.getProperty("user.dir")) : dir);

            // Find all candidate files
            File cands[] = searchDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File d, String name) {
                    return name.startsWith(prefix);
                }
            });

            if (cands == null || cands.length == 0) return null;

            // Compute the longest common prefix of the candidates
            String cand = cands[0].getName();
            int comlen = cand.length();
            for (int i = 1; i < cands.length; i++) {
                String s = cands[i].getName();
                while (! s.regionMatches(0, cand, 0, comlen))
                    comlen--;
            }

            assert comlen >= prefix.length();
            File result = new File(dir, cand.substring(0, comlen));

            // If a directory is the only candidate, add a trailing slash
            if (cands.length == 1 && result.isDirectory())
                return result.getPath() + File.separator;
            else if (comlen == prefix.length()) 
                return null; // No progress made
            else
                return result.getPath();
        }
    }
    
    /** Use the minibuffer to display a message, then wait for a keypress */
    public static void message(Display display, String format, 
            Object... args) {
        String msg = String.format(format, args);
        MiniBuffer mini = 
            new MiniBuffer(display, msg + " (press RETURN)", null);
        display.beep(); 
        display.flush();
        mini.setVisible(true);
        display.getKey(); // Any key will do!
        mini.setVisible(false);
    }
    
    /** Use the minibuffer to ask a yes/no question.  Unless the user types
     * "yes" exactly, the default answer is "no". */
    public static boolean ask(Display display, String question) {
        display.beep();
        String ans = readString(display, question + " (yes/no)", null);
        return (ans != null && ans.equals("yes"));
    }

}
