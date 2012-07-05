package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
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
	
	private ICompilationUnit fCUnit;
	private IVariableBinding fFieldBinding;
	private ITypeBinding fDeclaringClassBinding;
	private ASTRewrite fRewriter;
	private ImportRewrite fImportRewriter;
	private List<TextEditGroup> fGroupDescriptions;
	private boolean fIsFieldFinal;
	private RefactoringStatus fStatus;

	public AccessAnalyzerForAtomicInteger(
			ConvertToAtomicIntegerRefactoring refactoring, 
			ICompilationUnit unit, IVariableBinding field, 
			ITypeBinding declaringClass, ASTRewrite rewriter, 
			ImportRewrite importRewrite) {
		
		fCUnit= unit;
		fFieldBinding= field.getVariableDeclaration();
		fDeclaringClassBinding= declaringClass;
		fRewriter= rewriter;
		fImportRewriter= importRewrite;
		fGroupDescriptions= new ArrayList<TextEditGroup>();
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
	
		Expression invocation= null;
		
		if ((!node.isDeclaration()) && (considerBinding(resolveBinding(node)))) {
			invocation= (MethodInvocation) fRewriter.createStringPlaceholder(
					fFieldBinding.getName() + ".get()", ASTNode.METHOD_INVOCATION); //$NON-NLS-1$
			if (!(checkSynchronizedBlock(node, invocation, READ_ACCESS) || 
					checkSynchronizedMethod(node, invocation, READ_ACCESS))) {
				fRewriter.replace(node, invocation, createGroupDescription(READ_ACCESS));
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
		ASTNode statement= getParent(node, Statement.class);
		// if parent is not an instance of ExpressionStatement . . . 	
		if (!checkParent(node)) {
			
			if (statement instanceof ReturnStatement) {
				Block body= (Block) getParent(node, Block.class);
				AST ast= node.getAST();
				
				MethodInvocation getInvocation= ast.newMethodInvocation();
				MethodInvocation setInvocation=	ast.newMethodInvocation();
				
				setInvocation.setName(ast.newSimpleName("set")); //$NON-NLS-1$
				getInvocation.setName(ast.newSimpleName("get")); //$NON-NLS-1$
				
				Expression receiver= getReceiver(lhs);
				if (receiver != null) {
					// VIP!! Here we use node.coySubtree because the expresion/arguments might be overriden by the later code.
					// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
					// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
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
					fStatus.addWarning("Synchronized block contains a return statement with an assigment.  " + //$NON-NLS-1$
							"Cannot remove the synchronized modifier without introducing an unsafe thread environment."); //$NON-NLS-1$
				} else if (checkSynchronizedMethodForReturnStatement(node)) {
					fStatus.addWarning("Synchronized method contains a return statement with an assigment.  " + //$NON-NLS-1$
							"Cannot remove the synchronized modifier without introducing an unsafe thread environment."); //$NON-NLS-1$
				}
				return true;
			}
		}
		if (!fIsFieldFinal) {
			// Write access.
			AST ast= node.getAST();	
			MethodInvocation invocation= ast.newMethodInvocation();
			invocation.setName(ast.newSimpleName("set")); //$NON-NLS-1$
			Expression receiver= getReceiver(lhs);
			if (receiver != null) {
				// VIP!! Here we use node.coySubtree because the expresion/arguments might be overriden by the later code.
				// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
				// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
				invocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver)); 
			}
			List<Expression> arguments= invocation.arguments();
			// VIP!! Here we use node.coySubtree because the expresion/arguments might be overriden by the later code.
			// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
			// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
			Expression copyRHS= (Expression) ASTNode.copySubtree(ast, node.getRightHandSide());
			
			if (node.getOperator() == Assignment.Operator.ASSIGN) {
					
				Expression rightHandSide= node.getRightHandSide();
				
				if (rightHandSide instanceof InfixExpression) {
					
					InfixExpression infixExpression= (InfixExpression) rightHandSide;
					Expression leftOperand= infixExpression.getLeftOperand();
					
						if ((considerBinding(resolveBinding(leftOperand)))) {
							Operator operator= infixExpression.getOperator();
							if (operator  == InfixExpression.Operator.PLUS) {
								needToVisitRHS= false;
								invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
								arguments.add((Expression) fRewriter.createCopyTarget(infixExpression.getRightOperand()));
							} else if (operator == InfixExpression.Operator.MINUS) {
								needToVisitRHS= false;
								invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
								arguments.add(createNegativeExpression(infixExpression.getRightOperand()));
							} else {
								createUnsafeOperatorWarning(node);
								ASTNode statementAbove= getParent(node, Statement.class);
								Block body= (Block) getParent(node, Block.class);
								
								MethodInvocation getInvocation= ast.newMethodInvocation();
								getInvocation.setName(ast.newSimpleName("get")); //$NON-NLS-1$
								if (receiver != null) {
									// VIP!! Here we use node.coySubtree because the expresion/arguments might be overriden by the later code.
									// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
									// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
									getInvocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver)); 
								}
								List<Expression> setArguments= invocation.arguments();
								// VIP!! Here we use node.coySubtree because the expresion/arguments might be overriden by the later code.
								// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
								// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
								//Expression setCcopyRHS= (Expression) ASTNode.copySubtree(ast, node.getRightHandSide());
								InfixExpression setInfixExpression= ast.newInfixExpression();
								
								setInfixExpression.setOperator(operator);
								setInfixExpression.setLeftOperand(getInvocation);
								setInfixExpression.setRightOperand((Expression) ASTNode.copySubtree(ast, infixExpression.getRightOperand()));
								setArguments.add(setInfixExpression);
								ExpressionStatement setInvocationStatement= ast.newExpressionStatement(invocation);
									
								String todoComment= new String("// TODO The operations below cannot be executed atomically."); //$NON-NLS-1$
								LineComment lc= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
								ListRewrite lr= fRewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
								lr.insertBefore(lc, statementAbove, createGroupDescription(COMMENT));
								lr.replace(statementAbove, setInvocationStatement, createGroupDescription(READ_AND_WRITE_ACCESS));
								needToVisitRHS= false;
							}
						}
				}		
				if (needToVisitRHS) {
					arguments.add(copyRHS);
				}
			}
			if (node.getOperator() != Assignment.Operator.ASSIGN) {
				if (node.getOperator() == Assignment.Operator.PLUS_ASSIGN) {
					arguments.add(copyRHS);
					// This is the compound assignment case: field+= 10;=>
					// field.addAndGet(10);
					invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
				} else if (node.getOperator() == Assignment.Operator.MINUS_ASSIGN) {
					// This is the compound assignment case: field -= 10;=>
					// field.addAndGet(-10);
					invocation.setName(ast.newSimpleName("addAndGet")); //$NON-NLS-1$
					PrefixExpression negativeExpression= createNegativeExpression(node.getRightHandSide());
					arguments.add(negativeExpression);
				} else {
					createUnsafeOperatorWarning(node);
					ASTNode statementAbove= getParent(node, Statement.class);
					Block body= (Block) getParent(node, Block.class);
					MethodInvocation getInvocation= ast.newMethodInvocation();
					getInvocation.setName(ast.newSimpleName("get")); //$NON-NLS-1$
					if (receiver != null) {
						// VIP!! Here we use node.coySubtree because the expresion/arguments might be overriden by the later code.
						// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
						// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
						getInvocation.setExpression((Expression) ASTNode.copySubtree(ast, receiver)); 
					}
					List<Expression> setArguments= invocation.arguments();
					// VIP!! Here we use node.coySubtree because the expresion/arguments might be overriden by the later code.
					// If they are overriden later, using rewriter.createCopyTarget() will result in an orphan CopySourceEdit
					// without a matching CoypTargetEdit. This would lead later to a MalformedTreeException
					Expression setCopyRHS= (Expression) ASTNode.copySubtree(ast, node.getRightHandSide());
					Operator operatorFromAssignmentOperator= getOperatorFromAssignmentOperator(node.getOperator());
					InfixExpression infixExpression= ast.newInfixExpression();
					
					infixExpression.setOperator(operatorFromAssignmentOperator);
					infixExpression.setLeftOperand(getInvocation);
					infixExpression.setRightOperand(setCopyRHS);
					setArguments.add(infixExpression);
					ExpressionStatement setInvocationStatement= ast.newExpressionStatement(invocation);
					
					String todoComment= new String("// TODO The operations below cannot be executed atomically."); //$NON-NLS-1$
					LineComment lc= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
					ListRewrite lr= fRewriter.getListRewrite(body, Block.STATEMENTS_PROPERTY);
					lr.insertBefore(lc, statementAbove, createGroupDescription(COMMENT));
					lr.replace(statement, setInvocationStatement, createGroupDescription(READ_AND_WRITE_ACCESS));
				}	
			}
			if ( !(checkSynchronizedBlock(node, invocation, WRITE_ACCESS) || checkSynchronizedMethod(node, invocation, WRITE_ACCESS)) ) {
				fRewriter.replace(node, invocation, createGroupDescription(WRITE_ACCESS));
			}
		}	
		if (needToVisitRHS) {
			node.getRightHandSide().accept(this);
		}	
		return false;
	}

	private boolean checkSynchronizedBlockForReturnStatement(Assignment node) {

		ASTNode syncStatement= getParent(node, SynchronizedStatement.class);
		ASTNode methodDecl= getParent(node, MethodDeclaration.class);
		
		if (syncStatement != null) {
			Block methodBlock= ((MethodDeclaration) methodDecl).getBody();
			
			String todoComment= new String("// TODO The statements in the block below are not properly synchronized."); //$NON-NLS-1$
			LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
			ListRewrite rewriter= fRewriter.getListRewrite(methodBlock, Block.STATEMENTS_PROPERTY);
			rewriter.insertBefore(lineComment, syncStatement, createGroupDescription(COMMENT));
			return true;
		}	
		return false;
	}

	private boolean checkSynchronizedMethodForReturnStatement(Assignment node) {

		MethodDeclaration methodDecl= (MethodDeclaration) getParent(node, MethodDeclaration.class);
//		MethodDeclaration outerMethod= (MethodDeclaration) getParent(methodDecl, MethodDeclaration.class);
		TypeDeclaration typeDeclaration= (TypeDeclaration) getParent(methodDecl, TypeDeclaration.class);
		
		int modifiers= methodDecl.getModifiers();

		if (Modifier.isSynchronized(modifiers)) {
//			if (outerMethod != null) {
//				Block outerMethodBody= outerMethod.getBody();
//				String todoComment= new String("// TODO The statements in the method below are not properly synchronized."); //$NON-NLS-1$
//				LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
//				ListRewrite rewriter= fRewriter.getListRewrite(outerMethodBody, Block.STATEMENTS_PROPERTY);
//				rewriter.insertBefore(lineComment, (ASTNode) methodDecl.getBody().statements().get(0), createGroupDescription(COMMENT));
//			} else {
//				
				MethodDeclaration[] methods= typeDeclaration.getMethods();
				for (int i = 0; i < methods.length; i++) {
					if (methods[i] == methodDecl) {
						String todoComment= new String("// TODO The statements in the method below are not properly synchronized."); //$NON-NLS-1$
						LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
						ListRewrite rewriter= fRewriter.getListRewrite(typeDeclaration, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);
						rewriter.insertBefore(lineComment, methodDecl, createGroupDescription(COMMENT));
						break;
					}
				}
		//	}
//			String todoComment= new String("// TODO The statements in the method below are not properly synchronized."); //$NON-NLS-1$
//			LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
//			ListRewrite rewriter= fRewriter.getListRewrite(methodDecl.getBody(), Block.STATEMENTS_PROPERTY);
//			rewriter.insertBefore(lineComment, (ASTNode) methodBodyStatements.get(0), createGroupDescription(COMMENT)); //$NON-NLS-1$
//			for (Iterator iterator= methodBodyStatements.iterator(); iterator.hasNext();) {
//				Statement statement= (Statement) iterator.next();
//				
//			}
//			if (methodBodyStatements.size() == 1) {
//				ModifierRewrite methodRewriter= ModifierRewrite.create(fRewriter, methodDecl);
//				int synchronized1= Modifier.SYNCHRONIZED;
//				synchronized1= ~ synchronized1;
//				int newModifiersWithoutSync= modifiers & synchronized1;
//				methodRewriter.setModifiers(newModifiersWithoutSync, createGroupDescription(REMOVE_SYNCHRONIZED_MODIFIER));
//			}
			//checkMoreThanOneFieldReference((Statement)methodDecl.getBody().statements().get(0), methodDecl.getBody());
			//checkMoreThanOneFieldReference(node, methodDecl.getBody());
			//fRewriter.replace(node, invocation, createGroupDescription(accessType));
			return true;
		}
		return false;
	}

	private void createUnsafeOperatorError(Assignment node) {
		
		fStatus.addError("Cannot execute " //$NON-NLS-1$
				+ fFieldBinding.getName()
				+ ".set(" //$NON-NLS-1$
				+ fFieldBinding.getName()
				+ ".get()) " //$NON-NLS-1$
				+ "atomically. This would be required in converting " //$NON-NLS-1$
				+ node.toString()
				+ ". Consider using locks instead."); //$NON-NLS-1$
	}
	
	private void createUnsafeOperatorWarning(Assignment node) {
		
		//fStatus= new RefactoringStatus();
		fStatus.addWarning("Cannot execute " //$NON-NLS-1$
				+ fFieldBinding.getName()
				+ ".set(" //$NON-NLS-1$
				+ fFieldBinding.getName()
				+ ".get()) " //$NON-NLS-1$
				+ "atomically. This would be required in converting " //$NON-NLS-1$
				+ node.toString()
				+ ". Consider using locks instead."); //$NON-NLS-1$	
	}
	
	private void createErrorStatus(String message) {

		fStatus.addError(message);
	}

	private PrefixExpression createNegativeExpression(Expression expression) {
		
		AST ast= expression.getAST();
		PrefixExpression newPrefixExpression= ast.newPrefixExpression();
		
		newPrefixExpression.setOperator(PrefixExpression.Operator.MINUS);
		boolean needsParentheses= // ASTNodes.needsParentheses(expression); 
		needsParentheses(expression);
		ASTNode copyExpression=  ASTNode.copySubtree(ast, expression);   //fRewriter.createCopyTarget(expression);
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
//		ASTNode parent= expression.getParent();
//		if (!(parent instanceof ExpressionStatement)) {
//			fStatus.addError("Cannot convert postfix expression",  
//				JavaStatusContext.create(fCUnit, new SourceRange(expression)));
//			return false;
//		}
		AST ast= expression.getAST();
		MethodInvocation invocation= ast.newMethodInvocation();
		
		invocation.setExpression((Expression) fRewriter.createCopyTarget(operand));

		if (operator == PostfixExpression.Operator.INCREMENT) {
			invocation.setName(ast.newSimpleName("getAndIncrement")); //$NON-NLS-1$
		}
		else if (operator == PostfixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName("getAndDecrement")); //$NON-NLS-1$
		}
		if ( !(checkSynchronizedBlock(expression, invocation, POSTFIX_ACCESS) || checkSynchronizedMethod(expression, invocation, POSTFIX_ACCESS)) ) {
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
//		ASTNode parent= expression.getParent();
//		if (!(parent instanceof ExpressionStatement)) {
//			fStatus.addError("Cannot convert prefix expression",  
//				JavaStatusContext.create(fCUnit, new SourceRange(expression)));
//			return false;
//		}
		AST ast= expression.getAST();
		MethodInvocation invocation= ast.newMethodInvocation();
		
		invocation.setExpression((Expression) fRewriter.createCopyTarget(operand));

		if (operator == PrefixExpression.Operator.INCREMENT) {
			invocation.setName(ast.newSimpleName("incrementAndGet")); //$NON-NLS-1$
		}
		else if (operator == PrefixExpression.Operator.DECREMENT) {
			invocation.setName(ast.newSimpleName("decrementAndGet")); //$NON-NLS-1$
		}
		if ( !(checkSynchronizedBlock(expression, invocation, PREFIX_ACCESS) || checkSynchronizedMethod(expression, invocation, PREFIX_ACCESS)) ) {
			fRewriter.replace(expression, invocation, createGroupDescription(PREFIX_ACCESS));
		} 
		return false;
	}
	
	@Override
	public void endVisit(CompilationUnit node) {
		
		fImportRewriter.addImport("java.util.concurrent.atomic.AtomicInteger"); //$NON-NLS-1$
	}
	
	private boolean checkSynchronizedBlock(ASTNode node, Expression invocation, String accessType) {
		
		AST ast= node.getAST();
		ASTNode syncStatement= getParent(node, SynchronizedStatement.class);

		if (syncStatement != null) {
			Block syncBody= ((SynchronizedStatement) syncStatement).getBody();
			List<?> syncBodyStatements= syncBody.statements();
			if (syncBodyStatements.size() > 1) {
				fRewriter.replace(node, invocation, createGroupDescription(accessType));
				checkMoreThanOneFieldReference(node, syncBody);
			} else {
				Statement statement= (Statement) syncBodyStatements.get(0);
				if (!isReturnStatementWithIntField(statement)) {
					ExpressionStatement newExpressionStatement= ast.newExpressionStatement(invocation);
					fRewriter.replace(syncStatement, newExpressionStatement, createGroupDescription(REMOVE_SYNCHRONIZED_BLOCK));
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
		
		ASTNode enclosingStatement= getParent(node, Statement.class);
		List<Statement> statements= syncBody.statements();
		int numEntries= fStatus.getEntries().length + 1;
		
		for (Iterator<?> iterator= statements.iterator(); iterator.hasNext();) {
			Statement statement= (Statement) iterator.next();
			if (!statement.equals(enclosingStatement)){
				//fStatus.addWarning("Visited:" + statement.toString());
				statement.accept(new ASTVisitor(){
					@Override
					public boolean visit(SimpleName identifier){
						IBinding identifierBinding= resolveBinding(identifier);
						if (identifierBinding instanceof IVariableBinding) {
							IVariableBinding varBinding= (IVariableBinding) identifierBinding;
							if (varBinding.isField()) {
								RefactoringStatus warningStatus= RefactoringStatus.createWarningStatus("Synchronized block contains references to another field \"" //$NON-NLS-1$
										+ identifier.getIdentifier()
										+ "\". AtomicInteger cannot preserve invariants over two field accesses, " + //$NON-NLS-1$
												"consider using locks instead."); //$NON-NLS-1$
								RefactoringStatusEntry[] entries= fStatus.getEntries();
								boolean alreadyExistingWarning= false;
								for (int i= 0; i < entries.length; i++) {
									RefactoringStatusEntry refactoringStatusEntry= entries[i];
									if (refactoringStatusEntry.getMessage().equals(warningStatus.getMessageMatchingSeverity(RefactoringStatus.WARNING))) {
										alreadyExistingWarning= true;
									}
								}
								if (!alreadyExistingWarning) {
									fStatus.merge(warningStatus);
								}
							}
						}
						return true;
					}
				});
			}
			if (numEntries < (fStatus.getEntries().length + 1)) {
				RefactoringStatusEntry[] entries= fStatus.getEntries();	
				RefactoringStatusEntry refactoringStatusEntry= entries[(entries.length-1)];
				if (refactoringStatusEntry.getMessage().matches(
						"Synchronized block contains references to another field.*AtomicInteger cannot preserve invariants over two field accesses, " + //$NON-NLS-1$
						".*consider using locks instead.")) { //$NON-NLS-1$
					String todoComment= new String("// TODO The statement below is not properly synchronized."); //$NON-NLS-1$
					LineComment lc= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
					ListRewrite lr= fRewriter.getListRewrite(syncBody, Block.STATEMENTS_PROPERTY);
					lr.insertBefore(lc, statement, createGroupDescription(COMMENT));
				}
			}	
		}
		if (numEntries < (fStatus.getEntries().length + 1)) {
				String todoComment= new String("// TODO The statement below is not properly synchronized."); //$NON-NLS-1$
				LineComment lineComment= (LineComment) fRewriter.createStringPlaceholder(todoComment, ASTNode.LINE_COMMENT);
				ListRewrite rewriter= fRewriter.getListRewrite(syncBody, Block.STATEMENTS_PROPERTY);
				rewriter.insertBefore(lineComment, enclosingStatement, createGroupDescription(COMMENT)); //$NON-NLS-1$
		}
	}

	private boolean checkSynchronizedMethod(ASTNode node, Expression invocation, String accessType) {
		
		MethodDeclaration methodDecl= (MethodDeclaration) getParent(node, MethodDeclaration.class);
		int modifiers= methodDecl.getModifiers();

		if (Modifier.isSynchronized(modifiers)) {
			List<Statement> methodBodyStatements= methodDecl.getBody().statements();
			Statement statement= methodBodyStatements.get(0);
			if (methodBodyStatements.size() == 1) {
				if (!isReturnStatementWithIntField(statement)) {
					ModifierRewrite methodRewriter= ModifierRewrite.create(fRewriter, methodDecl);
					int synchronized1= Modifier.SYNCHRONIZED;
					synchronized1= ~ synchronized1;
					int newModifiersWithoutSync= modifiers & synchronized1;
					methodRewriter.setModifiers(newModifiersWithoutSync, createGroupDescription(REMOVE_SYNCHRONIZED_MODIFIER));
				}
			}
			checkMoreThanOneFieldReference(node, methodDecl.getBody());
			fRewriter.replace(node, invocation, createGroupDescription(accessType));
			return true;
		}
		return false;
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
	
	private boolean considerBinding(IBinding binding /*, ASTNode node*/) {
		
		if (!(binding instanceof IVariableBinding)) {
			return false;
		}
		boolean result= fFieldBinding.isEqualTo(((IVariableBinding) binding).getVariableDeclaration());
		//if (!result)
			return result;
			
//		if (binding instanceof IVariableBinding) {
//			AbstractTypeDeclaration type= (AbstractTypeDeclaration)ASTNodes.getParent(node, AbstractTypeDeclaration.class);
//			if (type != null) {
//				ITypeBinding declaringType= type.resolveBinding();
//				return Bindings.equals(fDeclaringClassBinding, declaringType);
//			}
//		}
//		return true;
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
	
	private ASTNode getParent(ASTNode node, Class<? extends ASTNode> parentClass) {
		
		do {
			node= node.getParent();
		} while (node != null && !parentClass.isInstance(node));
		return node;
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
}