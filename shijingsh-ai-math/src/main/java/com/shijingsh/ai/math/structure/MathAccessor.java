package com.shijingsh.ai.math.structure;

/**
 * 数学访问器
 *
 * <pre>
 * 分别实现并行的累计(Accumulator) / 归一化(Normalizer) / 初始化(Initializer)
 * </pre>
 *
 * @author Birdy
 *
 */
public interface MathAccessor<T> {

    /**
     * 访问元素
     *
     * <pre>
     * 与{@link MathIterator#iterateElement(MathCalculator, MathAccessor...)}相关
     * </pre>
     *
     * @param element
     */
    void accessElement(T element);

}
