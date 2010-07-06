package gr.uom.java.jdeodorant.refactoring.views;

import gr.uom.java.jdeodorant.refactoring.manipulators.ExtractClassRefactoring;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

public class ExtractClassInputPage extends UserInputWizardPage {
	
	private ExtractClassRefactoring refactoring;
	private IPackageFragment parentPackage;
	private List<String> parentPackageClassNames;
	private List<String> javaLangClassNames;
	private Map<Text, String> textMap;
	private Map<Text, String> defaultNamingMap;
	
	public ExtractClassInputPage(ExtractClassRefactoring refactoring) {
		super("State/Strategy Type Names");
		this.refactoring = refactoring;
		ICompilationUnit sourceCompilationUnit = (ICompilationUnit)refactoring.getSourceCompilationUnit().getJavaElement();
		this.parentPackage = (IPackageFragment)sourceCompilationUnit.getParent();
		this.parentPackageClassNames = new ArrayList<String>();
		try {
			for(ICompilationUnit compilationUnit : parentPackage.getCompilationUnits()) {
				String className = compilationUnit.getElementName();
				parentPackageClassNames.add(className.substring(0, className.indexOf(".java")));
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		this.javaLangClassNames = new ArrayList<String>();
		this.javaLangClassNames.add("Boolean");
		this.javaLangClassNames.add("Byte");
		this.javaLangClassNames.add("Character");
		this.javaLangClassNames.add("Class");
		this.javaLangClassNames.add("Double");
		this.javaLangClassNames.add("Enum");
		this.javaLangClassNames.add("Error");
		this.javaLangClassNames.add("Exception");
		this.javaLangClassNames.add("Float");
		this.javaLangClassNames.add("Integer");
		this.javaLangClassNames.add("Long");
		this.javaLangClassNames.add("Math");
		this.javaLangClassNames.add("Number");
		this.javaLangClassNames.add("Object");
		this.javaLangClassNames.add("Package");
		this.javaLangClassNames.add("Process");
		this.javaLangClassNames.add("Runtime");
		this.javaLangClassNames.add("Short");
		this.javaLangClassNames.add("String");
		this.javaLangClassNames.add("StringBuffer");
		this.javaLangClassNames.add("StringBuilder");
		this.javaLangClassNames.add("System");
		this.javaLangClassNames.add("Thread");
		this.javaLangClassNames.add("Void");
		this.textMap = new LinkedHashMap<Text, String>();
		this.defaultNamingMap = new LinkedHashMap<Text, String>();
	}

	public void createControl(Composite parent) {
		Composite result= new Composite(parent, SWT.NONE);
		setControl(result);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		result.setLayout(layout);
		
		FontData labelFontData = new FontData("Segoe UI", 9, SWT.NORMAL);
		Font font = new Font(parent.getDisplay(), labelFontData);
		
		Label extractedClassNameLabel = new Label(result, SWT.NONE);
		extractedClassNameLabel.setText("Extracted Class Name");
		extractedClassNameLabel.setFont(font);
		
		Text extractedClassNameField = new Text(result, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
		extractedClassNameField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		extractedClassNameField.setText(refactoring.getTargetTypeName());
		
		textMap.put(extractedClassNameField, refactoring.getTargetTypeName());
		defaultNamingMap.put(extractedClassNameField, refactoring.getTargetTypeName());
		
		final Button restoreButton = new Button(result, SWT.PUSH);
		restoreButton.setText("Restore Defaults");
		
		for(Text field : textMap.keySet()) {
			field.addModifyListener(new ModifyListener() {
				public void modifyText(ModifyEvent e) {
					handleInputChanged();
				}
			});
		}
		
		restoreButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				for(Text field : defaultNamingMap.keySet()) {
					field.setText(defaultNamingMap.get(field));
				}
			}
		});
		
		handleInputChanged();
	}
	
	private void handleInputChanged() {
		String classNamePattern = "[a-zA-Z_][a-zA-Z0-9_]*";
		for(Text text : textMap.keySet()) {
			if(!Pattern.matches(classNamePattern, text.getText())) {
				setPageComplete(false);
				String message = "Type name \"" + text.getText() + "\" is not valid";
				setMessage(message, ERROR);
				return;
			}
			else if(parentPackageClassNames.contains(text.getText())) {
				setPageComplete(false);
				String message = "A Type named \"" + text.getText() + "\" already exists in package " + parentPackage.getElementName();
				setMessage(message, ERROR);
				return;
			}
			else if(javaLangClassNames.contains(text.getText())) {
				setPageComplete(false);
				String message = "Type \"" + text.getText() + "\" already exists in package java.lang";
				setMessage(message, ERROR);
				return;
			}
			else {
				refactoring.setTargetTypeName(text.getText());
			}
		}
		setPageComplete(true);
		setMessage("", NONE);
	}

}
