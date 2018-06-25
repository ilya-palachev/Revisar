package br.ufcg.spg.analyzer.test;

import at.jku.risc.stout.urauc.algo.JustificationException;
import at.jku.risc.stout.urauc.util.ControlledException;
import br.ufcg.spg.bean.Tuple;
import br.ufcg.spg.cluster.Cluster;
import br.ufcg.spg.cluster.ClusterUnifier;
import br.ufcg.spg.cluster.ClusterUtils;
import br.ufcg.spg.config.TechniqueConfig;
import br.ufcg.spg.database.ClusterDao;
import br.ufcg.spg.database.DependenceDao;
import br.ufcg.spg.database.EditDao;
import br.ufcg.spg.dependence.DependenceUtils;
import br.ufcg.spg.edit.Edit;
import br.ufcg.spg.edit.EditStorage;
import br.ufcg.spg.exp.ExpUtils;
import br.ufcg.spg.log.TimeLogger;
import br.ufcg.spg.main.MainArguments;
import br.ufcg.spg.technique.Technique;
import br.ufcg.spg.technique.TechniqueUtils;
import br.ufcg.spg.transformation.TransformationUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.junit.Test;

public class TestSuite {
  
  @Test
  public void exp() throws IOException, JustificationException, ControlledException, CoreException {
    configMainArguments();
    final TechniqueConfig config = TechniqueConfig.getInstance();
    config.setAllCommits(true);
    config.setEditsToAnalyze(100);
    config.setTemplateIncludesType(false);
    final List<Tuple<String, String>> projects = ExpUtils.getProjects();
    testBaseTableExpProjects(projects);
  }

  private void configMainArguments() {
    final MainArguments arguments = MainArguments.getInstance();
    arguments.setProjects("projects.txt");
    arguments.setProjectFolder("../Projects");
  }
  
  @Test
  public void exp_Cluster() 
      throws IOException, JustificationException, ControlledException, CoreException {
    configMainArguments();
    Technique.clusterEdits();
    Technique.translateEdits();
    System.out.println("END.");
  }
  
  @Test
  public void exp_Dependence() 
      throws MissingObjectException, IncorrectObjectTypeException, 
      AmbiguousObjectException, IOException, ExecutionException,
      NoFilepatternException, GitAPIException {
    configMainArguments();
    DependenceUtils.dependences();
  }
  
  @Test
  public void exp_Translate() 
      throws IOException, JustificationException, ControlledException, CoreException {
    configMainArguments();
    Technique.translateEdits();
    System.out.println("END.");
  }
  
  @Test
  public void exp_TranslateMoreProjects() 
      throws IOException, JustificationException, ControlledException, CoreException {
    configMainArguments();
    TransformationUtils.transformationsMoreProjects();
    System.out.println("END.");
  }
  
  @Test
  public void exp_translate_id() 
      throws IOException, JustificationException, ControlledException, CoreException {
    Technique.translateEdits("1365589");
    System.out.println("END.");
  }
  
  @Test
  public void exp_cluster_id() 
      throws IOException, JustificationException, ControlledException, CoreException {
    configMainArguments();
    ClusterUtils.buildClusters("1290970");
    System.out.println("END.");
  }
  
  @Test
  public void exp_cluster_more_projects() throws IOException {
    configMainArguments();
    List<Cluster> clusters = TransformationUtils.getClusterMoreProjects();
    List<Edit> allEdits = new ArrayList<>();
    int i = clusters.size();
    System.out.println(i);
    for (Cluster c : clusters) {
      allEdits.addAll(c.getNodes());
    }
    Map<String, List<Edit>> dcaps = ClusterUnifier.getInstance().groupEditsByDCap(allEdits, TechniqueConfig.getInstance());
    List<Cluster> clustersDcap = new ArrayList<>();
    for (Entry<String, List<Edit>> entry : dcaps.entrySet()) {
      List<Cluster> clusterForDcap = ClusterUnifier.getInstance().clusterEdits(entry.getValue());
      clustersDcap.addAll(clusterForDcap);
    }
    TransformationUtils.transformations(clustersDcap);
    System.out.println("END.");
  }
  
  @Test
  public void exp_cluster_more_projects_no_dcap() throws IOException {
    configMainArguments();
    List<Cluster> clusters = TransformationUtils.getClusterMoreProjects();
    List<Edit> allEdits = new ArrayList<>();
    int i = clusters.size();
    System.out.println(i);
    for (Cluster c : clusters) {
      allEdits.addAll(c.getNodes());
    }
    List<Cluster> newClusters =  ClusterUnifier.getInstance().clusterEdits(allEdits);
    TransformationUtils.transformations(newClusters);
    System.out.println("END.");
  }
  
