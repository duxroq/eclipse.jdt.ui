package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTMatcher;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.concurrency.AccessAnalyzerForAtomicInteger.IfStatementProperties;
import org.eclipse.jdt.internal.corext.refactoring.concurrency.AccessAnalyzerForAtomicInteger.NodeFinder;

public class AtomicOpTodoCommenter {

	private static final String WRITE_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_write_access;
	private static final String COMMENT= ConcurrencyRefactorings.ConcurrencyRefactorings_comment;

	private ASTRewrite fRewriter;
	private RefactoringStatus fStatus;
	private HashMap<IfStatement, IfStatementProperties> fIfStatementsToNodes;
	private List<TextEditGroup> fGroupDescriptions;

	public AtomicOpTodoCommenter(ASTRewrite rewriter, RefactoringStatus status, HashMap<IfStatement, IfStatementProperties> ifStatementsToNodes,
			List<TextEditGroup> groupDescriptions) {

		fRewriter= rewriter;
		fStatus= status;
		fIfStatementsToNodes= ifStatementsToNodes;
		fGroupDescriptions= groupDescriptions;
	}

	public void addCommentBeforeNode(ASTNode node) {
		insertAtomicOpTodoComment(node);
	}

	private void insertAtomicOpTodoComment(ASTNode node) {

		if (insertAtomicOpTodoCommentIfStatement(node)
				|| insertAtomicOpTodoCommentForStatement(node)
				|| insertAtomicOpTodoCommentEnhancedForStatement(node)
				|| insertAtomicOpTodoCommentDoStatement(node)
				|| insertAtomicOpTodoCommentWhileStatement(node)
				|| insertAtomicOpTodoCommentSwitchStatement(node)) {
			return;
		}
		defaultAtomicOpTodoCommentInsertion(node);
	}

	private boolean insertAtomicOpTodoCommentSwitchStatement(ASTNode node) {

		SwitchStatement switchStatement= (SwitchStatement) ASTNodes.getParent(node, SwitchStatement.class);

		if (switchStatement != null) {
			List<Statement> statements= switchStatement.statements();
			for (Iterator<Statement> iterator= statements.iterator(); iterator.hasNext();) {
				Statement statement= iterator.next();
				ASTNode statementParent= ASTNodes.getParent(node, Statement.class);
				if (ASTMatcher.safeEquals(statement, statementParent)) {
					ListRewrite listRewrite= fRewriter.getListRewrite(switchStatement, SwitchStatement.STATEMENTS_PROPERTY);
					LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(
							ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically, ASTNode.LINE_COMMENT);
					listRewrite.insertBefore(lineComment, statement, createGroupDescription(COMMENT));
					createWarningStatus(node.toString() + ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
					return true;
				}
			}
		}
		return false;
	}

	private boolean insertAtomicOpTodoCommentWhileStatement(ASTNode node) {

		WhileStatement whileStatement= (WhileStatement) ASTNodes.getParent(node, WhileStatement.class);
		if (whileStatement != null) {
			Statement body= whileStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body);
				return true;
			}
		}
		return false;
	}

	private boolean insertAtomicOpTodoCommentDoStatement(ASTNode node) {

		DoStatement doStatement= (DoStatement) ASTNodes.getParent(node, DoStatement.class);
		if (doStatement != null) {
			Statement body= doStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body);
				return true;
			}
		}
		return false;
	}

	private boolean insertAtomicOpTodoCommentEnhancedForStatement(ASTNode node) {

		EnhancedForStatement enhancedForStatement= (EnhancedForStatement) ASTNodes.getParent(node, EnhancedForStatement.class);

		if (enhancedForStatement != null) {
			Statement body= enhancedForStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body);
				return true;
			}
		}
		return false;
	}

	private boolean insertAtomicOpTodoCommentForStatement(ASTNode node) {

		ForStatement forStatement= (ForStatement) ASTNodes.getParent(node, ForStatement.class);

		if (forStatement != null) {
			Statement body= forStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body);
				return true;
			}
		}
		return false;
	}

	private void defaultAtomicOpTodoCommentInsertion(ASTNode node) {

		ASTNode body= ASTNodes.getParent(node, Block.class);
		ASTNode statement= ASTNodes.getParent(node, Statement.class);

		if ((statement != null) && (body != null)) {
			insertLineCommentBeforeNode(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically,
					statement, body, Block.STATEMENTS_PROPERTY);
		}
		createWarningStatus(node.toString() + ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
	}

	private boolean insertAtomicOpTodoCommentIfStatement(ASTNode node) {

		boolean foundNode= false;
		for (Map.Entry<IfStatement, IfStatementProperties> entry : fIfStatementsToNodes.entrySet()) {
			IfStatement ifStatement= entry.getKey();
			IfStatementProperties properties= entry.getValue();
			if (properties.nodes.contains(node)) {
				foundNode= true;
				IfStatementProperties.NodeLocation location= properties.nodeLocation.get(properties.nodes.indexOf(node));
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
		return foundNode;
	}

	private void makeNewBlockInsertCommentCreateWarning(Statement body) {

		AST ast= body.getAST();
		Block newBlock= ast.newBlock();
		ASTNode createMoveTarget= fRewriter.createMoveTarget(body);
		fRewriter.replace(body, newBlock, createGroupDescription(WRITE_ACCESS));
		insertLineCommentBeforeMoveTarget(newBlock, createMoveTarget);
		createWarningStatus(ConcurrencyRefactorings.AtomicInteger_warning_cannot_be_refactored_atomically);
	}

	private void insertLineCommentBeforeMoveTarget(Block newBlock, ASTNode createMoveTarget) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(ConcurrencyRefactorings.AtomicInteger_todo_comment_op_cannot_be_executed_atomically_nl,
				ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(newBlock, Block.STATEMENTS_PROPERTY);
		rewriter.insertLast(createMoveTarget, createGroupDescription(WRITE_ACCESS));
		rewriter.insertFirst(lineComment, createGroupDescription(COMMENT));
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

	private ListRewrite insertLineCommentBeforeNode(String comment, ASTNode node, ASTNode body, ChildListPropertyDescriptor descriptor) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(comment, ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(body, descriptor);
		rewriter.insertBefore(lineComment, node, createGroupDescription(COMMENT));
		return rewriter;
	}

	private void createWarningStatus(String message) {
		fStatus.addWarning(message);
	}

	private TextEditGroup createGroupDescription(String name) {

		TextEditGroup result= new TextEditGroup(name);

		fGroupDescriptions.add(result);
		return result;
	}
}
