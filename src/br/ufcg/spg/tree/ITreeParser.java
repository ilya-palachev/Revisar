package br.ufcg.spg.tree;

import br.ufcg.spg.analyzer.util.AnalyzerUtil;
import br.ufcg.spg.bean.Tuple;
import br.ufcg.spg.util.PrintUtils;

import java.util.List;
import org.eclipse.jdt.core.dom.ASTNode;

public class ITreeParser {
  
  /**
   * parse the ant-unification.
   * @param unifier - ant-unification
   * @param node - node
   * @return parsed ant-unification
   */
  public static RevisarTree<Tuple<ASTNode, String>> parse(final RevisarTree<String> unifier, final ASTNode node) {
    final String value = "\n" + PrintUtils.prettyPrint(unifier);
    final Tuple<ASTNode, String> t = new Tuple<>(node, value);
    final RevisarTree<Tuple<ASTNode, String>> tree = new RevisarTree<Tuple<ASTNode, String>>(t);
    final List<RevisarTree<String>> auChildren = unifier.getChildren();
    if (auChildren.isEmpty()) {
      return tree;
    }
    final List<Object> childrenNodes = AnalyzerUtil.getChildren(node);
    final List<ASTNode> children = AnalyzerUtil.normalize(childrenNodes);
    if (children.size() > auChildren.size()) {
      return tree;
    }
    for (int i = 0; i < children.size(); i++) {
      final ASTNode childNode = children.get(i);
      final RevisarTree<String> childAtree = auChildren.get(i);
      final RevisarTree<Tuple<ASTNode, String>> parsedChild = parse(childAtree, childNode);
      tree.addChild(parsedChild);
    }
    return tree;
  }
}
