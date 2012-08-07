package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ConditionalExpression;
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
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.WhileStatement;

public class SideEffectsFinderAtomicInteger extends ASTVisitor {

	private final IVariableBinding notIncludingField;
	private boolean hasSideEffects;

	public SideEffectsFinderAtomicInteger(IVariableBinding notIncludingField) {
		this.notIncludingField= notIncludingField;
	}

	@Override
	public boolean visit(Assignment assignment){

		Expression rightHandSide= assignment.getRightHandSide();
		rightHandSide.accept(this);
		return true;
	}

	@Override
	public boolean visit(MethodInvocation methodInvocation) {

		hasSideEffects= true;
		return true;
	}

	@Override
	public boolean visit(IfStatement ifStatement) {

		hasSideEffects= true;
		return false;
	}

	@Override
	public boolean visit(ForStatement forStatement) {

		hasSideEffects= true;
		return false;
	}

	@Override
	public boolean visit(EnhancedForStatement enhancedForStatement) {

		hasSideEffects= true;
		return false;
	}

	@Override
	public boolean visit(WhileStatement whileStatement) {

		hasSideEffects= true;
		return false;
	}

	@Override
	public boolean visit(DoStatement doStatement) {

		hasSideEffects= true;
		return false;
	}

	@Override
	public boolean visit(ConditionalExpression conditionalExpression) {

		hasSideEffects= true;
		return true;
	}

	@Override
	public boolean visit(PostfixExpression postfixExpression) {

//		if (!considerBinding(resolveBinding(postfixExpression.getOperand()))) {
//			hasSideEffects= true;
//		}
//		return true;
		Expression operand= postfixExpression.getOperand();
		org.eclipse.jdt.core.dom.PostfixExpression.Operator operator= postfixExpression.getOperator();
		if (!considerBinding(resolveBinding(operand)) && !(operand instanceof NumberLiteral)
				&& ((operator == PostfixExpression.Operator.DECREMENT) || (operator == PostfixExpression.Operator.INCREMENT))) {
			hasSideEffects= true;
		}
		return true;
	}

	@Override
	public boolean visit(PrefixExpression prefixExpression) {

		Expression operand= prefixExpression.getOperand();
		Operator operator= prefixExpression.getOperator();
		if (!considerBinding(resolveBinding(operand)) && !(operand instanceof NumberLiteral)
				&& ((operator == PrefixExpression.Operator.DECREMENT) || (operator == PrefixExpression.Operator.INCREMENT))) {
			hasSideEffects= true;
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
			hasSideEffects= true;
		}

		if (noneAreChosenField) {
			hasSideEffects= true;
		} else if (bothAreChosenField) {
			hasSideEffects= true;
		} else if (oneIsChosenField) {
			if (leftOperandIsField &&
					!(rightOperand instanceof SimpleName) && !(rightOperand instanceof NumberLiteral)) {
				hasSideEffects= true;
			} else if (rightOperandIsField &&
					!(leftOperand instanceof SimpleName) && !(leftOperand instanceof NumberLiteral)) {
				hasSideEffects= true;
			}
		}
		if ((leftOperand instanceof MethodInvocation) || (rightOperand instanceof MethodInvocation)) {
			hasSideEffects= true;
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
		return notIncludingField.isEqualTo(((IVariableBinding) binding).getVariableDeclaration());
	}

	public boolean hasSideEffects(ASTNode node) {

		hasSideEffects= false;
		node.accept(this);
		return hasSideEffects;
	}
}
