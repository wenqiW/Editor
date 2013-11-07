// Undoable.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.util.Stack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** An mixin that can record a history of undoable actions. */
public abstract class Undoable<T> {
    /** A stack of undo scraps from executed actions. */
    private Stack<Scrap> history = new Stack<Scrap>();

    /** Index into undo stack.  Elements history[0..u) have been executed
            but not undone, and elements history[u..) have been undone. */
    private int undoPointer = 0;

    /** Do the work of an action, returning an undo scrap. */
    public abstract Scrap obey(Action<T> cmd);
    
    /** Beep on error. */
    public abstract void beep();

    /** Execute an action, recording undo info. */
    public void perform(Action<T> action) {
        Scrap scrap = obey(action);
        if (scrap != null) {
            history.setSize(undoPointer);

            if (! history.empty()) {
                Scrap prev = history.peek();
                if (prev.amalgamate(scrap)) return;
            }

            history.push(scrap); undoPointer++;
        }
    }

    /** Undo the latest command. */
    public void undo() { 
        if (undoPointer == 0) { beep(); return; }
        final Scrap scrap = history.elementAt(--undoPointer);
        scrap.undo();
    }

    /** Redo the next command. */
    public void redo() {
        if (undoPointer == history.size()) { beep(); return; }
        final Scrap scrap = history.elementAt(undoPointer++);
        scrap.redo();
    }

    /** Reset the history, e.g. after loading a new file */
    public void reset() {
        history.clear(); undoPointer = 0;
    }

    /** An element of the undo history. */
    public static abstract class Scrap {
        /** Reset the service to its previous state. */
        public abstract void undo();

        /** Reset the service to the state after the change. */
        public abstract void redo();

        /** Try to amalgamate this scrap with another. */
        public boolean amalgamate(Scrap other) { return false; }
    }

    /** A command that can be sent to an undoable. */
    public interface Action<T> {
        /** Execute the command, returning an undo scrap or null */
        public Scrap execute(T target);
    }
    
    /** An undoable action that can also be used as a keyboard command */
    public static abstract class AbstractAction<T extends Undoable<T>>
                implements Action<T>, Keymap.Command<T> {
        /** Perform the action as a keyboard command */
        public void command(T target) {
            target.perform(this);
        }
    }
    
    /** An action specified by reflection of a method */
    private static class ReflectAction<T extends Undoable<T>> 
                extends AbstractAction<T> {
        private Method method;
        private Object args[];
        private boolean undoable;
        
        public ReflectAction(Class<T> cl, String name, final Object... args) {
            this.method = Keymap.findMethod(cl, name, args);
            this.args = args;
            this.undoable = 
                Undoable.Scrap.class.isAssignableFrom(method.getReturnType());
        }
        
        public Scrap execute(T target) {
            try {
                if (undoable)
                    return (Scrap) method.invoke(target, args);
                else {
                    method.invoke(target, args); return null;
                }
            }
            catch (InvocationTargetException e) {
                throw new Error(e.getCause());
            }
            catch (IllegalAccessException _) {
                throw new Error("reflection failed");
            }
        }
    }
    
    /** Action that calls a specified method by reflection */
    public static <T extends Undoable<T>> AbstractAction<T>
            reflectAction(Class<T> cl, String name, Object... args) {
        return new ReflectAction<T>(cl, name, args);
    }
}
