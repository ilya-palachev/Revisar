package br.ufcg.spg.dependence;

import br.ufcg.spg.bean.Tuple;
import br.ufcg.spg.compile.CompilerUtils;
import br.ufcg.spg.constraint.ConstraintUtils;
import br.ufcg.spg.database.DependenceDao;
import br.ufcg.spg.database.EditDao;
import br.ufcg.spg.diff.DiffCalculator;
import br.ufcg.spg.diff.DiffTreeContext;
import br.ufcg.spg.edit.Edit;
import br.ufcg.spg.edit.EditUtils;
import br.ufcg.spg.exp.ExpUtils;
import br.ufcg.spg.git.CommitUtils;
import br.ufcg.spg.git.GitUtils;
import br.ufcg.spg.imports.Import;
import br.ufcg.spg.matcher.AbstractMatchCalculator;
import br.ufcg.spg.matcher.PositionMatchCalculator;
import br.ufcg.spg.project.ProjectAnalyzer;
import br.ufcg.spg.project.ProjectInfo;
import br.ufcg.spg.project.Version;

import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.tree.ITree;
import com.github.gumtreediff.tree.TreeContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.NoFilepatternException;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

public class DependenceUtils {
  
  /**
   * Compiles the code before and after the change.
   */
  public static void dependences()
      throws MissingObjectException, IncorrectObjectTypeException, 
      AmbiguousObjectException, IOException, ExecutionException, 
      NoFilepatternException, GitAPIException {
    final List<Tuple<String, String>> projs = ExpUtils.getProjects();
    final Edit lastEdit = DependenceDao.getInstance().lastDependence();
    final List<Tuple<String, String>> projects = projects(projs, lastEdit);
    for (final Tuple<String, String> project : projects) {
      final String pname = project.getItem1();
      final List<String> commits = EditDao.getInstance().getAllCommits(pname);
      int index = 0;
      if (project.getItem2() != null) {
        index = commits.indexOf(project.getItem1()) + 1;
      }
      for (int c = index; c < commits.size(); c++) {
        System.out.println(((double)c) / commits.size() + " % complete");
        final String commit = commits.get(c);
        final Map<Edit, List<Edit>> graph = computeGraph(commit);
        save(graph);
      }
    }
  }
  
  /**
   * Identifies the dependences for specified edit.
   * @param srcEdit source code edit.
   * @return dependences
   */
  public static List<ASTNode> dependences(final Edit srcEdit) 
      throws IOException, ExecutionException, NoFilepatternException, GitAPIException {
    //final List<Tuple<String, ITree>> imports = ImportUtils.imports(srcEdit);
    final Edit dstEdit = srcEdit.getDst();
    //final EditDao editDao = EditDao.getInstance();
    final List<Import> imports = dstEdit.getImports();
    final Tuple<String, Tuple<Integer, Integer>> edit = edit(srcEdit, srcEdit.getDst(), imports);
    final String after = edit.getItem1();
    final ProjectInfo pi = ProjectAnalyzer.project(srcEdit);
    //checkout the commit if current commit differs.
    CommitUtils.checkoutIfDiffer(dstEdit.getCommit(), pi);
    final String srcFile = srcEdit.getPath();
    final Tuple<CompilationUnit, CompilationUnit> baEdit = 
        EditUtils.beforeAfter(srcFile, after, pi.getSrcVersion());
    final CompilationUnit srcRoot = baEdit.getItem1();
    List<ASTNode> errorsSrc = ConstraintUtils.constraints(srcRoot);
    final AbstractMatchCalculator mcal = new PositionMatchCalculator(srcEdit.getStartPos(), 
        srcEdit.getEndPos());
    final ASTNode srcNode = mcal.getNode(srcRoot);
    errorsSrc = removeIntersect(errorsSrc, srcNode);
    final Tuple<Integer, Integer> loc = edit.getItem2();
    final CompilationUnit dstRoot = baEdit.getItem2();
    final AbstractMatchCalculator mcalc = 
        new PositionMatchCalculator(loc.getItem1(), loc.getItem2());
    final ASTNode mappedDstNode = mcalc.getNode(dstRoot);
    if (mappedDstNode == null) {
      return new ArrayList<ASTNode>();
    }
    List<ASTNode> errorsDst = ConstraintUtils.constraints(dstRoot);
    errorsDst = removeIntersect(errorsDst, mappedDstNode);
    System.out.println(errorsSrc.size() + " : " + errorsDst.size());
    final Tuple<TreeContext, TreeContext> baTreeEdit = EditUtils.beforeAfterCxt(srcFile, after);
    final TreeContext src = baTreeEdit.getItem1();
    final TreeContext dst = baTreeEdit.getItem2();
    final DiffCalculator diff = new DiffTreeContext(src, dst);
    diff.diff();
    final List<ASTNode> mapped = mapped(errorsDst, srcRoot, dstRoot, diff);
    final List<ASTNode> diffs = diffs(errorsSrc, mapped);
    return diffs;
  }
  