  @Test
  public void test_d_cap() 
      throws IOException, JustificationException, ControlledException, CoreException {
    configMainArguments();
    final ClusterDao dao = ClusterDao.getInstance();
    final List<Cluster> clusters = dao.getClusters("113406");
    final Cluster cluster = clusters.get(0);
    final Edit srcEdit = cluster.getNodes().get(0);
    final String srcDcap = srcEdit.getDcap3();
    final String dstDcap = srcEdit.getDst().getDcap3();
    final EditDao editDao = EditDao.getInstance();
    final List<Edit> srcList = editDao.getSrcEditsByDcap(srcDcap, dstDcap,  3);
    final Map<String, List<Edit>> groups = 
        srcList.stream().collect(Collectors.groupingBy(w -> w.getDst().getDcap3()));
    for (final Entry<String, List<Edit>> entry: groups.entrySet()) {
      final List<Edit> toAnalyze = entry.getValue();
      final List<Cluster> clts =  
          ClusterUnifier.getInstance().clusters(toAnalyze);
      final ClusterDao cdao = ClusterDao.getInstance();
      cdao.saveAll(clts);     
      TransformationUtils.transformations(clts);
    }
    System.out.println("END.");
  }
  
  @Test
  public void exp_learn_dependence_clusters() {
    configMainArguments();
    final ClusterDao dao = ClusterDao.getInstance();
    final List<String> commits = dao.getAllCommitsClusters();
    DependenceDao dependenceDao = DependenceDao.getInstance();
    final Edit edit = dependenceDao.lastDependence();
    final String lastCommit = edit.getCommit();
    final int index = commits.indexOf(lastCommit);
    final double size = commits.size();
    for (int i = index + 1; i < commits.size(); i++) {
      final String commit = commits.get(i);
      System.out.println(i / size + "% calculate : current commit: " + commit);
      try {
        DependenceUtils.computeGraph(commit);
      } catch (final Exception e) {
        e.printStackTrace();
      }
    }
  }
 
  /**
   * Gets e-mails.
   */
  public void getEmails() throws IOException, NoHeadException, GitAPIException {
    final List<String> emails = ExpUtils.allEmails();
    ExpUtils.saveEmails(emails, "email.txt");
    final List<String> shiffle = ExpUtils.shuffleList(emails);
    ExpUtils.saveEmails(shiffle, "shuffle_email.txt");
  }

  /**
   * Test for many projects.
   * @param projects projects.
   */
  public void testBaseTableExpProjects(final List<Tuple<String, String>> projects)
      throws IOException, JustificationException, ControlledException, CoreException {
    final long startTime = System.nanoTime();     
    for (final Tuple<String, String> project : projects) {
      TechniqueUtils.concreteEdits(project);
    }
    final long estimatedTime = System.nanoTime() - startTime;
    TimeLogger.getInstance().setTimeExtract(estimatedTime);
    Technique.clusterEdits();
    Technique.translateEdits();
    System.out.println("DEBUG: TOTAL COMMITS");
    final EditStorage storage = EditStorage.getInstance();
    for (final Tuple<String, String> project: projects) {
      System.out.println("=====================");
      System.out.println(project.getItem1());
      System.out.println("Total: " + storage.getCommitProjects().get(project.getItem1()).size());
      System.out.println("=====================");     
    }
    System.out.println("END.");
  }

  /**
   * Test base method.
   * @throws ExecutionException 
   * 
   */
  public void testBaseTable(final String project, final List<String> files)
      throws IOException, JustificationException, ControlledException, 
      CoreException, ExecutionException {
    testBaseTable(project, files, "");
  }
  
  /**
   * Test base method.
   * 
   */
  public void testBaseTable(final String project, final String commit)
      throws IOException, JustificationException, ControlledException, CoreException {
    Technique.addEdits(project, commit);
    Technique.clusterEdits();
    Technique.translateEdits();
  }
  
  /**
   * Test base method.
   * @throws ExecutionException 
   * 
   */
  public void testBaseTable(final String project, final List<String> files, final String commit)
      throws IOException, JustificationException, ControlledException, 
      CoreException, ExecutionException {
    // Computing before after edits
    Technique.addEdits(project, files, commit);
    Technique.clusterEdits();
    Technique.translateEdits();
  }
}