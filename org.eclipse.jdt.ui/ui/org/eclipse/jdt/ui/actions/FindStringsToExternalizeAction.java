/*******************************************************************************
 * Copyright (c) 2000, 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v0.5 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v05.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.ui.actions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;

import org.eclipse.jdt.internal.corext.refactoring.base.Refactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.ActionMessages;
import org.eclipse.jdt.internal.ui.refactoring.actions.ListDialog;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.viewsupport.ListContentProvider;

import org.eclipse.jdt.ui.JavaElementLabelProvider;

/**
 * Find all Strings in a package or project that aren't externalized yet.
 * 
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 2.0
 */
public class FindStringsToExternalizeAction extends SelectionDispatchAction {

	private static final String FIND_STRINGS_CHECKBOX= "FindStringAction.checkbox"; //$NON-NLS-1$
	
	/**
	 * Creates a new <code>FindStringsToExternalizeAction</code>.
	 * 
	 * @param site the site providing context information for this action
	 */
	public FindStringsToExternalizeAction(UnifiedSite site) {
		super(site);
		setText(ActionMessages.getString("FindStringsToExternalizeAction.label")); //$NON-NLS-1$
	}
	
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void selectionChanged(IStructuredSelection selection) {
		setEnabled(computeEnablementState(selection));
	}
	
	private boolean computeEnablementState(IStructuredSelection selection) {
		if (selection.size() == 0)
			return false;
		for (Iterator iter= selection.iterator(); iter.hasNext();) {
			Object element= (Object)iter.next();
			if (!(element instanceof IJavaElement))
				return false;
			int elementType= ((IJavaElement)element).getElementType();
			if (elementType !=  IJavaElement.PACKAGE_FRAGMENT && elementType != IJavaElement.PACKAGE_FRAGMENT_ROOT &&
					elementType != IJavaElement.JAVA_PROJECT)
				return false;
		}
		return true;
	}
	
	/* (non-Javadoc)
	 * Method declared on SelectionDispatchAction.
	 */
	protected void run(final IStructuredSelection selection) {
		new BusyIndicator().showWhile(getShell().getDisplay(), new Runnable() {
			public void run() {
				doRun(selection);
			}
		});
	}

	private void doRun(IStructuredSelection selection) {
		List elements= getSelectedElementList(selection);
		if (elements == null || elements.isEmpty())
			return;
		
		try{
			List l= new ArrayList();	
			for (Iterator iter= elements.iterator(); iter.hasNext();) {
				IJavaElement element= (IJavaElement) iter.next();
				if (element.getElementType() == IJavaElement.PACKAGE_FRAGMENT)
					l.addAll(analyze((IPackageFragment) element));
				else if (element.getElementType() == IJavaElement.PACKAGE_FRAGMENT_ROOT)
					l.addAll(analyze((IPackageFragmentRoot) element));
				 if (element.getElementType() == IJavaElement.JAVA_PROJECT)
					l.addAll(analyze((IJavaProject) element));
			}
			showResults(l);
		} catch(JavaModelException e) {
			ExceptionHandler.handle(e, 
				ActionMessages.getString("FindStringsToExternalizeAction.dialog.title"), //$NON-NLS-1$
				ActionMessages.getString("FindStringsToExternalizeAction.error.message")); //$NON-NLS-1$
		}
	}
	
	private void showResults(List l) {
		if (noStrings(l))
			MessageDialog.openInformation(getShell(), 
				ActionMessages.getString("FindStringsToExternalizeAction.dialog.title"), //$NON-NLS-1$
				ActionMessages.getString("FindStringsToExternalizeAction.noStrings")); //$NON-NLS-1$
		else	
			new NonNLSListDialog(getShell(), l, countStrings(l)).open();
	}
	
	private boolean noStrings(List l) {
		for (Iterator iter= l.iterator(); iter.hasNext();) {
			NonNLSElement element= (NonNLSElement)iter.next();
			if (element.count != 0)
				return false;
		}
		return true;
	}
	
