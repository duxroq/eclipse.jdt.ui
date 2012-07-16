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

	private static final String READ_ACCESS= "Read Access"; //$NON-NLS-1$
	private static final String WRITE_ACCESS= "Write Access"; //$NON-NLS-1$
	private static final String POSTFIX_ACCESS= "Postfix Access"; //$NON-NLS-1$
	private static final String PREFIX_ACCESS= "Prefix Access"; //$NON-NLS-1$
	private static final String REMOVE_SYNCHRONIZED_MODIFIER= "Remove Synchronized Modifier"; //$NON-NLS-1$
	private static final String REMOVE_SYNCHRONIZED_BLOCK= "Remove Synchronized Block"; //$NON-NLS-1$
	private static final String READ_AND_WRITE_ACCESS= "Read and Write Access"; //$NON-NLS-1$
	private static final String COMMENT= "Comment"; //$NON-NLS-1$
	
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
		Expression lhs= node.getLeftHandSide();
				
		if (!considerBinding(resolveBinding(lhs))) {
			return true;
		}
		ASTNode statement= ASTNodes.getParent(node, Statement.class);
		if (!checkParent(node) && statement instanceof ReturnStatement) {			
				refactorReturnAtomicIntegerAssignment(node, (ReturnStatement) statement, lhs);
				return true;
		}
		if (!fIsFieldFinal) {
			// Write access.
			AST ast= node.getAST();	
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName("set")); //$NON-NLS-1$
			Expression receiver= getReceiver(lhs);
			if (receiver != null) {
				// VIP!! Here we use node.coySubtree because the expression/arguments might be overriden by the later code.
				// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
				// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
				invocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver)); 
			}
			List<Expression> arguments= invocation.arguments();
			//Expression copyRHS= (Expression) fRewriter.createMoveTarget(node.getRightHandSide());
			Expression copyRHS= (Expression) ASTNode.copySubtree(ast, node.getRightHandSide());
			
			if (node.getOperator() == Assignment.Operator.ASSIGN) {
				Expression rightHandSide= node.getRightHandSide();
				if (rightHandSide instanceof InfixExpression) {		
					needToVisitRHS= infixExpressionHandler(node, ast, invocation, receiver, arguments, rightHandSide);
				} 
				// TODO
				if (needToVisitRHS) {
					copyRHS.accept(new ChangeFieldToGetInvocationVisitor());
					//fRewriter.replace(node.getRightHandSide(), copyRHS, null);
					arguments.add(copyRHS);
				}
			}
			if (node.getOperator() != Assignment.Operator.ASSIGN) {
				compoundAssignmentHandler(node, ast, invocation, receiver, arguments, copyRHS);
			}
			if ( !(changeSynchronizedBlock(node, invocation, WRITE_ACCESS) || changeSynchronizedMethod(node, invocation, WRITE_ACCESS)) ) {
				fRewriter.replace(node, invocation, createGroupDescription(WRITE_ACCESS));
			}
			System.out.println(fRewriter.toString());
		}	
