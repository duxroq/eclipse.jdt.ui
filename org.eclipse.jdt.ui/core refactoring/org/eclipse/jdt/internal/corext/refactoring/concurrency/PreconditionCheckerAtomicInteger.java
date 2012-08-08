package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;

public class PreconditionCheckerAtomicInteger extends ASTVisitor {

	private RefactoringStatus fStatus;
	private IBinding fField;

	public PreconditionCheckerAtomicInteger(
			IVariableBinding field) {

		fField= field;
		fStatus= new RefactoringStatus();
	}

	@Override
	public boolean visit(PostfixExpression postfixExpression) {

		ASTNode assignment= ASTNodes.getParent(postfixExpression, Assignment.class);
		Expression operand= postfixExpression.getOperand();
		if ((considerBinding(resolveBinding(operand))) && (assignment != null)) {
			fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
					+ assignment.toString()
					+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
					+ postfixExpression.toString());
			return false;
		}
		return true;
	}

	@Override
	public boolean visit(PrefixExpression prefixExpression) {

		ASTNode assignment= ASTNodes.getParent(prefixExpression, Assignment.class);
		Expression operand= prefixExpression.getOperand();
		if ((considerBinding(resolveBinding(operand))) && (assignment != null)) {
			fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
					+ assignment.toString()
					+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
					+ prefixExpression.toString());
			return false;
		}
		return true;
	}

	@Override
	public boolean visit(Assignment assignment) {

		Expression leftHandSide= assignment.getLeftHandSide();
		ASTNode assignmentParent= ASTNodes.getParent(assignment, Assignment.class);
		if ((considerBinding(resolveBinding(leftHandSide))) && (assignmentParent != null)) {
			fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
					+ assignmentParent.toString()
					+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
					+ assignment.toString());
			return false;
		}
		return true;
	}

	public RefactoringStatus getStatus() {
		return fStatus;
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

	private boolean considerBinding(IBinding binding) {

		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		return fField.isEqualTo(((IVariableBinding) binding).getVariableDeclaration());
	}
}
