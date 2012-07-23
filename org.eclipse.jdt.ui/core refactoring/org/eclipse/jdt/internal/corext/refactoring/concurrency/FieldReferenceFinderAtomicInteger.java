package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;

public class FieldReferenceFinderAtomicInteger extends ASTVisitor {

	private final RefactoringStatus status;

	public FieldReferenceFinderAtomicInteger(RefactoringStatus status) {

		this.status= status;
	}

	@Override
	public boolean visit(SimpleName identifier){

		IBinding identifierBinding= resolveBinding(identifier);

		if (identifierBinding instanceof IVariableBinding) {
			IVariableBinding varBinding= (IVariableBinding) identifierBinding;
			if (varBinding.isField()) {
				RefactoringStatus warningStatus= RefactoringStatus.createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_two_field_accesses
						+ identifier.getIdentifier()
						+ ConcurrencyRefactorings.AtomicInteger_warning_two_field_accesses2);
				RefactoringStatusEntry[] entries= status.getEntries();
				boolean alreadyExistingWarning= false;
				for (int i= 0; i < entries.length; i++) {
					RefactoringStatusEntry refactoringStatusEntry= entries[i];
					if (refactoringStatusEntry.getMessage().equals(warningStatus.getMessageMatchingSeverity(RefactoringStatus.WARNING))) {
						alreadyExistingWarning= true;
					}
				}
				if (!alreadyExistingWarning) {
					status.merge(warningStatus);
				}
			}
		}
		return true;
	}

	private IBinding resolveBinding(Expression expression) {

		if (expression instanceof SimpleName) {
			return ((SimpleName) expression).resolveBinding();
		} else if (expression instanceof QualifiedName) {
			return ((QualifiedName) expression).resolveBinding();
		} else if (expression instanceof FieldAccess) {
			return ((FieldAccess) expression).getName().resolveBinding();
		} else if (expression instanceof SuperFieldAccess) {
			return ((SuperFieldAccess) expression).getName().resolveBinding();
		}
		return null;
	}
}
