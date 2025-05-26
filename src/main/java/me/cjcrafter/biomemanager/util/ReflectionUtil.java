package me.cjcrafter.biomemanager.util;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

public class ReflectionUtil {

    public static @NotNull FieldAccessor getField(@NotNull Class<?> clazz, @NotNull String fieldName) {
        try {
            return new FieldAccessor(makeFieldAccessible(clazz.getDeclaredField(fieldName)));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }


    private static @NotNull Field makeFieldAccessible(@NotNull Field field) {
        if (!field.isAccessible()) {
            field.setAccessible(true);
        }

        return field;
    }

}
