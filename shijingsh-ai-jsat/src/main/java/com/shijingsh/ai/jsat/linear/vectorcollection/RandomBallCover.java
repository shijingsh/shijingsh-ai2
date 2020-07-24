package com.shijingsh.ai.jsat.linear.vectorcollection;

import static java.lang.Math.ceil;
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.shijingsh.ai.jsat.linear.Vec;
import com.shijingsh.ai.jsat.linear.distancemetrics.DistanceMetric;
import com.shijingsh.ai.jsat.linear.distancemetrics.EuclideanDistance;
import com.shijingsh.ai.jsat.utils.BoundedSortedList;
import com.shijingsh.ai.jsat.utils.IndexTable;
import com.shijingsh.ai.jsat.utils.ListUtils;
import com.shijingsh.ai.jsat.utils.concurrent.ParallelUtils;

import com.shijingsh.ai.jsat.linear.Vec;
import com.shijingsh.ai.jsat.linear.distancemetrics.DistanceMetric;
import com.shijingsh.ai.jsat.linear.distancemetrics.EuclideanDistance;
import com.shijingsh.ai.jsat.utils.BoundedSortedList;
import com.shijingsh.ai.jsat.utils.IndexTable;
import com.shijingsh.ai.jsat.utils.ListUtils;
import com.shijingsh.ai.jsat.utils.concurrent.ParallelUtils;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * An implementation of the exact search for the Random Ball Cover algorithm.
 * Unlike most algorithms, it attempts to satisfy queries in <i>O(sqrt(n))</i>
 * time. It does this to be more efficient in its computation and easily
 * parallelizable. Construction time is <i>O(n<sup>3/2</sup>)</i>. <br>
 * Unlike the original paper, which assumes single queries will be run in
 * parallel, the algorithm has been modified to perform additional pruning and
 * to support range queries. <br>
 * <br>
 * See: Cayton, L. (2012). <i>Accelerating Nearest Neighbor Search on Manycore
 * Systems</i>. 2012 IEEE 26th International Parallel and Distributed Processing
 * Symposium, 402–413. doi:10.1109/IPDPS.2012.45
 *
 * @author Edward Raff
 */
public class RandomBallCover<V extends Vec> implements IncrementalCollection<V> {

    private static final long serialVersionUID = 2437771973228849200L;
    private DistanceMetric dm;
    /**
     * The indices match with their representatives in R
     */
    private List<IntArrayList> ownedVecs;
    /**
     * The indices match with their representatives in R and in ownedVecs. Each
     * value indicates the distance of the point to its owner. They are not in any
     * order
     */
    private List<DoubleArrayList> ownedRDists;
    /**
     * The list of representatives
     */
    private IntList R;
    private int size;
    private List<V> allVecs;
    private DoubleList distCache;

    /**
     * Distance from representative i to its farthest neighbor it owns
     */
    private double[] repRadius;

    /**
     * Creates a new Random Ball Cover
     *
     * @param vecs     the vectors to place into the RBC
     * @param dm       the distance metric to use
     * @param parallel {@code true} if construction should be done in parallel,
     *                 {@code false} for single threaded.
     */
    public RandomBallCover(List<V> vecs, DistanceMetric dm, boolean parallel) {
        this.dm = dm;
        build(parallel, vecs, dm);
    }

    /**
     * Creates a new Random Ball Cover
     *
     * @param vecs the vectors to place into the RBC
     * @param dm   the distance metric to use
     */
    public RandomBallCover(List<V> vecs, DistanceMetric dm) {
        this(vecs, dm, false);
    }

    public RandomBallCover(DistanceMetric dm) {
        this.dm = dm;
        this.size = 0;
        this.allVecs = new ArrayList<>();
        if (dm.supportsAcceleration())
            this.distCache = new DoubleArrayList();
        this.R = new IntArrayList();
    }

    public RandomBallCover() {
        this(new EuclideanDistance());
    }

    /**
     * Copy constructor
     *
     * @param other the RandomBallCover to create a copy of
     */
    private RandomBallCover(RandomBallCover<V> other) {
        this.dm = other.dm.clone();
        this.size = other.size;
        if (other.allVecs != null)
            this.allVecs = new ArrayList<>(other.allVecs);
        if (other.distCache != null)
            this.distCache = new DoubleArrayList(other.distCache);
        if (other.ownedVecs != null)
            this.ownedVecs = new ArrayList<>(other.ownedVecs.size());
        if (other.ownedRDists != null)
            this.ownedRDists = new ArrayList<>(other.ownedRDists.size());
        if (other.ownedRDists != null)
            for (int i = 0; i < other.ownedRDists.size(); i++) {
                this.ownedRDists.add(new DoubleArrayList(other.ownedRDists.get(i)));
                this.ownedVecs.add(new IntArrayList(other.ownedVecs.get(i)));
            }
        this.R = new IntArrayList(other.R);
        if (other.repRadius != null)
            this.repRadius = Arrays.copyOf(other.repRadius, other.repRadius.length);
    }

