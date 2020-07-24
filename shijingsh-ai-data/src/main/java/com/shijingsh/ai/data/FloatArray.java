package com.shijingsh.ai.data;

import java.util.ArrayList;

/**
 * 浮点型数组
 *
 * @author Birdy
 *
 */
public class FloatArray implements CapacityArray {

    /** 最大容量 */
    private int maximumCapacity;

    /** 最小容量 */
    private int minimumCapacity;

    private float[] current;

    private ArrayList<float[]> datas;

    /** 大小 */
    private int size;

    public FloatArray() {
        this(1000, 1000 * 1000 * 1000);
    }

    public FloatArray(int minimumCapacity, int maximumCapacity) {
        this.minimumCapacity = minimumCapacity;
        this.maximumCapacity = maximumCapacity;
        if (minimumCapacity == 0 && maximumCapacity == 0) {
            this.datas = new ArrayList<>(0);
        } else {
            this.datas = new ArrayList<>(maximumCapacity / minimumCapacity + (maximumCapacity % minimumCapacity == 0 ? 0 : 1));
        }
        this.size = 0;
    }

    public void associateData(float data) {
        int position = size++ % minimumCapacity;
        if (position == 0) {
            if (size > maximumCapacity) {
                current = null;
                throw new IllegalStateException();
            } else {
                current = new float[minimumCapacity];
                datas.add(current);
            }
        }
        current[position] = data;
    }

    public float getData(int cursor) {
        return datas.get(cursor / minimumCapacity)[cursor % minimumCapacity];
    }

    public void setData(int cursor, float data) {
        datas.get(cursor / minimumCapacity)[cursor % minimumCapacity] = data;
    }

    @Override
    public int getMaximumCapacity() {
        return maximumCapacity;
    }

    @Override
    public int getMinimumCapacity() {
        return minimumCapacity;
    }

    @Override
    public int getSize() {
        return size;
    }

}
