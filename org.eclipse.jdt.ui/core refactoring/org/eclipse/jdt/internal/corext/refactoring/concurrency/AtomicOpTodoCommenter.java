package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.Iterator;
import java.util.List;

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

/**
 * This class is responsible for inserting todo line comments to signify that an operation cannot be
 * executed atomically. Since these comments can be applied in nearly any environment, be it a block
 * or the body of a switch statement, this class must be able to accomodate all possibilities to
 * avoid runtime errors.
 *
 * @author Alexandria Shearer
 *
 */
public class AtomicOpTodoCommenter {

	private static final String WRITE_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_write_access;
	private static final String COMMENT= ConcurrencyRefactorings.ConcurrencyRefactorings_comment;

	private ASTRewrite fRewriter;
	private RefactoringStatus fStatus;
	private List<TextEditGroup> fGroupDescriptions;

	public AtomicOpTodoCommenter(ASTRewrite rewriter, RefactoringStatus status, List<TextEditGroup> groupDescriptions) {

		fRewriter= rewriter;
		fStatus= status;
		fGroupDescriptions= groupDescriptions;
	}

	public void addCommentBeforeNode(ASTNode node) {
		insertAtomicOpTodoComment(node);
	}

	private void insertAtomicOpTodoComment(ASTNode node) {

		if (insertAtomicOpTodoCommentIfStatementNoBlock(node)
				|| insertAtomicOpTodoCommentForStatementNoBlock(node)
				|| insertAtomicOpTodoCommentEnhancedForStatementNoBlock(node)
				|| insertAtomicOpTodoCommentDoStatementNoBlock(node)
				|| insertAtomicOpTodoCommentWhileStatementNoBlock(node)
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

	private boolean insertAtomicOpTodoCommentWhileStatementNoBlock(ASTNode node) {

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

	private boolean insertAtomicOpTodoCommentDoStatementNoBlock(ASTNode node) {

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

	private boolean insertAtomicOpTodoCommentEnhancedForStatementNoBlock(ASTNode node) {

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

	private boolean insertAtomicOpTodoCommentForStatementNoBlock(ASTNode node) {

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

	private boolean insertAtomicOpTodoCommentIfStatementNoBlock(ASTNode node) {

		IfStatement ifStatement= (IfStatement) ASTNodes.getParent(node, IfStatement.class);
		if (ifStatement != null) {
			Statement elseStatement= ifStatement.getElseStatement();
			Statement thenStatement= ifStatement.getThenStatement();
			Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);

			if (thenStatement != null) {
				if (!(thenStatement instanceof Block)) {
					if (ASTMatcher.safeEquals(thenStatement, statement)) {
						makeNewBlockInsertCommentCreateWarning(thenStatement);
						return true;
					}
				}
			}
			if (elseStatement != null) {
				if (!(elseStatement instanceof Block)) {
					if (ASTMatcher.safeEquals(elseStatement, statement)) {
						makeNewBlockInsertCommentCreateWarning(elseStatement);
						return true;
					}
				}
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
