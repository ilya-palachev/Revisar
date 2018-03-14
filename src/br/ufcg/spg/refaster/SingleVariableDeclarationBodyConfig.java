package br.ufcg.spg.refaster;

import br.ufcg.spg.type.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

public class SingleVariableDeclarationBodyConfig implements IConfigBody {
  private final ASTNode template;
  private final MethodDeclaration method;
  private final AST ast;

  /**
   * Constructor.
   */
  public SingleVariableDeclarationBodyConfig(final ASTNode template, final MethodDeclaration method,
      final AST ast) {
    super();
    this.template = template;
    this.method = method;
    this.ast = ast;
  }

  @Override
  public MethodDeclaration config() {
    SingleVariableDeclaration decl = (SingleVariableDeclaration) template;
    decl = (SingleVariableDeclaration) ASTNode.copySubtree(ast, decl);
    final VariableDeclarationFragment varFrag = ast.newVariableDeclarationFragment();
    final SimpleName name = (SimpleName) ASTNode.copySubtree(ast, decl.getName());
    varFrag.setName(name);
    Type stmType = TypeUtils.extractType(decl, ast);
    stmType = (Type) ASTNode.copySubtree(ast, stmType);
    VariableDeclarationStatement statement = ast.newVariableDeclarationStatement(varFrag);
    statement.setType(stmType);
    final List<ASTNode> modifiers = decl.modifiers();
    final List<ASTNode> nmodifiers = new ArrayList<>();
    for (final ASTNode modifier : modifiers) {
      final ASTNode nmodifier = ASTNode.copySubtree(ast, modifier);
      nmodifiers.add(nmodifier);
    }
    statement.modifiers().addAll(nmodifiers);
    statement = (VariableDeclarationStatement) ASTNode.copySubtree(ast, statement);
    method.getBody().statements().remove(0);
    method.getBody().setFlags(2);
    method.getBody().statements().add(statement);
    return method;
  }
}