    @Override
    public void build(boolean parallel, List<V> collection, DistanceMetric dm) {
        setDistanceMetric(dm);
        this.size = collection.size();
        this.allVecs = new ArrayList<>(collection);
        this.distCache = dm.getAccelerationCache(allVecs, parallel);
        IntArrayList allIndices = new IntArrayList(allVecs.size());
        ListUtils.addRange(allIndices, 0, size, 1);
        setUp(allIndices, parallel);
    }

    @Override
    public DoubleList getAccelerationCache() {
        return distCache;
    }

    private void setUp(List<Integer> vecIndices, boolean parallel) {
        int repCount = (int) Math.max(1, Math.sqrt(vecIndices.size()));
        Collections.shuffle(vecIndices);

        R = new IntArrayList(vecIndices.subList(0, repCount));
        repRadius = new double[R.size()];
        ownedRDists = new ArrayList<>(repRadius.length);
        IntArrayList vecIndicesSub = new IntArrayList(vecIndices.subList(repCount, vecIndices.size()));
        ownedVecs = new ArrayList<>(repCount);

        for (int i = 0; i < repCount; i++) {
            ownedVecs.add(new IntArrayList(repCount));
            ownedRDists.add(new DoubleArrayList(repCount));
        }

        ParallelUtils.run(parallel, vecIndicesSub.size(), (start, end) -> {
            double tmp;
            for (int v : vecIndicesSub.subList(start, end)) {
                int bestRep = 0;
                double bestDist = dm.dist(v, R.get(0), allVecs, distCache);
                for (int potentialRep = 1; potentialRep < R.size(); potentialRep++)
                    if ((tmp = dm.dist(v, R.get(potentialRep), allVecs, distCache)) < bestDist) {
                        bestDist = tmp;
                        bestRep = potentialRep;
                    }

                synchronized (ownedVecs.get(bestRep)) {
                    ownedVecs.get(bestRep).add(v);
                    ownedRDists.get(bestRep).add(bestDist);
                    repRadius[bestRep] = Math.max(repRadius[bestRep], bestDist);
                }
            }
        });
    }

    @Override
    public void search(Vec query, double range, IntList neighbors, DoubleList distances) {
        neighbors.clear();
        distances.clear();

        List<Double> qi = dm.getQueryInfo(query);

        if (repRadius == null)// brute force search b/c small collection
        {
            for (int i = 0; i < allVecs.size(); i++) {
                double dist = dm.dist(i, query, qi, allVecs, distCache);
                if (dist <= range) {
                    distances.add(dist);
                    neighbors.add(i);
                }
            }

            return;
        }

        // Find the best representative r_q, and add its owned children to knn list.
        double[] queryRDists = new double[R.size()];

        for (int i = 0; i < R.size(); i++)
            if ((queryRDists[i] = dm.dist(R.get(i), query, qi, allVecs, distCache)) <= range) {
                neighbors.add(R.get(i));
                distances.add(queryRDists[i]);
            }

        IndexTable sorted = new IndexTable(queryRDists);
        // k-nn search through the rest of the data set
        for (int i_indx = 0; i_indx < R.size(); i_indx++) {
            int i = sorted.index(i_indx);
            // Prune our representatives that are just too far
            if (queryRDists[i] > range + repRadius[i])
                continue;

            // Add any new nn imediatly, hopefully shrinking the bound before
            // the next representative is tested
            double dist;
            for (int j = 0; j < ownedVecs.get(i).size(); j++) {
                double rDist = ownedRDists.get(i).getDouble(j);
                if (queryRDists[i] > range + rDist)// first inqueality on a per point basis
                    continue;
                if ((dist = dm.dist(ownedVecs.get(i).get(j), query, qi, allVecs, distCache)) <= range) {
                    neighbors.add(ownedVecs.get(i).get(j));
                    distances.add(dist);
                }
            }
        }

        IndexTable it = new IndexTable(distances);
        it.apply(neighbors);
        it.apply(distances);
    }

