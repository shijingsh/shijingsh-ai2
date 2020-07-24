package com.shijingsh.ai.math.algorithm.lsh;

import java.util.Random;

import com.shijingsh.ai.math.algorithm.correlation.distance.ManhattanDistance;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.ai.math.algorithm.correlation.distance.ManhattanDistance;
import com.shijingsh.ai.math.structure.vector.MathVector;

public class ManhattanHashFamily implements LshHashFamily {

    private static final ManhattanDistance distance = new ManhattanDistance();

    private int dimensions;

    private int w;

    public ManhattanHashFamily(int w, int dimensions) {
        this.dimensions = dimensions;
        this.w = w;
    }

    @Override
    public VectorHashFunction getHashFunction(Random random) {
        return new ManhattanHashFunction(random, dimensions, w);
    }

    @Override
    public float getCoefficient(MathVector leftVector, MathVector rightVector) {
        return distance.getCoefficient(leftVector, rightVector);
    }

}
