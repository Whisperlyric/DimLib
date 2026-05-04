package qouteall.dimlib;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.lang.reflect.Type;

public class DimLibUtil {
    
    public static final Gson GSON;
    
    public static long secondToNano(double second) {
        return (long) (second * 1000000000);
    }
    
    public static double nanoToSecond(long l) {
        return l / 1000000000.0;
    }
    
    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        
        gsonBuilder.registerTypeAdapter(
            new TypeToken<RegistryKey<World>>() {}.getType(),
            new DimensionIDJsonAdapter()
        );
        
        GSON = gsonBuilder.create();
    }
    
    private static class DimensionIDJsonAdapter
        implements JsonSerializer<RegistryKey<World>>, JsonDeserializer<RegistryKey<World>> {
        
        @Override
        public RegistryKey<World> deserialize(
            JsonElement json, Type typeOfT, JsonDeserializationContext context
        ) throws JsonParseException {
            String str = json.getAsString();
            return RegistryKey.of(
                RegistryKeys.WORLD, Identifier.of(str)
            );
        }
        
        @Override
        public JsonElement serialize(RegistryKey<World> src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.getValue().toString());
        }
    }
}