  /**
   * Save dependencies.
   * @param graph graph of dependencies
   */
  private static void save(final Map<Edit, List<Edit>> graph) {
    for (final Edit key : graph.keySet()) {
      final Dependence dependence = new Dependence();
      dependence.setEdit(key);
      dependence.setNodes(new ArrayList<>());
      final List<Edit> value = graph.get(key);
      for (final Edit edit : value) {
        if(!dependence.getNodes().contains(edit)) {
           dependence.addNode(edit);
        }
      }
      final DependenceDao dao = DependenceDao.getInstance();
      dao.save(dependence);
    }  
  }

  /**
   * Computes graph.
   * @param commit commit to be analyzed
   */
  public static Map<Edit, List<Edit>> computeGraph(final String commit)
      throws IOException, ExecutionException, NoFilepatternException, GitAPIException {
    final List<Edit> srcEdits = EditDao.getInstance().getSrcEdits(commit);
    return computeGraph(srcEdits);
  }
  
  /**
   * Computes graph.
   */
  public static Map<Edit, List<Edit>> computeGraph(final List<Edit> srcEdits)
      throws IOException, ExecutionException, NoFilepatternException, GitAPIException {
    final Map<Edit, List<Edit>> graph = new Hashtable<>();
    // fill each node with an empty list
    for (final Edit edit : srcEdits) {
      graph.put(edit, new ArrayList<>());
    }
    for (int i = 0; i < srcEdits.size(); i++) {
      final Edit srcEdit = srcEdits.get(i);
      final List<ASTNode> related = dependences(srcEdit);
      for (final ASTNode node : related) {
        final Edit original = original(srcEdits, node);
        if (original != null) {
          final List<Edit> listNode = graph.get(srcEdit);
          if (!listNode.contains(original)) {
            graph.get(srcEdit).add(original);
            graph.get(original).add(srcEdit);
          }
        }
      }
    }
    save(graph);
    return graph;
  }

  /**
   * Gets remaining projects.
   * 
   * @param projects
   *          projects
   * @param edit
   *          last edit
   * @return remaining project
   */
  private static List<Tuple<String, String>> projects(final List<Tuple<String, String>> 
      projects, final Edit edit) {
    final List<Tuple<String, String>> remainProjs = new ArrayList<>();
    if (edit == null) {
      return projects;
    }
    final String pname = edit.getProject();
    final String lastCommit = edit.getCommit();
    boolean include = false;
    for (final Tuple<String, String> pj : projects) {
      if ((pj.getItem1() + "_old").equals(pname)) {
        include = true;
        pj.setItem2(lastCommit);
      }
      if (include) {
        remainProjs.add(pj);
      }
    }
    return remainProjs;
  }

  /**
   * Gets the original version of the node.
   * 
   * @param allNodes
   *          all nodes
   * @param node
   *          node to find original node
   * @return original node
   */
  private static Edit original(final List<Edit> allNodes, final ASTNode node) 
      throws IOException, ExecutionException {
    for (int i = 0; i < allNodes.size(); i++) {
      final Edit t = allNodes.get(i);
      final GitUtils analyzer = new GitUtils();
      final ProjectInfo pi = ProjectAnalyzer.project(t);
      final Version srcVersion = pi.getSrcVersion();
      final String commit = t.getCommit();
      final CompilationUnit cu = CompilerUtils.getCunit(t, commit, pi.getSrcVersion(), pi);
      final AbstractMatchCalculator mcal = new PositionMatchCalculator(t.getStartPos(), t.getEndPos());//new IndexMatchCalculator(t.getIndex());
      final ASTNode n = mcal.getNode(cu);
      if (n != null && intersect(node, n)) {
        return t;
      }
    }
    return null;
  }

