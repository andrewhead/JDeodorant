package com.headstrong.tools.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.AbstractMethodDeclaration;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.decomposition.cfg.CFG;
import gr.uom.java.ast.decomposition.cfg.GraphEdge;
import gr.uom.java.ast.decomposition.cfg.GraphNode;
import gr.uom.java.ast.decomposition.cfg.PDG;
import gr.uom.java.ast.decomposition.cfg.PDGDependence;
import gr.uom.java.ast.decomposition.cfg.PDGNode;

public class SliceHandler extends AbstractHandler {
    
    public Object execute(ExecutionEvent event) throws ExecutionException {
        
        System.out.println("Executing SliceHandler event");
        
        // Get the current file and selection
        ITextEditor editor = (ITextEditor) HandlerUtil.getActiveEditor(event);
        ITextSelection sel = (ITextSelection) editor.getSelectionProvider().getSelection();
        
        // Parse the current file
        ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(editor.getEditorInput());
        ICompilationUnit icu = (ICompilationUnit) typeRoot.getAdapter(ICompilationUnit.class);
        ASTParser parser = ASTParser.newParser(AST.JLS4);
        parser.setSource(icu);
        parser.setResolveBindings(true);
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        
        // Find the node where the selection was made
        NodeFinder finder = new NodeFinder(cu, sel.getOffset(), sel.getLength());
        ASTNode node = finder.getCoveringNode();
        
        // Parse the project using JDeodorant. We parse with JDeodorant
        // instead of just the Eclipse JDT because it provides parse tree
        // objects that can be used in building the PDG.
        IJavaProject project = typeRoot.getJavaProject();
        try {
            new ASTReader(project, null);
        } catch (CompilationErrorDetectedException e) {
            System.out.println("Compilation error.  Exiting.");
        }
        SystemObject systemObject = ASTReader.getSystemObject();
        
        // Get the class and method that this line belongs to.
        // XXX: This probably doesn't handle lambdas and nested classes.
        ClassObject classObject = systemObject
                .getClassObject(typeRoot.findPrimaryType().getFullyQualifiedName());
        MethodDeclaration methodDeclaration = (MethodDeclaration) ASTNodes.getParent(node,
                ASTNode.METHOD_DECLARATION);
        IMethod method = (IMethod) methodDeclaration.resolveBinding().getJavaElement();
        AbstractMethodDeclaration methodObject = systemObject.getMethodObject(method);
        
        // Get the statement that the variable was selected in
        Statement statement = null;
        for (ASTNode parent = node; parent != null; parent = parent.getParent()) {
            if (parent instanceof Statement) {
                statement = (Statement) parent;
                break;
            }
        }
        
        // Assemble a PDG for the method
        CFG cfg = new CFG(methodObject);
        PDG pdg = new PDG(cfg, classObject.getIFile(),
                classObject.getFieldsAccessedInsideMethod(methodObject), null);
        
        // Find the node in the PDG that contains this statement
        PDGNode chosenNode = null;
        for (GraphNode graphNode : pdg.getNodes()) {
            if (graphNode instanceof PDGNode) {
                PDGNode pdgNode = (PDGNode) graphNode;
                Statement nodeStatement = pdgNode.getStatement().getStatement();
                if ((statement.getStartPosition() == nodeStatement.getStartPosition()
                        && statement.getLength() == nodeStatement.getLength())) {
                    chosenNode = pdgNode;
                }
            }
        }
        
        // Create a slice by traversing the dependency tree
        List<PDGNode> nodesToVisit = new ArrayList<PDGNode>();
        List<PDGNode> visitedNodes = new ArrayList<PDGNode>();
        nodesToVisit.add(chosenNode);
        while (nodesToVisit.size() > 0) {
            List<PDGNode> queuedNodes = new ArrayList<PDGNode>();
            for (PDGNode pdgNode : nodesToVisit) {
                for (GraphEdge edge : pdg.getEdges()) {
                    if (edge.getDst() == pdgNode && edge instanceof PDGDependence) {
                        PDGNode dependency = (PDGNode) edge.getSrc();
                        // XXX: This heuristic should be fixed. Currently, we check to see if
                        // there's a CFG node for a PDG node to tell whether it's just the line that
                        // declares the function. There's probably a better way to do it.
                        if (dependency.getCFGNode() != null && !visitedNodes.contains(dependency)
                                && !queuedNodes.contains(dependency)) {
                            queuedNodes.add((PDGNode) dependency);
                        }
                    }
                }
                visitedNodes.add(pdgNode);
            }
            nodesToVisit = queuedNodes;
        }
        
        Collections.sort(visitedNodes, new Comparator<PDGNode>() {
            public int compare(PDGNode node1, PDGNode node2) {
                int node1Position = node1.getStatement().getStatement().getStartPosition();
                int node2Position = node2.getStatement().getStatement().getStartPosition();
                return (node1Position < node2Position) ? -1
                        : (node1Position > node2Position) ? 1 : 0;
            }
        });
        
        AST ast = AST.newAST(AST.JLS4);
        CompilationUnit newCu = ast.newCompilationUnit();
        
        // Skip the package declaration: shouldn't be needed for an example.
        // PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
        // packageDeclaration.setName(ast.newSimpleName("klazz"));
        
        ImportDeclaration importDeclaration = ast.newImportDeclaration();
        importDeclaration.setName(ast.newName(new String[] { "java", "util", "ArrayList" }));
        newCu.imports().add(importDeclaration);
        
        TypeDeclaration typeDeclaration = ast.newTypeDeclaration();
        typeDeclaration.setName(ast.newSimpleName("ExtractedExample"));
        typeDeclaration.modifiers().add(ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD));
        newCu.types().add(typeDeclaration);
        
        MethodDeclaration methodDeclaration2 = ast.newMethodDeclaration();
        methodDeclaration2.setName(ast.newSimpleName("main"));
        methodDeclaration2.modifiers().add(ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD));
        methodDeclaration2.modifiers().add(ast.newModifier(ModifierKeyword.STATIC_KEYWORD));
        typeDeclaration.bodyDeclarations().add(methodDeclaration2);
        
        Block block = ast.newBlock();
        methodDeclaration2.setBody(block);
        
        // Add statements for the full slice to the program
        for (PDGNode pdgNode : visitedNodes) {
            block.statements().add(
                    ASTNode.copySubtree(newCu.getAST(), pdgNode.getStatement().getStatement()));
        }
        System.out.println(newCu);
        
        System.out.println("All finished with this method");
        return null;
    }
    
}