//		if (needToVisitRHS) {
//			node.getRightHandSide().accept(this);
//		}	
		return false;
	}

	private boolean compoundAssignmentHandler(Assignment node, AST ast,
			MethodInvocation invocation, Expression receiver, List<Expression> arguments, Expression copyRHS) {
		
		// TODO handle i+=(12 + foo());
		refactorIntoAddAndGet(node, ast, invocation, receiver, arguments, copyRHS, node.getOperator());
		
		return false;
	}

	// TODO make sure this is the right ast
	private boolean infixExpressionHandler(Assignment node, AST ast, MethodInvocation invocation,
			Expression receiver, List<Expression> arguments, Expression rightHandSide) {
		
		boolean needToVisitRHS= true;
		InfixExpression infixExpression= (InfixExpression) rightHandSide;
		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();
		Expression newLeftOperand= (Expression) fRewriter.createMoveTarget(leftOperand);
		Expression newRightOperand= (Expression) fRewriter.createMoveTarget(rightOperand);
		
		Operator operator= infixExpression.getOperator();

		boolean foundFieldToBeRefactoredInInfix= false;
		boolean leftOperandIsChosenField= considerBinding(resolveBinding(leftOperand));
		boolean rightOperandIsChosenField= considerBinding(resolveBinding(rightOperand));
		
		if (infixExpression.hasExtendedOperands()) {
			((Expression) infixExpression.extendedOperands().get(0)).accept(new ChangeFieldToGetInvocationVisitor());
		}
		if (leftOperandIsChosenField || rightOperandIsChosenField) {
			if (leftOperandIsChosenField) {
				newLeftOperand= (Expression) ASTNode.copySubtree(ast, infixExpression.getRightOperand());
				newLeftOperand.accept(new ChangeFieldToGetInvocationVisitor());
				if (infixExpression.hasExtendedOperands()) {
					newRightOperand= (Expression) ASTNode.copySubtree(ast, (ASTNode) infixExpression.extendedOperands().get(0));
					fRewriter.remove((ASTNode) infixExpression.extendedOperands().get(0), createGroupDescription(READ_ACCESS));
					infixExpression.extendedOperands().remove(0);
					newRightOperand.accept(new ChangeFieldToGetInvocationVisitor());
				} else {
					refactorIntoAddAndGet(node, ast, invocation, receiver, arguments, newLeftOperand, operator);
					needToVisitRHS= false;
					return needToVisitRHS;
				}
			} else if (rightOperandIsChosenField) {
				leftOperand.accept(new ChangeFieldToGetInvocationVisitor());
				if (infixExpression.hasExtendedOperands()) {
					newRightOperand= (Expression) ASTNode.copySubtree(ast, (ASTNode) infixExpression.extendedOperands().get(0));
					fRewriter.remove((ASTNode) infixExpression.extendedOperands().get(0), null);
					infixExpression.extendedOperands().remove(0);
					newRightOperand.accept(new ChangeFieldToGetInvocationVisitor());

				} else {
					refactorIntoAddAndGet(node, ast, invocation, receiver, arguments, leftOperand, operator);
					needToVisitRHS= false;
					return needToVisitRHS;
				}
			}
			
			fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(READ_ACCESS));
			fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(READ_ACCESS));
			
			infixExpression.setLeftOperand(newLeftOperand);
			infixExpression.setRightOperand(newRightOperand);
			
			System.out.println("The infix expression being added to change all the field refs in the ext.ops is : " + infixExpression.toString()); //$NON-NLS-1$
			changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
			needToVisitRHS= refactorIntoAddAndGet(node, invocation, infixExpression, operator);
		} else if (infixExpression.hasExtendedOperands()) {
			infixExpression.getLeftOperand().accept(new ChangeFieldToGetInvocationVisitor());
			infixExpression.getRightOperand().accept(new ChangeFieldToGetInvocationVisitor());
			foundFieldToBeRefactoredInInfix= findFieldInExtendedOperands(infixExpression);
			// TODO problem?
			if (foundFieldToBeRefactoredInInfix) {
				needToVisitRHS= refactorIntoAddAndGet(node, invocation, infixExpression, operator);
			} else {
				changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
				needToVisitRHS= true;
			}
		} else {
			infixExpression.getLeftOperand().accept(new ChangeFieldToGetInvocationVisitor());
			infixExpression.getRightOperand().accept(new ChangeFieldToGetInvocationVisitor());

			changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
			System.out.println("The invocation looks like: " + infixExpression.toString()); //$NON-NLS-1$
			needToVisitRHS= true;
		}
		return needToVisitRHS;
	}

	private void changeFieldReferencesInExtendedOperandsToGetInvocations(InfixExpression infixExpression) {
		//infixExpression.getLeftOperand().accept(new ChangeFieldToGetInvocationVisitor());
		//infixExpression.getRightOperand().accept(new ChangeFieldToGetInvocationVisitor());
		if (infixExpression.hasExtendedOperands()) {
			List<Expression> extendedOperands= infixExpression.extendedOperands();
			for (int i= 0; i < extendedOperands.size(); i++) {
				System.out.println("Expression in extended operands: infix =" + extendedOperands.get(i).toString()); //$NON-NLS-1$
				Expression expression= extendedOperands.get(i);
				expression.accept(new ChangeFieldToGetInvocationVisitor());
				//extendedOperands.remove(expression);
				//extendedOperands.add(i, (Expression) ASTNode.copySubtree(ast, expression));
			}
			//System.out.println("Extended Operands :" + infixExpression.toString());
		}
	}
	
	private MethodInvocation getMethodInvocationGet(AST ast, Expression expression) {
		MethodInvocation methodInvocation= ast.newMethodInvocation();
		methodInvocation.setExpression(expression);
		methodInvocation.setName(ast.newSimpleName("get")); //$NON-NLS-1$
		return methodInvocation;
	}

	private boolean findFieldInExtendedOperands(InfixExpression infixExpression) {
		
		List<Expression> extendedOperands= infixExpression.extendedOperands();
		boolean foundFieldToBeRefactoredInInfix= false;
		
		for (Iterator<Expression> iterator= extendedOperands.iterator(); iterator.hasNext();) {
			Expression expression= iterator.next();
			if (considerBinding(resolveBinding(expression)) && !foundFieldToBeRefactoredInInfix) {
				foundFieldToBeRefactoredInInfix= true;
				extendedOperands.remove(expression);
			}
		}
		return foundFieldToBeRefactoredInInfix;
	}

	private boolean refactorIntoAddAndGet(Assignment node, MethodInvocation invocation, InfixExpression infixExpression, Operator operator) {
		AST ast= invocation.getAST();
		boolean needToVisitRHS= false;
		System.out.println("The infix expression I want to add to the arguments is: " + infixExpression.toString());
		if (operator == InfixExpression.Operator.PLUS) {
			invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
			invocation.arguments().add(fRewriter.createMoveTarget(infixExpression));
			//invocation.arguments().add(infixExpression);
		} else if (operator == InfixExpression.Operator.MINUS) {
			invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
			// TODO fix subtraction
			invocation.arguments().add(fRewriter.createMoveTarget(infixExpression));
		} else { // TODO other operators
			createUnsafeOperatorWarning(node);
//			ASTNode statement= ASTNodes.getParent(node, Statement.class);
//			Block body= (Block) ASTNodes.getParent(node, Block.class);
//
//			ListRewrite rewriter= insertLineCommentBeforeNode("// TODO The operations below cannot be executed atomically.",  //$NON-NLS-1$
//					body, statement, Block.STATEMENTS_PROPERTY);
//			rewriter.replace(statement, setInvocationStatement, createGroupDescription(READ_AND_WRITE_ACCESS));
			needToVisitRHS= true;
		}
		return needToVisitRHS;
	}

	private void refactorIntoAddAndGet(Assignment node, AST ast, MethodInvocation invocation,
			Expression receiver, List<Expression> arguments, Expression operand, Object operator) {
		
		if (operator == InfixExpression.Operator.PLUS || operator == Assignment.Operator.PLUS_ASSIGN) {
			invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
			arguments.add((Expression) ASTNode.copySubtree(ast, operand));
		} else if (operator == InfixExpression.Operator.MINUS || operator == Assignment.Operator.MINUS_ASSIGN) {
			invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
			arguments.add(createNegativeExpression(operand));
		} else {
			createUnsafeOperatorWarning(node);
			ASTNode statement= ASTNodes.getParent(node, Statement.class);
			Block body= (Block) ASTNodes.getParent(node, Block.class);
			
			if (operator instanceof Assignment.Operator) {
				operator= getOperatorFromAssignmentOperator((Assignment.Operator) operator);
			}
			Statement setInvocationStatement= refactorUnsafeArithmeticOperations((InfixExpression.Operator) operator, invocation,
					ast, receiver, operand);
			
			ListRewrite rewriter= insertLineCommentBeforeNode("// TODO The operations below cannot be executed atomically.",  //$NON-NLS-1$
					body, statement, Block.STATEMENTS_PROPERTY);
			rewriter.replace(statement, setInvocationStatement, createGroupDescription(READ_AND_WRITE_ACCESS));
		}
	}

	private boolean checkSynchronizedBlockForReturnStatement(Assignment node) {

		ASTNode syncStatement= ASTNodes.getParent(node, SynchronizedStatement.class);
		ASTNode methodDecl= ASTNodes.getParent(node, MethodDeclaration.class);
		
		if (syncStatement != null) {
			Block methodBlock= ((MethodDeclaration) methodDecl).getBody();
			
			insertLineCommentBeforeNode("// TODO The statements in the block below are not properly synchronized.", //$NON-NLS-1$
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
					insertLineCommentBeforeNode("// TODO The statements in the method below are not properly synchronized.", //$NON-NLS-1$
							typeDeclaration, methodDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
					break;
				}
			}
			return true;
		}
		return false;
	}
	
	private void createUnsafeOperatorWarning(Assignment node) {
		
		fStatus.addWarning("Cannot execute " //$NON-NLS-1$
				+ fFieldBinding.getName()
				+ ".set(" //$NON-NLS-1$
				+ fFieldBinding.getName()
				+ ".get()) " //$NON-NLS-1$
				+ "atomically. This would be required in converting " //$NON-NLS-1$
				+ node.toString()
				+ ". Consider using locks instead."); //$NON-NLS-1$	
	}

	private PrefixExpression createNegativeExpression(Expression expression) {
		
		AST ast= expression.getAST();
		PrefixExpression newPrefixExpression= ast.newPrefixExpression();
		
		newPrefixExpression.setOperator(PrefixExpression.Operator.MINUS);
		boolean needsParentheses= needsParentheses(expression);
		ASTNode copyExpression=  ASTNode.copySubtree(ast, expression);
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
			invocation.setName(ast.newSimpleName("getAndIncrement")); //$NON-NLS-1$
		}
		else if (operator == PostfixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName("getAndDecrement")); //$NON-NLS-1$
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
			invocation.setName(ast.newSimpleName("incrementAndGet")); //$NON-NLS-1$
		}
		else if (operator == PrefixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName("decrementAndGet")); //$NON-NLS-1$
		}
		if ( !(changeSynchronizedBlock(expression, invocation, PREFIX_ACCESS) || changeSynchronizedMethod(expression, invocation, PREFIX_ACCESS)) ) {
			fRewriter.replace(expression, invocation, createGroupDescription(PREFIX_ACCESS));
		} 
		return false;
	}
	
	@Override
	public void endVisit(CompilationUnit node) {
		fImportRewriter.addImport("java.util.concurrent.atomic.AtomicInteger"); //$NON-NLS-1$
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
				if (!isReturnStatementWithIntField(statement) && !sideEffectsFinder.hasSideEffects(statement)) {
					fRewriter.replace(syncStatement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
				} else if (sideEffectsFinder.hasSideEffects(statement)) {
					fRewriter.replace(statement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
				}
			}
			return true;
		}	
		return false;
	}

	private boolean isReturnStatementWithIntField(Statement statement) {
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
				statement.accept(new FieldReferenceFinderAtomicInteger((Statement) enclosingStatement, fFieldBinding, fStatus));
			} else {
				if (sideEffectsFinder.hasSideEffects(statement)) {
					createWarningStatus("Synchronized block contains side effects. Consider using locks instead."); //$NON-NLS-1$
				}
			}
			insertTodoComments(syncBody, numEntries, statement);	
		}
		if (numEntries < (fStatus.getEntries().length + 1)) {
				insertLineCommentBeforeNode("// TODO The statement below is not properly synchronized.", //$NON-NLS-1$
						syncBody, enclosingStatement, Block.STATEMENTS_PROPERTY);
		}
	}

	private void insertTodoComments(Block syncBody, int numEntries, Statement statement) {
		if (numEntries < (fStatus.getEntries().length + 1)) {
			RefactoringStatusEntry[] entries= fStatus.getEntries();	
			RefactoringStatusEntry refactoringStatusEntry= entries[(entries.length-1)];
			if (refactoringStatusEntry.getMessage().matches(
					"Synchronized block contains references to another field.*AtomicInteger cannot preserve invariants over two field accesses, " + //$NON-NLS-1$
					".*consider using locks instead.")) { //$NON-NLS-1$
				insertLineCommentBeforeNode("// TODO The statement below is not properly synchronized.", //$NON-NLS-1$ 
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
					//System.out.println("" + statement.toString());
					if (!isReturnStatementWithIntField(statement) && !sideEffectsFinder.hasSideEffects(statement)) {
						removeSynchronizedModifier(methodDecl, modifiers);
					}
				} else {
					if (!isReturnStatementWithIntField(statement)) {
						checkMoreThanOneFieldReference(node, methodDecl.getBody());
					}
				}
				fRewriter.replace(node, invocation, createGroupDescription(accessType));
				return true;
			}
		}
		return false;
	}

	private boolean statementIsRefactored(Statement statement) {
		// TODO Auto-generated method stub
		if (statement instanceof ExpressionStatement) {
			Expression expression= ((ExpressionStatement) statement).getExpression();
			if (expression instanceof MethodInvocation) {
				SimpleName name= ((MethodInvocation) expression).getName();
				if (name.getFullyQualifiedName().equals("addAndGet") //$NON-NLS-1$
						|| name.getFullyQualifiedName().equals("getAndIncrement") //$NON-NLS-1$
						|| name.getFullyQualifiedName().equals("getAndDecrement") //$NON-NLS-1$
						|| name.getFullyQualifiedName().equals("set")) { //$NON-NLS-1$
					return true;
				}
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
	
	private void refactorReturnAtomicIntegerAssignment(Assignment node, ReturnStatement statement, Expression leftHandSide) {
		
		Block body= (Block) ASTNodes.getParent(node, Block.class);
		AST ast= node.getAST();
		
		MethodInvocation getInvocation= ast.newMethodInvocation();
		MethodInvocation setInvocation=	ast.newMethodInvocation();
		
		setInvocation.setName(ast.newSimpleName("set")); //$NON-NLS-1$
		getInvocation.setName(ast.newSimpleName("get")); //$NON-NLS-1$
		
		Expression receiver= getReceiver(leftHandSide);
		if (receiver != null) {
			setInvocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
			getInvocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
		}
		List<Expression> arguments= setInvocation.arguments();
		Expression copyRHS= (Expression) ASTNode.copySubtree(ast, node.getRightHandSide());
		arguments.add(copyRHS);
		
		ListRewrite rewriter= fRewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
		ExpressionStatement setInvocationStatement= ast.newExpressionStatement(setInvocation);
		rewriter.insertBefore(setInvocationStatement, statement, createGroupDescription(WRITE_ACCESS));
		
		ReturnStatement returnStatement= ast.newReturnStatement();
		returnStatement.setExpression(getInvocation);
		rewriter.replace(statement, returnStatement, createGroupDescription(READ_ACCESS));
		
		if (checkSynchronizedBlockForReturnStatement(node)) {
			createWarningStatus("Synchronized block contains a return statement with an assigment.  " + //$NON-NLS-1$
					"Cannot remove the synchronized modifier without introducing an unsafe thread environment."); //$NON-NLS-1$
		} else if (checkSynchronizedMethodForReturnStatement(node)) {
			createWarningStatus("Synchronized method contains a return statement with an assigment.  " + //$NON-NLS-1$
					"Cannot remove the synchronized modifier without introducing an unsafe thread environment."); //$NON-NLS-1$
		}
	}
	
	private void createWarningStatus(String message) {
		fStatus.addWarning(message);
	}

	private Statement refactorUnsafeArithmeticOperations(InfixExpression.Operator operator, MethodInvocation invocation,
			AST ast, Expression receiver, Expression operand) {
		
		MethodInvocation invocationGet= ast.newMethodInvocation();
		invocationGet.setName(ast.newSimpleName("get")); //$NON-NLS-1$
		if (receiver != null) {
			invocationGet.setExpression((Expression) ASTNode.copySubtree(ast, receiver)); 
		}
		List<Expression> argumentsSetInvocation= invocation.arguments();
		InfixExpression newInfixExpression= ast.newInfixExpression();
		
		newInfixExpression.setOperator(operator);
		newInfixExpression.setLeftOperand(invocationGet);
		newInfixExpression.setRightOperand((Expression) ASTNode.copySubtree(ast, operand));
		argumentsSetInvocation.add(newInfixExpression);
		ExpressionStatement setInvocationStatement= ast.newExpressionStatement(invocation);
		return setInvocationStatement;
	}
	
	private class ChangeFieldToGetInvocationVisitor extends ASTVisitor {
		
		@Override
		public boolean visit(SimpleName simpleName) {
			if (considerBinding(resolveBinding(simpleName)) && !simpleName.isDeclaration()) {
				AST ast= simpleName.getAST();

//				MethodInvocation methodInvocation= ast.newMethodInvocation();
//				methodInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));
//				methodInvocation.setName(ast.newSimpleName("get")); //$NON-NLS-1$
				
				ASTNode methodInvocation= fRewriter.createMoveTarget(simpleName);
				methodInvocation= getMethodInvocationGet(ast, (Expression) ASTNode.copySubtree(ast, simpleName));
				
				fRewriter.replace(simpleName, methodInvocation, createGroupDescription(READ_ACCESS));
				System.out.println("Rewriter " + fRewriter.toString()); //$NON-NLS-1$
			}
			return true;
		}
	}
}