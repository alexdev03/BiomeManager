package me.cjcrafter.biomemanager.util;

import java.lang.reflect.Method;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record MethodInvoker(@NotNull Method method) {

    public @Nullable Object invoke(@Nullable Object obj, Object... args) throws IllegalArgumentException {
        try {
            return this.method.invoke(obj, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}