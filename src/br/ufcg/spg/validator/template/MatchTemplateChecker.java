package br.ufcg.spg.validator.template;

import at.jku.risc.stout.urauc.algo.AntiUnifyProblem.VariableWithHedges;
import at.jku.risc.stout.urauc.data.Hedge;
import br.ufcg.spg.analyzer.util.AnalyzerUtil;
import br.ufcg.spg.antiunification.AntiUnificationUtils;
import br.ufcg.spg.antiunification.AntiUnifier;
import br.ufcg.spg.bean.Tuple;
import br.ufcg.spg.cluster.UnifierCluster;
import br.ufcg.spg.edit.Edit;
import br.ufcg.spg.equation.EquationUtils;
import br.ufcg.spg.match.Match;
import br.ufcg.spg.tree.RevisarTree;
import br.ufcg.spg.tree.RevisarTreeParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Checks mapping.
 */
public class MatchTemplateChecker implements ITemplateChecker {
  /**
   * Source code anti-unification.
   */
  private final transient String srcAu;
  /**
   * Destination code anti-unification.
   */
  private final transient String dstAu;
  
  /**
   * Edit list.
   */
  private final transient List<Edit> srcEdits;
  
  /**
   * Creates a new instance.
   */
  public MatchTemplateChecker(final String srcAu, 
      final String dstAu, final List<Edit> srcEdits) {
    this.srcAu = srcAu;
    this.dstAu = dstAu;
    this.srcEdits = srcEdits;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean checkIsValidUnification() {
    final Edit firstEdit = srcEdits.get(0);
    final List<Match> matchesFirst = getMatches(firstEdit, srcAu, dstAu);
    final Edit lastEdit = srcEdits.get(srcEdits.size() - 1);
    final Map<String, String> substutingsFirst = AntiUnificationUtils.getUnifierMatching(
        firstEdit.getTemplate(), srcAu);
    final Map<String, String> substitutingsLast = AntiUnificationUtils.getUnifierMatching(
        lastEdit.getTemplate(), srcAu);
    if (substutingsFirst.size() != substitutingsLast.size()) {
      return false;
    }
    if (matchesFirst == null) {
      return false;
    }
    final List<Match> matchesLast = getMatches(lastEdit, srcAu, dstAu); 
    if (matchesLast == null) {
      return false;
    }
    if (matchesFirst.size() != matchesLast.size()) {
      return false;
    }
    Set<Tuple<String, String>> set = new HashSet<>();
    for (final Match match: matchesFirst) {
      set.add(new Tuple<>(match.getSrcHash().trim(), match.getDstHash().trim()));
    }
    for (final Match match: matchesLast) {
      if (!set.contains(new Tuple<>(match.getSrcHash().trim(), match.getDstHash().trim()))) {
        return false;
      }
    }
    return true;
  }

  private List<Match> getMatches(final Edit srcEdit, final String srcAu, final String dstAu) {
    final Edit dstEdit = srcEdit.getDst();
    final String srcTemplate = srcEdit.getPlainTemplate();
    final String dstTemplate = dstEdit.getPlainTemplate();
    //Gets hash id and value of destination nodes.
    final Map<String, String> dstAuMatches = AntiUnificationUtils.getUnifierMatching(
        dstTemplate, dstAu);
    //checks that all abstracted variables from destination is present on source.
    final Map<String, RevisarTree<String>> srcMapping = getStringTreeMapping(srcTemplate);
    final Map<String, RevisarTree<String>> dstMapping = getStringTreeMapping(dstTemplate);
    final HashSet<String> dstNodes = getNodes(dstEdit, dstAu);
    final Map<String, RevisarTree<String>> srcDstMapping = new Hashtable<>();
    for (final String str: dstNodes) {
      if (!srcMapping.containsKey(str)) {
        return null;
      }
      srcDstMapping.put(str, dstMapping.get(str));
    }
    final Map<String, String> srcUniMatching = AntiUnificationUtils.getUnifierMatching(
        srcTemplate, srcAu);
    final List<Match> matches = getMatches(srcUniMatching, dstAuMatches);
    return matches;
  }
  
  private List<Match> getMatches(final Map<String, String> srcUniMatching, 
      final Map<String, String> dstUnitMatching) {
    final List<Match> matches = new ArrayList<>();
    for (final Entry<String, String> srcEntry  : srcUniMatching.entrySet()) {
      final String srcKey = srcEntry.getKey();
      final String srcValue = srcEntry.getValue();
      for (final Entry<String, String> dstEntry: dstUnitMatching.entrySet()) {
        final String dstKey = dstEntry.getKey();
        final String dstValue = dstEntry.getValue();
        if (srcValue.equals(dstValue)) {
          final Match match = new Match(srcKey, dstKey, srcValue);
          matches.add(match);
        }
      }
    }
    return matches;
  }

  /**
   * Gets variable matching.
   * @param abstracted abstracted matching.
   * @param srcDstMapping source destination mapping.
   * @return destination variable matching.
   */
  public Map<String, RevisarTree<String>> getVariableMatching(
      final Map<String, RevisarTree<String>> abstracted,
      final Map<String, RevisarTree<String>> srcDstMapping) {
    final Map<String, RevisarTree<String>> variableMatching = new Hashtable<>();
    for (final Entry<String, RevisarTree<String>> entry : srcDstMapping.entrySet()) {
      final RevisarTree<String> value = entry.getValue();
      for (final Entry<String, RevisarTree<String>> abs : abstracted.entrySet()) {
        final RevisarTree<String> absValue = abs.getValue();
        if (isIntersect(value, absValue)) {
          variableMatching.put(abs.getKey(), value);
        }
      }
    }
    return variableMatching;
  }

  /**
   * Gets abstraction mapping.
   */
  public Map<String, RevisarTree<String>> getAbstractionMapping(final String template, 
      final Map<String, String> unifierMatching) {
    final Map<String, RevisarTree<String>> abstracted = new Hashtable<>();
    final RevisarTree<String> absTemplate = RevisarTreeParser.parser(template);
    final List<RevisarTree<String>> dstTreeNodes = AnalyzerUtil.getNodes(absTemplate); 
    for (final Entry<String, String> entry: unifierMatching.entrySet()) {
      for (final RevisarTree<String> dstNode : dstTreeNodes) {
        final String absNode = EquationUtils.convertToEq(dstNode);
        final String key = entry.getKey();
        final String value = entry.getValue();
        if (value.equals(absNode)) {
          abstracted.put(key, dstNode);
        }
      }
    }
    return abstracted;
  }

  /**
   * Verifies if the two nodes intersects.
   * @param root root node
   * @param toVerify to verify node
   */
  private boolean isIntersect(final RevisarTree<String> root, final RevisarTree<String> toVerify) {
    return isStartInside(root, toVerify) || isStartInside(toVerify, root);
  }
  
  /**
   * Verify if start position of toVerify is inside root.
   * @param root root node.
   * @param toVerify to verify.
   */
  private boolean isStartInside(final RevisarTree<String> root, 
      final RevisarTree<String> toVerify) {
    final int toVerifyStart = toVerify.getPos();
    final int rootStart = root.getPos();
    final int rootEnd = root.getEnd();
    return rootStart <= toVerifyStart && toVerifyStart <= rootEnd;   
  }

  /**
   * Gets mapping between string and tree.
   * @param edit edit.
   * @return mapping between and tree.
   */
  private Map<String, RevisarTree<String>> getStringTreeMapping(final String template) {
    final Map<String, RevisarTree<String>> srcNodes = new Hashtable<>();
    final RevisarTree<String> srcTemplate = RevisarTreeParser.parser(template);
    final List<RevisarTree<String>> srcTreeNodes = AnalyzerUtil.getNodes(srcTemplate);
    for (final RevisarTree<String> variable : srcTreeNodes) {
      final String srcStr = EquationUtils.convertToEq(variable);
      srcNodes.put(srcStr, variable);
    }
    return srcNodes;
  }

  private HashSet<String> getNodes(final Edit edit, final String cau) {
    final HashSet<String> nodes = new HashSet<>();
    final AntiUnifier unifier = UnifierCluster.computeUnification(edit.getPlainTemplate(), cau);
    final List<VariableWithHedges> dstVariables = unifier.getValue().getVariables();
    for (final VariableWithHedges variable : dstVariables) {
      final String str = removeEnclosingParenthesis(variable.getRight());
      nodes.add(str);
    }
    return nodes;
  }

  /**
   * Remove parenthesis.
   * @param variable hedge variable
   * @return string without parenthesis
   */
  private String removeEnclosingParenthesis(final Hedge variable) {
    final String str = variable.toString().trim();
    final boolean startWithParen = str.startsWith("(");
    final boolean endWithParen = str.endsWith(")");
    if (!str.isEmpty() && startWithParen && endWithParen) {
      return str.substring(1, str.length() - 1);
    }
    return str;
  }
}
