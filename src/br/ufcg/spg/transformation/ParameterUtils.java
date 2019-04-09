package br.ufcg.spg.transformation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import br.ufcg.spg.bean.Tuple;
import br.ufcg.spg.stub.StubUtils;
import br.ufcg.spg.type.TypeUtils;
import org.eclipse.jdt.core.dom.*;

/**
 * Configure parameters.
 */
public final class ParameterUtils {
  
  private ParameterUtils() {
  }

  /**
   * Adds parameter to method.
   * @param types types to be analyzed
   * @param cuUnit compilation unit
   * @param method method 
   * @return method with parameters added.
   */
  @SuppressWarnings("unchecked")
  public static MethodDeclaration addParameter(final List<Type> types, List<String> varNames,
      final CompilationUnit cuUnit, MethodDeclaration method) {
    final AST ast = cuUnit.getAST();
    final List<ASTNode> parameters = new ArrayList<>();
    for (int i = 0; i < types.size(); i++) {
      // Create a new variable declaration to be added as parameter.
      final SingleVariableDeclaration singleVariableDeclaration = 
          ast.newSingleVariableDeclaration();
      final SimpleName name = ast.newSimpleName(varNames.get(i));
      singleVariableDeclaration.setName(name);
      Type type = types.get(i);
      type = (Type) ASTNode.copySubtree(ast, type);
      singleVariableDeclaration.setType(type);
      singleVariableDeclaration.setVarargs(false);
      final ASTNode singleVariableDeclarationCopy = ASTNode.copySubtree(
          ast, singleVariableDeclaration);
      parameters.add(singleVariableDeclarationCopy);
    }
    method = (MethodDeclaration) ASTNode.copySubtree(ast, method);
    method.parameters().addAll(parameters);
    return method;
  }

  public static MethodDeclaration findMethod(CompilationUnit templateClass, List<Type> argTypes, String name) {
    TypeDeclaration typeDeclaration = ClassUtils.getTypeDeclaration(templateClass);
    MethodDeclaration duplicate = null;
    List<ASTNode> nodes = (List<ASTNode>) typeDeclaration.bodyDeclarations();
    for (ASTNode node : nodes) {
      if (node instanceof MethodDeclaration) {
        MethodDeclaration methodDeclaration = (MethodDeclaration) node;
        if (methodDeclaration.getName().toString().equals(name)) {
          List<Type> parameters = new ArrayList<>();
          List<ASTNode> parametersList = (List<ASTNode>) methodDeclaration.parameters();
          for (ASTNode param : parametersList) {
            SingleVariableDeclaration parameter = (SingleVariableDeclaration) param;
            parameters.add(parameter.getType());
          }
          if (parameters.toString().equals(argTypes.toString())) {
            duplicate = methodDeclaration;
          }
        }
      }
    }
    return duplicate;
  }

