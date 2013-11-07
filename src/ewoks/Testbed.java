// Testbed.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;

/** A testbed for executing canned command sequences. */
class Testbed {
    public static void main(String args[]) {
        if (args.length != 2) {
            System.err.println("Usage: testbed file input");
            System.exit(2);
        }
        
        String file = args[0];
        String input = args[1];
        
        Reader testin;
        try {
            testin = new BufferedReader(new FileReader(input));
        }
        catch (FileNotFoundException e) {
            System.err.println("Can't read " + input);
            System.exit(1);
            throw null;
        }
        
        Playback terminal = new Playback(testin);
        Editor app = new Editor();
        Display display = new Display(terminal);
        app.activate(display);
        app.loadFile(file);
        app.commandLoop();
        terminal.writeScreen(new PrintWriter(
                new OutputStreamWriter(System.out)));
        System.exit(0);
    }

    /** A fake terminal that takes input from a file */
    private static class Playback implements Display.Hardware {
        private static final int WIDTH = Display.WIDTH;
        private static final int HEIGHT = Display.HEIGHT;

        /** The source for keystrokes. */
        private Reader testin;

        /** The characters displayed on the screen. */
        private char text[][] = new char[HEIGHT][WIDTH];
    
        /** The current location of the cursor. */
        private int curs_col = 0, curs_line = 0;
    
        public Playback(Reader testin) {
            clear();
            this.testin = testin;
        }

        /** Get a keystroke from the file. */
        public int getKey() {
            // Yawn.
            StringBuffer chars = new StringBuffer();
            char ch = getch();
        
            while (Character.isSpaceChar(ch)) ch = getch();
            
            if (! Character.isDigit(ch))
                throw new Error("Bad test input");
            
            while (Character.isDigit(ch)) {
                chars.append(ch); ch = getch();
            }
            
            return Integer.parseInt(chars.toString());
        }
        
        /** Get a character from the file. */
        private char getch() {
            try {
                int ch = testin.read();
                if (ch < 0) throw new Error("Unexpected EOF on test input");
                return (char) ch;
            }
            catch (IOException e) {
                throw new Error("I/O exception on test input");
            }
        }

        /** Discard any pending input */
        public void flush() { }

        /** Clear the whole screen */
        public void clear() { 
            curs_col = 0; curs_line = 0;
            for (int i = 0; i < HEIGHT; i++) {
                for (int j = 0; j < WIDTH; j++)
                    text[i][j] = ' ';
            } 
        }

        /** Clear from the cursor to the end of the line */
        public void clearLine() { 
            for (int j = curs_col; j < WIDTH; j++)
                text[curs_line][j] = ' ';
        }

        /** Move the cursor to a specified row and column */
        public void gotoRC(int row, int col) { 
            if (row < 0 || row >= HEIGHT || col < 0 || col >= WIDTH) return;
            curs_line = row; curs_col = col;
        }
        
        /** Display a string at the cursor */
        public void write(CharSequence s) { 
            for (int i = 0; i < s.length(); i++)
                write(s.charAt(i));
        }
        
        /** Display a character at the cursor */
        public void write(char ch) { 
            text[curs_line][curs_col] = ch;
            if (curs_col < WIDTH-1) curs_col++;
        }
        
        /** Set reverse video mode for future writes */
        public void setRevVideo(boolean rev) { }

        /** Ring the bell */
        public void beep() {
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
    }
}
