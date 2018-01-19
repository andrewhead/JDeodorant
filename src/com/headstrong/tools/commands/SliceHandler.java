package com.headstrong.tools.commands;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.AbstractMethodDeclaration;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.CompilationErrorDetectedException;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.decomposition.cfg.CFG;
import gr.uom.java.ast.decomposition.cfg.GraphEdge;
import gr.uom.java.ast.decomposition.cfg.GraphNode;
import gr.uom.java.ast.decomposition.cfg.PDG;
import gr.uom.java.ast.decomposition.cfg.PDGDependence;
import gr.uom.java.ast.decomposition.cfg.PDGNode;

public class SliceHandler extends AbstractHandler {
    
    public static final String EXAMPLES_PACKAGE = "examples";
    
    public CompilationUnit buildCompilationUnit(Collection<PDGNode> sliceNodes) {
        
        Collections.sort(new ArrayList<PDGNode>(sliceNodes), new Comparator<PDGNode>() {
            public int compare(PDGNode node1, PDGNode node2) {
                int node1Position = node1.getStatement().getStatement().getStartPosition();
                int node2Position = node2.getStatement().getStatement().getStartPosition();
                return (node1Position < node2Position) ? -1
                        : (node1Position > node2Position) ? 1 : 0;
            }
        });
        
        AST ast = AST.newAST(AST.JLS4);
        CompilationUnit newCu = ast.newCompilationUnit();
        
        PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
        packageDeclaration.setName(ast.newSimpleName("exmaples"));
        
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
        for (PDGNode pdgNode : sliceNodes) {
            block.statements().add(
                    ASTNode.copySubtree(newCu.getAST(), pdgNode.getStatement().getStatement()));
        }
        return newCu;
    }
    
    public Object execute(ExecutionEvent event) throws ExecutionException {
        
        System.out.println("Executing SliceHandler event");
        
        // Get the current file and selection
        ITextEditor activeEditor = (ITextEditor) HandlerUtil.getActiveEditor(event);
        ITextSelection sel = (ITextSelection) activeEditor.getSelectionProvider().getSelection();
        
        if (activeEditor == null)
            return null;
        
        // Get the project that contains the currently-selected editor
        IEditorInput input = activeEditor.getEditorInput();
        IProject project = input.getAdapter(IProject.class);
        if (project == null) {
            IResource resource = input.getAdapter(IResource.class);
            if (resource != null) {
                project = resource.getProject();
            }
        }
        
        // Parse the current file
        ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(activeEditor.getEditorInput());
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
        IJavaProject javaProject = typeRoot.getJavaProject();
        try {
            new ASTReader(javaProject, null);
        } catch (CompilationErrorDetectedException e) {
            System.out.println("Compilation error.  Exiting: " + e.toString());
            return null;
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
        
        CompilationUnit sliceCu = buildCompilationUnit(visitedNodes);
        
        // Create a new Java project to hold the example.
        // We split the new examples from the old project, because we don't want
        // compilation errors in the new example to affect whether the old project
        // can build. We may need to re-build the old project mid-extraction!
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IProject exampleProject = root.getProject("GeneratedExamples");
        try {
            if (!exampleProject.exists()) {
                exampleProject.create(null);
            }
            exampleProject.open(null);
            IProjectDescription description = exampleProject.getDescription();
            description.setNatureIds(new String[] { JavaCore.NATURE_ID });
            exampleProject.setDescription(description, null);
        } catch (CoreException e) {
            System.out.println("Couldn't create example project");
        }
        IJavaProject javaExampleProject = JavaCore.create(exampleProject);
        
        // Then create a folder to hold the example
        IFolder srcFolder = exampleProject.getFolder("src");
        try {
            if (!srcFolder.exists()) {
                srcFolder.create(IResource.NONE, true, null);
            }
        } catch (CoreException coreException) {
            System.out.println("Unable to create file: " + coreException.toString());
        }
        
        // Set the class path to include the source directory.
        // https://sdqweb.ipd.kit.edu/wiki/JDT_Tutorial:_Creating_Eclipse_Java_Projects_Programmatically
        IPackageFragmentRoot packageRoot = javaExampleProject.getPackageFragmentRoot(srcFolder);
        try {
            List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();
            IVMInstall vmInstall = JavaRuntime.getDefaultVMInstall();
            for (LibraryLocation location: JavaRuntime.getLibraryLocations(vmInstall)) {
                entries.add(JavaCore.newLibraryEntry(location.getSystemLibraryPath(), null, null));
            }
            entries.add(JavaCore.newSourceEntry(packageRoot.getPath()));
            javaExampleProject.setRawClasspath(entries.toArray(new IClasspathEntry[entries.size()]), null);
        } catch (JavaModelException e) {
            System.out.println("Error setting class path for new project:" + e.toString());
        }
        
        // Create a class in the package fragment for the example.
        ICompilationUnit exampleCu = null;
        try {
            packageRoot.open(null);
            IPackageFragment fragment = packageRoot.createPackageFragment("", true, null);
            fragment.open(null);
            exampleCu = fragment.createCompilationUnit("ExtractedExample.java", sliceCu.toString(),
                    true, null);
        } catch (JavaModelException e) {
            System.out.println("Couldn't create compilation unit: " + e.toString());
        }
        
        // Open the example for business
        try {
            if (exampleCu != null)
                exampleCu.open(null);
        } catch (JavaModelException e) {
            System.out.println("Couldn't open file: " + e.toString());
        }
        
        // Format the example so it looks pretty.
        CodeFormatter formatter = ToolFactory.createCodeFormatter(null);
        try {
            exampleCu.becomeWorkingCopy(null);
            TextEdit formatEdit = formatter.format(CodeFormatter.K_COMPILATION_UNIT,
                    exampleCu.getSource(), exampleCu.getSourceRange().getOffset(),
                    exampleCu.getSourceRange().getLength(), 0, null);
            exampleCu.applyTextEdit(formatEdit, null);
            exampleCu.commitWorkingCopy(true, null);
        } catch (JavaModelException e) {
            System.out.println("Error formatting file: " + e.toString());
        } catch (IllegalArgumentException e) {
            System.out.println("Error formatting file: " + e.toString());
        }
        
        try {
            IResource resource = exampleCu.getUnderlyingResource();
            if (resource.getType() == IResource.FILE) {
                IFile exampleFile = (IFile) resource;
                IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
                        .getActivePage();
                IDE.openEditor(page, exampleFile);
            }
        } catch (JavaModelException e) {
            System.out.println("Couldn't get resource for cu: " + e.toString());
        } catch (PartInitException e) {
            System.out.println("Couldn't open editor for file: " + e.toString());
        }
        
        System.out.println("All finished with this method");
        return null;
    }
    
}
