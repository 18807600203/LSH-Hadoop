/**
 * 
 */
package org.apache.mahout.cf.taste.neighborhood;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import lsh.core.Hasher;

import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.common.LongPrimitiveIterator;
import org.apache.mahout.common.distance.DistanceMeasure;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;

/**
 * @author lance
 *
 * Store LSH hash set of corners and users.
 * Corner includes both hash vector and LOD
 * 
 * Does not know of User or Item
 * 
 * LOD = Level Of Detail
 *     Largest LOD = 0 Simplex
 *     LOD = 1 means N dimensional simplices, # = #*N simplices
 *     Successive LOD = number of neighbors collapsed in power of 2
 *     Gridsize = original gridsize/LOD
 * TODO: could be made tighter.
 */
public class SimplexSpaceLOD {
  final Hasher hasher;
  FastByIDMap<Hash> idSetMap = new FastByIDMap<Hash>();
  Map<Hash, Set<Long>> hashSetMap = new HashMap<Hash, Set<Long>>();
  final int dimensions;
  public double distance = 0.0001;
  private final DistanceMeasure measure;

  public SimplexSpaceLOD(Hasher hasher, int dimensions) {
    this.hasher = hasher;
    this.dimensions = dimensions;
    this.measure = null;
  }

  public SimplexSpaceLOD(Hasher hasher, int dimensions, DistanceMeasure measure) {
    this.hasher = hasher;
    this.dimensions = dimensions;
    this.measure = measure;
  }

  // populate hashes
  public void addVector(Long userID, Vector v) {
    double[] values = new double[dimensions];
    getValues(v, values);
    Hash hash = new Hash(hasher.hash(values));
    idSetMap.put(userID, hash);
    Set<Long> hashLongs = hashSetMap.get(hash);
    if (null == hashLongs) {
      hashLongs = new HashSet<Long>();
      hashSetMap.put(hash, hashLongs);
    }
    hashLongs.add(userID);
  }

  private void getValues(Vector v, double[] values) {
    Iterator<Element> el = v.iterateNonZero();
    while(el.hasNext()) {
      Element e = el.next();
      values[e.index()] = e.get();
    }
  }    

  /*
   * Search for neighbors of given ID.
   *    expand - expand search N counts outward
   */

  public long[] findNeighbors(long id, int expand) {
    Hash central = idSetMap.get(id);
    if (null == central)
      return null;
    FastIDSet others = new FastIDSet();
    Hash cloned = (Hash) central.clone();
    int found = 0;
    for (int i = 0; i < dimensions; i++) {
      int last = found;
      cloned.hashes[i] = central.hashes[i] + 1;
      Set<Long> hashLongs = hashSetMap.get(cloned);
      if (null != hashLongs)
        for(Long otherID: hashLongs) {
          if (otherID != id)
            others.add(otherID);
        }
      cloned.hashes[i] = central.hashes[i] - 1;
      hashLongs = hashSetMap.get(cloned);
      if (null != hashLongs)
        for(Long otherID: hashLongs) {
          if (otherID != id)
            others.add(otherID);
        }     
      found = others.size();
      if (found > last) {
        System.out.println(i + "," + (found - last));
      }
    }
    long[] values = new long[others.size()];
    LongPrimitiveIterator lpi = others.iterator();
    for(int i = 0; i < others.size(); i++) {
      values[i] = lpi.nextLong();
    }
    return values;
  }

  //  public long[] findNeighbors(long id, int expand) {
  //    Hash hash = idSetMap.get(id);
  //    if (null == hash)
  //      return null;
  //    FastIDSet others = new FastIDSet();
  //    Set<Long> hashLongs = hashSetMap.get(hash);
  //    for(Long otherID: hashLongs) {
  //      if (otherID != id)
  //        others.add(otherID);
  //    }
  //    long[] values = new long[others.size()];
  //    LongPrimitiveIterator lpi = others.iterator();
  //    for(int i = 0; i < others.size(); i++) {
  //      values[i] = lpi.nextLong();
  //    }
  //    return values;
  //  }

  public double getDistance(long id1, long id2, DistanceMeasure measure) {
    if (null == measure)
      measure = this.measure;
    Hash h1 = idSetMap.get(id1);
    Hash h2 = idSetMap.get(id2);
    if (null == h1 || null == h2)
      return -1;

    double d = hashDistance(h1, h2, measure);
    return d;
  }

  public double getDistance(long id1, long id2, SimplexSpaceLOD otherSpace, DistanceMeasure measure) {
    if (null == measure)
      measure = this.measure;
    Hash h1 = idSetMap.get(id1);
    Hash h2 = otherSpace.idSetMap.get(id2);
    if (null == h1 || null == h2) {
      return -1;
    }

    double d = hashDistance(h1, h2, measure);
    if (d != 0.0)
      System.out.println(d);
    return d;
  }

  private double hashDistance(Hash h1, Hash h2, DistanceMeasure measure) {
    if (! h1.equals(h2)) 
      System.out.println(h1 + "," + h2);
    double[] d1 = new double[dimensions];
    double[] d2 = new double[dimensions];
    hasher.unhash(h1.hashes, d1);
    hasher.unhash(h2.hashes, d2);
    //     for(int i = 0; i < h1.hashes.length; i++) {
    //       d1[i] = h1.hashes[i];
    //       d2[i] = h2.hashes[i];
    //     }
    Vector v1 = new DenseVector(d1);
    Vector v2 = new DenseVector(d2);
    double distance = measure.distance(v1, v2);
    double x = v1.getDistanceSquared(v2);
    double y = distance - Math.sqrt(x);
    if (Math.abs(y) > 0.000001)
      System.out.println(y);
    return distance;
  }

  public int getDimensions() {
    return dimensions;
  }

  @Override
  public String toString() {
    String x = "";
    if (null != idSetMap) {
      x += "ID{";
      LongPrimitiveIterator lpi = idSetMap.keySetIterator();
      while (lpi.hasNext()) {
        long id = lpi.nextLong();
        Hash h = idSetMap.get(id);
        Set<Long> ids = hashSetMap.get(h);
        x += ids.size() + ",";
      }
      x += "}";
    }
    if (null != hashSetMap) {
      x += "HASH{";
      for(Hash h: hashSetMap.keySet()) {
        Set<Long> hs = hashSetMap.get(h);
        if (null == hs)
          x += "0,";
        else
          x += hs.size() + ",";
      }
      x += "}";
    }
    return x;
  }
}
