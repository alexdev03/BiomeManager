package me.cjcrafter.biomemanager.util;

import java.lang.RuntimeException;
import java.lang.reflect.Field;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// From MechanicsCore
public class FieldAccessor {
    private final @NotNull Field field;

    public FieldAccessor(@NotNull Field field) {
        this.field = field;
    }

    public @NotNull Field getField() {
        return this.field;
    }

    public boolean getBoolean(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getBoolean(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public @Nullable Object get(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void set(@Nullable Object obj, @Nullable Object value) throws IllegalArgumentException {
        try {
            this.field.set(obj, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setFloat(@Nullable Object obj, float f) throws IllegalArgumentException {
        try {
            this.field.setFloat(obj, f);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public byte getByte(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getByte(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setBoolean(@Nullable Object obj, boolean z) throws IllegalArgumentException {
        try {
            this.field.setBoolean(obj, z);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public char getChar(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getChar(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setDouble(@Nullable Object obj, double d) throws IllegalArgumentException {
        try {
            this.field.setDouble(obj, d);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setByte(@Nullable Object obj, byte b) throws IllegalArgumentException {
        try {
            this.field.setByte(obj, b);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public short getShort(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getShort(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setChar(@Nullable Object obj, char c) throws IllegalArgumentException {
        try {
            this.field.setChar(obj, c);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public int getInt(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getInt(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public long getLong(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getLong(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setShort(@Nullable Object obj, short s) throws IllegalArgumentException {
        try {
            this.field.setShort(obj, s);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setInt(@Nullable Object obj, int i) throws IllegalArgumentException {
        try {
            this.field.setInt(obj, i);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public float getFloat(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getFloat(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public double getDouble(@Nullable Object obj) throws IllegalArgumentException {
        try {
            return this.field.getDouble(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setLong(@Nullable Object obj, long l) throws IllegalArgumentException {
        try {
            this.field.setLong(obj, l);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
