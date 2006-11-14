/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringChangeDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;

import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptor;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.rename.TempDeclarationFinder;
import org.eclipse.jdt.internal.corext.refactoring.rename.TempOccurrenceAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

public class InlineTempRefactoring extends ScriptableRefactoring {

	private int fSelectionStart;
	private int fSelectionLength;
	private ICompilationUnit fCu;
	
	//the following fields are set after the construction
	private VariableDeclaration fVariableDeclaration;
	private CompilationUnit fASTRoot;

	/**
	 * Creates a new inline constant refactoring.
	 * @param unit the compilation unit, or <code>null</code> if invoked by scripting
	 * @param selectionStart
	 * @param selectionLength
	 */
	public InlineTempRefactoring(ICompilationUnit unit, int selectionStart, int selectionLength) {
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);
		fSelectionStart= selectionStart;
		fSelectionLength= selectionLength;
		fCu= unit;
		
		fASTRoot= null;
		fVariableDeclaration= null;
	}
	
	public InlineTempRefactoring(VariableDeclaration decl) {
		fVariableDeclaration= decl;
		ASTNode astRoot= decl.getRoot();
		Assert.isTrue(astRoot instanceof CompilationUnit);
		fASTRoot= (CompilationUnit) astRoot;
		Assert.isTrue(fASTRoot.getJavaElement() instanceof ICompilationUnit);
		
		fSelectionStart= decl.getStartPosition();
		fSelectionLength= decl.getLength();
		fCu= (ICompilationUnit) fASTRoot.getJavaElement();
	}
	
	public RefactoringStatus checkIfTempSelected() {
		VariableDeclaration decl= getVariableDeclaration();
		if (decl == null) {
			return CodeRefactoringUtil.checkMethodSyntaxErrors(fSelectionStart, fSelectionLength, getASTRoot(), RefactoringCoreMessages.InlineTempRefactoring_select_temp);
		}
		if (decl.getParent() instanceof FieldDeclaration) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InlineTemRefactoring_error_message_fieldsCannotBeInlined); 
		}
		return new RefactoringStatus();
	}	
	
	private CompilationUnit getASTRoot() {
		if (fASTRoot == null) {
			fASTRoot= RefactoringASTParser.parseWithASTProvider(fCu, true, null);
		}
		return fASTRoot;
	}
	
	public VariableDeclaration getVariableDeclaration() {
		if (fVariableDeclaration == null) {
			fVariableDeclaration= TempDeclarationFinder.findTempDeclaration(getASTRoot(), fSelectionStart, fSelectionLength);
		}
		return fVariableDeclaration;
	}
	
	/*
	 * @see IRefactoring#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.InlineTempRefactoring_name; 
	}
	
	/*
	 * @see Refactoring#checkActivation(IProgressMonitor)
	 */
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 1); //$NON-NLS-1$
			
			RefactoringStatus result= Checks.validateModifiesFiles(ResourceUtil.getFiles(new ICompilationUnit[]{fCu}), getValidationContext());
			if (result.hasFatalError())
				return result;
					
			VariableDeclaration declaration= getVariableDeclaration();
			
			result.merge(checkSelection(declaration));
			if (result.hasFatalError())
				return result;
			
			result.merge(checkInitializer(declaration));	
			return result;
		} finally {
			pm.done();
		}	
	}

    private RefactoringStatus checkInitializer(VariableDeclaration decl) {
		if (decl.getInitializer().getNodeType() == ASTNode.NULL_LITERAL)
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InlineTemRefactoring_error_message_nulLiteralsCannotBeInlined);
		return null;
	}

	private RefactoringStatus checkSelection(VariableDeclaration decl) {
		ASTNode parent= decl.getParent();
		if (parent instanceof MethodDeclaration) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InlineTempRefactoring_method_parameter); 
		}
		
		if (parent instanceof CatchClause) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InlineTempRefactoring_exceptions_declared); 
		}
		
		if (parent instanceof VariableDeclarationExpression && parent.getLocationInParent() == ForStatement.INITIALIZERS_PROPERTY) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InlineTempRefactoring_for_initializers); 
		}
		
		if (decl.getInitializer() == null) {
			String message= Messages.format(RefactoringCoreMessages.InlineTempRefactoring_not_initialized, decl.getName().getIdentifier());
			return RefactoringStatus.createFatalErrorStatus(message);
		}	
				
		return checkAssignments(decl);
	}
	
	private RefactoringStatus checkAssignments(VariableDeclaration decl) {
		TempAssignmentFinder assignmentFinder= new TempAssignmentFinder(decl);
		getASTRoot().accept(assignmentFinder);
		if (!assignmentFinder.hasAssignments())
			return new RefactoringStatus();
		ASTNode firstAssignment= assignmentFinder.getFirstAssignment();
		int start= firstAssignment.getStartPosition();
		int length= firstAssignment.getLength();
		ISourceRange range= new SourceRange(start, length);
		RefactoringStatusContext context= JavaStatusContext.create(fCu, range);	
		String message= Messages.format(RefactoringCoreMessages.InlineTempRefactoring_assigned_more_once, decl.getName().getIdentifier());
		return RefactoringStatus.createFatalErrorStatus(message, context);
	}
	
	/*
	 * @see Refactoring#checkInput(IProgressMonitor)
	 */
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 1); //$NON-NLS-1$
			return new RefactoringStatus();
		} finally {
			pm.done();
		}	
	}
	
	//----- changes

	public Change createChange(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask(RefactoringCoreMessages.InlineTempRefactoring_preview, 2);
			final Map arguments= new HashMap();
			String project= null;
			IJavaProject javaProject= fCu.getJavaProject();
			if (javaProject != null)
				project= javaProject.getElementName();
			
			VariableDeclaration variableDeclaration= getVariableDeclaration();
			
			final IVariableBinding binding= variableDeclaration.resolveBinding();
			String text= null;
			final IMethodBinding method= binding.getDeclaringMethod();
			if (method != null)
				text= BindingLabelProvider.getBindingLabel(method, JavaElementLabels.ALL_FULLY_QUALIFIED);
			else
				text= '{' + JavaElementLabels.ELLIPSIS_STRING + '}';
			final String description= Messages.format(RefactoringCoreMessages.InlineTempRefactoring_descriptor_description_short, binding.getName());
			final String header= Messages.format(RefactoringCoreMessages.InlineTempRefactoring_descriptor_description, new String[] { BindingLabelProvider.getBindingLabel(binding, JavaElementLabels.ALL_FULLY_QUALIFIED), text});
			final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
			comment.addSetting(Messages.format(RefactoringCoreMessages.InlineTempRefactoring_original_pattern, BindingLabelProvider.getBindingLabel(binding, JavaElementLabels.ALL_FULLY_QUALIFIED)));
			final JDTRefactoringDescriptor descriptor= new JDTRefactoringDescriptor(IJavaRefactorings.INLINE_LOCAL_VARIABLE, project, description, comment.asString(), arguments, RefactoringDescriptor.NONE);
			arguments.put(JDTRefactoringDescriptor.ATTRIBUTE_INPUT, descriptor.elementToHandle(fCu));
			arguments.put(JDTRefactoringDescriptor.ATTRIBUTE_SELECTION, String.valueOf(fSelectionStart) + ' ' + String.valueOf(fSelectionLength));
			
			CompilationUnitRewrite cuRewrite= new CompilationUnitRewrite(fCu, fASTRoot);
			
			inlineTemp(cuRewrite, variableDeclaration);
			removeTemp(cuRewrite, variableDeclaration);
			
			final CompilationUnitChange result= cuRewrite.createChange(RefactoringCoreMessages.InlineTempRefactoring_inline, false, new SubProgressMonitor(pm, 1));
			result.setDescriptor(new RefactoringChangeDescriptor(descriptor));
			return result;
		} finally {
			pm.done();
		}
	}

	private void inlineTemp(CompilationUnitRewrite cuRewrite, VariableDeclaration variableDeclaration) throws JavaModelException {
		SimpleName[] references= getReferences(variableDeclaration);

		TextEditGroup groupDesc= cuRewrite.createGroupDescription(RefactoringCoreMessages.InlineTempRefactoring_inline_edit_name);
		ASTRewrite rewrite= cuRewrite.getASTRewrite();

		for (int i= 0; i < references.length; i++){
			SimpleName curr= references[i];
			ASTNode initializerCopy= getInitializerSource(cuRewrite, needsBrackets(curr, variableDeclaration));
			rewrite.replace(curr, initializerCopy, groupDesc);
		}
	}
	
    private boolean needsBrackets(SimpleName name, VariableDeclaration variableDeclaration) {
		Expression initializer= variableDeclaration.getInitializer();
		if (initializer instanceof Assignment) //for esthetic reasons
			return true;
    		
    	return ASTNodes.substituteMustBeParenthesized(initializer, name);
    }
    

	private void removeTemp(CompilationUnitRewrite cuRewrite, VariableDeclaration variableDeclaration) throws JavaModelException {
		TextEditGroup groupDesc= cuRewrite.createGroupDescription(RefactoringCoreMessages.InlineTempRefactoring_remove_edit_name);
		ASTNode parent= variableDeclaration.getParent();
		ASTRewrite rewrite= cuRewrite.getASTRewrite();
		if (parent instanceof VariableDeclarationStatement && ((VariableDeclarationStatement) parent).fragments().size() == 1) {
			rewrite.remove(parent, groupDesc);
		} else {
			rewrite.remove(variableDeclaration, groupDesc);
		}
	}
	
	private Expression getInitializerSource(CompilationUnitRewrite rewrite, boolean brackets) throws JavaModelException {
		Expression copy= getModifiedInitializerSource(rewrite);
		if (brackets) {
			ParenthesizedExpression parentExpr= rewrite.getAST().newParenthesizedExpression();
			parentExpr.setExpression(copy);
			return parentExpr;
		}
		return copy;
	}
	
	private Expression getModifiedInitializerSource(CompilationUnitRewrite rewrite) throws JavaModelException {
		VariableDeclaration varDecl= getVariableDeclaration();
		
		Expression copy= (Expression) rewrite.getASTRewrite().createCopyTarget(varDecl.getInitializer());
		if (copy instanceof ArrayInitializer) {
			ITypeBinding typeBinding= varDecl.resolveBinding().getType();
			ArrayType newType= (ArrayType) rewrite.getImportRewrite().addImport(typeBinding, rewrite.getAST());
			
			ArrayCreation newArrayCreation= rewrite.getAST().newArrayCreation();
			newArrayCreation.setType(newType);
			newArrayCreation.setInitializer((ArrayInitializer) copy);
			return newArrayCreation;
		}
		return copy;
	}

	private SimpleName[] getReferences(VariableDeclaration varDecl) {
		TempOccurrenceAnalyzer analyzer= new TempOccurrenceAnalyzer(varDecl, false);
		analyzer.perform();
		return analyzer.getReferenceNodes();
	}

	public RefactoringStatus initialize(final RefactoringArguments arguments) {
		if (arguments instanceof JavaRefactoringArguments) {
			final JavaRefactoringArguments extended= (JavaRefactoringArguments) arguments;
			final String selection= extended.getAttribute(JDTRefactoringDescriptor.ATTRIBUTE_SELECTION);
			if (selection != null) {
				int offset= -1;
				int length= -1;
				final StringTokenizer tokenizer= new StringTokenizer(selection);
				if (tokenizer.hasMoreTokens())
					offset= Integer.valueOf(tokenizer.nextToken()).intValue();
				if (tokenizer.hasMoreTokens())
					length= Integer.valueOf(tokenizer.nextToken()).intValue();
				if (offset >= 0 && length >= 0) {
					fSelectionStart= offset;
					fSelectionLength= length;
				} else
					return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { selection, JDTRefactoringDescriptor.ATTRIBUTE_SELECTION}));
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JDTRefactoringDescriptor.ATTRIBUTE_SELECTION));
			final String handle= extended.getAttribute(JDTRefactoringDescriptor.ATTRIBUTE_INPUT);
			if (handle != null) {
				final IJavaElement element= JDTRefactoringDescriptor.handleToElement(extended.getProject(), handle, false);
				if (element == null || !element.exists() || element.getElementType() != IJavaElement.COMPILATION_UNIT)
					return createInputFatalStatus(element, IJavaRefactorings.INLINE_LOCAL_VARIABLE);
				else {
					fCu= (ICompilationUnit) element;
		        	if (checkIfTempSelected().hasFatalError())
						return createInputFatalStatus(element, IJavaRefactorings.INLINE_LOCAL_VARIABLE);
				}
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JDTRefactoringDescriptor.ATTRIBUTE_INPUT));
		} else
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments);
		return new RefactoringStatus();
	}
}
