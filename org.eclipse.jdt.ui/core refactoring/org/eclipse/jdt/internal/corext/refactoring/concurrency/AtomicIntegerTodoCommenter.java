package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.Iterator;
import java.util.List;

import org.eclipse.text.edits.TextEditGroup;

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
public class AtomicIntegerTodoCommenter {

	private static final String WRITE_ACCESS= ConcurrencyRefactorings.ConcurrencyRefactorings_write_access;
	private static final String COMMENT= ConcurrencyRefactorings.ConcurrencyRefactorings_comment;

	private ASTRewrite fRewriter;
	private List<TextEditGroup> fGroupDescriptions;

	public AtomicIntegerTodoCommenter(ASTRewrite rewriter, List<TextEditGroup> groupDescriptions) {

		fRewriter= rewriter;
		fGroupDescriptions= groupDescriptions;
	}

	public void addCommentBeforeNode(ASTNode node, String message) {
		insertTodoComment(node, message);
	}

	private void insertTodoComment(ASTNode node, String message) {

		if (insertTodoCommentIfStatementNoBlock(node, message)
				|| insertTodoCommentForStatementNoBlock(node, message)
				|| insertTodoCommentEnhancedForStatementNoBlock(node, message)
				|| insertTodoCommentDoStatementNoBlock(node, message)
				|| insertTodoCommentWhileStatementNoBlock(node, message)
				|| insertTodoCommentSwitchStatement(node, message)) {
			return;
		}
		defaultTodoCommentInsertion(node, message);
	}

	private boolean insertTodoCommentSwitchStatement(ASTNode node, String message) {

		SwitchStatement switchStatement= (SwitchStatement) ASTNodes.getParent(node, SwitchStatement.class);

		if (switchStatement != null) {
			List<Statement> statements= switchStatement.statements();
			for (Iterator<Statement> iterator= statements.iterator(); iterator.hasNext();) {
				Statement statement= iterator.next();
				ASTNode statementParent= ASTNodes.getParent(node, Statement.class);
				if (ASTMatcher.safeEquals(statement, statementParent)) {
					ListRewrite listRewrite= fRewriter.getListRewrite(switchStatement, SwitchStatement.STATEMENTS_PROPERTY);
					LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(message, ASTNode.LINE_COMMENT);
					listRewrite.insertBefore(lineComment, statement, createGroupDescription(COMMENT));
					return true;
				}
			}
		}
		return false;
	}

	private boolean insertTodoCommentWhileStatementNoBlock(ASTNode node, String message) {

		WhileStatement whileStatement= (WhileStatement) ASTNodes.getParent(node, WhileStatement.class);
		if (whileStatement != null) {
			Statement body= whileStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body, message);
				return true;
			}
		}
		return false;
	}

	private boolean insertTodoCommentDoStatementNoBlock(ASTNode node, String message) {

		DoStatement doStatement= (DoStatement) ASTNodes.getParent(node, DoStatement.class);
		if (doStatement != null) {
			Statement body= doStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body, message);
				return true;
			}
		}
		return false;
	}

	private boolean insertTodoCommentEnhancedForStatementNoBlock(ASTNode node, String message) {

		EnhancedForStatement enhancedForStatement= (EnhancedForStatement) ASTNodes.getParent(node, EnhancedForStatement.class);

		if (enhancedForStatement != null) {
			Statement body= enhancedForStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body, message);
				return true;
			}
		}
		return false;
	}

	private boolean insertTodoCommentForStatementNoBlock(ASTNode node, String message) {

		ForStatement forStatement= (ForStatement) ASTNodes.getParent(node, ForStatement.class);

		if (forStatement != null) {
			Statement body= forStatement.getBody();
			if (!(body instanceof Block)) {
				makeNewBlockInsertCommentCreateWarning(body, message);
				return true;
			}
		}
		return false;
	}

	private boolean insertTodoCommentIfStatementNoBlock(ASTNode node, String message) {

		IfStatement ifStatement= (IfStatement) ASTNodes.getParent(node, IfStatement.class);
		if (ifStatement != null) {
			Statement elseStatement= ifStatement.getElseStatement();
			Statement thenStatement= ifStatement.getThenStatement();
			Statement statement= (Statement) ASTNodes.getParent(node, Statement.class);

			if (thenStatement != null) {
				if (!(thenStatement instanceof Block)) {
					if (ASTMatcher.safeEquals(thenStatement, statement)) {
						makeNewBlockInsertCommentCreateWarning(thenStatement, message);
						return true;
					}
				}
			}
			if (elseStatement != null) {
				if (!(elseStatement instanceof Block)) {
					if (ASTMatcher.safeEquals(elseStatement, statement)) {
						makeNewBlockInsertCommentCreateWarning(elseStatement, message);
						return true;
					}
				}
			}
		}
		return false;
	}

	private void defaultTodoCommentInsertion(ASTNode node, String message) {

		ASTNode body= ASTNodes.getParent(node, Block.class);
		ASTNode statement= ASTNodes.getParent(node, Statement.class);

		if ((statement != null) && (body != null)) {
			insertLineCommentBeforeNode(message, statement, body, Block.STATEMENTS_PROPERTY);
		}
	}

	private void makeNewBlockInsertCommentCreateWarning(Statement body, String message) {

		AST ast= body.getAST();
		Block newBlock= ast.newBlock();
		ASTNode createMoveTarget= fRewriter.createMoveTarget(body);
		fRewriter.replace(body, newBlock, createGroupDescription(WRITE_ACCESS));
		insertLineCommentBeforeMoveTarget(newBlock, createMoveTarget, message);
	}

	private void insertLineCommentBeforeMoveTarget(Block newBlock, ASTNode createMoveTarget, String message) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(message + ConcurrencyRefactorings.newLine,
				ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(newBlock, Block.STATEMENTS_PROPERTY);
		rewriter.insertLast(createMoveTarget, createGroupDescription(WRITE_ACCESS));
		rewriter.insertFirst(lineComment, createGroupDescription(COMMENT));
	}

	private ListRewrite insertLineCommentBeforeNode(String message, ASTNode node, ASTNode body, ChildListPropertyDescriptor descriptor) {

		LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(message, ASTNode.LINE_COMMENT);
		ListRewrite rewriter= fRewriter.getListRewrite(body, descriptor);
		rewriter.insertBefore(lineComment, node, createGroupDescription(COMMENT));
		return rewriter;
	}

	private TextEditGroup createGroupDescription(String name) {

		TextEditGroup result= new TextEditGroup(name);

		fGroupDescriptions.add(result);
		return result;
	}
}
