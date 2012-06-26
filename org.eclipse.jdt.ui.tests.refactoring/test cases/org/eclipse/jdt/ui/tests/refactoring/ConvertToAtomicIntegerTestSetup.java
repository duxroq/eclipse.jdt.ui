package org.eclipse.jdt.ui.tests.refactoring;

import junit.framework.Test;

import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.ui.tests.refactoring.infra.RefactoringTestSetup;

public class ConvertToAtomicIntegerTestSetup extends RefactoringTestSetup {
	
	private IPackageFragment fBaseTypes;
	private IPackageFragment fObjectTypes;
	private IPackageFragment fInvalid;
	private IPackageFragment fStatic;
	private IPackageFragment fStaticRef;
	private IPackageFragment fExistingMethod;
	
	public ConvertToAtomicIntegerTestSetup(Test test) {
		super(test);
	}	
	
	protected void setUp() throws Exception {
		super.setUp();

		IPackageFragmentRoot root= getDefaultSourceFolder();
		
		fBaseTypes= root.createPackageFragment("base_in", true, null); //$NON-NLS-1$
		fObjectTypes= root.createPackageFragment("object_in", true, null); //$NON-NLS-1$
		fInvalid= root.createPackageFragment("invalid", true, null); //$NON-NLS-1$
		fStatic= root.createPackageFragment("static_in", true, null); //$NON-NLS-1$
		fStaticRef= root.createPackageFragment("static_ref_in", true, null); //$NON-NLS-1$
		fExistingMethod= root.createPackageFragment("existingmethods_in", true, null); //$NON-NLS-1$
	}

	public IPackageFragment getBasePackage() {
		return fBaseTypes;
	}	

	public IPackageFragment getObjectPackage() {
		return fObjectTypes;
	}	

	public IPackageFragment getInvalidPackage() {
		return fInvalid;
	}
	
	public IPackageFragment getStaticPackage() {
		return fStatic;
	}
	
	public IPackageFragment getStaticRefPackage() {
		return fStaticRef;
	}
	
	public IPackageFragment getExistingMethodPackage(){
		return fExistingMethod;
	}
}