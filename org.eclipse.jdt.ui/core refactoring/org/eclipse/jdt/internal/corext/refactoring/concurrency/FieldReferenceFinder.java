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
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;

public class FieldReferenceFinder extends ASTVisitor {

	private final IVariableBinding notIncludingField;
	private final RefactoringStatus status;
	private final Statement enclosingStatement;

	public FieldReferenceFinder(
			Statement enclosingStatement, IVariableBinding notIncludingField, RefactoringStatus status) {
				this.enclosingStatement = enclosingStatement;
				this.notIncludingField = notIncludingField;
				this.status = status;
	}
	
	@Override
	public boolean visit(SimpleName identifier){
		IBinding identifierBinding= resolveBinding(identifier);
		if (identifierBinding instanceof IVariableBinding) {
			IVariableBinding varBinding= (IVariableBinding) identifierBinding;
			if (varBinding.isField() /*&& !varBinding.equals(notIncludingField)*/) {
				RefactoringStatus warningStatus= RefactoringStatus.createWarningStatus("Synchronized block contains references to another field \"" //$NON-NLS-1$
						+ identifier.getIdentifier()
						+ "\". AtomicInteger cannot preserve invariants over two field accesses, " + //$NON-NLS-1$
								"consider using locks instead."); //$NON-NLS-1$
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
	
//	/*
//	 * An enclosing statement should be checked for side effects
//	 */
//	public boolean visit(Statement statement){
//		if (statement == enclosingStatement) {
//	
//		} else {
//			
//		}
//		return true;
//	}
	
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