  private static boolean intersect(final ASTNode nodei, final ASTNode nodej) {
    final int starti = nodei.getStartPosition();
    final int endi = starti + nodei.getLength();
    final int startj = nodej.getStartPosition();
    final int endj = startj + nodej.getLength();
    final boolean iandj = starti <= startj && startj <= endi;
    final boolean jandi = startj <= starti && starti < endj;
    return iandj || jandi;
  }

  /**
   * Returns true is the two node are equal.
   * 
   * @param node
   *          i
   * @param node
   *          j
   * @return true if the two nodes are equal.
   */
  private static boolean same(final ASTNode nodei, final ASTNode nodej) {
    final int starti = nodei.getStartPosition();
    final int endi = starti + nodei.getLength();
    final int startj = nodej.getStartPosition();
    final int endj = startj + nodej.getLength();
    final boolean equals = starti == startj && endi == endj;
    return equals;
  }

  private static List<ASTNode> removeIntersect(final List<ASTNode> errorsSrc, final ASTNode srcNode) {
    final List<ASTNode> nodes = new ArrayList<ASTNode>();
    for (final ASTNode node : errorsSrc) {
      if (!intersect(srcNode, node)) {
        nodes.add(node);
      }
    }
    return nodes;
  }

  /**
   * From the destination node list, gets the nodes in the source code that
   * corresponds to destination nodes.
   * 
   * @param destination
   *          nodes.
   * @param srcRoot
   *          root of the source code tree
   * @param dstRoot
   *          root of the destination source code
   * @param ITree
   *          node version of the destination source code
   * @param mappings
   *          mappings.
   */
  private static List<ASTNode> mapped(final List<ASTNode> dstNodes, final ASTNode srcRoot, final ASTNode dstAstRoot,
      final DiffCalculator diff) {
    final List<ASTNode> result = new ArrayList<>();
    final MappingStore mappings = diff.getMatcher().getMappings();
    final ITree dstRoot = diff.getDst().getRoot();
    for (final ASTNode dstNode : dstNodes) {
      AbstractMatchCalculator mcalc = new PositionMatchCalculator(dstNode);
      final ITree tree = mcalc.getNode(dstRoot);
      mcalc = new PositionMatchCalculator(dstNode);
      final ASTNode astNode = mcalc.getNode(dstAstRoot);
      final ITree srcTree = mappings.getSrc(tree);
      if (srcTree == null) {
        System.out.println("Cannot find the match node destination node: " + astNode);
        continue;
      }
      mcalc = new PositionMatchCalculator(srcTree);
      final ASTNode srcNode = mcalc.getNode(srcRoot);
      result.add(srcNode);
    }
    return result;
  }

  /**
   * gets the diff between the constraint of the before and after version.
   * 
   * @param errorsSrc
   *          - errors in source version
   * @param errorsDst
   *          - errors in destination version
   * @return diff between the constraint on the before and after version
   */
  private static List<ASTNode> diffs(final List<ASTNode> errorsSrc, final List<ASTNode> errorsDst) {
    final List<ASTNode> result = new ArrayList<ASTNode>();
    for (final ASTNode dstNode : errorsDst) {
      boolean found = false;
      for (final ASTNode srcNode : errorsSrc) {
        if (same(dstNode, srcNode)) {
          found = true;
        }
      }
      if (!found) {
        result.add(dstNode);
      }
    }
    return result;
  }

