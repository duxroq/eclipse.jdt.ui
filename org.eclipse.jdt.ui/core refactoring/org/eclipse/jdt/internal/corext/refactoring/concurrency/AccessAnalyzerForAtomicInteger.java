package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
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
import org.eclipse.jdt.core.dom.IfStatement;
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
	private static final String REPLACE_IF_STATEMENT_WITH_COMPARE_AND_SET= ConcurrencyRefactorings.AtomicIntegerRefactoring_replace_if_statement_with_compare_and_set;
	private static final String COMMENT= ConcurrencyRefactorings.ConcurrencyRefactorings_comment;

	private IVariableBinding fFieldBinding;
	private ASTRewrite fRewriter;
	private ImportRewrite fImportRewriter;
	private List<TextEditGroup> fGroupDescriptions;
	private boolean fIsFieldFinal;
	private RefactoringStatus fStatus;
	private SideEffectsFinderAtomicInteger sideEffectsFinder;
	private HashMap<IfStatement, IfStatementProperties> ifStatementsToNodes;

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
		ifStatementsToNodes= new HashMap<IfStatement, AccessAnalyzerForAtomicInteger.IfStatementProperties>();
	}

	@Override
	public void endVisit(CompilationUnit node) {

		for (Map.Entry<IfStatement, IfStatementProperties> entry : ifStatementsToNodes.entrySet()) {
			IfStatement ifStatement= entry.getKey();
			IfStatementProperties properties= entry.getValue();
			ArrayList<Boolean> nodeIsRefactorable= properties.nodeIsRefactorableForCompareAndSet;
			for (Boolean refactorable : nodeIsRefactorable) {
				if (!refactorable.booleanValue()) {
					properties.isRefactorable= false;
					break;
				}
			}
			if (properties.isRefactorable) {
				refactorIfStatementIntoCompareAndSetInvocation(ifStatement, properties.nodes);
			}
		}
		fImportRewriter.addImport(ConcurrencyRefactorings.AtomicIntegerRefactoring_import);
	}

	public Collection<TextEditGroup> getGroupDescriptions() {
		return fGroupDescriptions;
	}

	public RefactoringStatus getStatus() {
		return fStatus;
	}

	@Override
	public boolean visit(Assignment node) {

		boolean needToVisitRHS= true;
		boolean inReturnStatement= false;
		Expression lhs= node.getLeftHandSide();
		checkIfNodeIsInIfStatementOrForStatement(node);

		if (!considerBinding(resolveBinding(lhs))) {
			return true;
		}
		ASTNode statement= ASTNodes.getParent(node, Statement.class);
		if ((!checkParent(node)) && (statement instanceof ReturnStatement)) {
				inReturnStatement= true;
		}
		if (!fIsFieldFinal) {

			AST ast= node.getAST();
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_set));
			Expression receiver= getReceiver(lhs);

			if (receiver != null) {
				invocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
			}
			List<Expression> arguments= invocation.arguments();
			if (node.getOperator() == Assignment.Operator.ASSIGN) {
				Expression rightHandSide= node.getRightHandSide();
				if (rightHandSide instanceof InfixExpression) {
					needToVisitRHS= infixExpressionHandler(node, ast, invocation, rightHandSide, receiver);
				}
				if (needToVisitRHS) {
					// TODO next line causes 1 test to fail
					//node.getRightHandSide().accept(new ChangeFieldToGetInvocationVisitor());
					node.getRightHandSide().accept(new SideEffectsInAssignmentFinderAndCommenter());
					arguments.add((Expression) fRewriter.createMoveTarget(rightHandSide));
				}
			}
			if (node.getOperator() != Assignment.Operator.ASSIGN) {
				compoundAssignmentHandler(node, ast, invocation, arguments, node.getRightHandSide(), receiver);
			}
			if ((!inReturnStatement)
					&& (!changeSynchronizedBlock(node, invocation, WRITE_ACCESS))
					&& (!changeSynchronizedMethod(node, invocation, WRITE_ACCESS))) {

					fRewriter.replace(node, invocation, createGroupDescription(WRITE_ACCESS));
			} else if (inReturnStatement) {
				refactorReturnAtomicIntegerAssignment(node, (Statement) statement, invocation, receiver);
			}
		}
		return false;
	}

	@Override
	public boolean visit(InfixExpression infixExpression) {

		checkIfNodeIsInIfStatementOrForStatement(infixExpression);
		return true;
	}

	@Override
	public boolean visit(PostfixExpression expression) {

		Expression operand= expression.getOperand();
		PostfixExpression.Operator operator= expression.getOperator();

		//checkIfNodeIsInIfStatement(expression);

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

		//checkIfNodeIsInIfStatement(expression);

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
	public boolean visit(SimpleName node) {

		AST ast= node.getAST();
		AtomicInteger i= new AtomicInteger();

		checkForIntConversionsToDoublesFloatsLongsOrBytes(node, ast);

		if ((!node.isDeclaration()) && (considerBinding(resolveBinding(node)))) {
			MethodInvocation invocationGet= getMethodInvocationGet(ast, (Expression) ASTNode.copySubtree(ast, node));
			if (!(changeSynchronizedBlock(node, invocationGet, READ_ACCESS) || changeSynchronizedMethod(node, invocationGet, READ_ACCESS))) {
				fRewriter.replace(node, invocationGet, createGroupDescription(READ_ACCESS));
			}
		}
		return true;
	}

	// TODO needs to support other types of conversions, like casts
	private void checkForIntConversionsToDoublesFloatsLongsOrBytes(SimpleName node, AST ast) {

		// TODO make sure this works with synchronized methods or blocks
		MethodInvocation methodInvocationParent= (MethodInvocation) ASTNodes.getParent(node, MethodInvocation.class);
		if ((methodInvocationParent != null) && (methodInvocationParent.getName().toString().equals("toString"))) {
			if (methodInvocationParent.getExpression().toString().equals("Integer")) {
				MethodInvocation parent= (MethodInvocation) ASTNodes.getParent(methodInvocationParent, MethodInvocation.class);
				if ((parent != null)) {
					if ((parent.getExpression().toString().equals("Double")) && (parent.getName().toString().equals("parseDouble"))) {
						// replace parent with new invocation
						MethodInvocation methodInvocation= ast.newMethodInvocation();
						methodInvocation.setName(ast.newSimpleName("doubleValue"));
						methodInvocation.setExpression(ast.newSimpleName(node.toString()));
						fRewriter.replace(parent, methodInvocation, createGroupDescription(COMMENT));
					} else if ((parent.getExpression().toString().equals("Double")) && (parent.getName().toString().equals("parseDouble"))) {

					}
				}
			}
		}
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

	private boolean changeSynchronizedBlock(ASTNode node, Expression invocation, String accessType) {

		AST ast= node.getAST();
		Statement firstStatement= (Statement) ASTNodes.getParent(node, Statement.class);
		ASTNode syncStatement= ASTNodes.getParent(node, SynchronizedStatement.class);

		if (syncStatement != null) {
			Block syncBody= ((SynchronizedStatement) syncStatement).getBody();
			List<?> syncBodyStatements= syncBody.statements();
			Statement statement= (Statement) syncBodyStatements.get(0);
			if (syncBodyStatements.size() > 1) {
				if (firstStatement == statement) {
					insertLineCommentBeforeNode(
							ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_block,
							syncBody, statement, Block.STATEMENTS_PROPERTY);
				}
				fRewriter.replace(node, invocation, createGroupDescription(accessType));
				checkMoreThanOneFieldReference(node, syncBody);
			} else {
				ExpressionStatement newExpressionStatement= ast.newExpressionStatement(invocation);
				if (!isReturnStatementWithIntFieldAssignment(statement) && !sideEffectsFinder.hasSideEffects(statement)) {
					fRewriter.replace(syncStatement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
				} else if (sideEffectsFinder.hasSideEffects(statement)) {
					insertLineCommentBeforeNode(
							ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_block,
							syncBody, statement, Block.STATEMENTS_PROPERTY);
					fRewriter.replace(statement, newExpressionStatement, createGroupDescription(accessType));
				}
			}
			return true;
		}
		return false;
	}

	private boolean changeSynchronizedMethod(ASTNode node, Expression invocation, String accessType) {

		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);
		if (methodDecl != null) {
			int modifiers= methodDecl.getModifiers();

			if (Modifier.isSynchronized(modifiers)) {
				List<Statement> methodBodyStatements= methodDecl.getBody().statements();
				Statement firstStatement= methodBodyStatements.get(0);
				if (methodBodyStatements.size() == 1) {
					if ((!isReturnStatementWithIntFieldAssignment(statement)) && (!sideEffectsFinder.hasSideEffects(statement))) {
						removeSynchronizedModifier(methodDecl, modifiers);
					} else if (sideEffectsFinder.hasSideEffects(statement)) {
						insertStatementsNotSynchronizedInMethodComment(node, statement, methodDecl, firstStatement);
					}
				} else {
					if (!isReturnStatementWithIntFieldAssignment(statement)) {
						insertStatementsNotSynchronizedInMethodComment(node, statement, methodDecl, firstStatement);
						checkMoreThanOneFieldReference(node, methodDecl.getBody());
					}
				}
				fRewriter.replace(node, invocation, createGroupDescription(accessType));
				return true;
			}
		}
		return false;
	}

	// TODO comments for conditional statements
	// TODO look at the removal of synchronized tags for only if statements, for statements, etc!

	//----- Helper Methods -----

	private boolean checkIfNodeIsInIfStatementOrForStatement(ASTNode node) {
		IfStatement ifStatement= (IfStatement) ASTNodes.getParent(node, IfStatement.class);
//		ForStatement forStatement= (ForStatement) ASTNodes.getParent(node, ForStatement.class);
//		WhileStatement whileStatement= (WhileStatement) ASTNodes.getParent(node, WhileStatement.class);

		System.out.println("The node I am checking is:: " + node.toString());
		if (ifStatement != null) {
			System.out.println("The node is in an ifStatement");
			IfStatementProperties properties= null;
			if (!ifStatementsToNodes.containsKey(ifStatement)) {
				properties= new IfStatementProperties();
				ifStatementsToNodes.put(ifStatement, properties);

			} else {
				properties= ifStatementsToNodes.get(ifStatement);
			}
			if (!properties.nodes.contains(node)) {
				boolean nodeIsRefactorable= false;
				properties.nodes.add(node);
				nodeIsRefactorable= checkIfNodeIsRefactorableForCompareAndSet(node, ifStatement, nodeIsRefactorable);
				properties.nodeIsRefactorableForCompareAndSet.add(new Boolean(nodeIsRefactorable));
			}
			return true;
		}
		return false;
	}

	private boolean checkIfNodeIsRefactorableForCompareAndSet(ASTNode node, IfStatement ifStatement, boolean nodeIsRefactorable) {

		if (node instanceof Assignment) {
			Statement thenStatement= ifStatement.getThenStatement();
			ASTNode parentStatement= ASTNodes.getParent(node, Statement.class);
			if (!(thenStatement instanceof Block) || ((thenStatement instanceof Block) && (((Block) thenStatement).statements().size() == 1))) {
				if (thenStatement instanceof Block) {
					List<Statement> statements= ((Block) thenStatement).statements();
					for (Iterator<Statement> iterator= statements.iterator(); iterator.hasNext();) {
						Statement statement= iterator.next();
						if (ASTMatcher.safeEquals(parentStatement, statement)) {
							nodeIsRefactorable= checkIfAssignmentNodeIsRefactorableForCompareAndSet(node);
						}
					}
				} else if (ASTMatcher.safeEquals(parentStatement, thenStatement)) {
					nodeIsRefactorable= checkIfAssignmentNodeIsRefactorableForCompareAndSet(node);
				}
			}
		} else if (node instanceof InfixExpression) {
			Assignment assignmentParent= (Assignment) ASTNodes.getParent(node, Assignment.class);
			if (assignmentParent == null) {

				Expression expression= ifStatement.getExpression();
				if (ASTMatcher.safeEquals(expression, node)) {

					if (expression instanceof InfixExpression) {
						nodeIsRefactorable= checkIfInfixExpressionNodeIsRefactorableForCompareAndSet(expression);
					}
				}
			}
		}
		return nodeIsRefactorable;
	}

	private boolean checkIfInfixExpressionNodeIsRefactorableForCompareAndSet(Expression expression) {

		Operator operator= ((InfixExpression) expression).getOperator();
		Expression leftOperand= ((InfixExpression) expression).getLeftOperand();
		Expression rightOperand= ((InfixExpression) expression).getRightOperand();
		boolean leftOperandIsField= considerBinding(resolveBinding(leftOperand));
		boolean rightOperandIsField= considerBinding(resolveBinding(rightOperand));

		if ((operator == InfixExpression.Operator.EQUALS) && (leftOperandIsField != rightOperandIsField)) {
			return true;
		}
		return false;
	}

	private boolean checkIfAssignmentNodeIsRefactorableForCompareAndSet(ASTNode node) {

		Expression leftHandSide= ((Assignment) node).getLeftHandSide();

		if (considerBinding(resolveBinding(leftHandSide))) {
			return true;
		}
		return false;
	}
	private void checkMoreThanOneFieldReference(ASTNode node, Block syncBody) {

		ASTNode enclosingStatement= ASTNodes.getParent(node, Statement.class);
		List<Statement> statements= syncBody.statements();
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
		}
	}

	private boolean checkParent(ASTNode node) {

		ASTNode parent= node.getParent();
		return parent instanceof ExpressionStatement;
	}

	private boolean checkSynchronizedBlockForReturnStatement(Assignment node) {

		ASTNode syncStatement= ASTNodes.getParent(node, SynchronizedStatement.class);
		ASTNode methodDecl= ASTNodes.getParent(node, MethodDeclaration.class);

		if (syncStatement != null) {
			Block methodBlock= ((MethodDeclaration) methodDecl).getBody();

			insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_block,
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
					insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_method,
							typeDeclaration, methodDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
					break;
				}
			}
			return true;
		}
		return false;
	}

	private boolean compoundAssignmentHandler(Assignment node, AST ast,
			MethodInvocation invocation, List<Expression> arguments, Expression rightHandSide, Expression receiver) {

		Assignment.Operator operator= node.getOperator();

		if ((operator == Assignment.Operator.PLUS_ASSIGN) || (operator == Assignment.Operator.MINUS_ASSIGN)) {
			if (operator == Assignment.Operator.PLUS_ASSIGN) {
				invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
				rightHandSide= getNewOperandWithGetInvocations(ast, rightHandSide, receiver);
				arguments.add(rightHandSide);
			} else if (operator == Assignment.Operator.MINUS_ASSIGN) {
				invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
				rightHandSide.accept(new ChangeFieldToGetInvocationVisitor());
				arguments.add(createNegativeExpression(rightHandSide));
			}
			if (!(rightHandSide instanceof NumberLiteral) && !(rightHandSide instanceof SimpleName)) {
				insertAtomicOpTodoComment(node);
			}
		} else {
			createUnsafeOperatorWarning(node);
			insertAtomicOpTodoComment(node);
			InfixExpression.Operator newOperator;
			newOperator= ASTNodes.convertToInfixOperator(operator);
			MethodInvocation invocationGet= ast.newMethodInvocation();
			invocationGet.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
			if (receiver != null) {
				invocationGet.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
			}
			InfixExpression newInfixExpression= ast.newInfixExpression();
			rightHandSide= getNewOperandWithGetInvocations(ast, rightHandSide, receiver);
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
		}
		return false;
	}

	private boolean considerBinding(IBinding binding) {

		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		return fFieldBinding.isEqualTo(((IVariableBinding) binding).getVariableDeclaration());
	}

	private TextEditGroup createGroupDescription(String name) {

		TextEditGroup result= new TextEditGroup(name);

		fGroupDescriptions.add(result);
		return result;
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

	private void createWarningStatus(String message) {
		fStatus.addWarning(message);
	}

	private boolean foundFieldInExtendedOperands(InfixExpression infixExpression) {

		List<Expression> extendedOperands= infixExpression.extendedOperands();
		boolean foundFieldToBeRefactoredInInfix= false;

		for (Iterator<Expression> iterator= extendedOperands.iterator(); iterator.hasNext();) {
			Expression expression= iterator.next();
			if ((considerBinding(resolveBinding(expression))) && (!foundFieldToBeRefactoredInInfix)) {
				foundFieldToBeRefactoredInInfix= true;
				fRewriter.remove(expression, createGroupDescription(READ_ACCESS));
				extendedOperands.remove(expression);
			}
		}
		return foundFieldToBeRefactoredInInfix;
	}

	private void getExpressionsAndReplace(AST ast, Expression leftOperand, Expression rightOperand, Expression receiver) {

		Expression newLeftOperand;
		Expression newRightOperand;
		newLeftOperand= getNewOperandWithGetInvocations(ast, leftOperand, receiver);
		newRightOperand= getNewOperandWithGetInvocations(ast, rightOperand, receiver);

		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(READ_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(READ_ACCESS));
	}

	private MethodInvocation getMethodInvocationGet(AST ast, Expression expression) {

		MethodInvocation methodInvocation= ast.newMethodInvocation();
		if (expression != null) {
			methodInvocation.setExpression(expression);
		}
		methodInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		return methodInvocation;
	}

	private Expression getNewOperandWithGetInvocations(AST ast, Expression operand, Expression reciever) {

		Expression newOperand= null;

		if (considerBinding(resolveBinding(operand))) {
			newOperand= getMethodInvocationGet(ast, (Expression) ASTNode.copySubtree(ast, reciever));
		} else {
			operand.accept(new ChangeFieldToGetInvocationVisitor());
			newOperand= (Expression) fRewriter.createMoveTarget(operand);
		}
		return newOperand;
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

	private boolean infixExpressionHandler(Assignment node, AST ast, MethodInvocation invocation,
			Expression rightHandSide, Expression receiver) {

		boolean needToVisitRHS= true;
		InfixExpression infixExpression= (InfixExpression) rightHandSide;

		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();
		Operator operator= infixExpression.getOperator();

		boolean leftOperandIsField= considerBinding(resolveBinding(leftOperand));
		boolean rightOperandIsField= considerBinding(resolveBinding(rightOperand));

		if ((operator == InfixExpression.Operator.PLUS) || (operator == InfixExpression.Operator.MINUS)) {
			if (leftOperandIsField || rightOperandIsField) {
				if (leftOperandIsField) {
					leftOperandOfInfixExpressionHandler(rightOperand, leftOperand, receiver, ast,
							invocation, infixExpression, node, operator);
					return false;
				} else if (rightOperandIsField) {
					needToVisitRHS= rightOperandOfInfixExpressionHandler(rightOperand, leftOperand, receiver, ast,
							invocation, infixExpression, node, operator);
					return needToVisitRHS;
				}
			} else if (infixExpression.hasExtendedOperands()) {
				needToVisitRHS= extendedOperandsOfInfixExpressionHandler(rightOperand, leftOperand, receiver, ast, invocation, infixExpression, node, operator);
				return needToVisitRHS;
			} else {
				insertAtomicOpTodoComment(node);
				getExpressionsAndReplace(ast, leftOperand, rightOperand, receiver);
			}
		} else {
			createUnsafeOperatorWarning(node);
			insertAtomicOpTodoComment(node);
			getExpressionsAndReplace(ast, leftOperand, rightOperand, receiver);
			if (infixExpression.hasExtendedOperands()) {
				changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
			}
		}
		return needToVisitRHS;
	}

	private void leftOperandOfInfixExpressionHandler(Expression rightOperand, Expression leftOperand,
			Expression receiver, AST ast, MethodInvocation invocation, InfixExpression infixExpression, Assignment node,
			Operator operator) {

		Expression newLeftOperand= (Expression) fRewriter.createMoveTarget(leftOperand);
		Expression newRightOperand= (Expression) fRewriter.createMoveTarget(rightOperand);
		newLeftOperand= getNewOperandWithGetInvocations(ast, rightOperand, receiver);

		if (infixExpression.hasExtendedOperands()) {
			newRightOperand= getNewOperandWithGetInvocations(ast, (Expression) infixExpression.extendedOperands().get(0), receiver);
			replaceOperandsAndChangeFieldRefsInExtOpsToGetInvocations(infixExpression, leftOperand, rightOperand, newLeftOperand, newRightOperand);
			if (infixExpression.hasExtendedOperands() && operator != InfixExpression.Operator.MINUS) {
				fRewriter.remove((ASTNode) infixExpression.extendedOperands().get(0), createGroupDescription(READ_ACCESS));
				infixExpression.extendedOperands().remove(0);
			}
			insertAtomicOpTodoComment(node);
			refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver);
		} else {
			refactorInfixExpressionWithNoExtOperandsIntoAddAndGet(node, ast, invocation, receiver, rightOperand, operator);
		}
	}

	private boolean rightOperandOfInfixExpressionHandler(Expression rightOperand, Expression leftOperand,
			Expression receiver, AST ast, MethodInvocation invocation, InfixExpression infixExpression, Assignment node,
			Operator operator) {

		Expression newLeftOperand= (Expression) fRewriter.createMoveTarget(leftOperand);
		Expression newRightOperand= (Expression) fRewriter.createMoveTarget(rightOperand);
		newLeftOperand= getNewOperandWithGetInvocations(ast, leftOperand, receiver);
		newRightOperand= getNewOperandWithGetInvocations(ast, rightOperand, receiver);

		if (infixExpression.hasExtendedOperands() && operator != InfixExpression.Operator.MINUS) {
			newRightOperand= getNewOperandWithGetInvocations(ast, (Expression) infixExpression.extendedOperands().get(0), receiver);
			replaceOperandsAndChangeFieldRefsInExtOpsToGetInvocations(infixExpression, leftOperand, rightOperand, newLeftOperand, newRightOperand);
			if (infixExpression.hasExtendedOperands() && operator != InfixExpression.Operator.MINUS) {
				fRewriter.remove((ASTNode) infixExpression.extendedOperands().get(0), createGroupDescription(READ_ACCESS));
				infixExpression.extendedOperands().remove(0);
			}
			insertAtomicOpTodoComment(node);
			refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver);
			return false;
		} else if (operator != InfixExpression.Operator.MINUS) {
			leftOperand.accept(new ChangeFieldToGetInvocationVisitor());
			refactorAssignmentIntoAddAndGetInvocation(invocation, leftOperand, operator, receiver);
			if (!(leftOperand instanceof NumberLiteral) && !(leftOperand instanceof SimpleName)) {
				insertAtomicOpTodoComment(node);
			}
			return false;
		} else {
			insertAtomicOpTodoComment(node);
			replaceOperandsAndChangeFieldRefsInExtOpsToGetInvocations(infixExpression, leftOperand, rightOperand, newLeftOperand, newRightOperand);
			return true;
		}
	}

	private boolean extendedOperandsOfInfixExpressionHandler(Expression rightOperand, Expression leftOperand, Expression receiver,
			AST ast, MethodInvocation invocation, InfixExpression infixExpression, Assignment node, Operator operator) {

		getExpressionsAndReplace(ast, leftOperand, rightOperand, receiver);
		insertAtomicOpTodoComment(node);

		if (operator != InfixExpression.Operator.MINUS) {
			if (foundFieldInExtendedOperands(infixExpression)) {
				changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
				refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver);
				return false;
			} else {
				changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
			}
		} else {
			changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
		}
		return true;
	}

	private void refactorInfixExpressionWithNoExtOperandsIntoAddAndGet(Assignment node, AST ast, MethodInvocation invocation,
			Expression receiver, Expression rightOperand, Operator operator) {

		if (considerBinding(resolveBinding(rightOperand))) {
			MethodInvocation methodInvocation= ast.newMethodInvocation();
			if (receiver != null) {
				methodInvocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
			}
			methodInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			invocation.arguments().add(methodInvocation);
			insertAtomicOpTodoComment(node);
		} else {
			rightOperand.accept(new ChangeFieldToGetInvocationVisitor());
			refactorAssignmentIntoAddAndGetInvocation(invocation, rightOperand, operator, receiver);
			if (!(rightOperand instanceof NumberLiteral) && !(rightOperand instanceof SimpleName)) {
				insertAtomicOpTodoComment(node);
			}
		}
	}

	// TODO what if it is inside a for statement?
	// TODO does not consistently apply todo comments
	// TODO is there anywhere else where I need to accommodate if statements...
	private void insertAtomicOpTodoComment(ASTNode node) {

		for (Map.Entry<IfStatement, IfStatementProperties> entry : ifStatementsToNodes.entrySet()) {
			IfStatement ifStatement= entry.getKey();
			IfStatementProperties properties= entry.getValue();
			ArrayList<ASTNode> nodes= properties.nodes;
			AST ast= ifStatement.getAST();
			if (nodes.contains(node)) {
				Expression expression= ifStatement.getExpression();
				if (expression != null) {
					List<ASTNode> expressionChildren= ASTNodes.getChildren(expression);
					if (expressionChildren.contains(node)) {
						return;
					}
				}
				Statement thenStatement= ifStatement.getThenStatement();
				if (thenStatement != null) {
					//TODO maybe this is the problem?
					List<? extends ASTNode> thenStatementChildren= ASTNodes.getChildren(thenStatement);
					if (thenStatementChildren.contains(node)) {
						if (thenStatement instanceof Block) {
							Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
							if (statement != null) {
								insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
										thenStatement, statement, Block.STATEMENTS_PROPERTY);
							}
						} else {
							// make a new block
							// TODO use common code
							Block newBlock= ast.newBlock();
							ASTNode createMoveTarget= fRewriter.createMoveTarget(thenStatement);
							fRewriter.replace(thenStatement, newBlock, createGroupDescription(COMMENT));
							LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically_nl,
									ASTNode.LINE_COMMENT);
							ListRewrite rewriter= fRewriter.getListRewrite(newBlock, Block.STATEMENTS_PROPERTY);
							rewriter.insertLast(createMoveTarget, createGroupDescription(WRITE_ACCESS));
							rewriter.insertFirst(lineComment, createGroupDescription(COMMENT));
							ifStatement.setThenStatement(newBlock);
						}
					}
				}
				// TODO else statement
			}
			createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
			return;
		}
		ASTNode body= ASTNodes.getParent(node, Block.class);
		ASTNode statement= ASTNodes.getParent(node, Statement.class);
		if ((statement != null) && (body != null)) {
			insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
					body, statement, Block.STATEMENTS_PROPERTY);
		}
		createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
	}

	private ListRewrite insertLineCommentBeforeNode(String comment, ASTNode body, ASTNode node, ChildListPropertyDescriptor descriptor) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(comment, ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(body, descriptor);
		rewriter.insertBefore(lineComment, node, createGroupDescription(COMMENT));
		return rewriter;
	}

	private void insertStatementsNotSynchronizedInMethodComment(ASTNode node, Statement statement, MethodDeclaration methodDecl, Statement firstStatement) {

		TypeDeclaration typeDeclaration= (TypeDeclaration) ASTNodes.getParent(node, TypeDeclaration.class);

		if ((typeDeclaration != null) && (firstStatement == statement)) {
			insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_method,
					typeDeclaration, methodDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
		}
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

	private boolean needsParentheses(Expression expression) {

		int type= expression.getNodeType();

		return (type == ASTNode.INFIX_EXPRESSION) || (type == ASTNode.CONDITIONAL_EXPRESSION) ||
				(type == ASTNode.PREFIX_EXPRESSION) || (type == ASTNode.POSTFIX_EXPRESSION) ||
				(type == ASTNode.CAST_EXPRESSION) || (type == ASTNode.INSTANCEOF_EXPRESSION);
	}

	// TODO make more tests for this
	private void refactorIfStatementIntoCompareAndSetInvocation(IfStatement ifStatement, ArrayList<ASTNode> nodes) {

		AST ast= ifStatement.getAST();
		MethodInvocation compareAndSetInvocation= ast.newMethodInvocation();

		if (nodes.size() != 2) {
			return;
		} else {
			ASTNode firstNode= nodes.get(0);
			ASTNode secondNode= nodes.get(1);
			boolean oneIsAnAssignment= (firstNode instanceof Assignment) != (secondNode instanceof Assignment);
			boolean oneIsAnInfixExpression= (firstNode instanceof InfixExpression) != (secondNode instanceof InfixExpression);

			if (oneIsAnAssignment && oneIsAnInfixExpression) {

				compareAndSetInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_compareAndSet));
				compareAndSetInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));

				GetArguments getArguments= new GetArguments();

				firstNode.accept(getArguments);
				secondNode.accept(getArguments);
				Expression setExpression= getArguments.getSetExpression();
				Expression compareExpression= getArguments.getCompareExpression();

				if ((compareExpression != null) && (setExpression != null)) {
					compareAndSetInvocation.arguments().add(fRewriter.createMoveTarget(compareExpression));
					compareAndSetInvocation.arguments().add(fRewriter.createMoveTarget(setExpression));
				}
				ExpressionStatement compareAndSetStatement= ast.newExpressionStatement(compareAndSetInvocation);
				fRewriter.replace(ifStatement, compareAndSetStatement, createGroupDescription(REPLACE_IF_STATEMENT_WITH_COMPARE_AND_SET));
			}
		}
	}

	private void refactorAssignmentIntoAddAndGetInvocation(MethodInvocation invocation, Expression operand,
			Object operator, Expression receiver) {

		AST ast= invocation.getAST();

		if ((operator == InfixExpression.Operator.PLUS) || (operator == Assignment.Operator.PLUS_ASSIGN)) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			invocation.arguments().add(fRewriter.createMoveTarget(operand));
		} else if ((operator == InfixExpression.Operator.MINUS) || (operator == Assignment.Operator.MINUS_ASSIGN)) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));

			if (operand instanceof InfixExpression) {
				InfixExpression newInfixExpression= ast.newInfixExpression();
				Expression rightOperand= ((InfixExpression) operand).getRightOperand();
				Expression newLeftOperand= getNewOperandWithGetInvocations(ast, rightOperand, receiver);
				newInfixExpression.setLeftOperand(newLeftOperand);
				Expression newRightOperand= null;
				if (((InfixExpression) operand).hasExtendedOperands()) {
					newRightOperand= getNewOperandWithGetInvocations(ast, (Expression) ((InfixExpression) operand).extendedOperands().get(0), receiver);
					((InfixExpression) operand).extendedOperands().remove(0);
					newInfixExpression.setRightOperand(newRightOperand);
					List<Expression> extendedOperands= ((InfixExpression) operand).extendedOperands();
					for (int i= 0; i < extendedOperands.size(); i++) {
						Expression newOperandWithGetInvocations= getNewOperandWithGetInvocations(ast, extendedOperands.get(i), receiver);
						newInfixExpression.extendedOperands().add(newOperandWithGetInvocations);
					}
				}
				newInfixExpression.setOperator(InfixExpression.Operator.PLUS);
				PrefixExpression newPrefixExpression= ast.newPrefixExpression();
				newPrefixExpression.setOperator(PrefixExpression.Operator.MINUS);
				boolean needsParentheses= needsParentheses(operand);
				if (needsParentheses) {
					ParenthesizedExpression p= ast.newParenthesizedExpression();
					p.setExpression(newInfixExpression);
					newPrefixExpression.setOperand(p);
				} else {
					newPrefixExpression.setOperand(newInfixExpression);
				}
				invocation.arguments().add(newPrefixExpression);
			} else {
				invocation.arguments().add(createNegativeExpression(operand));
			}
		}
	}

	private void refactorReturnAtomicIntegerAssignment(Assignment node, Statement statement, MethodInvocation invocation, ASTNode receiver) {

		Block body= (Block) ASTNodes.getParent(node, Block.class);
		AST ast= node.getAST();
		MethodInvocation getInvocation= ast.newMethodInvocation();

		getInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		if (receiver != null) {
			getInvocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
		}
		ListRewrite rewriter= fRewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
		ExpressionStatement setInvocationStatement= ast.newExpressionStatement(invocation);
		rewriter.insertBefore(setInvocationStatement, statement, createGroupDescription(WRITE_ACCESS));

		ReturnStatement returnStatement= ast.newReturnStatement();
		returnStatement.setExpression(getInvocation);
		fRewriter.replace(statement, returnStatement, createGroupDescription(READ_ACCESS));
		insertLineCommentBeforeNode(
				ConcurrencyRefactorings.AtomicInteger_todo_comment_return_statement_could_not_be_executed_atomically,
				body, returnStatement, Block.STATEMENTS_PROPERTY);

		if (checkSynchronizedBlockForReturnStatement(node)) {
			createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_remove_synch_block_return_assignment);
		} else if (checkSynchronizedMethodForReturnStatement(node)) {
			createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_remove_synch_mod_return_assignment);
		}
	}

	private void removeSynchronizedModifier(MethodDeclaration methodDecl, int modifiers) {

		ModifierRewrite methodRewriter= ModifierRewrite.create(fRewriter, methodDecl);
		int synchronizedModifier= Modifier.SYNCHRONIZED;
		synchronizedModifier= ~ synchronizedModifier;
		int newModifiersWithoutSync= modifiers & synchronizedModifier;
		methodRewriter.setModifiers(newModifiersWithoutSync, createGroupDescription(REMOVE_SYNCHRONIZED_MODIFIER));
	}

	private void replaceOperandsAndChangeFieldRefsInExtOpsToGetInvocations(InfixExpression infixExpression,
			Expression leftOperand, Expression rightOperand, Expression newLeftOperand, Expression newRightOperand) {

		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(READ_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(READ_ACCESS));
		changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
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

	private class ChangeFieldToGetInvocationVisitor extends ASTVisitor {

		@Override
		public boolean visit(SimpleName simpleName) {

			if ((considerBinding(resolveBinding(simpleName))) && (!simpleName.isDeclaration())) {
				AST ast= simpleName.getAST();
				MethodInvocation methodInvocation= getMethodInvocationGet(ast, (Expression) ASTNode.copySubtree(ast, simpleName));
				fRewriter.replace(simpleName, methodInvocation, createGroupDescription(READ_ACCESS));
			}
			return true;
		}
	}

	//----- Helper classes

	private class GetArguments extends ASTVisitor {

		private Expression setExpression= null;
		private Expression compareExpression= null;

		public Expression getCompareExpression() {
			return compareExpression;
		}

		public Expression getSetExpression() {
			return setExpression;
		}

		@Override
		public boolean visit(Assignment assignment) {

			ASTNode assignmentParent= ASTNodes.getParent(assignment, Assignment.class);
			ASTNode infixExpressionParent= ASTNodes.getParent(assignment, InfixExpression.class);

			if ((assignmentParent == null) && (infixExpressionParent == null)) {
				Expression leftHandSide= assignment.getLeftHandSide();
				Expression rightHandSide= assignment.getRightHandSide();
				boolean considerBinding= considerBinding(resolveBinding(leftHandSide));
				if (considerBinding) {
					setExpression= rightHandSide;
				}
			}
			return false;
		}

		@Override
		public boolean visit(InfixExpression infixExpression) {

			ASTNode assignmentParent= ASTNodes.getParent(infixExpression, Assignment.class);
			ASTNode infixExpressionParent= ASTNodes.getParent(infixExpression, InfixExpression.class);

			if ((assignmentParent == null) && (infixExpressionParent == null)) {
				Expression leftOperand= infixExpression.getLeftOperand();
				Expression rightOperand= infixExpression.getRightOperand();
				boolean leftOperandIsField= considerBinding(resolveBinding(leftOperand));
				if (leftOperandIsField) {
					compareExpression= rightOperand;
				} else {
					compareExpression= leftOperand;
				}
			}
			return false;
		}
	}

	private class IfStatementProperties {

		private boolean isRefactorable= true;
		private ArrayList<Boolean> nodeIsRefactorableForCompareAndSet;
		private ArrayList<ASTNode> nodes;

		public IfStatementProperties() {
			isRefactorable= true;
			nodes= new ArrayList<ASTNode>();
			nodeIsRefactorableForCompareAndSet= new ArrayList<Boolean>();
		}
	}

	private class SideEffectsInAssignmentFinderAndCommenter extends ASTVisitor {

		@Override
		public boolean visit(PostfixExpression postfixExpression) {

			Expression operand= postfixExpression.getOperand();
			org.eclipse.jdt.core.dom.PostfixExpression.Operator operator= postfixExpression.getOperator();

			if (!considerBinding(resolveBinding(operand))) {
				if ((operator == PostfixExpression.Operator.INCREMENT) || (operator == PostfixExpression.Operator.DECREMENT)) {
					ASTNode assignment= ASTNodes.getParent(postfixExpression, Assignment.class);
					if (assignment != null) {
						insertAtomicOpTodoComment(postfixExpression);
					}
				}
			} else {
				ASTNode statement= ASTNodes.getParent(postfixExpression, Statement.class);
				if (statement != null) {
				fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
						+ statement.toString()
						+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
						+ postfixExpression.toString());
				} else {
					fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
							+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
							+ postfixExpression.toString());
				}
			}
			return true;
		}

		@Override
		public boolean visit(PrefixExpression prefixExpression) {

			Expression operand= prefixExpression.getOperand();
			org.eclipse.jdt.core.dom.PrefixExpression.Operator operator= prefixExpression.getOperator();

			if (!considerBinding(resolveBinding(operand))) {
				if ((operator == PrefixExpression.Operator.INCREMENT) || (operator == PrefixExpression.Operator.DECREMENT)) {
					ASTNode assignment= ASTNodes.getParent(prefixExpression, Assignment.class);
					if (assignment != null) {
						insertAtomicOpTodoComment(prefixExpression);
					}
				}
			} else {
				ASTNode statement= ASTNodes.getParent(prefixExpression, Statement.class);
				if (statement != null) {
					fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
							+ statement.toString()
							+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
							+ prefixExpression.toString());
				} else {
					fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
							+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
							+ prefixExpression.toString());
				}
			}
			return true;
		}

		@Override
		public boolean visit(Assignment assignment) {

			Expression leftHandSide= assignment.getLeftHandSide();
			if (considerBinding(resolveBinding(leftHandSide))) {
				ASTNode parent= ASTNodes.getParent(assignment, Assignment.class);
				if (parent != null) {
					ASTNode statement= ASTNodes.getParent(assignment, Statement.class);
					if (statement != null) {
					fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
							+ statement.toString()
							+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
							+ assignment.toString());
					} else {
						fStatus.addFatalError(ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment
								+ ConcurrencyRefactorings.AtomicInteger_error_side_effects_on_int_field_in_assignment2
								+ assignment.toString());
					}
				}
			}
			return true;
		}
	}
}