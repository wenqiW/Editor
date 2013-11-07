// PlaneText.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.io.IOException;
import java.io.Reader;

/** An extension of Text that keeps track of the division of the 
 * text into lines. */
class PlaneText extends Text {
    /* For present purposes, we count each newline character as part of the
     * line it terminates, so that every line has non-zero length.  Let's
     * also imagine that a special terminator character is added to the end of
     * the text, so that the very last line also has non-zero length.  For an
     * ordinary text file that ends in a newline, this last line will be empty
     * and be counted as having length 1, and the editor will count the
     * file as having one more line than there are newline characters. */
    private int nlines;
    private int linelen[];
    
    /* For efficiency, we keep track of the beginning of the most recent line
     * that was accessed.  This will help a lot if accesses are clustered. 
     * The invariant is linestart = sum linelen[0..curline) */
    private int curline, linestart;
    
    public PlaneText(int initial) {
        linelen = new int[initial];
        clear();
    }
    
    public PlaneText() { this(1000); }
    
    /** Return the number of lines, including the fictitious last line. */
    public int getNumLines() { return nlines; }
    
    /** Return the length of a line in the file */
    public int getLineLength(int n) { return linelen[n]; }
    
    /** Find the line number corresponding to a character index. */
    public int getRow(int pos) {
        findPos(pos); return curline;
    }
    
    /** Find the column number of a character index in its line. */
    public int getColumn(int pos) {
        findPos(pos); return pos - linestart;
    }
    
    // Augment the mutator methods of Text to maintain the line map
    
    @Override
    public void clear() {
        super.clear();
        nlines = 1;
        linelen[0] = 1;
        curline = 0;
        linestart = 0;
    }
    
    @Override
    public void insert(int pos, char ch) {
        super.insert(pos, ch);
        findPos(pos);
        if (ch != '\n')
            linelen[curline]++;
        else 
            mapLines();
    }

    @Override
    public void insert(int pos, String s) {
        super.insert(pos, s);
        mapLines();
    }
    
    @Override
    public void insertRange(int pos, Text t, int start, int nchars) {
        super.insertRange(pos, t, start, nchars);
        mapLines();
    }

    @Override
    public void insertFile(int pos, Reader in) throws IOException {
        try {
            super.insertFile(pos, in);
        }
        finally {
            // Even if an IOException is thrown, we still update the line map
            mapLines();
        }
    }
    
    @Override
    public void deleteChar(int pos) {
        char ch = charAt(pos);
        super.deleteChar(pos);
        findPos(pos);
        if (ch != '\n')
            linelen[curline]--;
        else 
            mapLines();
    }

    @Override
    public void deleteRange(int start, int len) {
        super.deleteRange(start, len);
        
        findPos(start);
        if (start + len < linestart + linelen[curline])
            linelen[curline] -= len;
        else
            mapLines();
    }

    /** Return the editing position closest to the specified coordinates */
    public int getPos(int row, int col) {
        int r = Math.min(Math.max(row, 0), nlines-1);
        findLine(r);
        int c = Math.min(Math.max(col, 0), linelen[curline]-1);
        return linestart + c;
    }
    
    /** Fetch the text of line n, without the trailing newline */
    public void fetchLine(int n, Text buf) {
        findLine(n);
        getRange(linestart, linelen[n]-1, buf);
    }
    
    /** Refresh the line map by scanning the whole file.
     * This is always a last resort if we choose not to update the
     * line map in a faster way. */
    private void mapLines() {
        nlines = 0;
        int c = 0;
        int totlen = length();

        for (int i = 0; i < totlen; i++) {
            c++;
            if (charAt(i) == '\n') {
                lineRoom();
                linelen[nlines++] = c; c = 0;
            } 
        }
        
        lineRoom();
        linelen[nlines++] = c+1;
        
        // Reset the cache
        curline = 0; linestart = 0;
    }
    
    /** Set curline to a specified line number. */
    private void findLine(int n) {
        assert n >= 0 && n < nlines;
        
        // Move forwards if necessary
        while (n > curline) linestart += linelen[curline++];

        // Move backwards if necessary
        while (n < curline) linestart -= linelen[--curline];
    }
    
    /** Set current line so that it contains a specified character index. */
    private void findPos(int j) {
        assert j >= 0 && j <= length();

        // Move backwards if necessary
        while (j < linestart) 
            linestart -= linelen[--curline];
        
        // Move forwards if necessary
        while (j >= linestart + linelen[curline])
            linestart += linelen[curline++];
        
        assert linestart <= j && j < linestart + linelen[curline];
    }
    
    /** Find room for one more line */
    private void lineRoom() {
        if (nlines >= linelen.length) {
            int newlen[] = new int[2 * linelen.length];
            System.arraycopy(linelen, 0, newlen, 0, nlines);
            linelen = newlen;
        }
    }
}
