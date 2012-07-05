package org.eclipse.jdt.core.refactoring.descriptors;

import java.util.Map;

import org.eclipse.jdt.core.refactoring.IJavaRefactorings;

/**
 * @since 1.6
 */
public class AtomicIntegerRefactoringDescriptor extends JavaRefactoringDescriptor {

	public AtomicIntegerRefactoringDescriptor(String project, String description,
			String comment, Map arguments, int flags) {
		super(IJavaRefactorings.ATOMIC_INTEGER, project, description, comment, arguments, flags);
	}
	
	public AtomicIntegerRefactoringDescriptor() {
		super(IJavaRefactorings.ATOMIC_INTEGER);
	}
}