package org.eclipse.jdt.ui.actions;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConvertToAtomicIntegerRefactoring;

import org.eclipse.jdt.internal.ui.refactoring.concurrency.ConvertToAtomicIntegerWizard;

public class ConvertIntToAtomicIntegerAction implements IObjectActionDelegate {

	private Shell shell;
	private IField fField;

	/**
	 * Constructor for Action1.
	 */
	public ConvertIntToAtomicIntegerAction() {
		
		super();
	}

	/**
	 * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
	 */
	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		
		shell = targetPart.getSite().getShell();
	}
	
	/**
	 * @see IActionDelegate#run(IAction)
	 */
	public void run(IAction action) {
		
		try {
			if (fField != null && shell != null && isConvertToAtomicIntegerAvailable()) {
				ConvertToAtomicIntegerRefactoring refactoring= new ConvertToAtomicIntegerRefactoring(fField);
				run(new ConvertToAtomicIntegerWizard(refactoring, "Convert to Atomic Integer"), //$NON-NLS-1$
							shell, "Convert to Atomic Integer"); //$NON-NLS-1$
			} else {
				MessageDialog.openError(shell, "Error ConvertToAtomicInteger", //$NON-NLS-1$
							"ConvertToAtomicInteger not applicable for current selection");  //$NON-NLS-1$ 
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}
	
	public void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
		
		try {
			RefactoringWizardOpenOperation operation= new RefactoringWizardOpenOperation(wizard);
			operation.run(parent, dialogTitle);
		} catch (InterruptedException exception) {
			// Do nothing
		}
	}

	/**
	 * @see IActionDelegate#selectionChanged(IAction, ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		
		fField= null;
		
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection extended= (IStructuredSelection) selection;
			Object[] elements= extended.toArray();
			if (elements.length == 1 && elements[0] instanceof IField) {
				fField= (IField) elements[0];
			}
		}
//		try {
//			action.setEnabled(isConvertToAtomicIntegerAvailable());
//		} catch (JavaModelException exception) {
//			action.setEnabled(false);
//		}
	}

	private boolean isConvertToAtomicIntegerAvailable() throws JavaModelException {
		
		return ((fField != null)
				&& (fField.exists())
				&& (fField.isStructureKnown())
				&& (!fField.getDeclaringType().isAnnotation())
				&& ("I".equals(fField.getTypeSignature()))); //$NON-NLS-1$
	}

}
