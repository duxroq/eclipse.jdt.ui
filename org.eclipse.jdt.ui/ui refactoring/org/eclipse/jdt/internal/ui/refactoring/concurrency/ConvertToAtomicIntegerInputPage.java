package org.eclipse.jdt.internal.ui.refactoring.concurrency;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;

import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConcurrencyRefactorings;
import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConvertToAtomicIntegerRefactoring;

import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;

public class ConvertToAtomicIntegerInputPage extends UserInputWizardPage {

	Text fNameField;
	private Button initializeDeclarationButton;

	public ConvertToAtomicIntegerInputPage(String name) {

		super(name);
	}

	public void createControl(Composite parent) {

		Composite result= new Composite(parent, SWT.NONE);

		setControl(result);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		result.setLayout(layout);
		Label label= new Label(result, SWT.NONE);
		label.setText(RefactoringMessages.ConvertToAtomicIntegerInputPage_getter_name);

		fNameField= createNameField(result);
		fNameField.setEditable(false);

		initializeDeclarationButton= new Button(result, SWT.CHECK);
		initializeDeclarationButton.setText(RefactoringMessages.ConvertToAtomicIntegerInputPage_initialize_field_declaration_label);
		GridData data= new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan= 2;
		data.verticalIndent= 2;
		initializeDeclarationButton.setLayoutData(data);

		final ConvertToAtomicIntegerRefactoring refactoring= getConvertToAtomicIntegerRefactoring();
		fNameField.setText(refactoring.getFieldName());

		fNameField.addModifyListener(new ModifyListener() {

			public void modifyText(ModifyEvent event) {
				handleInputChanged();
			}
		});

		initializeDeclarationButton.setSelection(true);

		initializeDeclarationButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				refactoring.setInitializeDeclaration(initializeDeclarationButton.getSelection());
			}

		});
	}

	private Text createNameField(Composite result) {

		Text field= new Text(result, SWT.SINGLE | SWT.LEFT | SWT.BORDER);

		field.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		return field;
	}

	private ConvertToAtomicIntegerRefactoring getConvertToAtomicIntegerRefactoring() {

		return (ConvertToAtomicIntegerRefactoring) getRefactoring();
	}

	void handleInputChanged() {

		RefactoringStatus status= new RefactoringStatus();
		ConvertToAtomicIntegerRefactoring refactoring= getConvertToAtomicIntegerRefactoring();

		status.merge(refactoring.setFieldName(fNameField.getText()));
		status.merge(refactoring.setInitializeDeclaration(initializeDeclarationButton.getSelection()));

		setPageComplete(!status.hasError());
		int severity= status.getSeverity();
		String message= status.getMessageMatchingSeverity(severity);
		if (severity >= RefactoringStatus.INFO) {
			setMessage(message, severity);
		} else {
			setMessage(ConcurrencyRefactorings.ConcurrencyRefactorings_empty_string, NONE);
		}
	}
}
