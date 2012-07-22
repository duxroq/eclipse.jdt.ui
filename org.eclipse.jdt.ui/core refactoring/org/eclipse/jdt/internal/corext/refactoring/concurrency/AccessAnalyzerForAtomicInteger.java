package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InfixExpression.Operator;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;

public class AccessAnalyzerForAtomicInteger extends ASTVisitor {

	private static final String READ_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_read_access;
	private static final String WRITE_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_write_access;
	private static final String POSTFIX_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_postfix_access;
	private static final String PREFIX_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_prefix_access;
	private static final String REMOVE_SYNCHRONIZED_MODIFIER= ConcurrencyRefactorings.ConcurrencyRefactorings_remove_synch_mod;
	private static final String REMOVE_SYNCHRONIZED_BLOCK= ConcurrencyRefactorings.ConcurrencyRefactorings_remove_synch_block;
	private static final String READ_AND_WRITE_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_read_and_write_access;
	private static final String COMMENT= ConcurrencyRefactorings.ConcurrencyRefactorings_comment;
	
	private IVariableBinding fFieldBinding;
	private ASTRewrite fRewriter;
	private ImportRewrite fImportRewriter;
	private List<TextEditGroup> fGroupDescriptions;
	private boolean fIsFieldFinal;
	private RefactoringStatus fStatus;
	private SideEffectsFinderAtomicInteger sideEffectsFinder;

	public AccessAnalyzerForAtomicInteger(
			ConvertToAtomicIntegerRefactoring refactoring, 
			IVariableBinding field, ASTRewrite rewriter, 
			ImportRewrite importRewrite) {
		
		fFieldBinding= field.getVariableDeclaration();
		fRewriter= rewriter;
		fImportRewriter= importRewrite;
		fGroupDescriptions= new ArrayList<TextEditGroup>();
		sideEffectsFinder= new SideEffectsFinderAtomicInteger(fFieldBinding);
		try {
			fIsFieldFinal= Flags.isFinal(refactoring.getField().getFlags());
		} catch (JavaModelException e) {
			// assume non final field
		}
		fStatus= new RefactoringStatus();
	}

	public RefactoringStatus getStatus() {
		return fStatus;
	}

	public Collection<TextEditGroup> getGroupDescriptions() {
		return fGroupDescriptions;
	}
	
	@Override
	public boolean visit(SimpleName node) {
	
		AST ast= node.getAST();
		
		if ((!node.isDeclaration()) && (considerBinding(resolveBinding(node)))) {
			MethodInvocation invocationGet= getMethodInvocationGet(ast, (Expression) ASTNode.copySubtree(ast, node));
			if (!(changeSynchronizedBlock(node, invocationGet, READ_ACCESS) || 
					changeSynchronizedMethod(node, invocationGet, READ_ACCESS))) {
				fRewriter.replace(node, invocationGet, createGroupDescription(READ_ACCESS));
			}
		}
		return true;
	}
	