	/*
	 * returns List of Strings
	 */
	private List analyze(IPackageFragment pack) throws JavaModelException{
		if (pack == null)
			return new ArrayList(0);
			
		ICompilationUnit[] cus= pack.getCompilationUnits();
		
		List l= new ArrayList(cus.length);
		for (int i= 0; i < cus.length; i++)
			l.add(analyze(cus[i]));
		return l;					
	}

	/*
	 * returns List of Strings
	 */	
	private List analyze(IPackageFragmentRoot sourceFolder) throws JavaModelException{
		IJavaElement[] children= sourceFolder.getChildren();
		List result= new ArrayList();
		for (int i= 0; i < children.length; i++) {
			IJavaElement iJavaElement= children[i];
			if (iJavaElement.getElementType() == IJavaElement.PACKAGE_FRAGMENT){
				IPackageFragment pack= (IPackageFragment)iJavaElement;
				if (! pack.isReadOnly())
					result.addAll(analyze(pack));
			}	
		}
		return result;
	}
	
	/*
	 * returns List of Strings
	 */
	private List analyze(IJavaProject project) throws JavaModelException{
		IPackageFragment[] packs= project.getPackageFragments();
		List result= new ArrayList();
		for (int i= 0; i < packs.length; i++) {
			if (! packs[i].isReadOnly())
				result.addAll(analyze(packs[i]));
		}
		return result;		
	}
	
	/*
	 * @param List of NonNLSElements
	 */
	private static int countStrings(List elements){
		int i= 0;
		for (Iterator iter= elements.iterator(); iter.hasNext(); )
			i += (((NonNLSElement)iter.next()).count);
		return i;	
	} 
	
	private NonNLSElement analyze(ICompilationUnit cu) throws JavaModelException{
		return new NonNLSElement(cu, countNotExternalizedStrings(cu));
	}
	
	private int countNotExternalizedStrings(ICompilationUnit cu){
		try{
			NLSLine[] lines= NLSScanner.scan(cu);
			int result= 0;
			for (int i= 0; i < lines.length; i++) {
				result += countNotExternalizedStrings(lines[i]);
			}
			return result;
		}catch(JavaModelException e) {
			ExceptionHandler.handle(e, 
				ActionMessages.getString("FindStringsToExternalizeAction.dialog.title"), //$NON-NLS-1$
				ActionMessages.getString("FindStringsToExternalizeAction.error.message")); //$NON-NLS-1$
			return 0;
		}catch(InvalidInputException iie) {
			JavaPlugin.log(iie);
			return 0;
		}	
	}

	private int countNotExternalizedStrings(NLSLine line){
		int result= 0;
		NLSElement[] elements= line.getElements();
		for (int i= 0; i < elements.length; i++){
			if (! elements[i].hasTag())
				result++;
		}
		return result;
	}

	/**
	 * returns <code>List</code> of <code>IPackageFragments</code>,  <code>IPackageFragmentRoots</code> or 
	 * <code>IJavaProjects</code> (all entries are of the same kind)
	 */
	private static List getSelectedElementList(IStructuredSelection selection) {
		if (selection == null)
			return null;
			
		return selection.toList();
	}
		
