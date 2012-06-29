package org.eclipse.jdt.internal.ui.refactoring.concurrency;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.wizard.IWizardPage;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConvertToFJTaskRefactoring;

public class ConvertToFJTaskInputPage extends UserInputWizardPage implements
		IWizardPage {

	private final class InputListener implements ModifyListener {
		public void modifyText(ModifyEvent event) {
			handleInputChanged();
		}
	}

	Text fRecursiveMethod;
	Text sequentialThreshold;
	Text FJTaskClassName;

	//Combo fTypeCombo;

	public ConvertToFJTaskInputPage(String name) {
		super(name);
	}

	public void createControl(Composite parent) {
		Composite result= new Composite(parent, SWT.NONE);

		setControl(result);

		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		result.setLayout(layout);

		Label label= new Label(result, SWT.NONE);
		label.setText("&Recursive method:");

		fRecursiveMethod= createNameField(result);
		fRecursiveMethod.setEditable(false);

		Label label2= new Label(result, SWT.NONE);
		label2.setText("&FJTask class name:");

		FJTaskClassName= createNameField(result);

		Label label3= new Label(result, SWT.NONE);
		label3.setText("&Sequential threshold:");

		sequentialThreshold= createNameField(result);
		
		final ConvertToFJTaskRefactoring refactoring= getConvertToFJTaskRefactoring();
		
		fRecursiveMethod.setText(refactoring.getMethodNameAndSignature());
		FJTaskClassName.setText(refactoring.suggestTaskName());
		sequentialThreshold.setText(refactoring.suggestSequentialThreshold());

		InputListener inputListener = new InputListener();
		FJTaskClassName.addModifyListener(inputListener);
		sequentialThreshold.addModifyListener(inputListener);
		
		handleInputChanged();
	}

	private Text createNameField(Composite result) {
		Text field= new Text(result, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
		field.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return field;
	}

	private ConvertToFJTaskRefactoring getConvertToFJTaskRefactoring() {
		return (ConvertToFJTaskRefactoring) getRefactoring();
	}

	void handleInputChanged() {
		RefactoringStatus status= new RefactoringStatus();
		ConvertToFJTaskRefactoring refactoring= getConvertToFJTaskRefactoring();
		status.merge(refactoring.setNameForFJTaskSubtype(FJTaskClassName.getText()));
		status.merge(refactoring.setSequentialThreshold(sequentialThreshold.getText()));
		
		setPageComplete(!status.hasError());
		int severity= status.getSeverity();
		String message= status.getMessageMatchingSeverity(severity);
		if (severity >= RefactoringStatus.INFO) {
			setMessage(message, severity);
		} else {
			setMessage("", NONE); //$NON-NLS-1$
		}
	}
}
