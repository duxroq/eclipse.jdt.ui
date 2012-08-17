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

/**
 * This class performs the analysis of the CompilationUnit and records the necessary changes for the
 * Convert int to AtomicInteger refactoring.
 *
 * @author Alexandria Shearer
 *
 */

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

	private AtomicIntegerTodoCommenter fTodoCommenter;

	private HashMap<IfStatement, CompareAndSetProperties> fIfStatements;
	private ArrayList<MethodDeclaration> fMethodsWithComments;
	private ArrayList<Block> fBlocksWithComments;
	private ArrayList<Statement> fVisitedSynchronizedBlocks;
	private ArrayList<MethodDeclaration> fVisitedSynchronizedMethods;
	private ArrayList<Statement> fCanRemoveSynchronizedBlockOrModifier;

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
		fTodoCommenter= new AtomicIntegerTodoCommenter(fRewriter, fGroupDescriptions);
		try {
			fIsFieldFinal= Flags.isFinal(refactoring.getField().getFlags());
		} catch (JavaModelException e) {
			// assume non final field
		}
		fStatus= new RefactoringStatus();
		fIfStatements= new HashMap<IfStatement, AccessAnalyzerForAtomicInteger.CompareAndSetProperties>();
	}

	@Override
	public void endVisit(CompilationUnit node) {
		fImportRewriter.addImport(ConcurrencyRefactorings.AtomicIntegerRefactoring_import);
	}

	@Override
	public boolean visit(Assignment assignment) {

		boolean assignmentIsRefactored= false;
		boolean inReturnStatement= false;
		Expression leftHandSide= assignment.getLeftHandSide();
		addNodeToIfStatementProperties(assignment);

		if (!considerBinding(resolveBinding(leftHandSide))) {
			return true;
		}
		if (!fIsFieldFinal) {

			AST ast= assignment.getAST();

			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_set));

			Statement statement= (Statement) ASTNodes.getParent(assignment, Statement.class);
			Expression receiver= getReceiver(leftHandSide);

			if (receiver != null) {
				invocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
			}
			if ((!checkIfParentIsExpressionStatement(assignment)) && (statement instanceof ReturnStatement)) {
				// return <assignment>
				inReturnStatement= true;
			}

			if (assignment.getOperator() == Assignment.Operator.ASSIGN) {
				// handles i = expression
				Expression rightHandSide= assignment.getRightHandSide();
				if (rightHandSide instanceof InfixExpression) {
					// handles i = <expression> OP <expression>
					assignmentIsRefactored= infixExpressionAssignmentHandler(assignment, invocation);
				}
				if (!assignmentIsRefactored) {
					// handles i = 2 OR i = f (where f is not a field)
					if (isAnAtomicAccess(rightHandSide)) {
						markParentStatementAsReadyForDesynchronization(assignment);
					}
					rightHandSide.accept(new ReplaceFieldWithGetter());
					rightHandSide.accept(new PostfixAndPrefixExpressionCommenter());
					invocation.arguments().add(fRewriter.createMoveTarget(rightHandSide));
				}
			}
			if (assignment.getOperator() != Assignment.Operator.ASSIGN) {
				// handles i (+=,-=,/=,*=, etc) <expression>
				compoundAssignmentHandler(assignment, invocation);
			}
			if ((!inReturnStatement)
					&& (!removedSynchBlock(assignment, invocation, WRITE_ACCESS))
					&& (!removedSynchModifier(assignment, invocation, WRITE_ACCESS))) {

				fRewriter.replace(assignment, invocation, createGroupDescription(WRITE_ACCESS));
			} else if (inReturnStatement) {
				// handles return i = <expression>
				refactorReturnAtomicIntegerAssignment(assignment, invocation);
			}
		}
		return false;
	}

	private boolean infixExpressionAssignmentHandler(Assignment assignment, MethodInvocation invocation) {

		InfixExpression infixExpression= (InfixExpression) assignment.getRightHandSide();

		boolean infixIsRefactored= false;
		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();
		Operator operator= infixExpression.getOperator();

		if ((operator == InfixExpression.Operator.PLUS) || (operator == InfixExpression.Operator.MINUS)) {
			if (considerBinding(resolveBinding(leftOperand)) || considerBinding(resolveBinding(rightOperand))) {
				if (considerBinding(resolveBinding(leftOperand))) {
					// handles i = i + 3
					shiftOperandsToTheLeftForAddAndGet(infixExpression, invocation, assignment);
					return true;
				} else if (considerBinding(resolveBinding(rightOperand))) {
					// handles i = 3 + i
					infixIsRefactored= shiftOperandsLeftKeepingLeftOpForAddAndGet(invocation, infixExpression, assignment);
					return infixIsRefactored;
				}
			} else if (infixExpression.hasExtendedOperands()) {
				// handles i = 3 + j + i
				infixIsRefactored= shiftOperandsLeftKeepingLeftAndRightOpsForAddAndGet(invocation, infixExpression, assignment);
				return infixIsRefactored;
			} else {
				insertAtomicOpTodoComment(assignment);
				replaceFieldRefsWithGettersInOperands(assignment);
			}
		} else {
			// handles i = i * 3
			insertAtomicOpTodoComment(assignment);
			replaceFieldRefsWithGettersInOperands(assignment);
			if (infixExpression.hasExtendedOperands()) {
				convertFieldRefsInExtOperandsToGetters(infixExpression);
			}
		}
		return infixIsRefactored;
	}

	private boolean compoundAssignmentHandler(Assignment assignment, MethodInvocation invocation) {

		AST ast= assignment.getAST();
		Expression rightHandSide= assignment.getRightHandSide();
		Expression receiver= getReceiver(assignment.getLeftHandSide());
		Assignment.Operator operator= assignment.getOperator();

		if ((operator == Assignment.Operator.PLUS_ASSIGN) || (operator == Assignment.Operator.MINUS_ASSIGN)) {
			if (operator == Assignment.Operator.PLUS_ASSIGN) {
				// handles i += expression
				invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
				rightHandSide= getOperandWithGetters(rightHandSide, receiver);
				invocation.arguments().add(rightHandSide);
			} else if (operator == Assignment.Operator.MINUS_ASSIGN) {
				// handles i -= <expression>
				invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
				rightHandSide.accept(new ReplaceFieldWithGetter());
				invocation.arguments().add(createNegativeExpression(rightHandSide));
			}
			if (!isAnAtomicAccess(rightHandSide)) {
				insertAtomicOpTodoComment(assignment);
			} else {
				// handles i (+=,-=) 2 OR i (+=,-=) f where f is not a field
				markParentStatementAsReadyForDesynchronization(assignment);
			}
		} else {
			// handles i (*=, /=, %=, |=, &=, etc) <expression>
			insertAtomicOpTodoComment(assignment);
			InfixExpression newInfixExpression= getInfixWithGetterAndRightHandSide(assignment);
			invocation.arguments().add(newInfixExpression);
		}
		return false;
	}

	private InfixExpression getInfixWithGetterAndRightHandSide(Assignment assignment) {

		// Example: i *= 2 => returns i.get()*2
		AST ast= assignment.getAST();
		Expression receiver= getReceiver(assignment.getLeftHandSide());
		Expression rightHandSide= assignment.getRightHandSide();
		InfixExpression.Operator newOperator= ASTNodes.convertToInfixOperator(assignment.getOperator());
		MethodInvocation invocationGet= ast.newMethodInvocation();
		InfixExpression newInfixExpression= ast.newInfixExpression();

		invocationGet.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		if (receiver != null) {
			invocationGet.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
		}
		rightHandSide= getOperandWithGetters(rightHandSide, receiver);
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
			// i++ ==> i.getAndIncrement()
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_getAndIncrement));
			markParentStatementAsReadyForDesynchronization(postfixExpression);
		} else if (operator == PostfixExpression.Operator.DECREMENT) {
			// i-- ==> i.getAndDecrement()
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_getAndDecrement));
			markParentStatementAsReadyForDesynchronization(postfixExpression);
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
			// ++i ==> i.incrementAndGet()
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_incrementAndGet));
			markParentStatementAsReadyForDesynchronization(prefixExpression);
		} else if (operator == PrefixExpression.Operator.DECREMENT) {
			// --i ==> i.decrementAndGet()
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_decrementAndGet));
			markParentStatementAsReadyForDesynchronization(prefixExpression);
		}
		if (!(removedSynchBlock(prefixExpression, invocation, PREFIX_ACCESS) || removedSynchModifier(prefixExpression, invocation, PREFIX_ACCESS))) {
			fRewriter.replace(prefixExpression, invocation, createGroupDescription(PREFIX_ACCESS));
		}
		return false;
	}

	@Override
	public boolean visit(SimpleName simpleName) {

		AST ast= simpleName.getAST();
		MethodInvocation invocation= ast.newMethodInvocation();
		ReplacementPair pair= checkForTypeConversions(simpleName, invocation);

		if ((!simpleName.isDeclaration()) && (considerBinding(resolveBinding(simpleName)))) {
			if (pair == null) {
				String accessType= READ_ACCESS;
				invocation= newGetter((Expression) ASTNode.copySubtree(ast, simpleName));
				markParentStatementAsReadyForDesynchronization(simpleName);
				if (!(removedSynchBlock(simpleName, invocation, accessType)
				|| removedSynchModifier(simpleName, invocation, accessType))) {
					// i ==> i.get()
					fRewriter.replace(simpleName, invocation, createGroupDescription(accessType));
				}
			} else {
				String accessType= REPLACE_TYPE_CONVERSION;
				if (!(removedSynchBlock(pair.whatToReplace, (Expression) pair.replacement, accessType)
				|| removedSynchModifier(pair.whatToReplace, (Expression) pair.replacement, accessType))) {
					// Example: ((float) i) ==> i.floatValue()
					fRewriter.replace(pair.whatToReplace, pair.replacement, createGroupDescription(accessType));
				}
			}
		}
		return true;
	}

	private ReplacementPair checkForTypeConversions(SimpleName simpleName, MethodInvocation invocation) {

		ReplacementPair replacementPair= checkForPrimitiveTypeCastConversions(simpleName, invocation);

		if (replacementPair != null) {
			return replacementPair;
		}
		replacementPair= checkForIntToDoubleConversion(simpleName, invocation);
		return replacementPair;
	}

	private ReplacementPair checkForIntToDoubleConversion(SimpleName simpleName, MethodInvocation invocation) {

		// Example: Double.parseDouble(Integer.toString(i)) ==> returns (Double.parseDouble(Integer.toString(i)), i.doubleValue())
		MethodInvocation methodInvocationParent= (MethodInvocation) ASTNodes.getParent(simpleName, MethodInvocation.class);

		if ((methodInvocationParent != null) && (methodInvocationParent.getName().toString().equals(ConcurrencyRefactorings.ToString))) {
			if (methodInvocationParent.getExpression().toString().equals(ConcurrencyRefactorings.Integer)) {
				MethodInvocation parent= (MethodInvocation) ASTNodes.getParent(methodInvocationParent, MethodInvocation.class);
				if ((parent != null)) {
					if ((parent.getExpression().toString().equals(ConcurrencyRefactorings.Double))
							&& (parent.getName().toString().equals(ConcurrencyRefactorings.ParseDouble))) {

						markParentStatementAsReadyForDesynchronization(simpleName);
						invocation= getTypeConversionInvocation(ConcurrencyRefactorings.AtomicInteger_doubleValue, simpleName);
						return new ReplacementPair(parent, invocation);
					}
				}
			}
		}
		return null;
	}

	private ReplacementPair checkForPrimitiveTypeCastConversions(SimpleName simpleName, MethodInvocation invocation) {

		// Example: ((float) i) ==> returns (((float) i), i.floatValue())
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
					invocation= getTypeConversionInvocation(ConcurrencyRefactorings.AtomicInteger_doubleValue, simpleName);
				} else if (primitiveTypeCode.equals(PrimitiveType.BYTE)) {
					invocation= getTypeConversionInvocation(ConcurrencyRefactorings.AtomicInteger_byteValue, simpleName);
				} else if (primitiveTypeCode.equals(PrimitiveType.FLOAT)) {
					invocation= getTypeConversionInvocation(ConcurrencyRefactorings.AtomicInteger_floatValue, simpleName);
				} else if (primitiveTypeCode.equals(PrimitiveType.SHORT)) {
					invocation= getTypeConversionInvocation(ConcurrencyRefactorings.AtomicInteger_shortValue, simpleName);
				} else if (primitiveTypeCode.equals(PrimitiveType.LONG)) {
					invocation= getTypeConversionInvocation(ConcurrencyRefactorings.AtomicInteger_longValue, simpleName);
				} else {
					return null;
				}
				markParentStatementAsReadyForDesynchronization(simpleName);
				return new ReplacementPair(expression, invocation);
			}
		}
		return null;
	}

	@Override
	public boolean visit(InfixExpression infixExpression) {

		addNodeToIfStatementProperties(infixExpression);
		return true;
	}

	@Override
	public boolean visit(IfStatement ifStatement) {

		CompareAndSetProperties properties= getIfStatementProperties(ifStatement);
		Statement elseStatement= ifStatement.getElseStatement();
		Statement thenStatement= ifStatement.getThenStatement();
		boolean thenStatementHasOneStatement=
				((thenStatement instanceof Block) && (((Block) thenStatement).statements().size() == 1))
						|| (!(thenStatement instanceof Block));
		if ((elseStatement != null) || (!thenStatementHasOneStatement)) {
			return true;
		}

		ifStatement.getExpression().accept(this);
		ifStatement.getThenStatement().accept(this);

		if (properties != null) {
			if (fIfStatements.get(ifStatement).ifStatementIsRefactorableIntoCompareAndSet()) {
				refactorIfStatementIntoCompareAndSetInvocation(ifStatement);
			} else {
				insertStatementsInMethodAreNotSynchronizedComment(ifStatement);
			}
		}
		return false;
	}

	private CompareAndSetProperties getIfStatementProperties(IfStatement ifStatement) {

		CompareAndSetProperties properties;
		if (!fIfStatements.containsKey(ifStatement)) {
			properties= new CompareAndSetProperties();
			if (ifStatement.getElseStatement() != null) {
				properties.isRefactorable= false;
			}
			fIfStatements.put(ifStatement, properties);
		} else {
			properties= fIfStatements.get(ifStatement);
		}
		return properties;
	}

	private boolean checkIfAssignmentNodeIsRefactorableIntoCompareAndSet(ASTNode node, IfStatement ifStatement) {

		Statement thenStatement= ifStatement.getThenStatement();
		CompareAndSetProperties properties= fIfStatements.get(ifStatement);
		ASTNode parentStatement= ASTNodes.getParent(node, Statement.class);
		boolean nodeIsRefactorable= false;

		if (!(thenStatement instanceof Block) || ((thenStatement instanceof Block) && (((Block) thenStatement).statements().size() == 1))) {
			if (thenStatement instanceof Block) {
				List<Statement> statements= ((Block) thenStatement).statements();
				for (Iterator<Statement> iterator= statements.iterator(); iterator.hasNext();) {
					Statement statement= iterator.next();
					if (ASTMatcher.safeEquals(parentStatement, statement)) {
						Expression leftHandSide= ((Assignment) node).getLeftHandSide();
						if (considerBinding(resolveBinding(leftHandSide))) {
							nodeIsRefactorable= true;
							properties.setExpression= ((Assignment) node).getRightHandSide();
						}
					}
				}
			} else if (ASTMatcher.safeEquals(parentStatement, thenStatement)) {
				Expression leftHandSide= ((Assignment) node).getLeftHandSide();
				if (considerBinding(resolveBinding(leftHandSide))) {
					nodeIsRefactorable= true;
					properties.setExpression= ((Assignment) node).getRightHandSide();
				}
			}
		}
		return nodeIsRefactorable;
	}

	private boolean checkIfInfixExpressionNodeIsRefactorableIntoCompareAndSet(ASTNode node, IfStatement ifStatement) {

		Assignment assignmentParent= (Assignment) ASTNodes.getParent(node, Assignment.class);
		CompareAndSetProperties properties= fIfStatements.get(ifStatement);
		boolean nodeIsRefactorable= false;

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
						if (leftOperandIsField) {
							properties.compareExpression= rightOperand;
						} else {
							properties.compareExpression= leftOperand;
						}
					}
				}
			}
		}
		return nodeIsRefactorable;
	}

	private void addNodeToIfStatementProperties(ASTNode node) {

		IfStatement ifStatement= (IfStatement) ASTNodes.getParent(node, IfStatement.class);

		if (ifStatement != null) {
			CompareAndSetProperties properties= getIfStatementProperties(ifStatement);

			if (!properties.nodes.contains(node)) {
				properties.nodes.add(node);
				boolean isRefactorableForCompareAndSet= isRefactorableForCompareAndSet(node, ifStatement);
				properties.nodeFitsCompareAndSet.add(new Boolean(isRefactorableForCompareAndSet));
			}
		}
		return;
	}

	private void insertStatementsInBlockAreNotSynchronizedComment(Assignment node) {

		SynchronizedStatement syncStatement= (SynchronizedStatement) ASTNodes.getParent(node, SynchronizedStatement.class);
		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);

		if (syncStatement != null) {
			Block body= syncStatement.getBody();
			if (body.statements().size() == 1) {
				createCannotRemoveSynchBlockWarningUnAtomicStatement(node, (Statement) body.statements().get(0));
			} else {
				createCannotRemoveSynchBlockWarningMultipleStatements(node);
			}
			insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_block,
					syncStatement, methodDecl.getBody(), Block.STATEMENTS_PROPERTY);
		}
	}

	private void insertStatementsInMethodAreNotSynchronizedComment(Assignment node) {

		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);
		TypeDeclaration typeDeclaration= (TypeDeclaration) ASTNodes.getParent(methodDecl, TypeDeclaration.class);
		int modifiers= methodDecl.getModifiers();

		if (Modifier.isSynchronized(modifiers)) {
			MethodDeclaration[] methods= typeDeclaration.getMethods();
			for (int i= 0; i < methods.length; i++) {
				if (methods[i] == methodDecl) {
					Block body= methodDecl.getBody();
					if (body.statements().size() == 1) {
						createCannotRemoveSynchMethodWarningUnAtomicStatement(methodDecl, (Statement) body.statements().get(0));
					} else {
						createCannotRemoveSynchMethodWarningMultipleStatements(methodDecl);
					}
					insertStatementsInMethodAreNotSynchronizedComment(node, methodDecl);
					break;
				}
			}
		}
	}

	private void convertFieldRefsInExtOperandsToGetters(InfixExpression infixExpression) {

		// Example: i = 12 + j + (i*2) ==> i = 12 + j + (i.get()*2)
		if (infixExpression.hasExtendedOperands()) {
			List<Expression> extendedOperands= infixExpression.extendedOperands();
			for (int i= 0; i < extendedOperands.size(); i++) {
				Expression expression= extendedOperands.get(i);
				expression.accept(new ReplaceFieldWithGetter());
			}
		}
	}

	private void shiftOperandsToTheLeftForAddAndGet(InfixExpression infixExpression, MethodInvocation invocation,
			Assignment assignment) {

		Expression receiver= getReceiver(assignment.getLeftHandSide());
		AST ast= infixExpression.getAST();
		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();
		Operator operator= infixExpression.getOperator();

		// Example: for i = i + 3 + j, the i on the right hand side is replaced with 3
		replaceOperandWithNewOperand(leftOperand, rightOperand, receiver);
		if (infixExpression.hasExtendedOperands()) {
			// then the duplicate 3 is replaced with j
			Expression operand= (Expression) infixExpression.extendedOperands().get(0);

			replaceOperandWithNewOperand(rightOperand, operand, receiver);
			convertFieldRefsInExtOperandsToGetters(infixExpression);
			fRewriter.remove(operand, createGroupDescription(WRITE_ACCESS));
			insertAtomicOpTodoComment(assignment);
			refactorAssignmentIntoAddAndGet(invocation, infixExpression, operator, receiver, assignment);
		} else {
			// i = i + 3 ==> i.addAndGet(3)
			refactorInfixExpressionWithNoExtOperandsIntoAddAndGet(assignment, ast, invocation, receiver, rightOperand, operator);
		}
	}

	private void refactorInfixExpressionWithNoExtOperandsIntoAddAndGet(Assignment assignment, AST ast, MethodInvocation invocation,
			Expression receiver, Expression rightOperand, Operator operator) {

		if (considerBinding(resolveBinding(rightOperand))) {
			// i = i + i ==> i.addAndGet(i.get())
			MethodInvocation newGetInvocation= newGetter((Expression) ASTNode.copySubtree(ast, receiver));
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			invocation.arguments().add(newGetInvocation);
			insertAtomicOpTodoComment(assignment);
		} else {
			// i = i + 3 ==> i.addAndGet(3)
			rightOperand.accept(new ReplaceFieldWithGetter());
			refactorAssignmentIntoAddAndGet(invocation, rightOperand, operator, receiver, assignment);
			if (!isAnAtomicAccess(rightOperand)) {
				// Example: i = i + <field>
				insertAtomicOpTodoComment(assignment);
			} else {
				// Example: i = i + 3
				markParentStatementAsReadyForDesynchronization(assignment);
			}
		}
	}

	private boolean shiftOperandsLeftKeepingLeftOpForAddAndGet(MethodInvocation invocation, InfixExpression infixExpression,
			Assignment assignment) {

		Expression receiver= getReceiver(assignment.getLeftHandSide());
		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();
		Operator operator= infixExpression.getOperator();
		leftOperand.accept(new ReplaceFieldWithGetter());

		if (infixExpression.hasExtendedOperands() && operator != InfixExpression.Operator.MINUS) {
			// Example: i = 12 + i + j
			Expression operand= (Expression) infixExpression.extendedOperands().get(0);
			// replace i in the infix with j, and delete duplicate j
			replaceOperandWithNewOperand(rightOperand, operand, receiver);
			convertFieldRefsInExtOperandsToGetters(infixExpression);
			fRewriter.remove(operand, createGroupDescription(WRITE_ACCESS));
			insertAtomicOpTodoComment(assignment);
			// i = 12 + j ==> i.addAndGet(12 + j)
			refactorAssignmentIntoAddAndGet(invocation, infixExpression, operator, receiver, assignment);
			return true;
		} else if (operator != InfixExpression.Operator.MINUS) {
			// Example: i = 12 + i
			// i = 12 + i ==> i.addAndGet(12)
			refactorAssignmentIntoAddAndGet(invocation, leftOperand, operator, receiver, assignment);
			if (!isAnAtomicAccess(leftOperand)) {
				insertAtomicOpTodoComment(assignment);
			} else {
				markParentStatementAsReadyForDesynchronization(assignment);
			}
			return true;
		} else {
			// i = 12 - i - j OR i = 12 - i
			insertAtomicOpTodoComment(assignment);
			replaceFieldRefsWithGettersInOperands(assignment);
			return false;
		}
	}

	private boolean shiftOperandsLeftKeepingLeftAndRightOpsForAddAndGet(MethodInvocation invocation, InfixExpression infixExpression, Assignment assignment) {

		Operator operator= infixExpression.getOperator();
		Expression receiver= getReceiver(assignment.getLeftHandSide());

		replaceFieldRefsWithGettersInOperands(assignment);
		insertAtomicOpTodoComment(assignment);

		// Example: i = 3 + f + i
		if (operator != InfixExpression.Operator.MINUS) {
			// i in the extended operands is removed
			boolean found= findFieldInExtOperandsAndRemoveFirst(infixExpression);
			if (found) {
				convertFieldRefsInExtOperandsToGetters(infixExpression);
				// i.addAndGet(3 + f)
				refactorAssignmentIntoAddAndGet(invocation, infixExpression, operator, receiver, assignment);
				return true;
			} else {
				convertFieldRefsInExtOperandsToGetters(infixExpression);
			}
		} else {
			convertFieldRefsInExtOperandsToGetters(infixExpression);
		}
		return false;
	}

	private void refactorAssignmentIntoAddAndGet(MethodInvocation invocation, Expression operand,
			Object operator, Expression receiver, ASTNode node) {

		AST ast= invocation.getAST();

		if ((operator == InfixExpression.Operator.PLUS) || (operator == Assignment.Operator.PLUS_ASSIGN)) {
			// i = i + 2 ==> i.addAndGet(2) OR i += 2 ==> i.addAndGet(2)
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));
			invocation.arguments().add(fRewriter.createMoveTarget(operand));
		} else if ((operator == InfixExpression.Operator.MINUS) || (operator == Assignment.Operator.MINUS_ASSIGN)) {
			// i = i - 2 ==> i.addAndGet(-2) OR i -= 2 ==> i.addAndGet(-2)
			invocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_addAndGet));

			if (operand instanceof InfixExpression) {
				PrefixExpression newPrefixExpression= factorOutMinusOpFromInfixIntoPrefixExpression((InfixExpression) operand, receiver);
				invocation.arguments().add(newPrefixExpression);
			} else {
				invocation.arguments().add(createNegativeExpression(operand));
			}
		}
		// If the new addAndGet invocation is inside an if statement, then the if statement will not be refactored into compare and set
		preserveIfStatementOverCompareAndSet(node);
	}

	private PrefixExpression factorOutMinusOpFromInfixIntoPrefixExpression(InfixExpression infixExpression, Expression receiver) {

		// Example: 12 - j ==> returns -(12 + j)

		// NOTE: in removing leftOperand in previous methods to
		// perform the shifts the negative prefix to the new
		// left operand 12 was lost. This method corrects that loss.

		AST ast= infixExpression.getAST();

		// have to craft a new infixExpression because directly changing the operator does
		// not persist in creating the text edits.
		InfixExpression newInfixExpression= cloneInfixWithPlusOperator(infixExpression, receiver);

		PrefixExpression newPrefixExpression= ast.newPrefixExpression();
		newPrefixExpression.setOperator(PrefixExpression.Operator.MINUS);
		ParenthesizedExpression p= ast.newParenthesizedExpression();
		p.setExpression(newInfixExpression);
		newPrefixExpression.setOperand(p);

		return newPrefixExpression;
	}

	private InfixExpression cloneInfixWithPlusOperator(InfixExpression infixExpression, Expression receiver) {

		// Example: (i.get()*3) - j ==> returns (i.get()*3) + j
		AST ast= infixExpression.getAST();
		Expression rightOperand= infixExpression.getRightOperand();
		Expression newLeftOperand= getOperandWithGetters(rightOperand, receiver);
		InfixExpression newInfixExpression= ast.newInfixExpression();

		newInfixExpression.setLeftOperand(newLeftOperand);
		Expression newRightOperand= null;
		if (infixExpression.hasExtendedOperands()) {
			newRightOperand= getOperandWithGetters((Expression) infixExpression.extendedOperands().get(0), receiver);
			infixExpression.extendedOperands().remove(0);
			newInfixExpression.setRightOperand(newRightOperand);
			List<Expression> extendedOperands= infixExpression.extendedOperands();
			for (int i= 0; i < extendedOperands.size(); i++) {
				Expression newOperandWithGetInvocations= getOperandWithGetters(extendedOperands.get(i), receiver);
				newInfixExpression.extendedOperands().add(newOperandWithGetInvocations);
			}
		}
		newInfixExpression.setOperator(InfixExpression.Operator.PLUS);
		return newInfixExpression;
	}

	private void preserveIfStatementOverCompareAndSet(ASTNode node) {

		for (Map.Entry<IfStatement, CompareAndSetProperties> entry : fIfStatements.entrySet()) {
			CompareAndSetProperties properties= entry.getValue();
			if (properties.nodes.contains(node)) {
				properties.isRefactorable= false;
			}
		}
	}

	private void refactorIfStatementIntoCompareAndSetInvocation(IfStatement ifStatement) {

		ExpressionStatement compareAndSetStatement= getCompareAndSetInvocationStatement(ifStatement);
		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(ifStatement, MethodDeclaration.class);
		SynchronizedStatement syncStatement= (SynchronizedStatement) ASTNodes.getParent(ifStatement, SynchronizedStatement.class);

		boolean removedSynchBlock= removedSynchBlockOrModifier(ifStatement, compareAndSetStatement, methodDecl, syncStatement);
		if (!removedSynchBlock) {
			fRewriter.replace(ifStatement, compareAndSetStatement, createGroupDescription(REPLACE_IF_STATEMENT_WITH_COMPARE_AND_SET));
		}
	}

	private boolean removedSynchBlockOrModifier(IfStatement ifStatement, ExpressionStatement compareAndSetStatement,
			MethodDeclaration methodDecl, SynchronizedStatement syncStatement) {

		CompareAndSetProperties properties= fIfStatements.get(ifStatement);
		boolean removedSynchBlock= false;
		if ((syncStatement != null)) {
			Block body= syncStatement.getBody();
			if ((properties.argsAreAtomicAccesses()) && (body.statements().size() == 1)) {
				fRewriter.replace(syncStatement, compareAndSetStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
				removedSynchBlock= true;
			} else if (!(properties.argsAreAtomicAccesses())) {
				insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
						ifStatement, body, Block.STATEMENTS_PROPERTY);
				insertStatementsInBlockAreNotSynchronizedComment(body);
			} else {
				insertStatementsInBlockAreNotSynchronizedComment(body);
			}
		} else if (methodDecl != null) {
			int modifiers= methodDecl.getModifiers();
			if (Modifier.isSynchronized(modifiers)) {
				List<Statement> methodBodyStatements= methodDecl.getBody().statements();
				if ((properties.argsAreAtomicAccesses()) && (methodBodyStatements.size() == 1)) {
					removeSynchronizedModifier(methodDecl);
				} else if (!(properties.argsAreAtomicAccesses()) && (methodBodyStatements.size() == 1)) {
					insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
							ifStatement, methodDecl.getBody(), Block.STATEMENTS_PROPERTY);
					insertStatementsInMethodAreNotSynchronizedComment(ifStatement, methodDecl);
					createCannotRemoveSynchMethodWarningUnAtomicStatement(methodDecl, methodBodyStatements.get(0));
				} else if (!(properties.argsAreAtomicAccesses()) && !(methodBodyStatements.size() == 1)) {
					insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
							ifStatement, methodDecl.getBody(), Block.STATEMENTS_PROPERTY);
					insertStatementsInMethodAreNotSynchronizedComment(ifStatement, methodDecl);
					createCannotRemoveSynchMethodWarningMultipleStatements(methodDecl);
				} else {
					insertStatementsInMethodAreNotSynchronizedComment(ifStatement, methodDecl);
					createCannotRemoveSynchMethodWarningMultipleStatements(methodDecl);
				}
			}
		}
		return removedSynchBlock;
	}

	private ExpressionStatement getCompareAndSetInvocationStatement(IfStatement ifStatement) {

		AST ast= ifStatement.getAST();
		CompareAndSetProperties properties= fIfStatements.get(ifStatement);

		MethodInvocation compareAndSetInvocation= ast.newMethodInvocation();
		compareAndSetInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_compareAndSet));
		compareAndSetInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));

		Expression setExpression= properties.setExpression;
		Expression compareExpression= properties.compareExpression;

		if ((compareExpression != null) && (setExpression != null)) {
			compareAndSetInvocation.arguments().add(fRewriter.createMoveTarget(compareExpression));
			compareAndSetInvocation.arguments().add(fRewriter.createMoveTarget(setExpression));
		}
		ExpressionStatement compareAndSetStatement= ast.newExpressionStatement(compareAndSetInvocation);

		return compareAndSetStatement;
	}

	private void markParentStatementAsReadyForDesynchronization(ASTNode node) {

		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);
		ASTNode assignment= ASTNodes.getParent(node, Assignment.class);
		ASTNode infixExpression= ASTNodes.getParent(node, InfixExpression.class);
		if ((statement != null)
				&& ((statement instanceof ExpressionStatement) || (statement instanceof ReturnStatement))
				&& (assignment == null) && (infixExpression == null)) {
			fCanRemoveSynchronizedBlockOrModifier.add(statement);
		}
	}

	private void refactorReturnAtomicIntegerAssignment(Assignment assignment, MethodInvocation invocation) {

		// Example: return i = 12; ==> i.set(12); return i.get();
		Expression receiver= getReceiver(assignment.getLeftHandSide());
		Statement statement= (Statement) ASTNodes.getParent(assignment, Statement.class);
		Block body= (Block) ASTNodes.getParent(assignment, Block.class);
		AST ast= assignment.getAST();
		MethodInvocation getter= ast.newMethodInvocation();

		getter.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		if (receiver != null) {
			getter.setExpression((Expression) ASTNode.copySubtree(ast, receiver));
		}
		ListRewrite rewriter= fRewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
		ExpressionStatement setInvocationStatement= ast.newExpressionStatement(invocation);
		rewriter.insertBefore(setInvocationStatement, statement, createGroupDescription(WRITE_ACCESS));

		ReturnStatement returnStatement= ast.newReturnStatement();
		returnStatement.setExpression(getter);
		fRewriter.replace(statement, returnStatement, createGroupDescription(READ_ACCESS));
		createCommentsAndWarningsForReturnAssignments(assignment, returnStatement);
	}

	private void createCommentsAndWarningsForReturnAssignments(Assignment assignment, ReturnStatement returnStatement) {

		Statement statement= (Statement) ASTNodes.getParent(assignment, Statement.class);
		Block body= (Block) ASTNodes.getParent(assignment, Block.class);
		insertLineCommentBeforeNode(
				ConcurrencyRefactorings.AtomicInteger_todo_comment_return_statement_could_not_be_executed_atomically,
				returnStatement, body, Block.STATEMENTS_PROPERTY);

		String message= new String(
				ConcurrencyRefactorings.BeginSingleQuote +
						statement.toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_cannot_be_refactored_into_an_atomic_operation);
		createWarningStatus(message);

		insertStatementsInBlockAreNotSynchronizedComment(assignment);
		insertStatementsInMethodAreNotSynchronizedComment(assignment);
	}

	private boolean removedSynchBlock(ASTNode node, Expression invocation, String accessType) {

		Statement syncStatement= (Statement) ASTNodes.getParent(node, SynchronizedStatement.class);

		if (syncStatement != null) {
			if (!fVisitedSynchronizedBlocks.contains(syncStatement)) {
				fVisitedSynchronizedBlocks.add(syncStatement);

				Block syncBody= ((SynchronizedStatement) syncStatement).getBody();
				List<?> syncBodyStatements= syncBody.statements();
				Statement firstStatement= (Statement) syncBodyStatements.get(0);

				if (syncBodyStatements.size() > 1) {
					insertStatementsInBlockAreNotSynchronizedComment(syncBody);
					createCannotRemoveSynchBlockWarningMultipleStatements(node);
					return false;
				} else {
					if (canRemoveSynchBlockOrModifier(firstStatement)) {
						removeSynchronizedBlock(node, invocation, accessType, syncStatement);
						return true;
					} else {
						insertStatementsInBlockAreNotSynchronizedComment(syncBody);
						createCannotRemoveSynchBlockWarningUnAtomicStatement(node, firstStatement);
						return false;
					}
				}
			}
		}
		return false;
	}

	private void createCannotRemoveSynchBlockWarningUnAtomicStatement(ASTNode node, Statement statement) {

		MethodDeclaration methodDeclaration= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);
		String message= new String(
				ConcurrencyRefactorings.AtomicInteger_cannot_remove_synch_block_from_the_method +
						ConcurrencyRefactorings.BeginSingleQuote +
						methodDeclaration.getName().toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_because_the_statement +
						ConcurrencyRefactorings.BeginSingleQuote +
						statement.toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_cannot_be_refactored_into_an_atomic_operation);
		createWarningStatus(message);
	}

	private void createCannotRemoveSynchBlockWarningMultipleStatements(ASTNode node) {

		MethodDeclaration methodDeclaration= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);
		String message= new String(
				ConcurrencyRefactorings.AtomicInteger_cannot_remove_synch_block_from_the_method +
						ConcurrencyRefactorings.BeginSingleQuote +
						methodDeclaration.getName().toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_because_there_are_multiple_statements_in_the_block_body);
		createWarningStatus(message);
	}

	private boolean removedSynchModifier(ASTNode node, Expression invocation, String accessType) {

		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(node, MethodDeclaration.class);

		if (methodDecl != null) {
			if (!fVisitedSynchronizedMethods.contains(methodDecl)) {
				fVisitedSynchronizedMethods.add(methodDecl);

				int modifiers= methodDecl.getModifiers();
				if (Modifier.isSynchronized(modifiers)) {

					List<Statement> methodBodyStatements= methodDecl.getBody().statements();
					Statement firstStatement= methodBodyStatements.get(0);

					if (methodBodyStatements.size() == 1) {
						if (canRemoveSynchBlockOrModifier(firstStatement)) {
							removeSynchronizedModifier(methodDecl);
							fRewriter.replace(node, invocation, createGroupDescription(accessType));
							return true;
						} else if (!(statementIsRefactorableIntoCompareAndSet(firstStatement))) {
							insertStatementsInMethodAreNotSynchronizedComment(node, methodDecl);
							createCannotRemoveSynchMethodWarningUnAtomicStatement(methodDecl, firstStatement);
							return false;
						}
					} else {
						insertStatementsInMethodAreNotSynchronizedComment(node, methodDecl);
						createCannotRemoveSynchMethodWarningMultipleStatements(methodDecl);
						return false;
					}
				}
			}
		}
		return false;
	}

	private void createCannotRemoveSynchMethodWarningUnAtomicStatement(MethodDeclaration methodDeclaration, Statement statement) {

		String message= new String(
				ConcurrencyRefactorings.AtomicInteger_cannot_remove_synch_modifier_from_the_method +
						ConcurrencyRefactorings.BeginSingleQuote +
						methodDeclaration.getName().toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_because_the_statement +
						ConcurrencyRefactorings.BeginSingleQuote +
						statement.toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_cannot_be_refactored_into_an_atomic_operation);
		createWarningStatus(message);
	}

	private void createCannotRemoveSynchMethodWarningMultipleStatements(MethodDeclaration methodDeclaration) {

		String message= new String(
				ConcurrencyRefactorings.AtomicInteger_cannot_remove_synch_modifier_from_the_method +
						ConcurrencyRefactorings.BeginSingleQuote +
						methodDeclaration.getName().toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_because_there_are_multiple_statements_in_the_method_body);
		createWarningStatus(message);
	}

	private void replaceOperandWithNewOperand(Expression operand, Expression newOperand, Expression receiver) {

		AST ast= operand.getAST();

		if (considerBinding(resolveBinding(newOperand))) {
			MethodInvocation newGetter= newGetter((Expression) ASTNode.copySubtree(ast, receiver));
			fRewriter.replace(operand, newGetter, createGroupDescription(READ_ACCESS));
		} else {
			newOperand.accept(new ReplaceFieldWithGetter());
			Expression newOperandTarget= (Expression) fRewriter.createMoveTarget(newOperand);
			fRewriter.replace(operand, newOperandTarget, createGroupDescription(READ_ACCESS));
		}
	}

	private class CompareAndSetProperties {

		private boolean isRefactorable= true;
		private ArrayList<Boolean> nodeFitsCompareAndSet;
		private ArrayList<ASTNode> nodes;
		private Expression setExpression= null;
		private Expression compareExpression= null;

		public CompareAndSetProperties() {
			isRefactorable= true;
			nodes= new ArrayList<ASTNode>();
			nodeFitsCompareAndSet= new ArrayList<Boolean>();
		}

		public boolean argsAreAtomicAccesses() {
			return (compareExpression != null) && (setExpression != null)
					&& (isAnAtomicAccess(compareExpression)) && (isAnAtomicAccess(setExpression));
		}

		public boolean ifStatementIsRefactorableIntoCompareAndSet() {
			if ((!isRefactorable) || (nodes.size() != 2)) {
				return false;
			}
			if (!(nodeFitsCompareAndSet.get(0).booleanValue()) || !(nodeFitsCompareAndSet.get(1).booleanValue())) {
				return false;
			}
			ASTNode firstNode= nodes.get(0);
			ASTNode secondNode= nodes.get(1);
			boolean oneIsAnAssignment= (firstNode instanceof Assignment) != (secondNode instanceof Assignment);
			boolean oneIsAnInfixExpression= (firstNode instanceof InfixExpression) != (secondNode instanceof InfixExpression);

			if (!(oneIsAnAssignment && oneIsAnInfixExpression)) {
				return false;
			}
			return true;
		}
	}

	private class ReplaceFieldWithGetter extends ASTVisitor {

		@Override
		public boolean visit(SimpleName simpleName) {

			if ((considerBinding(resolveBinding(simpleName))) && (!simpleName.isDeclaration())) {
				AST ast= simpleName.getAST();
				MethodInvocation methodInvocation= newGetter((Expression) ASTNode.copySubtree(ast, simpleName));
				fRewriter.replace(simpleName, methodInvocation, createGroupDescription(READ_ACCESS));
			}
			return true;
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

	private boolean findFieldInExtOperandsAndRemoveFirst(InfixExpression infixExpression) {

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

	private void replaceFieldRefsWithGettersInOperands(Assignment node) {

		InfixExpression infixExpression= (InfixExpression) node.getRightHandSide();
		Expression receiver= getReceiver(node.getLeftHandSide());
		Expression leftOperand= infixExpression.getLeftOperand();
		Expression rightOperand= infixExpression.getRightOperand();

		Expression newLeftOperand= getOperandWithGetters(leftOperand, receiver);
		Expression newRightOperand= getOperandWithGetters(rightOperand, receiver);

		fRewriter.replace(rightOperand, newRightOperand, createGroupDescription(WRITE_ACCESS));
		fRewriter.replace(leftOperand, newLeftOperand, createGroupDescription(WRITE_ACCESS));
	}

	private Expression getOperandWithGetters(Expression operand, Expression reciever) {

		AST ast= operand.getAST();
		Expression newOperand= null;

		if (considerBinding(resolveBinding(operand))) {
			if (reciever != null) {
				newOperand= newGetter((Expression) ASTNode.copySubtree(ast, reciever));
			}
		} else {
			operand.accept(new ReplaceFieldWithGetter());
			newOperand= (Expression) fRewriter.createMoveTarget(operand);
		}
		return newOperand;
	}

	private boolean isRefactorableForCompareAndSet(ASTNode node, IfStatement ifStatement) {

		boolean nodeIsRefactorable= false;
		if (node instanceof Assignment) {
			nodeIsRefactorable= checkIfAssignmentNodeIsRefactorableIntoCompareAndSet(node, ifStatement);
		} else if (node instanceof InfixExpression) {
			nodeIsRefactorable= checkIfInfixExpressionNodeIsRefactorableIntoCompareAndSet(node, ifStatement);
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

	private void removeSynchronizedBlock(ASTNode node, Expression invocation, String accessType, Statement syncStatement) {

		AST ast= node.getAST();
		Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);

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

	private void removeSynchronizedModifier(MethodDeclaration methodDecl) {

		int modifiers= methodDecl.getModifiers();
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

		fTodoCommenter.addCommentBeforeNode(node, ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically);
		createUnAtomicOperationWarning(node);
	}

	private void createUnAtomicOperationWarning(ASTNode node) {

		String message= new String(
				ConcurrencyRefactorings.BeginSingleQuote +
						node.toString() +
						ConcurrencyRefactorings.EndSingleQuote +
						ConcurrencyRefactorings.AtomicInteger_cannot_be_refactored_into_an_atomic_operation);
		createWarningStatus(message);
	}

	private ListRewrite insertLineCommentBeforeNode(String comment, ASTNode node, ASTNode body, ChildListPropertyDescriptor descriptor) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(comment, ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(body, descriptor);
		rewriter.insertBefore(lineComment, node, createGroupDescription(COMMENT));
		return rewriter;
	}

	private void insertStatementsInBlockAreNotSynchronizedComment(Block syncBody) {

		if (!fBlocksWithComments.contains(syncBody)) {
			fBlocksWithComments.add(syncBody);

			insertLineCommentBeforeNode(
					ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_block,
					(ASTNode) syncBody.statements().get(0), syncBody, Block.STATEMENTS_PROPERTY);
		}
	}

	private void insertStatementsInMethodAreNotSynchronizedComment(ASTNode node, MethodDeclaration methodDecl) {

		if (!fMethodsWithComments.contains(methodDecl)) {
			TypeDeclaration typeDeclaration= (TypeDeclaration) ASTNodes.getParent(node, TypeDeclaration.class);

			if (typeDeclaration != null) {
				fMethodsWithComments.add(methodDecl);
				insertLineCommentBeforeNode(
						ConcurrencyRefactorings.AtomicInteger_todo_comment_statements_not_properly_synchronized_method,
						methodDecl, typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
			}
		}
	}

	private void insertStatementsInMethodAreNotSynchronizedComment(IfStatement ifStatement) {

		MethodDeclaration methodDecl= (MethodDeclaration) ASTNodes.getParent(ifStatement, MethodDeclaration.class);
		if (methodDecl != null) {
			int modifiers= methodDecl.getModifiers();

			if (Modifier.isSynchronized(modifiers)) {
				List<Statement> methodBodyStatements= methodDecl.getBody().statements();
				if (methodBodyStatements.size() == 1) {
					insertStatementsInMethodAreNotSynchronizedComment(ifStatement, methodDecl);
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

	private MethodInvocation newGetter(Expression expression) {

		AST ast= expression.getAST();
		MethodInvocation methodInvocation= ast.newMethodInvocation();

		methodInvocation.setExpression(expression);
		methodInvocation.setName(ast.newSimpleName(ConcurrencyRefactorings.AtomicInteger_get));
		return methodInvocation;
	}

	private MethodInvocation getTypeConversionInvocation(String invocationName, SimpleName simpleName) {

		AST ast= simpleName.getAST();
		MethodInvocation methodInvocation= ast.newMethodInvocation();

		methodInvocation.setName(ast.newSimpleName(invocationName));
		methodInvocation.setExpression(ast.newSimpleName(fFieldBinding.getName()));
		return methodInvocation;
	}

	private boolean statementIsRefactorableIntoCompareAndSet(Statement statement) {

		if (statement instanceof IfStatement) {
			if (fIfStatements.containsKey(statement)) {
				CompareAndSetProperties properties= fIfStatements.get(statement);
				return (properties.isRefactorable);
			}
		}
		return false;
	}

	private boolean checkIfParentIsExpressionStatement(ASTNode node) {

		ASTNode parent= node.getParent();
		return parent instanceof ExpressionStatement;
	}

	private boolean isAnAtomicAccess(Expression expression) {

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