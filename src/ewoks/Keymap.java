// Keymap.java
// Copyright (c) 2013 J. M. Spivey

package ewoks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** A mapping from keystrokes to commands. */
class Keymap<T> {
    /** A HashMap that represents the mapping from keys to commands. */
    private Map<Integer, Command<T>> map = new HashMap<Integer, Command<T>>(100);
    
    /** Add an association to the mapping. */
    public void register(int key, Command<T> cmd) {
        map.put(key, cmd);
    }
    
    /** Find the command for a specified key, or return null. */
    public Command<T> find(int key) {
        return map.get(key);
    }
    
    /** Form a control character. Note that Ctrl-? = DEL */
    public static int ctrl(int ch) { return ch ^ 0x40; }
    
    /** A command that can be bound to a keystroke.  */
    public interface Command<T> {
        public void command(T target);
    }
    
    /** Find a method by name suitable for given arguments */
    public static Method findMethod(Class<?> cl, String name, Object args[]) {
        Class<?> types[] = new Class[args.length]; 
        
        for (int i = 0; i < args.length; i++) {
            Class<?> t = args[i].getClass();
            if (t == Integer.class)
                types[i] = Integer.TYPE;
            else if (t == Character.class)
                types[i] = Character.TYPE;
            else if (t == Boolean.class)
                types[i] = Boolean.TYPE;
            else
                types[i] = t;
        }

        try {
            return cl.getMethod(name, types);
        }
        catch (NoSuchMethodException _) {
            throw new Error("failed to bind " + name);
        }

    }
    
    /** A command that invokes a specified method by reflection */
    public static <T> Command<T> 
            reflectCommand(Class<T> cl, String name, final Object... args) {
        final Method method = findMethod(cl, name, args);

        return new Command<T>() {
            public void command(T target) {
                try {
                    method.invoke(target, args);
                }
                catch (InvocationTargetException e) {
                    throw new Error(e.getCause());
                }
                catch (IllegalAccessException _) {
                    throw new Error("Reflection failed");
                }
            }
        };
    }
}
