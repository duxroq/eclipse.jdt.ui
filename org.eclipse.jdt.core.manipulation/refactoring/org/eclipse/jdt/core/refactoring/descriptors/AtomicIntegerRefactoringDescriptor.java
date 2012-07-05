package org.eclipse.jdt.core.refactoring.descriptors;

import java.util.Map;

public class AtomicIntegerRefactoringDescriptor extends JavaRefactoringDescriptor {

	public AtomicIntegerRefactoringDescriptor(String id, String project, String description,
			String comment, Map arguments, int flags) {
		super(id, project, description, comment, arguments, flags);
	}
	
	protected AtomicIntegerRefactoringDescriptor(String id) {
		super(id);
	}
}