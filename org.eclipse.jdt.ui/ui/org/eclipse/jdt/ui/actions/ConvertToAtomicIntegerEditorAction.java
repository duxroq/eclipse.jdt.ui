package org.eclipse.jdt.ui.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorActionDelegate;
import org.eclipse.ui.IEditorPart;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConvertToAtomicIntegerRefactoring;

import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.refactoring.concurrency.ConvertToAtomicIntegerWizard;
//import mit.edu.concurrencyrefactorings.util.JavaEditor;
//import mit.edu.concurrencyrefactorings.util.SelectionConverter;

/**
 * @since 3.9
 */
public class ConvertToAtomicIntegerEditorAction implements IEditorActionDelegate{

	private JavaEditor fEditor;
	private ITextSelection fTextSelection;

	public void setActiveEditor(IAction action, IEditorPart targetEditor) {
		
		if (targetEditor instanceof JavaEditor) {
			fEditor= (JavaEditor) targetEditor;
		}
	}

	public void run(IAction action) {
		
		IJavaElement[] elements;
		
		try {
			elements= SelectionConverter.codeResolveForked(fEditor, true);
			if ((elements.length == 1) && (elements[0] instanceof IField)) {
				IField field= (IField) elements[0];
				
				if (isRefactoringAvailableFor(field)) {
					ConvertToAtomicIntegerRefactoring refactoring= new ConvertToAtomicIntegerRefactoring(field);
					run(new ConvertToAtomicIntegerWizard(refactoring, "Convert to Atomic Integer"), getShell(), "Convert to Atomic Integer"); //$NON-NLS-1$ //$NON-NLS-2$
					return;
				}
			}
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JavaModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		MessageDialog.openError(getShell(), "Error ConvertToAtomicInteger", "ConvertToAtomicInteger not applicable for current selection");  //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private boolean isRefactoringAvailableFor(IField field) throws JavaModelException {
		
		return field != null
				&& field.exists()
				&& field.isStructureKnown()
				&& !field.getDeclaringType().isAnnotation()
				&& "I".equals(field.getTypeSignature()); //$NON-NLS-1$
	}

	public void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
		
		try {
			RefactoringWizardOpenOperation operation= new RefactoringWizardOpenOperation(wizard);
			operation.run(parent, dialogTitle);
		} catch (InterruptedException exception) {
			// Do nothing
		}
	}
	
	
	private Shell getShell() {
		
		return fEditor.getSite().getShell();
	}

	public void selectionChanged(IAction action, ISelection selection) {
		
		if (selection instanceof ITextSelection) {
			fTextSelection= (ITextSelection) selection;
		}
	}
}
