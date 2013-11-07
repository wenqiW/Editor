// Text.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/** A sequence of characters that allows (efficient) insertion and deletion
 * in the middle. */
class Text implements CharSequence {
    /* A Text object represents the sequence of characters
     * buffer[0..gap) ++ buffer[max-len+gap..max) */
    
    /** The gap buffer */
    private char buffer[];
    
    /** Length of the represented text */
    private int len = 0;

    /** Position of the gap */
    private int gap = 0;

    /** Size of the buffer, stored for convenience */
    private int max;
    
    /** Construct a Text with a default initial capacity */
    public Text() { this(10000); }
    
    /** Construct a Text with a specified initial capacity */
    public Text(int max) {
        assert max >= 0;
        this.max = max; 
        buffer = new char[max];
    }
    
    /** Construct a Text containing a single character */
    public Text(char ch) {
        this(4);
        append(ch);
    }
    
    /** Construct a Text containing a specified string */
    public Text(String s) {
        this(s.length());
        insert(0, s);
    }
    
    /** Return the length of the text */
    public int length() { return len; }

    /** Return the character at a specified position */
    public char charAt(int pos) {
        assert 0 <= pos && pos < len;

        if (pos < gap)
            return buffer[pos];
        else
            return buffer[max-len+pos];
    }
    
    // Mutators: any changes or additions here require similar changes to
    // the subclass PlaneText (the fragile base class problem).
    
    /** Make the text empty. */
    public void clear() {
        gap = len = 0;
    }

    /** Append a character */
    public void append(char ch) { insert(len, ch); }
    
    /** Append another text */
    public void append(Text t) { insert(len, t); }
    
    /** Insert a single character. */
    public void insert(int pos, char ch) {
        assert 0 <= pos && pos <= len;
        makeRoom(1);
        moveGap(pos);
        buffer[gap++] = ch; len++;
    }
    
    /** Insert a string. */
    public void insert(int pos, String s) {
        assert 0 <= pos && pos <= len;
        int n = s.length();
        makeRoom(n); moveGap(pos);
        s.getChars(0, n, buffer, pos);
        gap += n; len += n;
    }
    
    /** Insert another text. */
    public void insert(int pos, Text t) {
        insertRange(pos, t, 0, t.length());
    }
    
    /** Insert an immutable text */
    public void insert(int pos, Immutable text) {
        insert(pos, text.contents);
    }
    
    /** Insert range [start..start+nchars) from another text */
    public void insertRange(int pos, Text t, int start, int nchars) {
        assert pos >= 0 && pos <= len && nchars >= 0 && t != this;  
        
        makeRoom(nchars); moveGap(pos);
        t.getChars(start, nchars, buffer, pos);
        len += nchars; gap += nchars;
    }

    /** Insert the contents of a file. */
    public void insertFile(int pos, Reader in) throws IOException {
        assert 0 <= pos && pos <= len;
        
        moveGap(pos);
        for (;;) {
            // Repeat until we have read the whole file
            makeRoom(4096);
            int nread = in.read(buffer, gap, max-len);
            if (nread < 0) break;
            gap += nread; len += nread;
        }
    }

    /** Delete a single character. */
    public void deleteChar(int pos) {
        assert 0 <= pos && pos < len;
        moveGap(pos); len--;
    }
    
    /** Delete the last character. */
    public void deleteLast() { deleteChar(len-1); }
    
    /** Delete a range of characters. */
    public void deleteRange(int start, int nchars) {
        assert start >= 0 && nchars >= 0 && start+nchars <= len;
        moveGap(start); len -= nchars;
    }
    
    /** Write the entire text on a file. */
    public void writeFile(Writer out) throws IOException {
        if (gap > 0) out.write(buffer, 0, gap);
        if (len > gap) out.write(buffer, max-len+gap, len-gap);
    }
    
    /** Copy range [start..start+nchars) into a char array arr[pos..) */
    public void getChars(int start, int nchars, char arr[], int pos) {
        /* This is used for display update, so for speed we avoid
           moving the gap. */

        assert start >= 0 && nchars >= 0 && start+nchars <= len;

        if (start+nchars <= gap)
            // Entirely in the low part
            System.arraycopy(buffer, start, arr, pos, nchars);
        else if (start >= gap)
            // Entirely in the high part
            System.arraycopy(buffer, max-len+start, arr, pos, nchars);
        else {
            int k = gap-start;
            System.arraycopy(buffer, start, arr, pos, k);
            System.arraycopy(buffer, max-len+gap, arr, pos+k, nchars-k);
        }
    }

    private String getString(int start, int nchars) {
        assert start >= 0 && nchars >= 0 && start + nchars <= len;
        
        if (gap < start+nchars) moveGap(start+nchars);
        return new String(buffer, start, nchars);
    }
    
    /** Fetch the range [start..start+nchars) as an immutable text */
    public Immutable getRange(int start, int nchars) {
        return new Immutable(getString(start, nchars));
    }
    
    public void getRange(int start, int nchars, Text buf) {
        buf.clear();
        buf.insertRange(0, this, start, nchars);
    }

    /** Return the contents of the text as a String.  Be careful when using
     * this for debugging: it has the (benign) side-effect of moving the gap
     * to the end. */
    public String toString() {
        return getString(0, len);
    }
   
    /** Select a range [start..end).  Required for CharSequence but unused. */
    public CharSequence subSequence(int start, int end) {
        if (start < 0 || end < start || len < end)
            throw new IndexOutOfBoundsException();
        return getRange(start, end-start);
    }
    
    /** Establish gap = pos by moving characters around */
    private void moveGap(int pos) {
        assert 0 <= pos && pos <= len;

        if (gap < pos)
            // buf[gap..pos) := buf[max+gap-len..max+pos-len)
            System.arraycopy(buffer, max-len+gap, buffer, gap, pos-gap);
        else if (gap > pos)
            // buf[max+pos-len..max+gap-len) := buf[pos..gap)
            System.arraycopy(buffer, pos, buffer, max-len+pos, gap-pos);

        gap = pos;
    }
    
    /** Ensure that there is space for n more characters. */
    private void makeRoom(int n) {
        assert n >= 0;
        
        if (max-len >= n) return;

        int newcap = Math.max(2 * max, len + n);
        char newbuf[] = new char[newcap];
        if (gap > 0)
            System.arraycopy(buffer, 0, newbuf, 0, gap);
        if (gap < len)
            System.arraycopy(buffer, max-len+gap, 
                             newbuf, newcap-len+gap, len-gap);
        max = newcap;
        buffer = newbuf;
    }
    
    /** An immutable text */
    public static class Immutable implements CharSequence {
        // The implementation here is trivial: just a Java string.
        
        private String contents;
        
        public Immutable(String contents) { this.contents = contents; }

        public char charAt(int index) { return contents.charAt(index); }

        public int length() { return contents.length(); }

        public CharSequence subSequence(int start, int end) {
            return contents.subSequence(start, end);
        }

        public boolean equals(Immutable other) {
            return contents.equals(other.contents);
        }

        public boolean equals(Object other) {
            return ((other instanceof Immutable) 
                    && this.equals((Immutable) other));
        }
    }
}