    @Override
    public void search(Vec query, int numNeighbors, IntList neighbors, DoubleList distances) {
        BoundedSortedList<IndexDistPair> knn = new BoundedSortedList<>(numNeighbors);

        neighbors.clear();
        distances.clear();

        List<Double> qi = dm.getQueryInfo(query);

        if (repRadius == null)// brute force search b/c small collection
        {
            for (int i = 0; i < allVecs.size(); i++)
                knn.add(new IndexDistPair(i, dm.dist(i, query, qi, allVecs, distCache)));
        } else {
            // Find the best representative r_q, and add its owned children to knn list.
            double[] queryRDists = new double[R.size()];
            Arrays.fill(queryRDists, Double.MAX_VALUE);
            int bestRep = 0;
            for (int i = 0; i < R.size(); i++)
                if ((queryRDists[i] = dm.dist(R.get(i), query, qi, allVecs, distCache)) < queryRDists[bestRep])
                    bestRep = i;
            // Other cluster reps R will get a chance to be added to the list later
            knn.add(new IndexDistPair(R.get(bestRep), queryRDists[bestRep]));

            // need k'th nearest representative R for bounds check
            IndexTable it = new IndexTable(queryRDists);
            int kth_best_rept;
            if (numNeighbors < R.size())// need the k'th closest, but if less than K we can't sure that bound
                kth_best_rept = it.index(numNeighbors - 1);
            else// You are asking for too many neighbors, we can't use the 2nd bound
                kth_best_rept = -1;// if somone uses this we will get an IndexOutOfBound, telling us about the bug!

            for (int v : ownedVecs.get(bestRep))
                knn.add(new IndexDistPair(v, dm.dist(v, query, qi, allVecs, distCache)));

            // k-nn search through the rest of the data set
            for (int sorted_order = 1; sorted_order < R.size(); sorted_order++) {// start at 1 b/c we brute forced the closest rep first
                final int i = it.index(sorted_order);

                if (knn.size() == numNeighbors)// no prunnig until we reach k-nns
                {
                    // Prune out representatives that are just too far
                    if (queryRDists[i] > knn.last().getDist() + repRadius[i])
                        continue;
                    // check to make sure we can use this bound before attempting
                    else if (kth_best_rept >= 0 && queryRDists[i] > 3 * queryRDists[kth_best_rept])
                        continue;
                }

                // Add any new nn imediatly, hopefully shrinking the bound before
                // the next representative is tested
                knn.add(new IndexDistPair(R.get(i), queryRDists[i]));
                final List<Integer> L_i_index = ownedVecs.get(i);
                final DoubleArrayList L_i_radius = ownedRDists.get(i);
                for (int j = 0; j < ownedVecs.get(i).size(); j++) {
                    double rDist = L_i_radius.getDouble(j);
                    // Check the first inequality on a per point basis
                    if (knn.size() == numNeighbors && queryRDists[i] > knn.last().getDist() + rDist)
                        continue;
                    int indx = L_i_index.get(j);
                    V v = allVecs.get(indx);

                    knn.add(new IndexDistPair(indx, dm.dist(indx, query, qi, allVecs, distCache)));
                }
            }
        }

        for (IndexDistPair v : knn) {
            neighbors.add(v.getIndex());
            distances.add(v.getDist());
        }
    }

