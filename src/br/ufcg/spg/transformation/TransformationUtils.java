package br.ufcg.spg.transformation;

import at.unisalzburg.dbresearch.apted.node.StringNodeData;
import br.ufcg.spg.bean.Tuple;
import br.ufcg.spg.cluster.Cluster;
import br.ufcg.spg.cluster.ClusterFormatter;
import br.ufcg.spg.cluster.ClusterUnifier;
import br.ufcg.spg.cluster.ClusterUtils;
import br.ufcg.spg.config.TechniqueConfig;
import br.ufcg.spg.database.ClusterDao;
import br.ufcg.spg.database.TransformationDao;
import br.ufcg.spg.edit.Edit;
import br.ufcg.spg.excel.PoiExcelWriter;
import br.ufcg.spg.excel.QuickFix;
import br.ufcg.spg.excel.QuickFixManager;
import br.ufcg.spg.exp.ExpUtils;
import br.ufcg.spg.filter.FilterManager;
import br.ufcg.spg.git.GitUtils;
import br.ufcg.spg.lsh.ConvertScriptToVector;
import br.ufcg.spg.lsh.ScriptLSHMinHash;
import br.ufcg.spg.ml.clustering.EditScriptUtils;
import br.ufcg.spg.ml.editoperation.Script;
import br.ufcg.spg.ml.metric.ScriptDistanceMetric;
import br.ufcg.spg.refaster.RefasterTranslator;
import br.ufcg.spg.validator.ClusterValidator;
import de.jail.geometry.schemas.Point;
import de.jail.statistic.clustering.density.DBScan;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * Utility class to perform transformations.
 */
public final class TransformationUtils {
  
  /**
   * Learned scripts.
   */
  public static List<Point> scripts = new ArrayList<>();
  
  private static List<Script<StringNodeData>> noise = new ArrayList<>();
  
  /**
   * Logger.
   */
  public static final Logger logger = LogManager.getLogger(TransformationUtils.class.getName());
  
  /**
   * Field only for test purpose.
   */
  private static int clusterIndex = 1;
  
  public static int getClusterIndex() {
    return clusterIndex;
  }

  public static int incrementClusterIndex() {
	return ++clusterIndex;
  }

  private TransformationUtils() {
  }
  
  /**
   * Computes the matches for all clusters.
   */
  public static void transformations() {
    final TransformationDao dao = TransformationDao.getInstance();
    final Long clusterId = dao.getLastClusterId();
    final List<Cluster> srcClusters = ClusterDao.getInstance().getSrcClusters();
    final List<Cluster> remainingClusters = new ArrayList<>();
    if (clusterId == -1) {
      transformations(srcClusters);
    } else {
      boolean include = false;
      for (final Cluster cluster : srcClusters) {
        if (include) {
          remainingClusters.add(cluster);
        }
        if (cluster.getId().equals(clusterId)) {
          include = true;
        }
      }
      transformations(remainingClusters);
    }
  }
    
  /**
   * Computes the template for some cluster.
   * @param clusterId label of the cluster
   */
  public static void transformations(final String clusterId) {
    final ClusterDao dao = ClusterDao.getInstance();
    final List<Cluster> clusters = dao.getClusters(clusterId);
    transformations(clusters);
  }
  