	private static LabelProvider createLabelProvider() {
		return new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT){ 
			public String getText(Object element) {
				NonNLSElement nlsel= (NonNLSElement)element;
				String elementName= nlsel.cu.getElementName();
				try{
					elementName= Refactoring.getResource(nlsel.cu).getProjectRelativePath().toString();
				}catch (JavaModelException e){
				}	
				return ActionMessages.getFormattedString(
					"FindStringsToExternalizeAction.foundStrings", //$NON-NLS-1$
					new Object[] {new Integer(nlsel.count), elementName} );
			}		
			public Image getImage(Object element) {
				return super.getImage(((NonNLSElement)element).cu);
			}
		};
	}
	
	private static boolean loadCheckboxState(boolean defaultValue){
		String res= JavaPlugin.getDefault().getDialogSettings().get(FIND_STRINGS_CHECKBOX);
		if (res == null)
			return defaultValue;
		return Boolean.valueOf(res).booleanValue();	
	}
	
	private static void storeCheckboxState(boolean selected){
		JavaPlugin.getDefault().getDialogSettings().put(FIND_STRINGS_CHECKBOX, selected);	
	}
		
	//-------private classes --------------
		
	private static class NonNLSListDialog extends ListDialog {
		
		private static final int OPEN_BUTTON_ID= IDialogConstants.CLIENT_ID + 1;
		
		private Button fCheckbox;
		private Button fOpenButton;
		
		NonNLSListDialog(Shell parent, Object input, int count) {
			super(parent);
			setInput(input);
			setTitle(ActionMessages.getString("FindStringsToExternalizeAction.dialog.title"));  //$NON-NLS-1$
			setMessage(ActionMessages.getFormattedString("FindStringsToExternalizeAction.not_externalized", new Object[] {new Integer(count)} )); //$NON-NLS-1$
			setContentProvider(new ListContentProvider());
			setLabelProvider(createLabelProvider());
		}

		public void create() {
			setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN);
			super.create();
		}

		protected Point getInitialSize() {
			return getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
		}

		protected Control createDialogArea(Composite parent) {
			Composite result= (Composite)super.createDialogArea(parent);
			addCheckbox(result);
			getTableViewer().addSelectionChangedListener(new ISelectionChangedListener(){
				public void selectionChanged(SelectionChangedEvent event){
					if (fOpenButton != null){
						fOpenButton.setEnabled(! getTableViewer().getSelection().isEmpty());
					}
				}
			});
			getTableViewer().getTable().addSelectionListener(new SelectionAdapter(){
				public void widgetDefaultSelected(SelectionEvent e) {
					NonNLSElement element= (NonNLSElement)e.item.getData();
					openWizard(element.cu);
				}
			});
			return result;
		}
		
		private void addCheckbox(Composite result) {
			fCheckbox= new Button(result, SWT.CHECK);
			fCheckbox.setText(ActionMessages.getString("FindStringsToExternalizeAction.hide")); //$NON-NLS-1$
			fCheckbox.setSelection(loadCheckboxState(true));
			
			if (fCheckbox.getSelection() && ! NonNLSListDialog.this.hasFilters())
				getTableViewer().addFilter(new ZeroStringsFilter());
			
			fCheckbox.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					storeCheckboxState(NonNLSListDialog.this.fCheckbox.getSelection());
					boolean showAll= ! NonNLSListDialog.this.fCheckbox.getSelection();
					if  (showAll && NonNLSListDialog.this.hasFilters())
						NonNLSListDialog.this.getTableViewer().resetFilters();
					else if (! showAll && ! NonNLSListDialog.this.hasFilters())	
						NonNLSListDialog.this.getTableViewer().addFilter(new ZeroStringsFilter());
				}
			});
		}	
		
		protected void createButtonsForButtonBar(Composite parent) {
			fOpenButton= createButton(parent, OPEN_BUTTON_ID, ActionMessages.getString("FindStringsToExternalizeAction.button.label"), true); //$NON-NLS-1$
			fOpenButton.setEnabled(false);
			createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		}

		protected void buttonPressed(int buttonId) {
			if (buttonId != OPEN_BUTTON_ID){
				super.buttonPressed(buttonId);
				return;
			}	
			ISelection s= getTableViewer().getSelection();
			if (s instanceof IStructuredSelection){
				IStructuredSelection ss= (IStructuredSelection)s;
				if (ss.getFirstElement() instanceof NonNLSElement)
					openWizard(((NonNLSElement)ss.getFirstElement()).cu);
			}
		}

		private void openWizard(ICompilationUnit unit) {
			try {
				ExternalizeStringsAction.openExternalizeStringsWizard(unit);
			} catch (JavaModelException e) {
				ExceptionHandler.handle(e, 
					ActionMessages.getString("FindStringsToExternalizeAction.dialog.title"), //$NON-NLS-1$
					ActionMessages.getString("FindStringsToExternalizeAction.error.message")); //$NON-NLS-1$
			}
		}
	}
		
	private static class NonNLSElement{
		ICompilationUnit cu;
		int count;
		NonNLSElement(ICompilationUnit cu, int count){
			this.cu= cu;
			this.count= count;
		}
	}
	
	private static class ZeroStringsFilter extends ViewerFilter{
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			return ((NonNLSElement)element).count != 0;
		}
	}
}
