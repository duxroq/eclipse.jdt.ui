package org.eclipse.jdt.internal.ui.refactoring.concurrency;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConcurrencyRefactorings;
import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConvertToAtomicIntegerRefactoring;

public class ConvertToAtomicIntegerWizard extends RefactoringWizard {

	public ConvertToAtomicIntegerWizard(Refactoring refactoring, int flags) {
		super(refactoring, flags);
	}

	public ConvertToAtomicIntegerWizard(ConvertToAtomicIntegerRefactoring refactoring, String string) {

		super(refactoring, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
		setDefaultPageTitle(string);
	}

	@Override
	protected void addUserInputPages() {
		addPage(new ConvertToAtomicIntegerInputPage(ConcurrencyRefactorings.AtomicIntegerWizard_name));
	}

}
