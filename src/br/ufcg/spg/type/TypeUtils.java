package br.ufcg.spg.type;

import br.ufcg.spg.binding.BindingSolver;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;

public class TypeUtils {
  
  private TypeUtils() {
  }

  /**
   * Extracts the type of the node.
   * @param astNode before node
   * @param ast at
   * @return the type of the node.
   */
  public static Type extractType(final ASTNode astNode, final AST ast) {
    // for simple variable declaration
    if (astNode instanceof SingleVariableDeclaration) {
      final SingleVariableDeclaration decl = (SingleVariableDeclaration) astNode;
      return decl.getType();
    }
    if (astNode instanceof VariableDeclarationStatement) {
      final VariableDeclarationStatement stm = (VariableDeclarationStatement) astNode;
      return stm.getType();
    }
    if (astNode instanceof FieldDeclaration) {
      final FieldDeclaration tdecl = (FieldDeclaration) astNode;
      return tdecl.getType();
    }
    if (astNode instanceof TypeParameter) {
      final TypeParameter tparam = (TypeParameter) astNode;
      final WildcardType type = ast.newWildcardType();
      final List<?> boundList = tparam.typeBounds();
      Type bound = null;
      if (!boundList.isEmpty()) {
        bound = (Type) boundList.get(0);
        bound = (Type) ASTNode.copySubtree(ast, bound);
      }
      if (bound != null) {
        type.setBound(bound, true);
      }
      return type;
    }
    // for simple type
    if (astNode instanceof SimpleType) {
      final ASTNode node = TypeUtils.nodeForType(astNode);
      final ITypeBinding typeBinding = BindingSolver.typeBinding(node);
      return TypeUtils.typeFromBinding(ast, typeBinding);
    }
    if (astNode instanceof ArrayType) {
      final ArrayType arr = (ArrayType) astNode;
      final Type type = arr;
      return type;
    }
    // for parameterized type
    if (astNode instanceof ParameterizedType) {
      final ParameterizedType type = (ParameterizedType) astNode;
      return TypeUtils.typeFromParameterizedType(ast, type);
    }
    // TODO: add other types on demand
    final ITypeBinding binding = BindingSolver.typeBinding(astNode);
    if (binding == null) {
      return ast.newPrimitiveType(PrimitiveType.VOID);
    }
    final Type type = TypeUtils.typeFromBinding(ast, binding);
    return type;
  }

  /**
   * Returns the type for binding.
   * 
   * @param ast
   *          ast
   * @param typeBinding
   *          type binding
   * @return returns the type for binding
   */
  @SuppressWarnings("unchecked")
  public static Type typeFromBinding(final AST ast, final ITypeBinding typeBinding) {
    if (ast == null) {
      throw new NullPointerException("ast is null");
    }
    if (typeBinding == null) {
      throw new NullPointerException("typeBinding is null");
    }
    if (typeBinding.isPrimitive()) {
      return ast.newPrimitiveType(PrimitiveType.toCode(typeBinding.getName()));
    }
    if (typeBinding.isTypeVariable()) {
      final WildcardType capType = ast.newWildcardType();
      final ITypeBinding bound = typeBinding.getBound();
      if (bound != null) {
        capType.setBound(typeFromBinding(ast, bound), typeBinding.isUpperbound());
      }
      return capType;
    }
    if (typeBinding.isCapture()) {
      final ITypeBinding wildCard = typeBinding.getWildcard();
      final WildcardType capType = ast.newWildcardType();
      final ITypeBinding bound = wildCard.getBound();
      if (bound != null) {
        capType.setBound(typeFromBinding(ast, bound), wildCard.isUpperbound());
      }
      return capType;
    }
    if (typeBinding.isTypeVariable()) {
      final WildcardType type = ast.newWildcardType();
      final ITypeBinding bound = typeBinding.getBound();
      if (bound != null) {
        type.setBound(typeFromBinding(ast, bound), typeBinding.isUpperbound());
      }
      return type;
    }
    if (typeBinding.isArray()) {
      final Type elType = typeFromBinding(ast, typeBinding.getElementType());
      return ast.newArrayType(elType, typeBinding.getDimensions());
    }
    if (typeBinding.isParameterizedType()) {
      final Type typeErasure = typeFromBinding(ast, typeBinding.getErasure());
      final ParameterizedType type = ast.newParameterizedType(typeErasure);
      final List<Type> newTypeArgs = new ArrayList<>();
      for (final ITypeBinding typeArg : typeBinding.getTypeArguments()) {
        newTypeArgs.add(typeFromBinding(ast, typeArg));
      }
      type.typeArguments().addAll(newTypeArgs);
      return type;
    }
    if (typeBinding.isWildcardType()) {
      final WildcardType type = ast.newWildcardType();
      return type;
    }
    // simple or raw type
    final String qualName = typeBinding.getQualifiedName();
    if ("".equals(qualName)) {
      throw new IllegalArgumentException("No name for type binding.");
    }
    return ast.newSimpleType(ast.newName(qualName));
  }

  public static Type typeFromParameterizedType(final AST ast, final ParameterizedType param) {
    final Type type = (Type) ASTNode.copySubtree(ast, param);
    return type;
  }

  /**
   * Returns the name for the simple type.
   * 
   * @param type
   *          simple type
   * @return name for the simple type
   */
  public static ASTNode nodeForType(final ASTNode type) {
    if (type.getNodeType() == ASTNode.SIMPLE_TYPE) {
      final SimpleType smType = (SimpleType) type;
      final ASTNode name = smType.getName();
      return name;
    }
    return type;
  }

  /**
   * Extract types.
   */
  public static List<Type> extractTypes(List<ASTNode> targetList, 
      final AST refasterRule) {
    final List<Type> paramTypes = new ArrayList<>();
    for (int i = 0; i < targetList.size(); i++) {
      final ASTNode tbefore = targetList.get(i);
      final Type paramType = extractType(tbefore, refasterRule);
      paramTypes.add(paramType);
    }
    return paramTypes;
  }

}
