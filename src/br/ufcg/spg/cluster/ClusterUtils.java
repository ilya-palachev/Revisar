package br.ufcg.spg.cluster;

import br.ufcg.spg.database.ClusterDao;
import br.ufcg.spg.edit.Edit;
import br.ufcg.spg.transformation.TransformationUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Cluster utility class.
 */
public final class ClusterUtils {
  private ClusterUtils() {
  }
  
  /**
   * Builds cluster data set.
   */
  public static void buildClusters() {
    final ClusterUnifier unifierCluster = ClusterUnifier.getInstance();
    try {
      unifierCluster.clusters();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Builds cluster given a cluster id.
   */
  public static void buildClusters(String clusterId) {
    ClusterDao dao = ClusterDao.getInstance();
    List<Cluster> clusters = dao.getClusters(clusterId);
    List<Edit> srcEdits = clusters.get(0).getNodes();
    final ClusterUnifier unifierCluster = ClusterUnifier.getInstance();
    List<Cluster> srcClusters = unifierCluster.clusterEdits(srcEdits);
    TransformationUtils.transformations(srcClusters);
  }
  
  /**
   * Builds cluster given a cluster id.
   */
  public static List<Cluster> buildClustersSegmentByType(String clusterId) throws 
      BadLocationException, IOException, GitAPIException {
    ClusterDao dao = ClusterDao.getInstance();
    List<Cluster> clusters = dao.getClusters(clusterId);
    List<Edit> srcEdits = clusters.get(0).getNodes();
    final ClusterUnifier unifierCluster = ClusterUnifier.getInstance();
    final List<Cluster> clusterList = new ArrayList<>();
    List<Cluster> srcClusters = unifierCluster.clusterEdits(srcEdits);
    for (Cluster cluster : srcClusters) {
      List<Cluster> cls = segmentByType(cluster);
      clusterList.addAll(cls);
    }
    return clusterList;
  }

  public static List<Cluster> segmentByType(Cluster srcCluster) throws 
      BadLocationException, IOException, GitAPIException {
    final Map<String, Cluster> map = new Hashtable<>();
    for (final Edit edit : srcCluster.getNodes()) {
      final String refaster = TransformationUtils.createRefasterRule(srcCluster, edit);
      if (!map.containsKey(refaster)) {
        final String srcAu = srcCluster.getAu();
        final String srcLabel = srcCluster.getLabel();
        final Cluster dstCluster = srcCluster.getDst();
        final String dstAu = dstCluster.getAu();
        final String dstLabel = dstCluster.getLabel();
        final Cluster newCluster = new Cluster(srcAu, srcLabel);
        final Cluster newDstCluster = new Cluster(dstAu, dstLabel);
        newCluster.setDst(newDstCluster);
        map.put(refaster, newCluster);
      }
      final Cluster cluster = map.get(refaster);
      cluster.getNodes().add(edit);
      cluster.getDst().getNodes().add(edit.getDst());
    }
    return new ArrayList<>(map.values());
  }
}