    @Override
    public void insert(V x) {
        final int new_indx = allVecs.size();
        allVecs.add(x);
        List<Double> qi = dm.getQueryInfo(x);
        if (distCache != null)
            distCache.addAll(qi);
        size++;
        if (size < 10)// brute force for now
        {
            R.add(new_indx);// use R for brute force
            return;
        } else if (repRadius == null)// initial normal build
        {
            R.add(new_indx);
            setUp(new IntArrayList(R), false);
            return;
        }
        // else, normal addition

        // Find the best representative
        double[] queryRDists = new double[R.size()];
        Arrays.fill(queryRDists, Double.MAX_VALUE);
        int bestRep = 0;
        for (int i = 0; i < R.size(); i++)
            if ((queryRDists[i] = dm.dist(R.get(i), x, qi, allVecs, distCache)) < queryRDists[bestRep])
                bestRep = i;
        // Add new point and update information
        ownedVecs.get(bestRep).add(new_indx);
        ownedRDists.get(bestRep).add(queryRDists[bestRep]);
        repRadius[bestRep] = Math.max(repRadius[bestRep], queryRDists[bestRep]);

        if (pow(ceil(sqrt(size)), 2) != size)
            return;// we are done
        // else, expand R set
        int new_r_vec_indx = -1;
        {// lets randomly sample a point that isn't a rep
            int ran_val = new Random().nextInt(size - R.size() - 1);
            int R_pos = 0;
            while (ran_val >= 0) {
                if (ran_val >= ownedVecs.get(R_pos).size())
                    ran_val -= ownedVecs.get(R_pos++).size();
                else// found the list to grab from
                {
                    new_r_vec_indx = ownedVecs.get(R_pos).remove(ran_val);
                    ownedRDists.get(R_pos).removeDouble(ran_val);
                    // update radius
                    repRadius[R_pos] = 0;
                    for (double d : ownedRDists.get(R_pos))
                        repRadius[R_pos] = Math.max(repRadius[R_pos], d);
                    // stop loop
                    break;

                }
            }

        }
        // We now have a new rep, we need to find the people it will own
        double max_radius = 0;
        for (double d : repRadius)
            max_radius = Math.max(max_radius, d);
        IntArrayList potentialChildIndx = new IntArrayList();
        DoubleArrayList potentialChildDist = new DoubleArrayList();
        search(get(new_r_vec_indx), max_radius, potentialChildIndx, potentialChildDist);

        // add new R to set after to avoid search issues
        repRadius = Arrays.copyOf(repRadius, repRadius.length + 1);
        R.add(new_r_vec_indx);
        ownedRDists.add(new DoubleArrayList());
        ownedVecs.add(new IntArrayList());
        final int r_new = R.size() - 1;

        /*
         * Existing structure of RBC bookkeping dosn't lend itself to the insertion
         * case. B/c the R set expansion is rare, we don't modify that internal
         * structure. Instead, we will create a new temporary strucutre to store things
         * based on the index of the data point. This gives us easy direct indexing
         * ability. We then fix-up the RBC structure at the end.
         */
        int[] whoOwnsMe = new int[allVecs.size()];
        Arrays.fill(whoOwnsMe, -1);
        double[] distToMyOwner = new double[allVecs.size()];
        for (int i = 0; i < R.size() - 1; i++)// technicaly this is O(n), but its really fast - so who cares
        {
            List<Integer> L_ry = ownedVecs.get(i);
            for (int j = 0; j < L_ry.size(); j++) {
                whoOwnsMe[L_ry.get(j)] = i;
                distToMyOwner[L_ry.get(j)] = ownedRDists.get(i).getDouble(j);
            }
        }
        boolean[] R_is_dirty = new boolean[R.size()];
        Arrays.fill(R_is_dirty, false);
        R_is_dirty[r_new] = true;

        for (int i = 0; i < potentialChildIndx.size(); i++) {
            double d_y_r_new = potentialChildDist.getDouble(i);
            int y_indx = potentialChildIndx.getInt(i);
            // find who owns y_indx
            int r_y = whoOwnsMe[y_indx];
            if (r_y == -1)// Represantative, skip
                continue;

            double d_y_ry = distToMyOwner[y_indx];

            if (d_y_ry > d_y_r_new)// change ownership
            {
                R_is_dirty[r_y] = true;
                whoOwnsMe[y_indx] = r_new;
                distToMyOwner[y_indx] = d_y_r_new;
            }
        }
        // update representative radi
        for (int r_indx = 0; r_indx < R.size(); r_indx++)
            if (R_is_dirty[r_indx])// clear vecs so we can re-populate
            {
                repRadius[r_indx] = 0;
                ownedRDists.get(r_indx).clear();
                ownedVecs.get(r_indx).clear();
            }
        for (int i = 0; i < whoOwnsMe.length; i++) {
            int r_i = whoOwnsMe[i];
            if (r_i == -1)// Represantative, skip
                continue;
            if (R_is_dirty[r_i]) {
                repRadius[r_i] = Math.max(repRadius[r_i], distToMyOwner[i]);
                ownedRDists.get(r_i).add(distToMyOwner[i]);
                ownedVecs.get(r_i).add(i);
            }
        }
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public V get(int indx) {
        return allVecs.get(indx);
    }

    @Override
    public RandomBallCover<V> clone() {
        return new RandomBallCover<>(this);
    }

    @Override
    public void setDistanceMetric(DistanceMetric dm) {
        this.dm = dm;
    }

    @Override
    public DistanceMetric getDistanceMetric() {
        return dm;
    }
}
