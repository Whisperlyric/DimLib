package qouteall.dimlib.ducks;

import net.minecraft.resources.Identifier;

public interface IMappedRegistry {
    public boolean dimlib_forceRemove(Identifier id);
    
    boolean dimlib_getIsFrozen();
    
    void dimlib_setIsFrozen(boolean cond);
}
