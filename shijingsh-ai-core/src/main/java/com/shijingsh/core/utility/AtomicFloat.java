package com.shijingsh.core.utility;

import static java.lang.Float.floatToRawIntBits;
import static java.lang.Float.intBitsToFloat;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class AtomicFloat extends Number implements java.io.Serializable {
    private static final long serialVersionUID = 0L;

    private transient volatile int value;

    private static final AtomicIntegerFieldUpdater<AtomicFloat> updater = AtomicIntegerFieldUpdater.newUpdater(AtomicFloat.class, "value");

    /**
     * Creates a new {@code AtomicFloat} with the given initial value.
     *
     * @param initialValue the initial value
     */
    public AtomicFloat(float initialValue) {
        value = floatToRawIntBits(initialValue);
    }

    /** Creates a new {@code AtomicFloat} with initial value {@code 0.0}. */
    public AtomicFloat() {
        // assert doubleToRawLongBits(0.0) == 0L;
    }

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public final float get() {
        return intBitsToFloat(value);
    }

    /**
     * Sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(float newValue) {
        int next = floatToRawIntBits(newValue);
        value = next;
    }

    /**
     * Eventually sets to the given value.
     *
     * @param newValue the new value
     */
    public final void lazySet(float newValue) {
        int next = floatToRawIntBits(newValue);
        updater.lazySet(this, next);
    }

    /**
     * Atomically sets to the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final float getAndSet(float newValue) {
        int next = floatToRawIntBits(newValue);
        return intBitsToFloat(updater.getAndSet(this, next));
    }

    /**
     * Atomically sets the value to the given updated value if the current value is
     * <a href="#bitEquals">bitwise equal</a> to the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     *         value was not bitwise equal to the expected value.
     */
    public final boolean compareAndSet(float expect, float update) {
        return updater.compareAndSet(this, floatToRawIntBits(expect), floatToRawIntBits(update));
    }

    /**
     * Atomically sets the value to the given updated value if the current value is
     * <a href="#bitEquals">bitwise equal</a> to the expected value.
     *
     * <p>
     * May <a href=
     * "http://download.oracle.com/javase/7/docs/api/java/util/concurrent/atomic/package-summary.html#Spurious">
     * fail spuriously</a> and does not provide ordering guarantees, so is only
     * rarely an appropriate alternative to {@code compareAndSet}.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful
     */
    public final boolean weakCompareAndSet(float expect, float update) {
        return updater.weakCompareAndSet(this, floatToRawIntBits(expect), floatToRawIntBits(update));
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final float getAndAdd(float delta) {
        while (true) {
            int current = value;
            float currentVal = intBitsToFloat(current);
            float nextVal = currentVal + delta;
            int next = floatToRawIntBits(nextVal);
            if (updater.compareAndSet(this, current, next)) {
                return currentVal;
            }
        }
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final float addAndGet(float delta) {
        while (true) {
            int current = value;
            float currentVal = intBitsToFloat(current);
            float nextVal = currentVal + delta;
            int next = floatToRawIntBits(nextVal);
            if (updater.compareAndSet(this, current, next)) {
                return nextVal;
            }
        }
    }

    /**
     * Returns the String representation of the current value.
     *
     * @return the String representation of the current value
     */
    public String toString() {
        return Float.toString(get());
    }

    /**
     * Returns the value of this {@code AtomicFloat} as an {@code int} after a
     * narrowing primitive conversion.
     */
    public int intValue() {
        return (int) get();
    }

    /**
     * Returns the value of this {@code AtomicFloat} as a {@code long} after a
     * narrowing primitive conversion.
     */
    public long longValue() {
        return (long) get();
    }

    /**
     * Returns the value of this {@code AtomicFloat} as a {@code float} after a
     * narrowing primitive conversion.
     */
    public float floatValue() {
        return get();
    }

    /** Returns the value of this {@code AtomicFloat} as a {@code double}. */
    public double doubleValue() {
        return get();
    }

    /**
     * Saves the state to a stream (that is, serializes it).
     *
     * @serialData The current value is emitted (a {@code double}).
     */
    private void writeObject(java.io.ObjectOutputStream s) throws java.io.IOException {
        s.defaultWriteObject();

        s.writeFloat(get());
    }

    /** Reconstitutes the instance from a stream (that is, deserializes it). */
    private void readObject(java.io.ObjectInputStream s) throws java.io.IOException, ClassNotFoundException {
        s.defaultReadObject();

        set(s.readFloat());
    }
}
