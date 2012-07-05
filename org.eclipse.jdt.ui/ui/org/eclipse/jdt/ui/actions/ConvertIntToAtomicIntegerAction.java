package org.eclipse.jdt.ui.actions;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringExecutionStarter;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
//import org.eclipse.jdt.internal.ui.refactoring.concurrency.ConvertToAtomicIntegerWizard;

/**
 * @since 3.9
 */
public class ConvertIntToAtomicIntegerAction extends SelectionDispatchAction {

	private IField fField;

	private JavaEditor fEditor;

	/**
	 * Note: This constructor is for internal use only. Clients should not call this constructor.
	 * @param editor the java editor
	 *
	 * @noreference This constructor is not intended to be referenced by clients.
	 */
	public ConvertIntToAtomicIntegerAction(JavaEditor editor) {
		this(editor.getEditorSite());
		fEditor= editor;
		setEnabled(SelectionConverter.canOperateOn(fEditor));
	}

	/**
	 * Creates a new <code>ConvertAnonymousToNestedAction</code>. The action requires
	 * that the selection provided by the site's selection provider is of type
	 * <code>org.eclipse.jface.viewers.IStructuredSelection</code>.
	 *
	 * @param site the site providing context information for this action
	 */
	public ConvertIntToAtomicIntegerAction(IWorkbenchSite site) {
		super(site);
		setText(ActionMessages.AtomicIntegerAction_label);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.ATOMIC_INTEGER_ACTION);
	}
	
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void selectionChanged(ITextSelection selection) {
		setEnabled(true);
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 * 
	 * @param selection the Java text selection
	 * @noreference This method is not intended to be referenced by clients.
	 */
	@Override
	public void selectionChanged(JavaTextSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isConvertAtomicIntegerAvailable(selection));
		} catch (JavaModelException e) {
			// http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);//no UI
		}
	}
	
	@Override
	public void selectionChanged(IStructuredSelection selection) {
		try {
			setEnabled(RefactoringAvailabilityTester.isConvertAtomicIntegerAvailable(selection));
		} catch (JavaModelException e) {
			if (JavaModelUtil.isExceptionToBeLogged(e))
				JavaPlugin.log(e);
			setEnabled(false);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(IStructuredSelection selection) {
		try {
			IField firstElement= (IField)selection.getFirstElement();
			if (!ActionUtil.isEditable(getShell(), firstElement))
				return;
			if (RefactoringAvailabilityTester.isConvertAtomicIntegerAvailable(selection)) {
				run(firstElement);
			}
		} catch (JavaModelException e) {
			ExceptionHandler.handle(e, RefactoringMessages.OpenRefactoringWizardAction_refactoring, RefactoringMessages.OpenRefactoringWizardAction_exception);
		}
	}

	//---- private helpers --------------------------------------------------------

	/*
	 * Should be private. But got shipped in this state in 2.0 so changing this is a
	 * breaking API change.
	 */
	public void run(IField field) {
		if (! ActionUtil.isEditable(fEditor, getShell(), field))
			return;
		//RefactoringExecutionStarter.startSelfEncapsulateRefactoring(field, getShell());
		RefactoringExecutionStarter.startAtomicIntegerRefactoring(field, getShell());
	}
	
	/**
	 * @see IActionDelegate#run(IAction)
	 */
//	public void run(IAction action) {
//		
//		try {
//			if (fField != null && shell != null && isConvertToAtomicIntegerAvailable()) {
//				ConvertToAtomicIntegerRefactoring refactoring= new ConvertToAtomicIntegerRefactoring(fField);
//				run(new ConvertToAtomicIntegerWizard(refactoring, "Convert to Atomic Integer"), //$NON-NLS-1$
//							shell, "Convert to Atomic Integer"); //$NON-NLS-1$
//			} else {
//				MessageDialog.openError(shell, "Error ConvertToAtomicInteger", //$NON-NLS-1$
//							"ConvertToAtomicInteger not applicable for current selection");  //$NON-NLS-1$ 
//			}
//		} catch (JavaModelException e) {
//			e.printStackTrace();
//		}
//	}
	
	public void run(RefactoringWizard wizard, Shell parent, String dialogTitle) {
		
		try {
			RefactoringWizardOpenOperation operation= new RefactoringWizardOpenOperation(wizard);
			operation.run(parent, dialogTitle);
		} catch (InterruptedException exception) {
			// Do nothing
		}
	}

	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	@Override
	public void run(ITextSelection selection) {
		try {
			if (!ActionUtil.isEditable(fEditor))
				return;
			IJavaElement[] elements= SelectionConverter.codeResolve(fEditor);
			if (elements.length != 1 || !(elements[0] instanceof IField)) {
				MessageDialog.openInformation(getShell(), 
						ActionMessages.AtomicIntegerAction_dialog_title, ActionMessages.AtomicIntegerAction_dialog_unavailable);
				return;
			}
			IField field= (IField)elements[0];

			if (!RefactoringAvailabilityTester.isConvertAtomicIntegerAvailable(field)) {
				MessageDialog.openInformation(getShell(),
						ActionMessages.AtomicIntegerAction_dialog_title, ActionMessages.AtomicIntegerAction_dialog_unavailable);
				return;
			}
			run(field);
		} catch (JavaModelException exception) {
			JavaPlugin.log(exception);
			return;
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
		try {
			action.setEnabled(isConvertToAtomicIntegerAvailable());
		} catch (JavaModelException exception) {
			action.setEnabled(false);
		}
	}

	private boolean isConvertToAtomicIntegerAvailable() throws JavaModelException {
		return ((fField != null)
				&& (fField.exists())
				&& (fField.isStructureKnown())
				&& (!fField.getDeclaringType().isAnnotation())
				&& ("I".equals(fField.getTypeSignature()))); //$NON-NLS-1$
	}

}
