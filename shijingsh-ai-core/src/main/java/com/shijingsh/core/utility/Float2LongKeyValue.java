package com.shijingsh.core.utility;

import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Float2LongKeyValue {

    /** 键 */
    private float key;

    /** 值 */
    private long value;

    Float2LongKeyValue() {
    }

    public Float2LongKeyValue(float key, long value) {
        this.key = key;
        this.value = value;
    }

    /**
     * 获取键
     *
     * @return
     */
    public float getKey() {
        return key;
    }

    /**
     * 设置键
     *
     * @param newKey
     * @return
     */
    public float setKey(float newKey) {
        float oldKey = key;
        key = newKey;
        return oldKey;
    }

    /**
     * 获取值
     *
     * @return
     */
    public long getValue() {
        return value;
    }

    /**
     * 设置值
     *
     * @param newValue
     * @return
     */
    public long setValue(long newValue) {
        long oldValue = value;
        value = newValue;
        return oldValue;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null)
            return false;
        if (getClass() != object.getClass())
            return false;
        Float2LongKeyValue that = (Float2LongKeyValue) object;
        if (this.key != that.key) {
            return false;
        }
        if (this.value != that.value) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hash = new HashCodeBuilder();
        hash.append(key);
        hash.append(value);
        return hash.toHashCode();
    }

    @Override
    public String toString() {
        return "KeyValue [key=" + key + ", value=" + value + "]";
    }

}
