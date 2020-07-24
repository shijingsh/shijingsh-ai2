package com.shijingsh.core.utility;

import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Long2DoubleKeyValue {

    /** 键 */
    private long key;

    /** 值 */
    private double value;

    Long2DoubleKeyValue() {
    }

    public Long2DoubleKeyValue(long key, double value) {
        this.key = key;
        this.value = value;
    }

    /**
     * 获取键
     *
     * @return
     */
    public long getKey() {
        return key;
    }

    /**
     * 设置键
     *
     * @param newKey
     * @return
     */
    public long setKey(long newKey) {
        long oldKey = key;
        key = newKey;
        return oldKey;
    }

    /**
     * 获取值
     *
     * @return
     */
    public double getValue() {
        return value;
    }

    /**
     * 设置值
     *
     * @param newValue
     * @return
     */
    public double setValue(double newValue) {
        double oldValue = value;
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
        Long2DoubleKeyValue that = (Long2DoubleKeyValue) object;
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
