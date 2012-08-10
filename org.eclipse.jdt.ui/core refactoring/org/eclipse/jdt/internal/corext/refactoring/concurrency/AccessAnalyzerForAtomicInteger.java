package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.ArrayList;
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

	private IVariableBinding fFieldBinding;
	private ASTRewrite fRewriter;
	private ImportRewrite fImportRewriter;
	private List<TextEditGroup> fGroupDescriptions;
	private boolean fIsFieldFinal;
	private RefactoringStatus fStatus;

	private HashMap<IfStatement, IfStatementProperties> fIfStatementsToNodes;
	private ArrayList<MethodDeclaration> fMethodsWithComments;
	private ArrayList<Block> fBlocksWithComments;
	private ArrayList<Statement> fVisitedSynchronizedBlocks;
	private ArrayList<MethodDeclaration> fVisitedSynchronizedMethods;
	private ArrayList<Statement> fCanRemoveSynchronizedBlockOrModifier;

	// TODO fix warnings
	// TODO organize and simplify tests

	public AccessAnalyzerForAtomicInteger(
			ConvertToAtomicIntegerRefactoring refactoring,
			IVariableBinding field,
			ASTRewrite rewriter,
			ImportRewrite importRewrite) {

		fFieldBinding= field.getVariableDeclaration();
		fRewriter= rewriter;
		fImportRewriter= importRewrite;
		fGroupDescriptions= new ArrayList<TextEditGroup>();
		fMethodsWithComments= new ArrayList<MethodDeclaration>();
		fBlocksWithComments= new ArrayList<Block>();
		fVisitedSynchronizedBlocks= new ArrayList<Statement>();
		fVisitedSynchronizedMethods= new ArrayList<MethodDeclaration>();
		fCanRemoveSynchronizedBlockOrModifier= new ArrayList<Statement>();
		try {
			fIsFieldFinal= Flags.isFinal(refactoring.getField().getFlags());
		} catch (JavaModelException e) {
			// assume non final field
		}
		fStatus= new RefactoringStatus();
		fIfStatementsToNodes= new HashMap<IfStatement, AccessAnalyzerForAtomicInteger.IfStatementProperties>();
	}

	@Override
	public boolean visit(Assignment assignment) {

		boolean assignmentIsRefactored= false;
		boolean inReturnStatement= false;
		Expression lhs= assignment.getLeftHandSide();
		checkIfNodeIsInIfStatement(assignment);

		if (!considerBinding(resolveBinding(lhs))) {
			return true;
		}
		if (!fIsFieldFinal) {
			Statement statement= (Statement) ASTNodes.getParent(assignment, Statement.class);
			AST ast= assignment.getAST();
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_set));
			Expression receiver= getReceiver(lhs);

			if (receiver != null) {
				invocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
			}
			if ((!checkIfParentIsExpressionStatement(assignment)) && (statement instanceof ReturnStatement)) {
				inReturnStatement= true;
			}
			List<Expression> arguments= invocation.arguments();
			if (assignment.getOperator() == Assignment.Operator.ASSIGN) {
				Expression rightHandSide= assignment.getRightHandSide();
				if (rightHandSide instanceof InfixExpression) {
					assignmentIsRefactored= infixExpressionAssignmentHandler(assignment, ast, invocation, rightHandSide, receiver);
				}
				if (!assignmentIsRefactored) {
					if (isNumLiteralOrIsNotField(rightHandSide)) {
						addToCanRemoveSynchBlockOrModifierList(assignment);
					}
					rightHandSide.accept(new ChangeFieldToGetInvocationVisitor());
					// TODO find a better way to have postfix and prefix expressions commented
					rightHandSide.accept(new PostfixAndPrefixExpressionCommenter());
					arguments.add((Expression) fRewriter.createMoveTarget(rightHandSide));
				}
			}
			if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
				compoundAssignmentHandler(assignment, ast, invocation, arguments, assignment.getRightHandSide(), receiver);
			}
			// TODO find some way to handle return statements through the side effect finder or lists?
			if ((!inReturnStatement)
					&& (!removedSynchBlock(assignment, invocation, WRITE_ACCESS))
					&& (!removedSynchModifier(assignment, invocation, WRITE_ACCESS))) {

				fRewriter.replace(assignment, invocation, createGroupDescription(WRITE_ACCESS));
			} else if (inReturnStatement) {
				refactorReturnAtomicIntegerAssignment(assignment, statement, invocation, receiver);
			}
		}
		return false;
	}

	private boolean infixExpressionAssignmentHandler(Assignment node, AST ast, MethodInvocation invocation,
			Expression rightHandSide, Expression receiver) {

		boolean infixIsRefactored= false;
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
					return true;
				} else if (rightOperandIsField) {
					infixIsRefactored= rightOperandInfixExpressionHandler(rightOperand, leftOperand, receiver, ast,
							invocation, infixExpression, node, operator);
					return infixIsRefactored;
				}
			} else if (infixExpression.hasExtendedOperands()) {
				infixIsRefactored= extendedOperandsOfInfixExpressionHandler(rightOperand, leftOperand, receiver, ast, invocation, infixExpression, node, operator);
				return infixIsRefactored;
			} else {
				insertAtomicOpTodoComment(node);
				replaceOldOperandsWithNewOperands(ast, leftOperand, rightOperand, receiver);
			}
		} else {
			createUnsafeOperatorWarning(node);
			insertAtomicOpTodoComment(node);
			replaceOldOperandsWithNewOperands(ast, leftOperand, rightOperand, receiver);
			if (infixExpression.hasExtendedOperands()) {
				convertFieldRefsInExtOperandsToGetInvocations(infixExpression);
			}
		}
		return infixIsRefactored;
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
			if (!isNumLiteralOrIsNotField(rightHandSide)) {
				insertAtomicOpTodoComment(node);
			} else {
				addToCanRemoveSynchBlockOrModifierList(node);
			}
		} else {
			createUnsafeOperatorWarning(node);
			insertAtomicOpTodoComment(node);
			InfixExpression newInfixExpression= createNewSetAndGetInvocationWithAnInfix(ast, rightHandSide, receiver, operator);
			arguments.add(newInfixExpression);
		}
		return false;
	}

	private InfixExpression createNewSetAndGetInvocationWithAnInfix(AST ast, Expression rightHandSide, Expression receiver, Assignment.Operator operator) {

		InfixExpression.Operator newOperator= ASTNodes.convertToInfixOperator(operator);
		MethodInvocation invocationGet= ast.newMethodInvocation();
		InfixExpression newInfixExpression= ast.newInfixExpression();

		invocationGet.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		if (receiver != null) {
			invocationGet.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
		}
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
		return newInfixExpression;
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
			addToCanRemoveSynchBlockOrModifierList(postfixExpression);
		} else if (operator == PostfixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_getAndDecrement));
			addToCanRemoveSynchBlockOrModifierList(postfixExpression);
		}
		if (!(removedSynchBlock(postfixExpression, invocation, POSTFIX_ACCESS) || removedSynchModifier(postfixExpression, invocation, POSTFIX_ACCESS))) {
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
			addToCanRemoveSynchBlockOrModifierList(prefixExpression);
		} else if (operator == PrefixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_decrementAndGet));
			addToCanRemoveSynchBlockOrModifierList(prefixExpression);
		}
		if (!(removedSynchBlock(prefixExpression, invocation, PREFIX_ACCESS) || removedSynchModifier(prefixExpression, invocation, PREFIX_ACCESS))) {
			fRewriter.replace(prefixExpression, invocation, createGroupDescription(PREFIX_ACCESS));
		}
		return false;
	}

	@Override
	public boolean visit(SimpleName simpleName) {

		// TODO possibly get this visitor to handle the right hand side of an assignment as well

		AST ast= simpleName.getAST();
		ReplacementPair pair= null;
		String accessType= REPLACE_TYPE_CONVERSION;
		MethodInvocation invocation= ast.newMethodInvocation();
		pair= checkForTypeConversions(simpleName, invocation, ast);

		if ((!simpleName.isDeclaration()) && (considerBinding(resolveBinding(simpleName)))) {
			if (pair == null) {
				accessType= READ_ACCESS;
				invocation= newGetInvocation(ast, (Expression) ASTNode.copySubtree(ast, simpleName));
				addToCanRemoveSynchBlockOrModifierList(simpleName);
				if (!(removedSynchBlock(simpleName, invocation, accessType)
				|| removedSynchModifier(simpleName, invocation, accessType))) {
					fRewriter.replace(simpleName, invocation, createGroupDescription(accessType));
				}
			} else {
				if (!(removedSynchBlock(pair.whatToReplace, (Expression) pair.replacement, accessType)
				|| removedSynchModifier(pair.whatToReplace, (Expression) pair.replacement, accessType))) {
					fRewriter.replace(pair.whatToReplace, pair.replacement, createGroupDescription(accessType));
				}
			}
		}
		return true;
	}

	private ReplacementPair checkForTypeConversions(SimpleName simpleName, MethodInvocation invocation, AST ast) {

		ReplacementPair replacementPair= null;

		replacementPair= checkForIntToDoubleConversion(simpleName, invocation, ast);
		if (replacementPair != null) {
			return replacementPair;
		}
		replacementPair= checkForPrimitiveTypeCastConversions(simpleName, invocation, ast);
		return replacementPair;
	}

	private ReplacementPair checkForIntToDoubleConversion(SimpleName simpleName, MethodInvocation invocation, AST ast) {

		MethodInvocation methodInvocationParent= (MethodInvocation) ASTNodes.getParent(simpleName, MethodInvocation.class);

		if ((methodInvocationParent != null) && (methodInvocationParent.getName().toString().equals(ConcurrencyRefactorings.ToString))) {
			if (methodInvocationParent.getExpression().toString().equals(ConcurrencyRefactorings.Integer)) {
				MethodInvocation parent= (MethodInvocation) ASTNodes.getParent(methodInvocationParent, MethodInvocation.class);
				if ((parent != null)) {
					if ((parent.getExpression().toString().equals(ConcurrencyRefactorings.Double))
							&& (parent.getName().toString().equals(ConcurrencyRefactorings.ParseDouble))) {

						addToCanRemoveSynchBlockOrModifierList(simpleName);
						invocation= getTypeConversionInvocation(ast, ConcurrencyRefactorings.AtomicInteger_doubleValue);
						return new ReplacementPair(parent, invocation);
					}
				}
			}
		}
		return null;
	}

	private ReplacementPair checkForPrimitiveTypeCastConversions(SimpleName simpleName, MethodInvocation invocation, AST ast) {

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
					invocation= getTypeConversionInvocation(ast, ConcurrencyRefactorings.AtomicInteger_doubleValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.BYTE)) {
					invocation= getTypeConversionInvocation(ast, ConcurrencyRefactorings.AtomicInteger_byteValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.FLOAT)) {
					invocation= getTypeConversionInvocation(ast, ConcurrencyRefactorings.AtomicInteger_floatValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.SHORT)) {
					invocation= getTypeConversionInvocation(ast, ConcurrencyRefactorings.AtomicInteger_shortValue);
				} else if (primitiveTypeCode.equals(PrimitiveType.LONG)) {
					invocation= getTypeConversionInvocation(ast, ConcurrencyRefactorings.AtomicInteger_longValue);
				} else {
					return null;
				}
				ASTNode assignment= ASTNodes.getParent(simpleName, Assignment.class);
				ASTNode infix= ASTNodes.getParent(simpleName, InfixExpression.class);
				ASTNode returnStatement= ASTNodes.getParent(simpleName, ReturnStatement.class);
				if ((assignment == null) && (infix == null) && (returnStatement != null)) {
					addToCanRemoveSynchBlockOrModifierList(simpleName);
				}
				return new ReplacementPair(expression, invocation);
			}
		}
		return null;
	}

	@Override
	public boolean visit(InfixExpression infixExpression) {

		checkIfNodeIsInIfStatement(infixExpression);
		return true;
	}

	@Override
	public void endVisit(CompilationUnit node) {

		for (Map.Entry<IfStatement, IfStatementProperties> entry : fIfStatementsToNodes.entrySet()) {
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

	private boolean checkIfAssignmentNodeIsRefactorableIntoCompareAndSet(ASTNode node, IfStatement ifStatement, boolean nodeIsRefactorable) {

		Statement thenStatement= ifStatement.getThenStatement();
		ASTNode parentStatement= ASTNodes.getParent(node, Statement.class);

		if (!(thenStatement instanceof Block) || ((thenStatement instanceof Block) && (((Block) thenStatement).statements().size() == 1))) {
			if (thenStatement instanceof Block) {
				List<Statement> statements= ((Block) thenStatement).statements();
				for (Iterator<Statement> iterator= statements.iterator(); iterator.hasNext();) {
					Statement statement= iterator.next();
					if (ASTMatcher.safeEquals(parentStatement, statement)) {
						Expression leftHandSide= ((Assignment) node).getLeftHandSide();
						if (considerBinding(resolveBinding(leftHandSide))) {
							nodeIsRefactorable= true;
						} else {
							nodeIsRefactorable= false;
						}
					}
				}
			} else if (ASTMatcher.safeEquals(parentStatement, thenStatement)) {
				Expression leftHandSide= ((Assignment) node).getLeftHandSide();
				if (considerBinding(resolveBinding(leftHandSide))) {
					nodeIsRefactorable= true;
				} else {
					nodeIsRefactorable= false;
				}
			}
		}
		return nodeIsRefactorable;
	}

	private boolean checkIfInfixExpressionNodeIsRefactorableIntoCompareAndSet(ASTNode node, IfStatement ifStatement, boolean nodeIsRefactorable) {

		Assignment assignmentParent= (Assignment) ASTNodes.getParent(node, Assignment.class);
		if (assignmentParent == null) {

			Expression expression= ifStatement.getExpression();
			if (ASTMatcher.safeEquals(expression, node)) {

				if (expression instanceof InfixExpression) {
					Operator operator= ((InfixExpression) expression).getOperator();
					Expression leftOperand= ((InfixExpression) expression).getLeftOperand();
					Expression rightOperand= ((InfixExpression) expression).getRightOperand();
					boolean leftOperandIsField= considerBinding(resolveBinding(leftOperand));
					boolean rightOperandIsField= considerBinding(resolveBinding(rightOperand));

					if ((operator == InfixExpression.Operator.EQUALS) && (leftOperandIsField != rightOperandIsField)) {
						nodeIsRefactorable= true;
					} else {
						nodeIsRefactorable= false;
					}
				}
			}
		}
		return nodeIsRefactorable;
	}

	private boolean checkIfNodeIsInIfStatement(ASTNode node) {

		IfStatement ifStatement= (IfStatement) ASTNodes.getParent(node, IfStatement.class);

		if (ifStatement != null) {
			IfStatementProperties properties= null;
			if (!fIfStatementsToNodes.containsKey(ifStatement)) {
				properties= new IfStatementProperties();
				if (ifStatement.getElseStatement() != null) {
					properties.isRefactorable= false;
				}
				fIfStatementsToNodes.put(ifStatement, properties);
			} else {
				properties= fIfStatementsToNodes.get(ifStatement);
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

	private void checkMoreThanOneFieldReference(ASTNode node, Block syncBody) {

		ASTNode enclosingStatement= ASTNodes.getParent(node, Statement.class);
		List<Statement> statements= syncBody.statements();
		for (Iterator<?> iterator= statements.iterator(); iterator.hasNext();) {
			Statement statement= (Statement) iterator.next();
			if (!statement.equals(enclosingStatement)){
				statement.accept(new FieldReferenceFinderAtomicInteger(fStatus));
			} else {
				if (!canRemoveSynchBlockOrModifier(statement)) {
					createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_side_effects1
							+ ConcurrencyRefactorings.AtomicInteger_warning_side_effects2
							+ ConcurrencyRefactorings.AtomicInteger_warning_side_effects3
							+ statement.toString()
							+ ConcurrencyRefactorings.AtomicInteger_warning_side_effects4);
				}
			}
		}
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

	private void convertFieldRefsInExtOperandsToGetInvocations(InfixExpression infixExpression) {

		if (infixExpression.hasExtendedOperands()) {
			List<Expression> extendedOperands= infixExpression.extendedOperands();
			for (int i= 0; i < extendedOperands.size(); i++) {
				Expression expression= extendedOperands.get(i);
				expression.accept(new ChangeFieldToGetInvocationVisitor());
			}
		}
	}

	private void leftOperandOfInfixExpressionHandler(Expression rightOperand, Expression leftOperand,
			Expression receiver, AST ast, MethodInvocation invocation, InfixExpression infixExpression, Assignment node,
			Operator operator) {

		replaceOperandWithNewOperand(leftOperand, rightOperand, receiver, ast);
		if (infixExpression.hasExtendedOperands()) {
			Expression operand= (Expression) infixExpression.extendedOperands().get(0);

			replaceOperandWithNewOperand(rightOperand, operand, receiver, ast);
			convertFieldRefsInExtOperandsToGetInvocations(infixExpression);
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

	private void refactorInfixExpressionWithNoExtOperandsIntoAddAndGet(Assignment node, AST ast, MethodInvocation invocation,
			Expression receiver, Expression rightOperand, Operator operator) {

		if (considerBinding(resolveBinding(rightOperand))) {
			MethodInvocation newGetInvocation= newGetInvocation(ast, (Expression) ASTNode.copySubtree(ast, receiver));
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			invocation.arguments().add(newGetInvocation);
			insertAtomicOpTodoComment(node);
		} else {
			rightOperand.accept(new ChangeFieldToGetInvocationVisitor());
			refactorAssignmentIntoAddAndGetInvocation(invocation, rightOperand, operator, receiver, node);
			if (!isNumLiteralOrIsNotField(rightOperand)) {
				insertAtomicOpTodoComment(node);
			} else {
				addToCanRemoveSynchBlockOrModifierList(node);
			}
		}
	}

	private boolean rightOperandInfixExpressionHandler(Expression rightOperand, Expression leftOperand,
			Expression receiver, AST ast, MethodInvocation invocation, InfixExpression infixExpression, Assignment node,
			Operator operator) {

		leftOperand.accept(new ChangeFieldToGetInvocationVisitor());

		if (infixExpression.hasExtendedOperands() && operator != InfixExpression.Operator.MINUS) {
			Expression operand= (Expression) infixExpression.extendedOperands().get(0);
			replaceOperandWithNewOperand(rightOperand, operand, receiver, ast);
			convertFieldRefsInExtOperandsToGetInvocations(infixExpression);
			fRewriter.remove(operand, createGroupDescription(WRITE_ACCESS));
			infixExpression.extendedOperands().remove(0);
			insertAtomicOpTodoComment(node);
			refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver, node);
			return true;
		} else if (operator != InfixExpression.Operator.MINUS) {
			refactorAssignmentIntoAddAndGetInvocation(invocation, leftOperand, operator, receiver, node);
			if (!isNumLiteralOrIsNotField(leftOperand)) {
				insertAtomicOpTodoComment(node);
			} else {
				addToCanRemoveSynchBlockOrModifierList(node);
			}
			return true;
		} else {
			insertAtomicOpTodoComment(node);
			replaceOldOperandsWithNewOperands(ast, leftOperand, rightOperand, receiver);
			return false;
		}
	}

	private boolean extendedOperandsOfInfixExpressionHandler(Expression rightOperand, Expression leftOperand, Expression receiver,
			AST ast, MethodInvocation invocation, InfixExpression infixExpression, Assignment node, Operator operator) {

		replaceOldOperandsWithNewOperands(ast, leftOperand, rightOperand, receiver);
		insertAtomicOpTodoComment(node);

		if (operator != InfixExpression.Operator.MINUS) {
			if (foundFieldInExtendedOperands(infixExpression)) {
				convertFieldRefsInExtOperandsToGetInvocations(infixExpression);
				refactorAssignmentIntoAddAndGetInvocation(invocation, infixExpression, operator, receiver, node);
				return true;
			} else {
				convertFieldRefsInExtOperandsToGetInvocations(infixExpression);
			}
		} else {
			convertFieldRefsInExtOperandsToGetInvocations(infixExpression);
		}
		return false;
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
				PrefixExpression newPrefixExpression= factorOutMinusOpFromInfixIntoPrefixExpression(operand, receiver, ast);
				invocation.arguments().add(newPrefixExpression);
			} else {
				invocation.arguments().add(createNegativeExpression(operand));
			}
		}
		preserveIfStatementOverCompareAndSet(node);
	}

	private PrefixExpression factorOutMinusOpFromInfixIntoPrefixExpression(Expression operand, Expression receiver, AST ast) {

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
		return newPrefixExpression;
	}

	private void preserveIfStatementOverCompareAndSet(ASTNode node) {

		for (Map.Entry<IfStatement, IfStatementProperties> entry : fIfStatementsToNodes.entrySet()) {
			IfStatementProperties properties= entry.getValue();
			if (properties.nodes.contains(node)) {
				properties.isRefactorable= false;
			}
		}
	}

	private void refactorIfStatementIntoCompareAndSetInvocation(IfStatement ifStatement, ArrayList<ASTNode> nodes) {

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

				GetArgumentsCompareAndSet getArguments= new GetArgumentsCompareAndSet();
				ExpressionStatement compareAndSetStatement= getCompareAndSetInvocationStatement(ifStatement, nodes, getArguments);

				MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(ifStatement, MethodDeclaration.class);
				SynchronizedStatement syncStatement= (SynchronizedStatement) ASTNodes.getParent(ifStatement, SynchronizedStatement.class);

				boolean removedSynchBlock= removedSynchBlockOrModifier(ifStatement, getArguments, compareAndSetStatement, methodDecl, syncStatement);
				if (!removedSynchBlock) {
					fRewriter.replace(ifStatement, compareAndSetStatement, createGroupDescription(REPLACE_IF_STATEMENT_WITH_COMPARE_AND_SET));
				}
			}
		}
	}

	private boolean removedSynchBlockOrModifier(IfStatement ifStatement, GetArgumentsCompareAndSet getArguments, ExpressionStatement compareAndSetStatement, MethodDeclaration methodDecl,
			SynchronizedStatement syncStatement) {

		boolean removedSynchBlock= false;
		if ((syncStatement != null)) {
			Block body= syncStatement.getBody();
			if (getArguments.argsAreNumLiteralsOrAreNotFields()) {
				if (body.statements().size() == 1) {
					fRewriter.replace(syncStatement, compareAndSetStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
					removedSynchBlock= true;
				}
			} else {
				insertStatementsInBlockAreNotSynchronizedComment(body, (Statement) body.statements().get(0));
			}
		} else if (methodDecl != null) {
			int modifiers= methodDecl.getModifiers();
			if (Modifier.isSynchronized(modifiers)) {
				List<Statement> methodBodyStatements= methodDecl.getBody().statements();
				if (methodBodyStatements.size() == 1) {
					if (getArguments.argsAreNumLiteralsOrAreNotFields()) {
						removeSynchronizedModifier(methodDecl, modifiers);
					} else {
						insertStatementsNotSynchronizedInMethodComment(ifStatement, methodDecl);
					}
				}
			}
		}
		return removedSynchBlock;
	}

	private ExpressionStatement getCompareAndSetInvocationStatement(IfStatement ifStatement, ArrayList<ASTNode> nodes, GetArgumentsCompareAndSet getArguments) {

		AST ast= ifStatement.getAST();
		ASTNode firstNode= nodes.get(0);
		ASTNode secondNode= nodes.get(1);

		MethodInvocation compareAndSetInvocation= ast.newMethodInvocation();
		compareAndSetInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_compareAndSet));
		compareAndSetInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));

		firstNode.accept(getArguments);
		secondNode.accept(getArguments);
		Expression setExpression= getArguments.getSetExpression();
		Expression compareExpression= getArguments.getCompareExpression();

		if ((compareExpression != null) && (setExpression != null)) {
			compareAndSetInvocation.arguments().add(fRewriter.createMoveTarget(compareExpression));
			compareAndSetInvocation.arguments().add(fRewriter.createMoveTarget(setExpression));
		}
		ExpressionStatement compareAndSetStatement= ast.newExpressionStatement(compareAndSetInvocation);

		return compareAndSetStatement;
	}

	private void addToCanRemoveSynchBlockOrModifierList(ASTNode node) {

		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
		ASTNode assignment= ASTNodes.getParent(node, Assignment.class);
		ASTNode infix= ASTNodes.getParent(node, InfixExpression.class);
		if ((statement != null)
				&& ((statement instanceof ExpressionStatement) || (statement instanceof ReturnStatement))
				&& (assignment == null) && (infix == null)) {
			fCanRemoveSynchronizedBlockOrModifier.add(statement);
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

	private boolean removedSynchBlock(ASTNode node, Expression invocation, String accessType) {

		AST ast= node.getAST();
		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
		Statement syncStatement= (Statement) ASTNodes.getParent(node, SynchronizedStatement.class);

		if (syncStatement != null) {
			if (!fVisitedSynchronizedBlocks.contains(syncStatement)) {
				fVisitedSynchronizedBlocks.add(syncStatement);

				Block syncBody= ((SynchronizedStatement) syncStatement).getBody();
				List<?> syncBodyStatements= syncBody.statements();
				Statement firstStatement= (Statement) syncBodyStatements.get(0);

				if (syncBodyStatements.size() > 1) {
					insertStatementsInBlockAreNotSynchronizedComment(syncBody, firstStatement);
					checkMoreThanOneFieldReference(node, syncBody);
					return false;
				} else {
					if ((ASTMatcher.safeEquals(statement, firstStatement))
							&& (canRemoveSynchBlockOrModifier(firstStatement))) {

						removeSynchronizedBlock(node, invocation, accessType, ast, statement, syncStatement);
						return true;
					} else if (!canRemoveSynchBlockOrModifier(firstStatement)) {
						insertStatementsInBlockAreNotSynchronizedComment(syncBody, firstStatement);
						checkMoreThanOneFieldReference(node, syncBody);
						return false;
					}
				}
			}
		}
		return false;
	}

	private boolean removedSynchModifier(ASTNode node, Expression invocation, String accessType) {

		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);

		if (methodDecl != null) {
			if (!fVisitedSynchronizedMethods.contains(methodDecl)) {
				fVisitedSynchronizedMethods.add(methodDecl);

				int modifiers= methodDecl.getModifiers();
				if (Modifier.isSynchronized(modifiers)) {

					List<Statement> methodBodyStatements= methodDecl.getBody().statements();
					Statement firstStatement= methodBodyStatements.get(0);

					if (methodBodyStatements.size() == 1) {
						if ((ASTMatcher.safeEquals(statement, firstStatement))
								&& (canRemoveSynchBlockOrModifier(firstStatement))) {

							removeSynchronizedModifier(methodDecl, modifiers);
							fRewriter.replace(node, invocation, createGroupDescription(accessType));
							return true;
						} else if (!canRemoveSynchBlockOrModifier(firstStatement)
								&& !(statementIsRefactorableIntoCompareAndSet(firstStatement))) {

							insertStatementsNotSynchronizedInMethodComment(node, methodDecl);
							checkMoreThanOneFieldReference(node, methodDecl.getBody());
							return false;
						}
					} else {
						insertStatementsNotSynchronizedInMethodComment(node, methodDecl);
						checkMoreThanOneFieldReference(node, methodDecl.getBody());
						return false;
					}
				}
			}
		}
		return false;
	}

	private void replaceOperandWithNewOperand(Expression operand, Expression newOperand, Expression receiver, AST ast) {

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

	public static class IfStatementProperties {

		boolean isRefactorable= true;
		ArrayList<Boolean> nodeIsRefactorableForCompareAndSet;
		ArrayList<ASTNode> nodes;
		ArrayList<Location> nodeLocation;

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

	public static class NodeFinder extends ASTVisitor {

		ASTNode nodeToFind;
		boolean containsNode;

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

	private class ChangeFieldToGetInvocationVisitor extends ASTVisitor {

		@Override
		public boolean visit(SimpleName simpleName) {

			if ((considerBinding(resolveBinding(simpleName))) && (!simpleName.isDeclaration())) {
				AST ast= simpleName.getAST();
				MethodInvocation methodInvocation= newGetInvocation(ast, (Expression) ASTNode.copySubtree(ast, simpleName));
				fRewriter.replace(simpleName, methodInvocation, createGroupDescription(READ_ACCESS));
			}
			return true;
		}
	}

	private class GetArgumentsCompareAndSet extends ASTVisitor {

		private Expression setExpression= null;
		private Expression compareExpression= null;

		public boolean argsAreNumLiteralsOrAreNotFields() {
			return (compareExpression != null) && (setExpression != null)
					&& (isNumLiteralOrIsNotField(compareExpression)) && (isNumLiteralOrIsNotField(setExpression));
		}

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

	/*
	 * This class was created to comment and add warnings when there are postfix and
	 * prefix expressions on the right hand side of an assignment with side effects.
	 *
	 * This is necessary because our assignment visitor does not let us visit the
	 * right hand side.
	 */
	private class PostfixAndPrefixExpressionCommenter extends ASTVisitor {

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
			}
			return false;
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

	private boolean foundFieldInExtendedOperands(InfixExpression infixExpression) {

		List<Expression> extendedOperands= infixExpression.extendedOperands();
		boolean foundField= false;

		for (Iterator<Expression> iterator= extendedOperands.iterator(); iterator.hasNext();) {
			Expression expression= iterator.next();
			if ((considerBinding(resolveBinding(expression))) && (!foundField)) {
				foundField= true;
				fRewriter.remove(expression, createGroupDescription(WRITE_ACCESS));
				extendedOperands.remove(expression);
			}
		}
		return foundField;
	}

	private void replaceOldOperandsWithNewOperands(AST ast, Expression leftOperand, Expression rightOperand, Expression receiver) {

		Expression newLeftOperand;
		Expression newRightOperand;
		newLeftOperand= getNewOperandWithGetInvocations(ast, leftOperand, receiver);
		newRightOperand= getNewOperandWithGetInvocations(ast, rightOperand, receiver);

		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(WRITE_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(WRITE_ACCESS));
	}

	private Expression getNewOperandWithGetInvocations(AST ast, Expression operand, Expression reciever) {

		Expression newOperand= null;

		if (considerBinding(resolveBinding(operand))) {
			newOperand= newGetInvocation(ast, (Expression) ASTNode.copySubtree(ast, reciever));
		} else {
			operand.accept(new ChangeFieldToGetInvocationVisitor());
			newOperand= (Expression) fRewriter.createMoveTarget(operand);
		}
		return newOperand;
	}

	private boolean isRefactorableForCompareAndSet(ASTNode node, IfStatement ifStatement, boolean nodeIsRefactorable) {

		if (node instanceof Assignment) {
			nodeIsRefactorable= checkIfAssignmentNodeIsRefactorableIntoCompareAndSet(node, ifStatement, nodeIsRefactorable);
		} else if (node instanceof InfixExpression) {
			nodeIsRefactorable= checkIfInfixExpressionNodeIsRefactorableIntoCompareAndSet(node, ifStatement, nodeIsRefactorable);
		}
		return nodeIsRefactorable;
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

	private void removeSynchronizedBlock(ASTNode node, Expression invocation, String accessType, AST ast, Statement statement, Statement syncStatement) {

		if (statement instanceof ExpressionStatement) {
			ExpressionStatement newExpressionStatement= ast.newExpressionStatement(invocation);
			fRewriter.replace(syncStatement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
		} else if (statement instanceof ReturnStatement) {
			ReturnStatement newReturnStatement= ast.newReturnStatement();
			newReturnStatement.setExpression(invocation);
			fRewriter.replace(syncStatement, newReturnStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
		} else {
			fRewriter.replace(node, invocation, createGroupDescription(accessType));
		}
	}

	private void removeSynchronizedModifier(MethodDeclaration methodDecl, int modifiers) {

		ModifierRewrite methodRewriter= ModifierRewrite.create(fRewriter, methodDecl);
		int synchronizedModifier= Modifier.SYNCHRONIZED;
		synchronizedModifier= ~synchronizedModifier;
		int newModifiersWithoutSync= modifiers & synchronizedModifier;
		methodRewriter.setModifiers(newModifiersWithoutSync, createGroupDescription(REMOVE_SYNCHRONIZED_MODIFIER));
	}

	private Expression getReceiver(Expression expression) {

		int type= expression.getNodeType();

		switch (type) {
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

	private void insertAtomicOpTodoComment(ASTNode node) {

		AtomicOpTodoCommenter todoCommenter= new AtomicOpTodoCommenter(fRewriter, fStatus, fIfStatementsToNodes, fGroupDescriptions);
		todoCommenter.addCommentBeforeNode(node);
	}

	private ListRewrite insertLineCommentBeforeNode(String comment, ASTNode node, ASTNode body, ChildListPropertyDescriptor descriptor) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(comment, ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(body, descriptor);
		rewriter.insertBefore(lineComment, node, createGroupDescription(COMMENT));
		return rewriter;
	}

	private void insertStatementsInBlockAreNotSynchronizedComment(Block syncBody, Statement firstStatement) {

		if (!fBlocksWithComments.contains(syncBody)) {
			fBlocksWithComments.add(syncBody);
			insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_block,
					firstStatement, syncBody, Block.STATEMENTS_PROPERTY);
			createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_execute_statement_atomically);
		}
	}

	private void insertStatementsNotSynchronizedInMethodComment(ASTNode node, MethodDeclaration methodDecl) {

		if (!fMethodsWithComments.contains(methodDecl)) {
			TypeDeclaration typeDeclaration= (TypeDeclaration) ASTNodes.getParent(node, TypeDeclaration.class);

			if (typeDeclaration != null) {
				fMethodsWithComments.add(methodDecl);
				insertLineCommentBeforeNode(
						ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_method,
						methodDecl, typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
				createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
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

	private boolean needsParentheses(Expression expression) {

		int type= expression.getNodeType();

		return (type == ASTNode.INFIX_EXPRESSION) || (type == ASTNode.CONDITIONAL_EXPRESSION) ||
				(type == ASTNode.PREFIX_EXPRESSION) || (type == ASTNode.POSTFIX_EXPRESSION) ||
				(type == ASTNode.CAST_EXPRESSION) || (type == ASTNode.INSTANCEOF_EXPRESSION);
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

	private MethodInvocation newGetInvocation(AST ast, Expression expression) {

		MethodInvocation methodInvocation= ast.newMethodInvocation();
		if (expression != null) {
			methodInvocation.setExpression(expression);
		}
		methodInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		return methodInvocation;
	}

	private MethodInvocation getTypeConversionInvocation(AST ast, String invocationName) {

		MethodInvocation methodInvocation= ast.newMethodInvocation();
		methodInvocation.setName(ast.newSimpleName(invocationName));
		methodInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));
		return methodInvocation;
	}

	private void createUnsafeOperatorWarning(Assignment node) {

		createWarningStatus(ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning1
				+ fFieldBinding.getName()
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning2
				+ fFieldBinding.getName()
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning3
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning4
				+ node.toString()
				+ ConcurrencyRefactorings.AtomicInteger_unsafe_operator_warning5);
	}

	private boolean statementIsRefactorableIntoCompareAndSet(Statement statement) {

		if (statement instanceof IfStatement) {
			if (fIfStatementsToNodes.containsKey(statement)) {
				IfStatementProperties properties= fIfStatementsToNodes.get(statement);
				return (properties.isRefactorable);
			}
		}
		return false;
	}

	private boolean checkIfParentIsExpressionStatement(ASTNode node) {

		ASTNode parent= node.getParent();
		return parent instanceof ExpressionStatement;
	}

	private boolean isNumLiteralOrIsNotField(Expression expression) {
		IBinding resolveBinding= resolveBinding(expression);
		if (resolveBinding instanceof IVariableBinding) {
			return !((IVariableBinding) resolveBinding).isField();
		}
		return expression instanceof NumberLiteral;
	}

	private void createWarningStatus(String message) {
		fStatus.addWarning(message);
	}

	private boolean canRemoveSynchBlockOrModifier(Statement statement) {
		return fCanRemoveSynchronizedBlockOrModifier.contains(statement);
	}

	public List<TextEditGroup> getGroupDescriptions() {
		return fGroupDescriptions;
	}

	public RefactoringStatus getStatus() {
		return fStatus;
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
}