package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

public class SideEffectsFinderAtomicInteger extends ASTVisitor {

	private final IVariableBinding notIncludingField;
	private final RefactoringStatus status;
	//private final Statement enclosingStatement;
	private final ASTRewrite rewriter;
	private final List<TextEditGroup> groupDescriptions;
	private RefactoringStatusEntry statusEntry;
	//private boolean foundFieldInAnInfix= false;
	private boolean hasSideEffects= false;
	private ArrayList<InfixExpression.Operator> operators= new ArrayList<InfixExpression.Operator>();
	private boolean isSimpleInfixExpression;
	
	private static final String READ_ACCESS= "Read Access"; //$NON-NLS-1$

	public SideEffectsFinderAtomicInteger(
			IVariableBinding notIncludingField, RefactoringStatus status, ASTRewrite rewriter, List<TextEditGroup> groupDescriptions) {
		//this.enclosingStatement= enclosingStatement;
		this.notIncludingField= notIncludingField;
		this.status= status;
		this.rewriter= rewriter;
		this.groupDescriptions= groupDescriptions;
	}

	@Override
	public boolean visit(Assignment assignment){
		// TODO find side effects
		Expression rightHandSide= assignment.getRightHandSide();
		rightHandSide.accept(this);
//		if (rightHandSide instanceof InfixExpression) {
//			assignment.getRightHandSide().accept(this);
//			if (foundFieldInAnInfix) {
//				// refactor into an addAndGet() possibly
//			}
//		}
		return true;
	}
	
	public boolean visit(Statement statement) {
		hasSideEffects= false;
		return true;
	}
	
	@Override
	public boolean visit(MethodInvocation methodInvocation) {
		hasSideEffects= true;
		return true;
	}
	
	@Override
	public boolean visit(PostfixExpression postfixExpression) {
		if (!considerBinding(resolveBinding(postfixExpression.getOperand()))) {
			hasSideEffects= true;
		}
		return true;
	}
	
	@Override
	public boolean visit(PrefixExpression prefixExpression) {
		if (!considerBinding(resolveBinding(prefixExpression.getOperand()))) {
			hasSideEffects= true;
		}
		return true;
	}

	@Override
	public boolean visit(InfixExpression infixExpression) {
		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();
		InfixExpression.Operator operator= infixExpression.getOperator();

//		if (operators.get(operators.size() - 1) != operator ) {
//			
//		}
		operators.add(operator);
		
		// TODO expand for one of the base units to be a method invocation...
		if (!(leftOperand instanceof SimpleName)) {
			hasSideEffects= true;
			leftOperand.accept(this);
		} else {
			// TODO what to do here...
//			if (considerBinding(resolveBinding(leftOperand))) {
//				// change i to i.get()? only if appropriate
////				if (!foundFieldInAnInfix) {
////					foundFieldInAnInfix= true;
////				}
//			}
			if (rightOperand instanceof SimpleName) {
				if (!considerBinding(resolveBinding(leftOperand)) && !considerBinding(resolveBinding(rightOperand))) {
					hasSideEffects=true;
				} 
			}
		}
		if (!(rightOperand instanceof SimpleName)) {
			rightOperand.accept(this);
			hasSideEffects= true;
		}
//		} else {
//			// TODO what to do here...
////			if (considerBinding(resolveBinding(rightOperand))) {
////				if (!foundFieldInAnInfix) {
////					// change i to i.get()? only if appropriate
////					foundFieldInAnInfix= true;
////				}
////			}
//			if (!considerBinding(resolveBinding(rightOperand))) {
//				
//			}
//		}
		return true;
	}

	@Override
	public boolean visit(ParenthesizedExpression parenthesizedExpression) {
		Expression expression= parenthesizedExpression.getExpression();
		expression.accept(this);
		return true;
	}

	public boolean hasSideEffects() {
		return hasSideEffects;
	}
	
	@Override
	public boolean visit(SimpleName simpleName) {
		Expression invocation= null;

		if ((!simpleName.isDeclaration()) && (considerBinding(resolveBinding(simpleName)))) {
			if (isSimpleInfixExpression) {
				//refactorToAddAndGet();
			} else {
				invocation= (MethodInvocation) rewriter.createStringPlaceholder(
						notIncludingField.getName() + ".get()", ASTNode.METHOD_INVOCATION); //$NON-NLS-1$
				rewriter.replace(simpleName, invocation, createGroupDescription(READ_ACCESS));
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

	private boolean considerBinding(IBinding binding) {

		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		return notIncludingField.isEqualTo(((IVariableBinding) binding).getVariableDeclaration());
	}

	private TextEditGroup createGroupDescription(String name) {
		
		TextEditGroup result= new TextEditGroup(name);
		groupDescriptions.add(result);
		return result;
	}
}
