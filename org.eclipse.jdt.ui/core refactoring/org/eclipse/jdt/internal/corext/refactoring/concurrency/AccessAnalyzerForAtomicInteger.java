package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.ForStatement;
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
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.WhileStatement;
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
	private static final String REPLACE_IF_STATEMENT_WITH_COMPARE_AND_SET= ConcurrencyRefactorings.AtomicIntegerRefactoring_replace_if_statement_with_compare_and_set;
	private static final String REPLACE_TYPE_CONVERSION= ConcurrencyRefactorings.AtomicIntegerRefactoring_replace_type_conversion;
	private static final String COMMENT= ConcurrencyRefactorings.ConcurrencyRefactorings_comment;

	// TODO move precondition checking to a different class

	private IVariableBinding fFieldBinding;
	private ASTRewrite fRewriter;
	private ImportRewrite fImportRewriter;
	private List<TextEditGroup> fGroupDescriptions;
	private boolean fIsFieldFinal;
	private RefactoringStatus fStatus;
	private SideEffectsFinderAtomicInteger sideEffectsFinder;
	private HashMap<IfStatement, IfStatementProperties> ifStatementsToNodes;
	private ArrayList<MethodDeclaration> methodsWithComments;
	private ArrayList<Block> blocksWithComments;
	private ArrayList<Statement> visitedSynchronizedBlocks;
	private ArrayList<MethodDeclaration> visitedSynchronizedMethods;

	private ArrayList<Statement> cannotRemoveSynchronizedBlockOrModifier;

	public AccessAnalyzerForAtomicInteger(
			ConvertToAtomicIntegerRefactoring refactoring,
			IVariableBinding field, ASTRewrite rewriter,
			ImportRewrite importRewrite) {

		fFieldBinding= field.getVariableDeclaration();
		fRewriter= rewriter;
		fImportRewriter= importRewrite;
		fGroupDescriptions= new ArrayList<TextEditGroup>();
		sideEffectsFinder= new SideEffectsFinderAtomicInteger(fFieldBinding);
		methodsWithComments= new ArrayList<MethodDeclaration>();
		blocksWithComments= new ArrayList<Block>();
		visitedSynchronizedBlocks= new ArrayList<Statement>();
		visitedSynchronizedMethods= new ArrayList<MethodDeclaration>();
		cannotRemoveSynchronizedBlockOrModifier= new ArrayList<Statement>();
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
			} else {
				insertStatementsNotSynchronizedInMethodComment(ifStatement);
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
	public boolean visit(Assignment assignment) {

		// TODO rename needToVisitRHS
		boolean needToVisitRHS= true;
		boolean inReturnStatement= false;
		Expression lhs= assignment.getLeftHandSide();
		checkIfNodeIsInIfStatement(assignment);

		if (!considerBinding(resolveBinding(lhs))) {
			return true;
		}
		Statement statement= (Statement) ASTNodes.getParent(assignment, Statement.class);
		if ((!checkParent(assignment)) && (statement instanceof ReturnStatement)) {
			cannotRemoveSynchronizedBlockOrModifier.add(statement);
			inReturnStatement= true;
		}
		if (!fIsFieldFinal) {

			AST ast= assignment.getAST();
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_set));
			Expression receiver= getReceiver(lhs);

			if (receiver != null) {
				invocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
			}
			List<Expression> arguments= invocation.arguments();
			if (assignment.getOperator() == Assignment.Operator.ASSIGN) {
				Expression rightHandSide= assignment.getRightHandSide();
				if (rightHandSide instanceof InfixExpression) {
					needToVisitRHS= infixExpressionHandler(assignment, ast, invocation, rightHandSide, receiver);
				}
				if (needToVisitRHS) {
					assignment.getRightHandSide().accept(new ChangeFieldToGetInvocationVisitor());
					assignment.getRightHandSide().accept(new SideEffectsInAssignmentFinderAndCommenter());
					arguments.add((Expression) fRewriter.createMoveTarget(rightHandSide));
				}
			}
			if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
				compoundAssignmentHandler(assignment, ast, invocation, arguments, assignment.getRightHandSide(), receiver);
			}
			if ((!inReturnStatement)
					&& (!checkIfInSynchronizedBlockAndRemoveBlock(assignment, invocation, WRITE_ACCESS))
					&& (!checkIfInSynchronizedMethodAndRemoveModifier(assignment, invocation, WRITE_ACCESS))) {

					fRewriter.replace(assignment, invocation, createGroupDescription(WRITE_ACCESS));
			} else if (inReturnStatement) {
				refactorReturnAtomicIntegerAssignment(assignment, statement, invocation, receiver);
			}
		}
		return false;
	}

	@Override
	public boolean visit(InfixExpression infixExpression) {

		checkIfNodeIsInIfStatement(infixExpression);
		return true;
	}

	@Override
	public boolean visit(PostfixExpression postfixExpression) {

		Expression operand= postfixExpression.getOperand();
		PostfixExpression.Operator operator= postfixExpression.getOperator();

		if (!considerBinding(resolveBinding(operand))) {
			return true;
		}
		AST ast= postfixExpression.getAST();
		MethodInvocation invocation= ast.newMethodInvocation();

		invocation.setExpression((Expression) fRewriter.createCopyTarget(operand));

		if (operator == PostfixExpression.Operator.INCREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_getAndIncrement));
		}
		else if (operator == PostfixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_getAndDecrement));
		}
		if (!(checkIfInSynchronizedBlockAndRemoveBlock(postfixExpression, invocation, POSTFIX_ACCESS) || checkIfInSynchronizedMethodAndRemoveModifier(postfixExpression, invocation, POSTFIX_ACCESS))) {
			fRewriter.replace(postfixExpression, invocation, createGroupDescription(POSTFIX_ACCESS));
		}
		return false;
	}

	@Override
	public boolean visit(PrefixExpression prefixExpression) {

		Expression operand= prefixExpression.getOperand();
		PrefixExpression.Operator operator= prefixExpression.getOperator();

		if (!considerBinding(resolveBinding(operand))) {
			return true;
		}
		AST ast= prefixExpression.getAST();
		MethodInvocation invocation= ast.newMethodInvocation();

		invocation.setExpression((Expression) fRewriter.createCopyTarget(operand));

		if (operator == PrefixExpression.Operator.INCREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_incrementAndGet));
		}
		else if (operator == PrefixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_decrementAndGet));
		}
		if (!(checkIfInSynchronizedBlockAndRemoveBlock(prefixExpression, invocation, PREFIX_ACCESS) || checkIfInSynchronizedMethodAndRemoveModifier(prefixExpression, invocation, PREFIX_ACCESS))) {
			fRewriter.replace(prefixExpression, invocation, createGroupDescription(PREFIX_ACCESS));
		}
		return false;
	}

	@Override
	public boolean visit(SimpleName simpleName) {

		// TODO
		AST ast= simpleName.getAST();
		ReplacementPair replacementPair= null;
		String accessType= REPLACE_TYPE_CONVERSION;
		MethodInvocation invocation= ast.newMethodInvocation();
		replacementPair= checkForTypeConversionsAndReplace(simpleName, invocation, ast);

		if ((!simpleName.isDeclaration()) && (considerBinding(resolveBinding(simpleName)))) {
			if (replacementPair == null) {
				accessType= READ_ACCESS;
				invocation= getMethodInvocationGet(ast, (Expression) ASTNode.copySubtree(ast, simpleName));
				if (!(checkIfInSynchronizedBlockAndRemoveBlock(simpleName, invocation, accessType)
				|| checkIfInSynchronizedMethodAndRemoveModifier(simpleName, invocation, accessType))) {

					fRewriter.replace(simpleName, invocation, createGroupDescription(accessType));
				}
			} else {
				if (!(checkIfInSynchronizedBlockAndRemoveBlock(replacementPair.whatToReplace, (Expression) replacementPair.replacement, accessType)
				|| checkIfInSynchronizedMethodAndRemoveModifier(replacementPair.whatToReplace, (Expression) replacementPair.replacement, accessType))) {

					fRewriter.replace(replacementPair.whatToReplace, replacementPair.replacement, createGroupDescription(accessType));
				}
			}
		}
		return true;
	}

	private ReplacementPair checkForTypeConversionsAndReplace(SimpleName simpleName, MethodInvocation invocation, AST ast) {

		MethodInvocation methodInvocationParent= (MethodInvocation) ASTNodes.getParent(simpleName, MethodInvocation.class);

		if ((methodInvocationParent != null) && (methodInvocationParent.getName().toString().equals(ConcurrencyRefactorings.ToString))) {
			if (methodInvocationParent.getExpression().toString().equals(ConcurrencyRefactorings.Integer)) {
				MethodInvocation parent= (MethodInvocation) ASTNodes.getParent(methodInvocationParent, MethodInvocation.class);
				if ((parent != null)) {
					if ((parent.getExpression().toString().equals(ConcurrencyRefactorings.Double))
							&& (parent.getName().toString().equals(ConcurrencyRefactorings.ParseDouble))) {
						invocation= replaceTypeConversion(ast, ConcurrencyRefactorings.AtomicInteger_doubleValue);
						return new ReplacementPair(parent, invocation);
					}
				}
			}
		}
		ASTNode castExpression= ASTNodes.getParent(simpleName, CastExpression.class);
		if (castExpression != null) {
			Type type= ((CastExpression) castExpression).getType();
			ASTNode parenthesizedExpression= ASTNodes.getParent(castExpression, ParenthesizedExpression.class);
			ASTNode expression= castExpression;
			if (ASTMatcher.safeEquals(((ParenthesizedExpression) parenthesizedExpression).getExpression(), castExpression)) {
				expression= parenthesizedExpression;
			}
			if (type instanceof PrimitiveType) {
				Code primitiveTypeCode= ((PrimitiveType) type).getPrimitiveTypeCode();
				if (primitiveTypeCode.equals(PrimitiveType.DOUBLE)) {
					invocation= replaceTypeConversion(ast, ConcurrencyRefactorings.AtomicInteger_doubleValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.BYTE)) {
					invocation= replaceTypeConversion(ast, ConcurrencyRefactorings.AtomicInteger_byteValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.FLOAT)) {
					invocation= replaceTypeConversion(ast, ConcurrencyRefactorings.AtomicInteger_floatValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.SHORT)) {
					invocation= replaceTypeConversion(ast, ConcurrencyRefactorings.AtomicInteger_shortValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.LONG)) {
					invocation= replaceTypeConversion(ast, ConcurrencyRefactorings.AtomicInteger_longValue);
				} else {
					return null;
				}
				return new ReplacementPair(expression, invocation);
			}
		}
		return null;
	}

	private MethodInvocation replaceTypeConversion(AST ast, String invocationName) {

		MethodInvocation methodInvocation= ast.newMethodInvocation();
		methodInvocation.setName(ast.newSimpleName(invocationName));
		methodInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));
		return methodInvocation;
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

	private boolean checkIfInSynchronizedBlockAndRemoveBlock(ASTNode node, Expression invocation, String accessType) {

		AST ast= node.getAST();
		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
		Statement syncStatement= (Statement) ASTNodes.getParent(node, SynchronizedStatement.class);

		if (syncStatement != null) {
			if (!visitedSynchronizedBlocks.contains(syncStatement)) {
				visitedSynchronizedBlocks.add(syncStatement);
				Block syncBody= ((SynchronizedStatement) syncStatement).getBody();
				List<?> syncBodyStatements= syncBody.statements();
				Statement firstStatement= (Statement) syncBodyStatements.get(0);
				if (syncBodyStatements.size() > 1) {
					insertStatementsInBlockAreNotSynchronizedComment(syncBody, firstStatement);
					fRewriter.replace(node, invocation, createGroupDescription(accessType));
					checkMoreThanOneFieldReference(node, syncBody);
				} else {
					if ((!sideEffectsFinder.hasSideEffects(firstStatement))
							&& (ASTMatcher.safeEquals(statement, firstStatement))
							&& ((!cannotRemoveSynchronizedBlockOrModifier.contains(firstStatement)))) {

						ExpressionStatement newExpressionStatement= ast.newExpressionStatement(invocation);
						fRewriter.replace(syncStatement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
					} else if (sideEffectsFinder.hasSideEffects(firstStatement)) {
						insertStatementsInBlockAreNotSynchronizedComment(syncBody, firstStatement);
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}

	private void insertStatementsInBlockAreNotSynchronizedComment(Block syncBody, Statement firstStatement) {

		if (!blocksWithComments.contains(syncBody)) {
			blocksWithComments.add(syncBody);
			insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_block,
					firstStatement, syncBody, Block.STATEMENTS_PROPERTY);
		}
	}

	private boolean checkIfInSynchronizedMethodAndRemoveModifier(ASTNode node, Expression invocation, String accessType) {

		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);

		if (methodDecl != null) {
			if (!visitedSynchronizedMethods.contains(methodDecl)) {
				visitedSynchronizedMethods.add(methodDecl);
				int modifiers= methodDecl.getModifiers();
				if (Modifier.isSynchronized(modifiers)) {
					List<Statement> methodBodyStatements= methodDecl.getBody().statements();
					Statement firstStatement= methodBodyStatements.get(0);
					if (methodBodyStatements.size() == 1) {
						if ((!sideEffectsFinder.hasSideEffects(firstStatement))
								&& (ASTMatcher.safeEquals(statement, firstStatement))
								&& (!cannotRemoveSynchronizedBlockOrModifier.contains(firstStatement))) {

							removeSynchronizedModifier(methodDecl, modifiers);
						} else if ((sideEffectsFinder.hasSideEffects(firstStatement))
								&& !(statementIsRefactorableIntoCompareAndSet(firstStatement))) {

							insertStatementsNotSynchronizedInMethodComment(node, methodDecl);
						}
					} else {
						insertStatementsNotSynchronizedInMethodComment(node, methodDecl);
						checkMoreThanOneFieldReference(node, methodDecl.getBody());
					}
					fRewriter.replace(node, invocation, createGroupDescription(accessType));
					return true;
				}
			}
		}
		return false;
	}

	private boolean statementIsRefactorableIntoCompareAndSet(Statement statement) {
		if (statement instanceof IfStatement) {
			if (ifStatementsToNodes.containsKey(statement)) {
				IfStatementProperties properties= ifStatementsToNodes.get(statement);
				return (properties.isRefactorable);
			}
		}
		return false;
	}

	//----- Helper Methods -----

	private boolean checkIfNodeIsInIfStatement(ASTNode node) {

		IfStatement ifStatement= (IfStatement) ASTNodes.getParent(node, IfStatement.class);

		if (ifStatement != null) {
			IfStatementProperties properties= null;
			if (!ifStatementsToNodes.containsKey(ifStatement)) {
				properties= new IfStatementProperties();
				if (ifStatement.getElseStatement() != null) {
					properties.isRefactorable= false;
				}
				ifStatementsToNodes.put(ifStatement, properties);
			} else {
				properties= ifStatementsToNodes.get(ifStatement);
			}
			if (!properties.nodes.contains(node)) {
				boolean nodeIsRefactorable= false;
				IfStatementProperties.Location nodeLocation;
				properties.nodes.add(node);
				nodeIsRefactorable= isRefactorableForCompareAndSet(node, ifStatement, nodeIsRefactorable);
				nodeLocation= findNodeLocationWithinIfStatement(node, ifStatement);
				properties.nodeIsRefactorableForCompareAndSet.add(new Boolean(nodeIsRefactorable));
				properties.nodeLocation.add(nodeLocation);
			}
			return true;
		}
		return false;
	}

	private IfStatementProperties.Location findNodeLocationWithinIfStatement(ASTNode node, IfStatement ifStatement) {

		Expression expression= ifStatement.getExpression();
		Statement thenStatement= ifStatement.getThenStatement();
		Statement elseStatement= ifStatement.getElseStatement();

		NodeFinder nodeFinder= new NodeFinder(node);
		expression.accept(nodeFinder);
		if (nodeFinder.containsNode) {
			return IfStatementProperties.Location.EXPRESSION;

		} else {
			thenStatement.accept(nodeFinder);
			if (nodeFinder.containsNode) {
				return IfStatementProperties.Location.THEN_STATEMENT;
			} else {
				elseStatement.accept(nodeFinder);
				if (nodeFinder.containsNode) {
					return IfStatementProperties.Location.ELSE_STATEMENT;
				} else {
					return null;
				}
			}
		}
	}

	private boolean isRefactorableForCompareAndSet(ASTNode node, IfStatement ifStatement, boolean nodeIsRefactorable) {

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
					syncStatement, methodBlock, Block.STATEMENTS_PROPERTY);
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
							methodDecl, typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
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
				fRewriter.remove(expression, createGroupDescription(WRITE_ACCESS));
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

		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(WRITE_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(WRITE_ACCESS));
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

		replaceOperand(rightOperand, leftOperand, receiver, ast);
		if (infixExpression.hasExtendedOperands()) {
			Expression operand= (Expression) infixExpression.extendedOperands().get(0);

			replaceOperand(operand, rightOperand, receiver, ast);
			changeFieldReferencesInExtendedOperandsToGetInvocations(infixExpression);
			if (operator != InfixExpression.Operator.MINUS) {
				fRewriter.remove(operand, createGroupDescription(WRITE_ACCESS));
				infixExpression.extendedOperands().remove(0);
			}
			insertAtomicOpTodoComment(node);
			refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver, node);
		} else {
			refactorInfixExpressionWithNoExtOperandsIntoAddAndGet(node, ast, invocation, receiver, rightOperand, operator);
		}
	}

	private void replaceOperand(Expression newOperand, Expression operand, Expression receiver, AST ast) {

		if (considerBinding(resolveBinding(newOperand))) {
			Expression expression= (Expression) ASTNode.copySubtree(ast, receiver);
			MethodInvocation methodInvocation= ast.newMethodInvocation();
			if (expression != null) {
				methodInvocation.setExpression(expression);
			}
			methodInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
			fRewriter.replace(operand, methodInvocation, createGroupDescription(READ_ACCESS));
		} else {
			newOperand.accept(new ChangeFieldToGetInvocationVisitor());
			Expression newOperandTarget= (Expression) fRewriter.createMoveTarget(newOperand);
			fRewriter.replace(operand, newOperandTarget, createGroupDescription(READ_ACCESS));
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
				fRewriter.remove((ASTNode) infixExpression.extendedOperands().get(0), createGroupDescription(WRITE_ACCESS));
				infixExpression.extendedOperands().remove(0);
			}
			insertAtomicOpTodoComment(node);
			refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver, node);
			return false;
		} else if (operator != InfixExpression.Operator.MINUS) {
			leftOperand.accept(new ChangeFieldToGetInvocationVisitor());
			refactorAssignmentIntoAddAndGetInvocation(invocation, leftOperand, operator, receiver, node);
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
				refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver, node);
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
			refactorAssignmentIntoAddAndGetInvocation(invocation, rightOperand, operator, receiver, node);
			if (!(rightOperand instanceof NumberLiteral) && !(rightOperand instanceof SimpleName)) {
				insertAtomicOpTodoComment(node);
			}
		}
	}

	private void insertAtomicOpTodoComment(ASTNode node) {

		boolean foundNodeInAnIfStatement= false;
		foundNodeInAnIfStatement= insertAtomicOpTodoCommentIfStatement(node, foundNodeInAnIfStatement);
		if (!foundNodeInAnIfStatement) {
			ForStatement forStatement= (ForStatement) ASTNodes.getParent(node, ForStatement.class);
			if (forStatement != null) {
				Statement body= forStatement.getBody();
				if (!(body instanceof Block)) {
					makeNewBlockInsertCommentCreateWarning(body);
					return;
				}
			}
			EnhancedForStatement enhancedForStatement= (EnhancedForStatement) ASTNodes.getParent(node, EnhancedForStatement.class);
			if (enhancedForStatement != null) {
				Statement body= enhancedForStatement.getBody();
				if (!(body instanceof Block)) {
					makeNewBlockInsertCommentCreateWarning(body);
					return;
				}
			}
			DoStatement doStatement= (DoStatement) ASTNodes.getParent(node, DoStatement.class);
			if (doStatement != null) {
				Statement body= doStatement.getBody();
				if (!(body instanceof Block)) {
					makeNewBlockInsertCommentCreateWarning(body);
					return;
				}
			}
			WhileStatement whileStatement= (WhileStatement) ASTNodes.getParent(node, WhileStatement.class);
			if (whileStatement != null) {
				Statement body= whileStatement.getBody();
				if (!(body instanceof Block)) {
					makeNewBlockInsertCommentCreateWarning(body);
					return;
				}
			}
			ASTNode body= ASTNodes.getParent(node, Block.class);
			ASTNode statement= ASTNodes.getParent(node, Statement.class);
			if ((statement != null) && (body != null)) {
				insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
						statement, body, Block.STATEMENTS_PROPERTY);
			}
			createWarningStatus(node.toString() + ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
		}
	}

	private boolean insertAtomicOpTodoCommentIfStatement(ASTNode node, boolean foundNodeInAnIfStatement) {

		for (Map.Entry<IfStatement, IfStatementProperties> entry : ifStatementsToNodes.entrySet()) {
			IfStatement ifStatement= entry.getKey();
			IfStatementProperties properties= entry.getValue();
			if (properties.nodes.contains(node)) {
				foundNodeInAnIfStatement= true;
				IfStatementProperties.Location location= properties.nodeLocation.get(properties.nodes.indexOf(node));
				switch (location) {
					case EXPRESSION:
						break;
					case THEN_STATEMENT:
						Statement thenStatement= ifStatement.getThenStatement();
						insertAtomicOpTodoCommentIfStatement(node, thenStatement);
						break;
					case ELSE_STATEMENT:
						Statement elseStatement= ifStatement.getElseStatement();
						insertAtomicOpTodoCommentIfStatement(node, elseStatement);
						break;
					default:
						break;
				}
			}
		}
		return foundNodeInAnIfStatement;
	}

	private void makeNewBlockInsertCommentCreateWarning(Statement body) {

		AST ast= body.getAST();
		Block newBlock= ast.newBlock();
		ASTNode createMoveTarget= fRewriter.createMoveTarget(body);
		fRewriter.replace(body, newBlock, createGroupDescription(WRITE_ACCESS));
		insertLineCommentBeforeMoveTarget(newBlock, createMoveTarget);
		createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
	}

	private void insertAtomicOpTodoCommentIfStatement(ASTNode node, Statement statement) {

		if (statement != null) {
			NodeFinder nodeFinder= new NodeFinder(node);
			statement.accept(nodeFinder);
			if (nodeFinder.containsNode) {
				if (statement instanceof Block) {
					Statement parentStatement= (Statement) ASTNodes.getParent(node, Statement.class);
					if (parentStatement != null) {
						insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
								parentStatement, statement, Block.STATEMENTS_PROPERTY);
					}
				} else {
					makeNewBlockInsertCommentCreateWarning(statement);
				}
			}
		}
	}

	private void insertLineCommentBeforeMoveTarget(Block newBlock, ASTNode createMoveTarget) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically_nl,
				ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(newBlock, Block.STATEMENTS_PROPERTY);
		rewriter.insertLast(createMoveTarget, createGroupDescription(WRITE_ACCESS));
		rewriter.insertFirst(lineComment, createGroupDescription(COMMENT));
	}

	private ListRewrite insertLineCommentBeforeNode(String comment, ASTNode node, ASTNode body, ChildListPropertyDescriptor descriptor) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(comment, ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(body, descriptor);
		rewriter.insertBefore(lineComment, node, createGroupDescription(COMMENT));
		return rewriter;
	}

	private void insertStatementsNotSynchronizedInMethodComment(ASTNode node, MethodDeclaration methodDecl) {

		if (!methodsWithComments.contains(methodDecl)) {
			TypeDeclaration typeDeclaration= (TypeDeclaration) ASTNodes.getParent(node, TypeDeclaration.class);

			if (typeDeclaration != null) {
				methodsWithComments.add(methodDecl);
				insertLineCommentBeforeNode(
						ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_method,
						methodDecl, typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			}
		}
	}

	private boolean needsParentheses(Expression expression) {

		int type= expression.getNodeType();

		return (type == ASTNode.INFIX_EXPRESSION) || (type == ASTNode.CONDITIONAL_EXPRESSION) ||
				(type == ASTNode.PREFIX_EXPRESSION) || (type == ASTNode.POSTFIX_EXPRESSION) ||
				(type == ASTNode.CAST_EXPRESSION) || (type == ASTNode.INSTANCEOF_EXPRESSION);
	}

	private void refactorIfStatementIntoCompareAndSetInvocation(IfStatement ifStatement, ArrayList<ASTNode> nodes) {

		AST ast= ifStatement.getAST();
		MethodInvocation compareAndSetInvocation= ast.newMethodInvocation();

		// There can only be 2 qualifying nodes within an IfStatement for it to be refactorable
		if (nodes.size() != 2) {
			insertStatementsNotSynchronizedInMethodComment(ifStatement);
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

				MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(ifStatement, MethodDeclaration.class);
				SynchronizedStatement syncStatement= (SynchronizedStatement) ASTNodes.getParent(ifStatement, SynchronizedStatement.class);

				if (syncStatement != null) {
					Block body= syncStatement.getBody();
					if (body.statements().size() == 1) {
						fRewriter.replace(syncStatement, compareAndSetStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
					}
				} else if (methodDecl != null) {
					int modifiers= methodDecl.getModifiers();
					if (Modifier.isSynchronized(modifiers)) {
						List<Statement> methodBodyStatements= methodDecl.getBody().statements();
						if (methodBodyStatements.size() == 1) {
							removeSynchronizedModifier(methodDecl, modifiers);
						}
					}
					fRewriter.replace(ifStatement, compareAndSetStatement, createGroupDescription(REPLACE_IF_STATEMENT_WITH_COMPARE_AND_SET));
				} else {
					fRewriter.replace(ifStatement, compareAndSetStatement, createGroupDescription(REPLACE_IF_STATEMENT_WITH_COMPARE_AND_SET));
				}
			}
		}
	}

	private void insertStatementsNotSynchronizedInMethodComment(IfStatement ifStatement) {

		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(ifStatement, MethodDeclaration.class);
		if (methodDecl != null) {
			int modifiers= methodDecl.getModifiers();

			if (Modifier.isSynchronized(modifiers)) {
				List<Statement> methodBodyStatements= methodDecl.getBody().statements();
				if (methodBodyStatements.size() == 1) {
					insertStatementsNotSynchronizedInMethodComment(ifStatement, methodDecl);
				}
			}
		}
	}

	private void refactorAssignmentIntoAddAndGetInvocation(MethodInvocation invocation, Expression operand,
			Object operator, Expression receiver, ASTNode node) {

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
		preserveIfStatementOverCompareAndSet(node);
	}

	private void preserveIfStatementOverCompareAndSet(ASTNode node) {

		for (Map.Entry<IfStatement, IfStatementProperties> entry : ifStatementsToNodes.entrySet()) {
			IfStatementProperties properties= entry.getValue();
			if (properties.nodes.contains(node)) {
				properties.isRefactorable= false;
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
				returnStatement, body, Block.STATEMENTS_PROPERTY);

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

		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(WRITE_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(WRITE_ACCESS));
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

	private static class IfStatementProperties {

		private boolean isRefactorable= true;
		private ArrayList<Boolean> nodeIsRefactorableForCompareAndSet;
		private ArrayList<ASTNode> nodes;
		private ArrayList<Location> nodeLocation;

		public IfStatementProperties() {
			isRefactorable= true;
			nodes= new ArrayList<ASTNode>();
			nodeIsRefactorableForCompareAndSet= new ArrayList<Boolean>();
			nodeLocation= new ArrayList<AccessAnalyzerForAtomicInteger.IfStatementProperties.Location>();
		}

		public enum Location {
			EXPRESSION, THEN_STATEMENT, ELSE_STATEMENT
		}
	}

	private class ReplacementPair {
		private ASTNode whatToReplace;
		private ASTNode replacement;

		public ReplacementPair(ASTNode whatToReplace, ASTNode replacement) {
			this.whatToReplace= whatToReplace;
			this.replacement= replacement;
		}
	}

	private class NodeFinder extends ASTVisitor {

		private ASTNode nodeToFind;
		private boolean containsNode;

		public NodeFinder(ASTNode node) {
			this.nodeToFind= node;
			containsNode= false;
		}

		@Override
		public boolean visit(Assignment assignment) {
			if (assignment == nodeToFind) {
				containsNode= true;
			}
			return true;
		}

		@Override
		public boolean visit(InfixExpression infixExpression) {
			if (infixExpression == nodeToFind) {
				containsNode= true;
			}
			return true;
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
			return false;
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
			return false;
		}

		@Override
		public boolean visit(Assignment assignment) {

			Expression leftHandSide= assignment.getLeftHandSide();
			if (considerBinding(resolveBinding(leftHandSide))) {
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
			return false;
		}
	}
}