  /**
   * Return the modified version of the node.
   * 
   * @param srcFilePath
   *          path to source
   * @param dstFilePath
   *          path to destination
   * @param srcEdit
   *          before version of the node
   * @param dstEdit
   *          after version of the node
   * @param imports
   *          import statements
   * @return modified version and index of modified node
   */
  private static Tuple<String, Tuple<Integer, Integer>> edit(final Edit srcEdit,
      final Edit dstEdit, final List<Import> imports) 
          throws IOException, ExecutionException {
    final String srcFilePath = srcEdit.getPath();
    final String dstFilePath = dstEdit.getPath();
    final ProjectInfo pi =  ProjectAnalyzer.project(srcEdit);
    final String srcCommit = dstEdit.getCommit();
    final CompilationUnit srcCu = CompilerUtils.getCunit(srcEdit, srcCommit, pi.getSrcVersion(), pi);
    final CompilationUnit dstCu = CompilerUtils.getCunit(dstEdit, dstEdit.getCommit(), pi.getDstVersion(), pi);
    AbstractMatchCalculator mcal = new PositionMatchCalculator(srcEdit.getStartPos(), srcEdit.getEndPos());//new IndexMatchCalculator(srcEdit.getIndex());
    final ASTNode srcNode = mcal.getNode(srcCu);
    mcal = new PositionMatchCalculator(dstEdit.getStartPos(), dstEdit.getEndPos());//new IndexMatchCalculator(dstEdit.getIndex());
    final ASTNode dstNode = mcal.getNode(dstCu);
    String srcContent = "";
    String dstContent = "";
    try {
      srcContent = new String(Files.readAllBytes(Paths.get(srcFilePath)));
      dstContent = new String(Files.readAllBytes(Paths.get(dstFilePath)));
    } catch (final IOException e) {
      e.printStackTrace();
    }
    final String beforeEdit = srcContent.substring(0, srcNode.getStartPosition());
    final int dstStart = dstNode.getStartPosition();
    final int dstLength = dstNode.getStartPosition() + dstNode.getLength();
    final int srcLength = srcNode.getStartPosition() + srcNode.getLength();
    if (dstLength >= dstContent.length()) {
      System.out.println();
    }
    final String change = dstContent.substring(dstStart, dstLength);
    final String afterEdit = srcContent.substring(srcLength, srcContent.length());
    int start = beforeEdit.length();
    String result = beforeEdit + change + afterEdit;
    for (final Import ttree : imports) {
      //final ITree tree = ttree.getItem2();
      final int i = result.indexOf("import");
      final String b = result.substring(0, i);
      final String c = ttree.getText() + '\n';
      //final String c = dstContent.substring(tree.getPos(), tree.getEndPos()) + "\n";
      final String a = result.substring(i, result.length());
      result = b + c + a;
      start += c.length();
    }
    final Tuple<Integer, Integer> location = new Tuple<Integer, Integer>(start, start + change.length());
    return new Tuple<String, Tuple<Integer, Integer>>(result, location);
  }

  /**
   * Returns the list of errors.
   * 
   * @param srcFilePath
   *          - path
   * @param errors
   *          - errors
   * @return the list of errors
   */
  public List<br.ufcg.spg.bean.Error> getErrors(final String srcFilePath, 
      final List<br.ufcg.spg.bean.Error> errors) {
    final File file = new File(srcFilePath);
    String absolutePath = "";
    try {
      absolutePath = file.getCanonicalPath();
    } catch (final IOException e) {
      e.printStackTrace();
    }
    final List<br.ufcg.spg.bean.Error> errorList = new ArrayList<br.ufcg.spg.bean.Error>();
    for (final br.ufcg.spg.bean.Error er : errors) {
      if (er.getFile().equals(absolutePath)) {
        errorList.add(er);
      }
    }
    return errorList;
  }

  /*
   * /** Compiles the code before and after the change.
   * 
   * @param srcFilePath source file
   * 
   * @param dstFilePath destination file
   * 
   * @param srcNode source code
   * 
   * @param dstNode destination node
   */
  /*
   * private void compileBeforeAfter(String srcFilePath, String dstFilePath,
   * ASTNode srcNode, ASTNode dstNode, List<ITree> imports) throws IOException {
   * String before = new String(Files.readAllBytes(Paths.get(srcFilePath))); //
   * if(!before.substring(srcNode.getStartPosition(), //
   * srcNode.getStartPosition() + //
   * srcNode.getLength()).contains("Vector<String>")) return; // compiles the
   * before version of the source code List<spg.bean.Error> errorsSrc =
   * JCompiler.compileFiles(srcFilePath); String modifiedVersion =
   * modifyFile(srcFilePath, dstFilePath, srcNode, dstNode, imports);
   * FileUtils.writeStringToFile(new File(srcFilePath), modifiedVersion);
   * List<spg.bean.Error> errorsDst = JCompiler.compileFiles(srcFilePath);
   * FileUtils.writeStringToFile(new File(srcFilePath), before);
   * List<spg.bean.Error> srcErrorsFile = getErrors(srcFilePath, errorsSrc);
   * List<spg.bean.Error> dstErrorsFile = getErrors(srcFilePath, errorsDst); }
   */
}
