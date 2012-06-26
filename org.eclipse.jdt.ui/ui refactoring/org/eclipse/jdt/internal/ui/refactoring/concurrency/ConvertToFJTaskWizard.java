package org.eclipse.jdt.internal.ui.refactoring.concurrency;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConvertToFJTaskRefactoring;

public class ConvertToFJTaskWizard extends RefactoringWizard {

	public ConvertToFJTaskWizard(Refactoring refactoring, int flags) {
		super(refactoring, flags);
	}

	public ConvertToFJTaskWizard(
			ConvertToFJTaskRefactoring refactoring, String string) {
		super(refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(string);
	}

	@Override
	protected void addUserInputPages() {
		addPage(new ConvertToFJTaskInputPage("ConvertToFJTask"));
	}

}
