
package com.shijingsh.ai.jsat.linear.distancemetrics;

import java.util.List;

import com.shijingsh.ai.jsat.linear.SparseVector;
import com.shijingsh.ai.jsat.linear.Vec;
import com.shijingsh.ai.jsat.utils.concurrent.ParallelUtils;

import com.shijingsh.ai.jsat.linear.SparseVector;
import com.shijingsh.ai.jsat.linear.Vec;
import com.shijingsh.ai.jsat.utils.concurrent.ParallelUtils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;

/**
 * In many applications, the squared {@link EuclideanDistance} is used because
 * it avoids an expensive {@link Math#sqrt(double) } operation. However, the
 * Squared Euclidean Distance is not a truly valid metric, as it does not obey
 * the {@link #isSubadditive() triangle inequality}.
 *
 * @author Edward Raff
 */
public class SquaredEuclideanDistance implements DistanceMetric {

    private static final long serialVersionUID = 2966818558802484702L;

    @Override
    public double dist(Vec a, Vec b) {
        if (a.length() != b.length())
            throw new ArithmeticException("Length miss match, vectors must have the same length");
        double d = 0;

        if (a instanceof SparseVector && b instanceof SparseVector) {
            // Just square the pNorm for now... not easy code to write, and the sparceness
            // is more important
            return Math.pow(a.pNormDist(2, b), 2);
        } else {
            double tmp;
            for (int i = 0; i < a.length(); i++) {
                tmp = a.get(i) - b.get(i);
                d += tmp * tmp;
            }
        }

        return d;
    }

    @Override
    public boolean isSymmetric() {
        return true;
    }

    @Override
    public boolean isSubadditive() {
        return false;
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
        return "Squared Euclidean Distance";
    }

    @Override
    public SquaredEuclideanDistance clone() {
        return new SquaredEuclideanDistance();
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

        return (cache.get(a) + cache.get(b) - 2 * vecs.get(a).dot(vecs.get(b)));
    }

    @Override
    public double dist(int a, Vec b, List<? extends Vec> vecs, List<Double> cache) {
        if (cache == null)
            return dist(vecs.get(a), b);

        return (cache.get(a) + b.dot(b) - 2 * vecs.get(a).dot(b));
    }

    @Override
    public DoubleArrayList getQueryInfo(Vec q) {
        DoubleArrayList qi = new DoubleArrayList(1);
        qi.add(q.dot(q));
        return qi;
    }

    @Override
    public double dist(int a, Vec b, List<Double> qi, List<? extends Vec> vecs, List<Double> cache) {
        if (cache == null)
            return dist(vecs.get(a), b);

        return (cache.get(a) + qi.get(0) - 2 * vecs.get(a).dot(b));
    }
}