  /**
   * Computes transformations for a set of clusters.
   */
  public static void transformations(final List<Cluster> srcClusters) {
    try {
      String folderPath = "../Projects/cluster/";
      transformations(folderPath, srcClusters);
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Computes transformations for a set of clusters.
   */
  public static void transformations(final String folderPath,final List<Cluster> srcClusters) {
    try {
      for (int i = 0; i < srcClusters.size(); i++) {
        logger.trace(((double) i) / srcClusters.size() + " % completed.");
        final Cluster clusteri = srcClusters.get(i);
        /*if (!clusteri.getNodes().get(0).getText().contains(
            "new ArrayList<Task>(children.size())")) {
          continue;
        }*/
        // Analyze clusters with two or more elements.
        if (clusteri.getNodes().size() < 2) {
          continue;
        }
        Transformation transformation = tranformation(clusteri);
        //save the transformation, for debug purpose let's comment it for now.
        //TransformationDao.getInstance().save(transformation);
        Edit edit = clusteri.getNodes().get(0);
        clusterIndex = i;
        saveTransformation(folderPath, transformation, edit);
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Computes the template for some cluster.
   */
  public static void transformationsLargestClusters() {
    final List<Cluster> clusters = ClusterDao.getInstance().getLargestClusters();
    transformations(clusters);
  }
  
  /**
   * Computes the template for some cluster.
   */
  @SuppressWarnings("unchecked")
public static void transformationsMoreProjects(List<Cluster> clusters) {
    clusters = rebuildClusters(clusters);
    transformations(clusters);
    clusterIndex = 1;
    ScriptDistanceMetric<StringNodeData> metric = 
        new ScriptDistanceMetric<>();
    DBScan dbscan = new DBScan(0.51, 1, metric);
    List<de.jail.statistic.clustering.Cluster> clusteres = dbscan.cluster(scripts);
    analyzeCommitMessages();
    analyzeCommitMessages2();
    boolean [][] dataset = ConvertScriptToVector.vector(scripts);
    /*List<List<Integer>> scriptClusters = ScriptLSHMinHash.Lsh(dataset);
    int i = 0;
    for (List<Integer> clusterInts : scriptClusters) {
    	List<Script<StringNodeData>> points = new ArrayList<>();
    	for (int index : clusterInts) {
    		points.add((Script<StringNodeData>) scripts.get(index));
    	}
    	ClusterUtils.saveClusterToFile(i, "lsh/"+ i++ + "/", points, new ArrayList<>());
    }*/
    int countCluster = 0;
    List<Script<StringNodeData>> clusteredScriptsList = new ArrayList<>();
    for (de.jail.statistic.clustering.Cluster list : clusteres) {
      List<Script<StringNodeData>> ls = new ArrayList<>();
      for (Point p : list.getAllPoints()) {
        Script<StringNodeData> sc = (Script<StringNodeData>) p;
        ls.add(sc);
      }
      clusteredScriptsList.addAll(ls);
      List<QuickFix> potentials = QuickFixManager
          .getInstance().getPotentialPatterns();
      ClusterUtils.saveClusterToFile(++countCluster, "", ls, potentials);
    }
    if (!noise.isEmpty()) {
      List<QuickFix> bads = QuickFixManager.getInstance().getBadPatterns();
      ClusterUtils.saveClusterToFile(++countCluster, "noise/", noise, bads);
    }
    TransformationUtils.saveSingleClusters(countCluster, clusteredScriptsList);
    try {
      QuickFixManager qfm = QuickFixManager.getInstance();
      PoiExcelWriter.save("../Projects/cluster/data_bad.xls", "Bad", qfm.getBadPatterns());
      PoiExcelWriter.save("../Projects/cluster/data_good.xls", "Good", qfm.getPotentialPatterns());
      ClusterUtils.saveSingleQuickFixes("potential/", qfm.getPotentialPatterns());
    } catch (IOException e) {
      e.printStackTrace();
    }
 }

 private static void analyzeCommitMessages() {
   List<Cluster> nonFiltered = getClustersScript(noise);
   List<Edit> edits = ClusterUtils.getAllEdits(nonFiltered);
   List<String> filtered;
   try {
     filtered = (new GitUtils()).getCommitMessagesLog(edits);
     String folderPath = "../Projects/cluster/";
	 ExpUtils.save(filtered, folderPath + "commit_messages.txt");
	 Map<String, Integer> words = new HashMap<>();
	 for (String str : filtered) {
		 String list [] = str.split("[ ]+");
		 for (String word : list) { 
			 if (!word.matches("\\b[a-zA-Z]+\\b")) {
				 continue;
			 }
			 word = word.toLowerCase();
			 if (!words.containsKey(word)) {
				 words.put(word, 0);
			 }
			 words.put(word, words.get(word) + 1);
		 }
		 
	 }
	 removeStopWords(words);
	 //PoiExcelWriter.save(folderPath + "../Projects/cluster/freq_noise.xls", "Noise", words);
	 ExpUtils.save(words, folderPath + "words_freq_noise.csv");
	 /*Map<String, List<String>> wordFreq = new HashMap<>();
	 for (String str : filtered) {
		 String list [] = str.split("[ ]+");
		 for (String word : list) {
			 if (!words.containsKey(word)) {
				 words.put(word, 0);
			 }
			 words.put(word, words.get(word) + 1);
		 }
		 
	 }*/
   } catch (Exception e) {
	 e.printStackTrace();
   }
  }
 
 private static void analyzeCommitMessages2() {
	   List<Cluster> nonFiltered = getClusters(scripts);
	   List<Edit> edits = ClusterUtils.getAllEdits(nonFiltered);
	   List<String> filtered;
	   try {
	     filtered = (new GitUtils()).getCommitMessagesLog(edits);
	     String folderPath = "../Projects/cluster/";
		 ExpUtils.save(filtered, folderPath + "commit_messages.txt");
		 Map<String, Integer> words = new HashMap<>();
		 for (String str : filtered) {
			 String list [] = str.split("[ ]+");
			 for (String word : list) { 
				 if (!word.matches("\\b[a-zA-Z]+\\b")) {
					 continue;
				 }
				 word = word.toLowerCase();
				 if (!words.containsKey(word)) {
					 words.put(word, 0);
				 }
				 words.put(word, words.get(word) + 1);
			 }
			 
		 }
		 removeStopWords(words);
		 //PoiExcelWriter.save("../Projects/cluster/freq_potential.xls", "Potential", words);
		 ExpUtils.save(words, folderPath + "words_freq_potential.csv");
		 /*Map<String, List<String>> wordFreq = new HashMap<>();
		 for (String str : filtered) {
			 String list [] = str.split("[ ]+");
			 for (String word : list) {
				 if (!words.containsKey(word)) {
					 words.put(word, 0);
				 }
				 words.put(word, words.get(word) + 1);
			 }
			 
		 }*/
	   } catch (Exception e) {
		 e.printStackTrace();
	   }
	  }
 
 
 public static void removeStopWords(Map<String, Integer> map) {
	 String stop[] = "a,able,about,across,after,all,almost,also,am,among,an,and,any,are,as,at,be,because,been,but,by,can,cannot,could,dear,did,do,does,either,else,ever,every,for,from,get,got,had,has,have,he,her,hers,him,his,how,however,i,if,in,into,is,it,its,just,least,let,like,likely,may,me,might,most,must,my,neither,no,nor,not,of,off,often,on,only,or,other,our,own,rather,said,say,says,she,should,since,so,some,than,that,the,their,them,then,there,these,they,this,tis,to,too,twas,us,wants,was,we,were,what,when,where,which,while,who,whom,why,will,with,would,yet,you,your".split(",");
     for (String s : stop) {
    	 if (map.containsKey(s)) {
    		 map.remove(s);
    	 }
     }
 }
  
  private static List<Cluster> getClustersScript(List<Script<StringNodeData>> scripts) {
	List<Cluster> clusters = new ArrayList<>();
    for (Script<StringNodeData> script : scripts) {
    	Cluster cluster = script.getCluster();
    	clusters.add(cluster);
    }
	return clusters;
  }
  
  private static List<Cluster> getClusters(List<Point> scripts) {
		List<Cluster> clusters = new ArrayList<>();
	    for (Point point : scripts) {
	    	@SuppressWarnings("unchecked")
			Script<StringNodeData> script = (Script<StringNodeData>) point;
	    	Cluster cluster = script.getCluster();
	    	clusters.add(cluster);
	    }
		return clusters;
	  }

  public static void print(boolean[] array) {
	    System.out.print("[");
	    for (boolean v : array) {
	      System.out.print(v ? "1" : "0");
	    }
	    System.out.print("]");
	  }

  private static List<Cluster> rebuildClusters(List<Cluster> clusters) {
    List<Cluster> clustersList = new ArrayList<>();
    for (Cluster cluster : clusters) {
      Tuple<Cluster, Cluster> clt = ClusterUnifier.getInstance().cluster(
          cluster.getNodes(), cluster.getId() + "");
      clustersList.add(clt.getItem1());
    }
    return clustersList;
  }

  /**
   * Learns a transformation for a cluster.
   */
  public static Transformation tranformation(final Cluster srcCluster) {
    try {     
      final boolean isValid = ClusterValidator.isValidTrans(srcCluster);
      final Transformation trans = new Transformation();
      trans.setCluster(srcCluster);
      trans.setValid(isValid);
      return trans;
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Create a Refaster rule.
   */
  public static String createRefasterRule(final Cluster srcCluster, 
      final Edit srcEdit)
      throws BadLocationException, IOException, GitAPIException {
    String refaster;
    if (TechniqueConfig.getInstance().isCreateRule()) {    
      try {
        refaster = RefasterTranslator.translate(srcCluster, srcEdit);
      } catch (Exception e) {
        refaster = "";
      }
    } else {
      refaster = "";
    }
    return refaster;
  }
  
  /**
   * Saves a transformation. 
   */
  public static void saveTransformation(final Transformation trans, final Edit edit) 
      throws IOException, BadLocationException, GitAPIException {
    String path = "../Projects/cluster/";
    saveTransformation(path, trans, edit);
  }

  /**
   * Saves a transformation. 
   */
  public static void saveTransformation(final String folderPath, 
      final Transformation trans, final Edit edit) 
      throws IOException, BadLocationException, GitAPIException {
    final Cluster clusteri = trans.getCluster();
    final Cluster clusterj = clusteri.getDst();
    boolean isNoise = FilterManager.isNoise(folderPath, trans, clusteri, clusterj);
    if (!isNoise) {
      final String refaster = createRefasterRule(clusteri, edit);
      trans.setTransformation(refaster);
      QuickFix qf = new QuickFix();
      qf.setId(clusterIndex);
      qf.setCluster(clusteri);
      QuickFixManager.getInstance().getQuickFixes().add(qf);
      String counterFormated =  String.format("%03d", clusterIndex++);
      String path = folderPath + trans.isValid() + '/' + counterFormated + ".txt";
      final File clusterFile = new File(path);
      StringBuilder content = ClusterFormatter.getInstance()
          .formatCluster(clusteri, clusterj, refaster);
      FileUtils.writeStringToFile(clusterFile, content.toString());
      Script<StringNodeData> script = EditScriptUtils.getCluster(clusteri);
      if (FilterManager.isNoise(script)) {
        noise.add(script);
      } else {
        scripts.add(script);
      }
    }
  }

/**
   * Save single clusters.
   */
  public static int saveSingleClusters(
      int countCluster, List<Script<StringNodeData>> clusteredScriptsList) {
    /*Point point0 = null;
    Point point1 = null;*/
    for (final Point point : scripts) {
      @SuppressWarnings("unchecked")
      Script<StringNodeData> sc = (Script<StringNodeData>) point;
      Cluster clusteri = sc.getCluster();
      Cluster clusterj = clusteri.getDst();
      if (!clusteredScriptsList.contains(sc)) {
        StringBuilder content = new StringBuilder("");
        content.append(ClusterFormatter.formatList(sc.getList())).append('\n');
        content.append(ClusterFormatter.getInstance().formatHeader());
        content.append(ClusterFormatter.getInstance().formatClusterContent(clusteri, clusterj));
        content.append(ClusterFormatter.getInstance().formatFooter());
        /*if (countCluster == 36) {
          point0 = sc;
        } else if (countCluster == 42) {
          point1 = sc;
          ScriptDistanceMetric<StringNodeData> m =
              new ScriptDistanceMetric<>();
          m.calculate(point0, point1);
        }*/
        String counterFormated =  String.format("%03d", ++ countCluster);
        String path = "../Projects/cluster/clusters/" + counterFormated + ".txt";
        final File clusterFile = new File(path);
        QuickFix qf = new QuickFix();
        qf.setId(incrementClusterIndex());
        qf.setCluster(clusteri);
        QuickFixManager.getInstance().getPotentialPatterns().add(qf);
        try {
          FileUtils.writeStringToFile(clusterFile, content.toString());
        } catch (IOException e) {
          logger.error(e.getStackTrace());
        }
      }
    }
    return countCluster;
  }
}
