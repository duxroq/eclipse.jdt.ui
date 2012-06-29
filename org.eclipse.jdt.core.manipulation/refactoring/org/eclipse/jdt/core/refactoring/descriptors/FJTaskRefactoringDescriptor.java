package org.eclipse.jdt.core.refactoring.descriptors;

import java.util.Map;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.core.refactoring.descriptors.JavaRefactoringDescriptorUtil;

public class FJTaskRefactoringDescriptor extends JavaRefactoringDescriptor {

	private ICompilationUnit fUnit;

	public FJTaskRefactoringDescriptor(String id, String project,
			String description, String comment, Map arguments, int flags) {
		super(id, project, description, comment, arguments, flags);
		// TODO Auto-generated constructor stub
	}

	protected FJTaskRefactoringDescriptor(String id) {
		super(id);
		// TODO Auto-generated constructor stub
	}

	@Override
	public Refactoring createRefactoring(RefactoringStatus status)
			throws CoreException {
		// TODO Auto-generated method stub
		return super.createRefactoring(status);
	}

	@Override
	protected void populateArgumentMap() {
		// TODO Auto-generated method stub
		super.populateArgumentMap();
		JavaRefactoringDescriptorUtil.setString(fArguments, ATTRIBUTE_INPUT, "does this show up?");

	}

	@Override
	public void setComment(String comment) {
		// TODO Auto-generated method stub
		super.setComment(comment);
	}

	@Override
	public void setDescription(String description) {
		// TODO Auto-generated method stub
		super.setDescription(description);
	}

	@Override
	public void setFlags(int flags) {
		// TODO Auto-generated method stub
		super.setFlags(flags);
	}

	@Override
	public void setProject(String project) {
		// TODO Auto-generated method stub
		super.setProject(project);
	}

	@Override
	public RefactoringStatus validateDescriptor() {
		// TODO Auto-generated method stub
		return super.validateDescriptor();
	}

	/**
	 * Sets the compilation unit which contains the local variable.
	 *
	 * @param unit
	 *            the compilation unit to set
	 */
	public void setCompilationUnit(final ICompilationUnit unit) {
		Assert.isNotNull(unit);
		fUnit= unit;
	}

}

