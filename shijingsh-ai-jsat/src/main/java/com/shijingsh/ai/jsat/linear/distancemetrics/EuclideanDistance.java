
package com.shijingsh.ai.jsat.linear.distancemetrics;

import java.util.List;

import com.shijingsh.ai.jsat.linear.IndexValue;
import com.shijingsh.ai.jsat.linear.Vec;
import com.shijingsh.ai.jsat.utils.concurrent.ParallelUtils;

import com.shijingsh.ai.jsat.linear.IndexValue;
import com.shijingsh.ai.jsat.linear.Vec;
import com.shijingsh.ai.jsat.utils.concurrent.ParallelUtils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

/**
 * Euclidean Distance is the L<sub>2</sub> norm.
 *
 * @author Edward Raff
 */
public class EuclideanDistance implements DenseSparseMetric {

    private static final long serialVersionUID = 8155062933851345574L;

    @Override
    public double dist(Vec a, Vec b) {
        return a.pNormDist(2, b);
    }

    @Override
    public boolean isSymmetric() {
        return true;
    }

    @Override
    public boolean isSubadditive() {
        return true;
    }

    @Override
    public boolean isIndiscemible() {
        return true;
    }

    @Override
    public double metricBound() {
        return Double.POSITIVE_INFINITY;
    }

    @Override
    public String toString() {
        return "Euclidean Distance";
    }

    @Override
    public EuclideanDistance clone() {
        return new EuclideanDistance();
    }

    @Override
    public double getVectorConstant(Vec vec) {
        /*
         * Returns the sum of squarred differences if the other vec had been all zeros.
         * That means this is one sqrt away from being the euclidean distance to the
         * zero vector.
         */
        return Math.pow(vec.pNorm(2), 2.0);
    }

    @Override
    public double dist(double summaryConst, Vec main, Vec target) {
        if (!target.isSparse())
            return dist(main, target);
        /**
         * Summary contains the squared differences to the zero vec, only a few of the
         * indices are actually non zero - we correct those values
         */
        double addBack = 0.0;
        double takeOut = 0.0;
        for (IndexValue iv : target) {
            int i = iv.getIndex();
            double mainVal = main.get(i);
            takeOut += Math.pow(main.get(i), 2);
            addBack += Math.pow(main.get(i) - iv.getValue(), 2.0);
        }
        return Math.sqrt(Math.max(summaryConst - takeOut + addBack, 0));// Max incase of numerical issues
    }

    @Override
    public boolean supportsAcceleration() {
        return true;
    }

    @Override
    public DoubleList getAccelerationCache(List<? extends Vec> vecs, boolean parallel) {
        // Store the pnorms in the cache
        double[] cache = new double[vecs.size()];
        ParallelUtils.run(parallel, vecs.size(), (start, end) -> {
            for (int i = start; i < end; i++) {
                Vec v = vecs.get(i);
                cache[i] = v.dot(v);
            }
        });
        return DoubleArrayList.wrap(cache, vecs.size());
    }

    @Override
    public double dist(int a, int b, List<? extends Vec> vecs, List<Double> cache) {
        if (cache == null)
            return dist(vecs.get(a), vecs.get(b));

        return Math.sqrt(Math.max(cache.get(a) + cache.get(b) - 2 * vecs.get(a).dot(vecs.get(b)), 0));// Max incase of numerical issues
    }

    @Override
    public double dist(int a, Vec b, List<? extends Vec> vecs, List<Double> cache) {
        if (cache == null)
            return dist(vecs.get(a), b);

        return Math.sqrt(Math.max(cache.get(a) + b.dot(b) - 2 * vecs.get(a).dot(b), 0));// Max incase of numerical issues
    }

    @Override
    public DoubleList getQueryInfo(Vec q) {
        DoubleArrayList qi = new DoubleArrayList(1);
        qi.add(q.dot(q));
        return qi;
    }

    @Override
    public double dist(int a, Vec b, List<Double> qi, List<? extends Vec> vecs, List<Double> cache) {
        if (cache == null)
            return dist(vecs.get(a), b);

        return Math.sqrt(Math.max(cache.get(a) + qi.get(0) - 2 * vecs.get(a).dot(b), 0));// Max incase of numerical issues
    }

}