	@Override
	public boolean visit(Assignment node) {
		
		boolean needToVisitRHS= true;
		boolean inReturnStatement= false;
		Expression lhs= node.getLeftHandSide();
				
		if (!considerBinding(resolveBinding(lhs))) {
			return true;
		}
		ASTNode statement= ASTNodes.getParent(node, Statement.class);
		if (!checkParent(node) && statement instanceof ReturnStatement) {
			// TODO return i=i+foo() is a malformed tree exception
			// i+=foo() => malformed tree exception
			// i=i+i does not change the last i to i.get() :(
				inReturnStatement= true;
		}
		if (!fIsFieldFinal) {
			// Write access.
			AST ast= node.getAST();	
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_set));
			Expression receiver= getReceiver(lhs);
			if (receiver != null) {
				// VIP!! Here we use node.copySubtree because the expression/arguments might be overriden by the later code.
				// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
				// without a matching CopyTargetEdit. This would lead later to a MalformedTreeException
				invocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver)); 
			}
			List<Expression> arguments= invocation.arguments();
			if (node.getOperator() == Assignment.Operator.ASSIGN) {
				Expression rightHandSide= node.getRightHandSide();
				if (rightHandSide instanceof InfixExpression) {	
					needToVisitRHS= infixExpressionHandler(node, ast, invocation, arguments, rightHandSide);
				} 
				if (needToVisitRHS) {
					arguments.add((Expression) fRewriter.createMoveTarget(rightHandSide));
				}
			}
			if (node.getOperator() != Assignment.Operator.ASSIGN) {
				compoundAssignmentHandler(node, ast, invocation, arguments, node.getRightHandSide());
			}
			if (!inReturnStatement) {
				if ( !(changeSynchronizedBlock(node, invocation, WRITE_ACCESS) || changeSynchronizedMethod(node, invocation, WRITE_ACCESS)) ) {
					fRewriter.replace(node, invocation, createGroupDescription(WRITE_ACCESS));
				}
			} else {
				refactorReturnAtomicIntegerAssignment(node, (ReturnStatement) statement, invocation);
			}
		}		
		return false;
	}

	private boolean compoundAssignmentHandler(Assignment node, AST ast,
			MethodInvocation invocation, List<Expression> arguments, Expression rightHandSide) {
		
		Assignment.Operator operator= node.getOperator();
		
		if (operator == Assignment.Operator.PLUS_ASSIGN || operator == Assignment.Operator.MINUS_ASSIGN) {
			if (operator == Assignment.Operator.PLUS_ASSIGN) {
				invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
				rightHandSide= getNewOperandWithGetInvocations(ast, rightHandSide);
				arguments.add(rightHandSide);
			} else if (operator == Assignment.Operator.MINUS_ASSIGN) {
				invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
				rightHandSide.accept(new ChangeFieldToGetInvocationVisitor());
				arguments.add(createNegativeExpression(rightHandSide));
			}
			if (!(rightHandSide instanceof NumberLiteral || rightHandSide instanceof SimpleName)) {
				insertTodoCommentAtomicOperationAndCreateWarning(node);
			}
		} else {
			createUnsafeOperatorWarning(node);
			ASTNode statement= ASTNodes.getParent(node, Statement.class);
			Block body= (Block) ASTNodes.getParent(node, Block.class);
			InfixExpression.Operator newOperator;
			newOperator= getOperatorFromAssignmentOperator(operator);
			MethodInvocation invocationGet= ast.newMethodInvocation();
			invocationGet.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
			invocationGet.setExpression(ast.newSimpleName(fFieldBinding.getName()));
			InfixExpression newInfixExpression= ast.newInfixExpression();
			rightHandSide= getNewOperandWithGetInvocations(ast, rightHandSide);
			newInfixExpression.setOperator(newOperator);
			newInfixExpression.setLeftOperand(invocationGet);
			if (needsParentheses(rightHandSide)) {
				ParenthesizedExpression parenthesizedExpression= ast.newParenthesizedExpression();
				parenthesizedExpression.setExpression(rightHandSide);
				newInfixExpression.setRightOperand(parenthesizedExpression);
			} else {			
				newInfixExpression.setRightOperand(rightHandSide);
			}
			arguments.add(newInfixExpression);
			insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
					body, statement, Block.STATEMENTS_PROPERTY);
		}
		return false;
	}

	private boolean infixExpressionHandler(Assignment node, AST ast, MethodInvocation invocation,
			List<Expression> arguments, Expression rightHandSide) {
		
		boolean needToVisitRHS= true;	
		InfixExpression infixExpression= (InfixExpression) rightHandSide;
		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();
		Operator operator= infixExpression.getOperator();
		Expression newLeftOperand= (Expression) fRewriter.createMoveTarget(leftOperand);
		Expression newRightOperand= (Expression) fRewriter.createMoveTarget(rightOperand);
		boolean leftOperandIsChosenField= considerBinding(resolveBinding(leftOperand));
		boolean rightOperandIsChosenField= considerBinding(resolveBinding(rightOperand));

		// TODO fix TODO commentssss
		// TODO still getting no target edit provided error
		if (operator == InfixExpression.Operator.PLUS || operator == InfixExpression.Operator.MINUS) {
			if (leftOperandIsChosenField || rightOperandIsChosenField) {
				if (leftOperandIsChosenField) {
					newLeftOperand= getNewOperandWithGetInvocations(ast, rightOperand);
					if (infixExpression.hasExtendedOperands()) {
						newRightOperand= getNewOperandWithGetInvocations(ast, (Expression) infixExpression.extendedOperands().get(0));
					} else {
						if (considerBinding(resolveBinding(rightOperand))) {
							MethodInvocation methodInvocation= ast.newMethodInvocation();
							methodInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));
							methodInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
							invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
							arguments.add(methodInvocation);
//							if (sideEffectsFinder.hasSideEffects(leftOperand)) {
//								insertTodoCommentAtomicOperationAndCreateWarning(node);
//							}
						} else {
							Expression newOperand;
							rightOperand.accept(new ChangeFieldToGetInvocationVisitor());
							newOperand= (Expression) fRewriter.createMoveTarget(rightOperand);
							newOperand.accept(new ChangeFieldToGetInvocationVisitor());
							refactorIntoAddAndGet(node, ast, invocation, arguments, rightOperand, operator);
						}
						needToVisitRHS= false;
						return needToVisitRHS;
					}
				} else if (rightOperandIsChosenField) {
					newLeftOperand= getNewOperandWithGetInvocations(ast, leftOperand);
					newRightOperand= getNewOperandWithGetInvocations(ast, rightOperand);
					if (infixExpression.hasExtendedOperands() && operator != InfixExpression.Operator.MINUS) {
						newRightOperand= getNewOperandWithGetInvocations(ast, (Expression) infixExpression.extendedOperands().get(0));
					} else if (operator != InfixExpression.Operator.MINUS) {
						leftOperand.accept(new ChangeFieldToGetInvocationVisitor());
						refactorIntoAddAndGet(node, ast, invocation, arguments, leftOperand, operator);
						needToVisitRHS= false;
						insertTodoCommentAtomicOperationAndCreateWarning(node);						
						return needToVisitRHS;
					} else {
						replaceOperandsAndChangeFieldRefsInExtOpsToGetInvocations(infixExpression, leftOperand, rightOperand, newLeftOperand, newRightOperand);
						insertTodoCommentAtomicOperationAndCreateWarning(node);
						return needToVisitRHS;
					}
				}
				replaceOperandsAndChangeFieldRefsInExtOpsToGetInvocations(infixExpression, leftOperand, rightOperand, newLeftOperand, newRightOperand);
				if (infixExpression.hasExtendedOperands() && operator != InfixExpression.Operator.MINUS) {
					fRewriter.remove((ASTNode) infixExpression.extendedOperands().get(0), createGroupDescription(READ_ACCESS));
					infixExpression.extendedOperands().remove(0);
				}
				needToVisitRHS= refactorIntoAddAndGet(node, invocation, infixExpression, operator);
			} else if (infixExpression.hasExtendedOperands()) {
				getExpressionsAndReplace(ast, leftOperand, rightOperand);
				if (operator != InfixExpression.Operator.MINUS) {
					if (foundFieldInExtendedOperands(infixExpression)) {
						changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
						needToVisitRHS= refactorIntoAddAndGet(node, invocation, infixExpression, operator);
					} else {
						changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
					}
				} else {
					changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
					insertTodoCommentAtomicOperationAndCreateWarning(node);
					return needToVisitRHS;
				}
			} else {
				getExpressionsAndReplace(ast, leftOperand, rightOperand);
			}
		} else {
			createUnsafeOperatorWarning(node);
			getExpressionsAndReplace(ast, leftOperand, rightOperand);
			if (infixExpression.hasExtendedOperands()) {
				changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
			}			
		}
		// TODO fix commenting :( and check warnings again
		insertTodoCommentAtomicOperationAndCreateWarning(node);
		return needToVisitRHS;
	}

	private void replaceOperandsAndChangeFieldRefsInExtOpsToGetInvocations(InfixExpression infixExpression, Expression leftOperand, Expression rightOperand, Expression newLeftOperand,
			Expression newRightOperand) {
		
		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(READ_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(READ_ACCESS));
		changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
	}

	private void getExpressionsAndReplace(AST ast, Expression leftOperand, Expression rightOperand) {
		
		Expression newLeftOperand;
		Expression newRightOperand;
		newLeftOperand= getNewOperandWithGetInvocations(ast, leftOperand);
		newRightOperand= getNewOperandWithGetInvocations(ast, rightOperand);

		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(READ_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(READ_ACCESS));
	}

	private void insertTodoCommentAtomicOperationAndCreateWarning(Assignment node) {
		
		ASTNode statement= ASTNodes.getParent(node, Statement.class);
		Block body= (Block) ASTNodes.getParent(node, Block.class);
		
		insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
				body, statement, Block.STATEMENTS_PROPERTY);
		createWarningStatus(ConcurrencyRefactorings.AtomicInteger_statement + statement.toString()
				+ ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
	}

	private Expression getNewOperandWithGetInvocations(AST ast, Expression operand) {
		
		Expression newOperand;
		if (considerBinding(resolveBinding(operand))) {
			newOperand= getMethodInvocationGet(ast, ast.newSimpleName(fFieldBinding.getName()));
		} else {
			operand.accept(new ChangeFieldToGetInvocationVisitor());
			newOperand= (Expression) fRewriter.createMoveTarget(operand);
		}
		return newOperand;
	}

	private void changeFieldReferencesInExtendedOperandsToGetInvocations(InfixExpression infixExpression) {
		
		if (infixExpression.hasExtendedOperands()) {
			List<Expression> extendedOperands= infixExpression.extendedOperands();
			for (int i= 0; i < extendedOperands.size(); i++) {
				Expression expression= extendedOperands.get(i);
				expression.accept(new ChangeFieldToGetInvocationVisitor());
			}
		}
	}
	
	private MethodInvocation getMethodInvocationGet(AST ast, Expression expression) {
		
		MethodInvocation methodInvocation= ast.newMethodInvocation();
		methodInvocation.setExpression(expression);
		methodInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		return methodInvocation;
	}

	private boolean foundFieldInExtendedOperands(InfixExpression infixExpression) {
		
		List<Expression> extendedOperands= infixExpression.extendedOperands();
		boolean foundFieldToBeRefactoredInInfix= false;
		
		for (Iterator<Expression> iterator= extendedOperands.iterator(); iterator.hasNext();) {
			Expression expression= iterator.next();
			if (considerBinding(resolveBinding(expression)) && !foundFieldToBeRefactoredInInfix) {
				foundFieldToBeRefactoredInInfix= true;
				fRewriter.remove(expression, createGroupDescription(READ_ACCESS));
				extendedOperands.remove(expression);
			}
		}
		return foundFieldToBeRefactoredInInfix;
	}

	private boolean refactorIntoAddAndGet(Assignment node, MethodInvocation invocation, InfixExpression infixExpression, Operator operator) {
		
		AST ast= invocation.getAST();
		boolean needToVisitRHS= false;
		
		if (operator == InfixExpression.Operator.PLUS) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			invocation.arguments().add(fRewriter.createMoveTarget(infixExpression));
		} else if (operator == InfixExpression.Operator.MINUS) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			
			InfixExpression newInfixExpression= ast.newInfixExpression();
			Expression rightOperand= infixExpression.getRightOperand();
			Expression newLeftOperand= getNewOperandWithGetInvocations(ast, rightOperand);
			newInfixExpression.setLeftOperand(newLeftOperand);
			Expression newRightOperand= null;
			
			if (infixExpression.hasExtendedOperands()) {
				newRightOperand= getNewOperandWithGetInvocations(ast, (Expression) infixExpression.extendedOperands().get(0));
				infixExpression.extendedOperands().remove(0);
				newInfixExpression.setRightOperand(newRightOperand);
				List<Expression> extendedOperands= infixExpression.extendedOperands();
				for (int i= 0; i < extendedOperands.size(); i++) {
					Expression newOperandWithGetInvocations= getNewOperandWithGetInvocations(ast, extendedOperands.get(i));
					newInfixExpression.extendedOperands().add(newOperandWithGetInvocations);
				}
			}
			newInfixExpression.setOperator(InfixExpression.Operator.PLUS);
			PrefixExpression newPrefixExpression= ast.newPrefixExpression();
			newPrefixExpression.setOperator(PrefixExpression.Operator.MINUS);
			
			boolean needsParentheses= needsParentheses(infixExpression);
			if (needsParentheses) {
				ParenthesizedExpression p= ast.newParenthesizedExpression();
				p.setExpression(newInfixExpression);
				newPrefixExpression.setOperand(p);
			} else {
				newPrefixExpression.setOperand(newInfixExpression);
			}
			invocation.arguments().add(newPrefixExpression);
		
		} else {
			createUnsafeOperatorWarning(node);
			insertTodoCommentAtomicOperationAndCreateWarning(node);
			needToVisitRHS= true;
		}
		return needToVisitRHS;
	}

	private void refactorIntoAddAndGet(Assignment node, AST ast, MethodInvocation invocation,
			List<Expression> arguments, Expression operand, Object operator) {
		
		if (operator == InfixExpression.Operator.PLUS || operator == Assignment.Operator.PLUS_ASSIGN) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			arguments.add((Expression) fRewriter.createMoveTarget(operand));
			//arguments.add(operand);
		} else if (operator == InfixExpression.Operator.MINUS || operator == Assignment.Operator.MINUS_ASSIGN) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			arguments.add(createNegativeExpression(operand));
		} else {
			createUnsafeOperatorWarning(node);
			ASTNode statement= ASTNodes.getParent(node, Statement.class);
			Block body= (Block) ASTNodes.getParent(node, Block.class);
			if (operator instanceof Assignment.Operator) {
				operator= getOperatorFromAssignmentOperator((Assignment.Operator) operator);
			}
			MethodInvocation invocationGet= ast.newMethodInvocation();
			invocationGet.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
			invocationGet.setExpression(ast.newSimpleName(fFieldBinding.getName()));
			InfixExpression newInfixExpression= ast.newInfixExpression();
			Expression newOperand= getNewOperandWithGetInvocations(ast, operand);
			
			newInfixExpression.setOperator((InfixExpression.Operator) operator);
			newInfixExpression.setLeftOperand(invocationGet);
			if (needsParentheses(operand)) {
				ParenthesizedExpression parenthesizedExpression= ast.newParenthesizedExpression();
				parenthesizedExpression.setExpression(newOperand);
				newInfixExpression.setRightOperand(parenthesizedExpression);
			} else {			
				newInfixExpression.setRightOperand(newOperand);
			}
			arguments.add(newInfixExpression);
			ExpressionStatement setInvocationStatement1= ast.newExpressionStatement(invocation);
			Statement setInvocationStatement= setInvocationStatement1;
			ListRewrite rewriter= insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
					body, statement, Block.STATEMENTS_PROPERTY);
			rewriter.replace(statement, setInvocationStatement, createGroupDescription(READ_AND_WRITE_ACCESS));
		}
	}

	private boolean checkSynchronizedBlockForReturnStatement(Assignment node) {

		ASTNode syncStatement= ASTNodes.getParent(node, SynchronizedStatement.class);
		ASTNode methodDecl= ASTNodes.getParent(node, MethodDeclaration.class);
		
		if (syncStatement != null) {
			Block methodBlock= ((MethodDeclaration) methodDecl).getBody();
			
			insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_statement_not_properly_synchronized_block,
					methodBlock, syncStatement, Block.STATEMENTS_PROPERTY);
			return true;
		}	
		return false;
	}

	private boolean checkSynchronizedMethodForReturnStatement(Assignment node) {

		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);
		TypeDeclaration typeDeclaration= (TypeDeclaration) ASTNodes.getParent(methodDecl, TypeDeclaration.class);

		int modifiers= methodDecl.getModifiers();

		if (Modifier.isSynchronized(modifiers)) {
			MethodDeclaration[] methods= typeDeclaration.getMethods();
			for (int i= 0; i < methods.length; i++) {
				if (methods[i] == methodDecl) {
					insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_statement_not_properly_synchronized_method,
							typeDeclaration, methodDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
					break;
				}
			}
			return true;
		}
		return false;
	}
	
	private void createUnsafeOperatorWarning(Assignment node) {

		fStatus.addWarning(ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning1
				+ fFieldBinding.getName()
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning2
				+ fFieldBinding.getName()
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning3
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning4
				+ node.toString()
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning5);
	}

	private PrefixExpression createNegativeExpression(Expression expression) {
		
		AST ast= expression.getAST();
		PrefixExpression newPrefixExpression= ast.newPrefixExpression();
		newPrefixExpression.setOperator(PrefixExpression.Operator.MINUS);
		
		boolean needsParentheses= needsParentheses(expression);
		ASTNode copyExpression= fRewriter.createMoveTarget(expression);
		if (needsParentheses) {
			ParenthesizedExpression p= ast.newParenthesizedExpression();
			p.setExpression((Expression) copyExpression);
			copyExpression= p;
		}
		newPrefixExpression.setOperand((Expression) copyExpression);
		return newPrefixExpression;
	}
	
	@Override
	public boolean visit(PostfixExpression expression)
	{
		Expression operand= expression.getOperand();
		PostfixExpression.Operator operator= expression.getOperator();
		
		if (!considerBinding(resolveBinding(operand))) {
			return true;
		}
		AST ast= expression.getAST();
		MethodInvocation invocation= ast.newMethodInvocation();
		
		invocation.setExpression((Expression) fRewriter.createCopyTarget(operand));

		if (operator == PostfixExpression.Operator.INCREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_getAndIncrement));
		}
		else if (operator == PostfixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_getAndDecrement));
		}
		if ( !(changeSynchronizedBlock(expression, invocation, POSTFIX_ACCESS) || changeSynchronizedMethod(expression, invocation, POSTFIX_ACCESS)) ) {
			fRewriter.replace(expression, invocation, createGroupDescription(POSTFIX_ACCESS));
		} 
		return false;
	}
	
	@Override
	public boolean visit(PrefixExpression expression) {
		
		Expression operand= expression.getOperand();
		PrefixExpression.Operator operator= expression.getOperator();
		
		if (!considerBinding(resolveBinding(operand))) {
			return true;
		}
		AST ast= expression.getAST();
		MethodInvocation invocation= ast.newMethodInvocation();
		
		invocation.setExpression((Expression) fRewriter.createCopyTarget(operand));

		if (operator == PrefixExpression.Operator.INCREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_incrementAndGet));
		}
		else if (operator == PrefixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_decrementAndGet));
		}
		if ( !(changeSynchronizedBlock(expression, invocation, PREFIX_ACCESS) || changeSynchronizedMethod(expression, invocation, PREFIX_ACCESS)) ) {
			fRewriter.replace(expression, invocation, createGroupDescription(PREFIX_ACCESS));
		} 
		return false;
	}
	
	@Override
	public void endVisit(CompilationUnit node) {
		fImportRewriter.addImport(ConcurrencyRefactorings.AtomicIntegerRefactoring_import);
	}
	
	private boolean changeSynchronizedBlock(ASTNode node, Expression invocation, String accessType) {
		
		AST ast= node.getAST();
		ASTNode syncStatement= ASTNodes.getParent(node, SynchronizedStatement.class);

		if (syncStatement != null) {
			Block syncBody= ((SynchronizedStatement) syncStatement).getBody();
			List<?> syncBodyStatements= syncBody.statements();
			if (syncBodyStatements.size() > 1) {
				fRewriter.replace(node, invocation, createGroupDescription(accessType));
				checkMoreThanOneFieldReference(node, syncBody);
			} else {
				Statement statement= (Statement) syncBodyStatements.get(0);
				ExpressionStatement newExpressionStatement= ast.newExpressionStatement(invocation);
				if (!isReturnStatementWithIntFieldAssignment(statement) && !sideEffectsFinder.hasSideEffects(statement)) {
					fRewriter.replace(syncStatement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
				} else if (sideEffectsFinder.hasSideEffects(statement)) {
					fRewriter.replace(statement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
				}
			}
			return true;
		}	
		return false;
	}

	private boolean isReturnStatementWithIntFieldAssignment(Statement statement) {
		if (statement instanceof ReturnStatement) {
			Expression expression= ((ReturnStatement)statement).getExpression();
			if (expression instanceof Assignment) {
				Expression leftHandSide = ((Assignment) expression).getLeftHandSide();
				if (leftHandSide instanceof SimpleName) {
					IBinding identifierBinding= resolveBinding(leftHandSide);
					if (identifierBinding instanceof IVariableBinding) {
						IVariableBinding varBinding= (IVariableBinding) identifierBinding;
						if (varBinding.isField() && considerBinding(identifierBinding)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
	
	private void checkMoreThanOneFieldReference(ASTNode node, Block syncBody) {
		
		ASTNode enclosingStatement= ASTNodes.getParent(node, Statement.class);
		List<Statement> statements= syncBody.statements();
		int numEntries= fStatus.getEntries().length + 1;
		
		for (Iterator<?> iterator= statements.iterator(); iterator.hasNext();) {
			Statement statement= (Statement) iterator.next();
			if (!statement.equals(enclosingStatement)){
				statement.accept(new FieldReferenceFinderAtomicInteger(fStatus));
			} else {
				if (sideEffectsFinder.hasSideEffects(statement)) {
					createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_side_effects1
							+ ConcurrencyRefactorings.AtomicInteger_warning_side_effects2
							+ ConcurrencyRefactorings.AtomicInteger_warning_side_effects3
							+ statement.toString()
							+ ConcurrencyRefactorings.AtomicInteger_warning_side_effects4);
				}
			}
			insertTodoComments(syncBody, numEntries, statement);	
		}
		if (numEntries < (fStatus.getEntries().length + 1)) {
				insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_statement_not_properly_synchronized,
						syncBody, enclosingStatement, Block.STATEMENTS_PROPERTY);
		}
	}

	private void insertTodoComments(Block syncBody, int numEntries, Statement statement) {
		if (numEntries < (fStatus.getEntries().length + 1)) {
			RefactoringStatusEntry[] entries= fStatus.getEntries();	
			RefactoringStatusEntry refactoringStatusEntry= entries[(entries.length-1)];
			if (refactoringStatusEntry.getMessage().matches(ConcurrencyRefactorings.AtomicInteger_warning_two_field_accesses)) {
				insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_statement_not_properly_synchronized,
						syncBody, statement, Block.STATEMENTS_PROPERTY);
			}
		}
	}

	private boolean changeSynchronizedMethod(ASTNode node, Expression invocation, String accessType) {
		
		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);
		if (methodDecl != null) {
			int modifiers= methodDecl.getModifiers();

			if (Modifier.isSynchronized(modifiers)) {
				List<Statement> methodBodyStatements= methodDecl.getBody().statements();
				Statement statement= methodBodyStatements.get(0);
				if (methodBodyStatements.size() == 1) {
					if (!isReturnStatementWithIntFieldAssignment(statement) && !sideEffectsFinder.hasSideEffects(statement)) {
						removeSynchronizedModifier(methodDecl, modifiers);
					}
				} else {
					if (!isReturnStatementWithIntFieldAssignment(statement)) {
						checkMoreThanOneFieldReference(node, methodDecl.getBody());
					}
				}
				fRewriter.replace(node, invocation, createGroupDescription(accessType));
				return true;
			}
		}
		return false;
	}

	private void removeSynchronizedModifier(MethodDeclaration methodDecl, int modifiers) {
		
		ModifierRewrite methodRewriter= ModifierRewrite.create(fRewriter, methodDecl);
		int synchronizedModifier= Modifier.SYNCHRONIZED;
		synchronizedModifier= ~ synchronizedModifier;
		int newModifiersWithoutSync= modifiers & synchronizedModifier;
		methodRewriter.setModifiers(newModifiersWithoutSync, createGroupDescription(REMOVE_SYNCHRONIZED_MODIFIER));
	}
	
	private Expression getReceiver(Expression expression) {
		
		int type= expression.getNodeType();
		
		switch(type) {
			case ASTNode.SIMPLE_NAME:
				return expression;
			case ASTNode.QUALIFIED_NAME:
				return ((QualifiedName) expression).getQualifier();
			case ASTNode.FIELD_ACCESS:
				return expression;
			case ASTNode.SUPER_FIELD_ACCESS:
				return expression;
			case ASTNode.THIS_EXPRESSION:
				return expression;
			default:
				return null;
		}
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
	
	private TextEditGroup createGroupDescription(String name) {
		
		TextEditGroup result= new TextEditGroup(name);
		
		fGroupDescriptions.add(result);
		return result;
	}
	
	private boolean considerBinding(IBinding binding) {
		
		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		return fFieldBinding.isEqualTo(((IVariableBinding) binding).getVariableDeclaration());
	}
	
	private boolean checkParent(ASTNode node) {
		
		ASTNode parent= node.getParent();
		return parent instanceof ExpressionStatement;
	}

	private boolean needsParentheses(Expression expression) {
		
		int type= expression.getNodeType();
		
		return type == ASTNode.INFIX_EXPRESSION || type == ASTNode.CONDITIONAL_EXPRESSION ||
				type == ASTNode.PREFIX_EXPRESSION || type == ASTNode.POSTFIX_EXPRESSION ||
				type == ASTNode.CAST_EXPRESSION || type == ASTNode.INSTANCEOF_EXPRESSION;
	}
	
	private InfixExpression.Operator getOperatorFromAssignmentOperator(Assignment.Operator operator) {
		if (operator == Assignment.Operator.DIVIDE_ASSIGN) {
			return InfixExpression.Operator.DIVIDE;
		} else if (operator == Assignment.Operator.TIMES_ASSIGN) {
			return InfixExpression.Operator.TIMES;
		} else if (operator == Assignment.Operator.BIT_AND_ASSIGN) {
			return InfixExpression.Operator.AND;
		} else if (operator == Assignment.Operator.BIT_OR_ASSIGN) {
			return InfixExpression.Operator.OR;
		} else if (operator == Assignment.Operator.BIT_XOR_ASSIGN) {
			return InfixExpression.Operator.XOR;
		} else if (operator == Assignment.Operator.LEFT_SHIFT_ASSIGN) {
			return InfixExpression.Operator.LEFT_SHIFT;
		} else if (operator == Assignment.Operator.REMAINDER_ASSIGN) {
			return InfixExpression.Operator.REMAINDER;
		} else if (operator == Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN) {
			return InfixExpression.Operator.RIGHT_SHIFT_SIGNED;
		} else if (operator == Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN) {
			return InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED;
		} else {
			// will never occur
			return null;
		}
	}
	
	private ListRewrite insertLineCommentBeforeNode(String comment, ASTNode body, ASTNode node, ChildListPropertyDescriptor descriptor) {
		
		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(comment, ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(body, descriptor);
		rewriter.insertBefore(lineComment, node, createGroupDescription(COMMENT));
		return rewriter;
	}
	
	private void refactorReturnAtomicIntegerAssignment(Assignment node, ReturnStatement statement, MethodInvocation invocation) {
		
		Block body= (Block) ASTNodes.getParent(node, Block.class);
		AST ast= node.getAST();
		MethodInvocation getInvocation= ast.newMethodInvocation();
		
		getInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		getInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));
		
		ListRewrite rewriter= fRewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
		ExpressionStatement setInvocationStatement= ast.newExpressionStatement(invocation);
		rewriter.insertBefore(setInvocationStatement, statement, createGroupDescription(WRITE_ACCESS));
		
		ReturnStatement returnStatement= ast.newReturnStatement();
		returnStatement.setExpression(getInvocation);
		rewriter.replace(statement, returnStatement, createGroupDescription(READ_ACCESS));
		insertLineCommentBeforeNode(
				ConcurrencyRefactorings.AtomicInteger_todo_comment_return_statement_could_not_be_executed_atomically,
				body, returnStatement, Block.STATEMENTS_PROPERTY);
		
		if (checkSynchronizedBlockForReturnStatement(node)) {
			createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_remove_synch_block_return_assignment);
		} else if (checkSynchronizedMethodForReturnStatement(node)) {
			createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_remove_synch_mod_return_assignment);
		}
	}
	
	private void createWarningStatus(String message) {
		fStatus.addWarning(message);
	}

	private class ChangeFieldToGetInvocationVisitor extends ASTVisitor {
		
		@Override
		public boolean visit(SimpleName simpleName) {
			
			if (considerBinding(resolveBinding(simpleName)) && !simpleName.isDeclaration()) {
				AST ast= simpleName.getAST();
				MethodInvocation methodInvocation= getMethodInvocationGet(ast, (Expression) ASTNode.copySubtree(ast, simpleName));
				fRewriter.replace(simpleName, methodInvocation, createGroupDescription(READ_ACCESS));
			}
			return true;
		}
	}
}