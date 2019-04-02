package br.ufcg.spg.transformation;

import br.ufcg.spg.stub.StubUtils;
import org.eclipse.jdt.core.dom.*;

import java.util.List;

public class ImportUtils {
  public static Type getTypeBasedOnImports(CompilationUnit unit, String className) {
    AST ast = unit.getAST();
    ASTNode imp = findImport(unit, className);
    return getTypeFromImport(className, ast, imp);
  }

  public static Type getTypeFromImport(String classname, AST ast, ASTNode imp) {
    Name name;
    SimpleName sname = ast.newSimpleName(classname);
    if (imp != null) {
      name = ast.newName(imp.toString().substring(7, imp.toString().lastIndexOf(".")));
    }
    else {
      name = ast.newName("java.lang");
      System.out.println("imported type: " + classname);
    }
    return ast.newNameQualifiedType(name, sname);
  }

  public static ASTNode findImport(CompilationUnit unit, String typeName) {
    List<ASTNode> imports = StubUtils.getNodes(unit, ASTNode.IMPORT_DECLARATION);
    for (ASTNode imp : imports) {
      String impStr = imp.toString();
      String imported = getClassNameFromImport(impStr);
      if (imported.equals(typeName)) {
        return imp;
      }
    }
    return null;
  }

  public static String getClassNameFromImport(String impStr) {
    return impStr.substring(impStr.lastIndexOf(".") + 1, impStr.length() - 2);
  }
}
