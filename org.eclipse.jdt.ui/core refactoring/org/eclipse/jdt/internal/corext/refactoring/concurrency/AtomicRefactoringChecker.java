package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AssertStatement;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression.Operator;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class AtomicRefactoringChecker extends ASTVisitor {

	private final IVariableBinding fField;
	private boolean cannotRefactorAtomically;

	public AtomicRefactoringChecker(IVariableBinding field) {
		this.fField= field;
	}

	@Override
	public boolean visit(Assignment assignment){

		Expression rightHandSide= assignment.getRightHandSide();
		rightHandSide.accept(this);
		return true;
	}

	@Override
	public boolean visit(SimpleName simpleName) {

		IBinding binding= resolveBinding(simpleName);
		if (!considerBinding(binding)) {
			if (binding instanceof IVariableBinding) {
				if (((IVariableBinding) binding).isField()) {
					cannotRefactorAtomically= true;
				}
			}
		}
		return true;
	}

	@Override
	public boolean visit(MethodInvocation methodInvocation) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(IfStatement ifStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(ForStatement forStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(EnhancedForStatement enhancedForStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(WhileStatement whileStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(DoStatement doStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(SuperConstructorInvocation superConstructorInvocation) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(VariableDeclarationStatement variableDeclarationStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(ConstructorInvocation constructorInvocation) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(TypeDeclaration typeDeclaration) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(AssertStatement assertStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(SwitchStatement switchStatement) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(ConditionalExpression conditionalExpression) {

		cannotRefactorAtomically= true;
		return true;
	}

	@Override
	public boolean visit(PostfixExpression postfixExpression) {

		Expression operand= postfixExpression.getOperand();
		org.eclipse.jdt.core.dom.PostfixExpression.Operator operator= postfixExpression.getOperator();
		if (!considerBinding(resolveBinding(operand)) && !(operand instanceof NumberLiteral)
				&& ((operator == PostfixExpression.Operator.DECREMENT) || (operator == PostfixExpression.Operator.INCREMENT))) {
			cannotRefactorAtomically= true;
		}
		return true;
	}

	@Override
	public boolean visit(PrefixExpression prefixExpression) {

		Expression operand= prefixExpression.getOperand();
		Operator operator= prefixExpression.getOperator();
		if (!considerBinding(resolveBinding(operand)) && !(operand instanceof NumberLiteral)
				&& ((operator == PrefixExpression.Operator.DECREMENT) || (operator == PrefixExpression.Operator.INCREMENT))) {
			cannotRefactorAtomically= true;
		}
		return true;
	}

	@Override
	public boolean visit(InfixExpression infixExpression) {

		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();

		boolean rightOperandIsField= considerBinding(resolveBinding(rightOperand));
		boolean leftOperandIsField= considerBinding(resolveBinding(leftOperand));
		boolean bothAreChosenField= leftOperandIsField && rightOperandIsField;
		boolean noneAreChosenField= !leftOperandIsField && !rightOperandIsField;
		boolean oneIsChosenField= leftOperandIsField != rightOperandIsField;

		if (infixExpression.hasExtendedOperands()) {
			cannotRefactorAtomically= true;
		}

		if (noneAreChosenField) {
			cannotRefactorAtomically= true;
		} else if (bothAreChosenField) {
			cannotRefactorAtomically= true;
		} else if (oneIsChosenField) {
			if (leftOperandIsField &&
					!(rightOperand instanceof NumberLiteral)) {
				cannotRefactorAtomically= true;
			} else if (rightOperandIsField &&
					!(leftOperand instanceof NumberLiteral)) {
				cannotRefactorAtomically= true;
			}
		}
		if ((leftOperand instanceof MethodInvocation) || (rightOperand instanceof MethodInvocation)) {
			cannotRefactorAtomically= true;
		}
		return true;
	}

	@Override
	public boolean visit(ParenthesizedExpression parenthesizedExpression) {

		Expression expression= parenthesizedExpression.getExpression();
		expression.accept(this);
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

	private boolean considerBinding(IBinding binding) {

		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		return fField.isEqualTo(((IVariableBinding) binding).getVariableDeclaration());
	}

	public boolean cannotRefactorAtomically(ASTNode node) {

		cannotRefactorAtomically= false;
		node.accept(this);
		return cannotRefactorAtomically;
	}
}