  public static List<Type> getArgTypes(CompilationUnit unit, MethodInvocation invocation, List<ASTNode> arguments) throws IOException {
    List<Type> argTypes = new ArrayList<>();
    for(ASTNode arg : arguments) {
      Type argType;
      if (arg.toString().equals("null")) {
        argType = TypeUtils.createType(unit.getAST(), "java.lang", "Object");
      } else {
        argType = TypeUtils.extractType(arg, arg.getAST());
      }
      if (arg instanceof MethodInvocation) {
        MethodInvocation methodInvocationArg = (MethodInvocation) arg;
        try {
          if (((MethodInvocation) arg).getExpression() instanceof QualifiedName) {
            Type classType = TypeUtils.getTypeFromQualifiedName(unit, methodInvocationArg.getExpression());
            ClassUtils.getTemplateClass(unit, classType);
          }
          if (argType.toString().equals("void")) {
            argType = getArgType(unit, invocation);
          }
          argType = StubUtils.processMethodInvocation(unit, argType, arg);
          if (!methodInvocationArg.getExpression().toString().contains(".")) {
            Type newType = ImportUtils.getTypeBasedOnImports(unit, methodInvocationArg.getExpression().toString());
            List<Type> types = MethodInvocationUtils.returnType(unit, newType, methodInvocationArg.getName().toString());
            if (argType != null && argType.toString().contains("syntethic") && !types.isEmpty()) {
              argType = types.get(0);
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      else if (arg instanceof ClassInstanceCreation) {
        ClassInstanceCreation initializer = (ClassInstanceCreation) arg;
        Type type = TypeUtils.extractType(arg, arg.getAST());
        argType = type;
        ExpressionUtils.processExpressionBase(unit, type, initializer);
      }
      else if (arg instanceof QualifiedName) {
        QualifiedName qualifiedName = (QualifiedName) arg;
        System.out.println(qualifiedName.getName() + " : " + qualifiedName.getQualifier());
        ASTNode imp = ImportUtils.findImport(unit, qualifiedName.getQualifier().toString());
        Tuple<String, String> inner = InnerClassUtils.getInnerClassImport(qualifiedName.getQualifier().toString());
        String fullName;
        if (imp != null) {
           fullName = imp.toString().substring(7, imp.toString().indexOf(inner.getItem1())-1);
        }
        else {
          String qualifiedNameStr = "defaultpkg." + qualifiedName.getQualifier().toString();
          fullName = qualifiedNameStr.substring(0, qualifiedNameStr.indexOf(inner.getItem1().trim())-1);
        }
        StubUtils.processInnerClass(unit, inner, fullName);
        Type type = SyntheticClassUtils.getSyntheticType(unit.getAST());
        FieldDeclaration fieldDeclaration = FieldDeclarationUtils.createFieldDeclaration(unit, qualifiedName.getName(), type);
        FieldDeclarationUtils.addModifier(fieldDeclaration, Modifier.ModifierKeyword.STATIC_KEYWORD);
        Type typeOuter = TypeUtils.createType(unit.getAST(), fullName, inner.getItem1());
        Type typeInner = TypeUtils.createType(unit.getAST(), fullName, inner.getItem2());
        CompilationUnit outer = ClassUtils.getTemplateClass(unit, typeOuter);
        CompilationUnit innerClass = ClassUtils.getTemplateClass(unit, typeInner);
        TypeDeclaration outerTypeDeclaration = ClassUtils.getTypeDeclaration(outer);
        TypeDeclaration declaration = InnerClassUtils.getTypeDeclarationIfNeeded(inner.getItem2(), outerTypeDeclaration, innerClass);
        fieldDeclaration = (FieldDeclaration) ASTNode.copySubtree(declaration.getAST(), fieldDeclaration);
        declaration.bodyDeclarations().add(fieldDeclaration);
        ClassUtils.addModifier(declaration, Modifier.ModifierKeyword.STATIC_KEYWORD);
        argType = type;
      }
      else if (arg instanceof CastExpression) {
        throw new RuntimeException();
      }
      //Needed to resolve a bug in eclipse JDT.
      if (argType.toString().contains(".") && !argType.toString().contains("syntethic")) {
        String typeName = NameUtils.extractSimpleName(argType);
        argType = ImportUtils.getTypeBasedOnImports(unit, typeName);
      }
      argTypes.add(argType);
    }
    return argTypes;
  }

  public static List<String> getVarNames(List<ASTNode> arguments) {
    List<String> varNames = new ArrayList<>();
    int i = 0;
    for(ASTNode arg : arguments) {
      varNames.add("v_" + i++);
    }
    return varNames;
  }

  private static Type getArgType(CompilationUnit unit, MethodInvocation invocation) {
    Type argType;
    Type newParamType = null;
    if (invocation != null) {
      Type classType = TypeUtils.extractType((invocation).getExpression(), invocation.getAST());
      if (classType.isParameterizedType()) {
        ParameterizedType parameterizedType = (ParameterizedType) classType;
        Type paramType = (Type) parameterizedType.typeArguments().get(0);
        String simpleName = NameUtils.extractSimpleName(paramType);
        newParamType = ImportUtils.getTypeBasedOnImports(unit, simpleName);
      }
    }
    if (newParamType != null) {
      argType = newParamType;
    }
    else {
      argType = SyntheticClassUtils.getSyntheticType(unit.getAST());
    }
    return argType;
  }

}
