
package org.eclipse.jdt.ui.tests.refactoring;

import java.util.Hashtable;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.concurrency.ConvertToAtomicIntegerRefactoring;

public class ConvertToAtomicIntegerTests extends AbstractSelectionTestCase {

	private static ConvertToAtomicIntegerTestSetup fgTestSetup;
	
	public ConvertToAtomicIntegerTests(String name) {
		super(name);
	}
	
	public static Test suite() {
		fgTestSetup= new ConvertToAtomicIntegerTestSetup(new TestSuite(ConvertToAtomicIntegerTests.class));
		return fgTestSetup;
	}
	
	public static Test setUpTest(Test test) {
		fgTestSetup= new ConvertToAtomicIntegerTestSetup(test);
		return fgTestSetup;
	}
	
	protected void setUp() throws Exception {
		super.setUp();
		fIsPreDeltaTest= true;
	}

	protected String getResourceLocation() {
		return "ConvertToAtomicInteger/"; 
	}
	
	protected String adaptName(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1) + ".java"; 
	}	
	
	protected void performTest(IPackageFragment packageFragment, String id, String outputFolder, String fieldName) throws Exception {
		ICompilationUnit unit= createCU(packageFragment, id);
		IField field= getField(unit, fieldName);
		assertNotNull(field);
		
		initializePreferences();

		ConvertToAtomicIntegerRefactoring refactoring= //new ConvertToAtomicIntegerRefactoring(field);
				((Checks.checkAvailability(field).hasFatalError() ||
						!RefactoringAvailabilityTester.isConvertAtomicIntegerAvailable(field))
						? null : new ConvertToAtomicIntegerRefactoring(field));
		performTest(unit, refactoring, COMPARE_WITH_OUTPUT, getProofedContent(outputFolder, id), true);
	}

	protected void performTestWithWarning(IPackageFragment packageFragment,
			String id, String outputFolder, String fieldName)  throws Exception {
		ICompilationUnit unit= createCU(packageFragment, id);
		IField field= getField(unit, fieldName);
		assertNotNull(field);
		
		initializePreferences();

		ConvertToAtomicIntegerRefactoring refactoring= new ConvertToAtomicIntegerRefactoring(field);
		RefactoringStatus status= refactoring.checkAllConditions(new NullProgressMonitor());
		assertTrue(status.hasWarning());
		performTest(unit, refactoring, COMPARE_WITH_OUTPUT, getProofedContent(outputFolder, id), true);
	}
	
	protected void performInvalidTest(IPackageFragment packageFragment,
			String id, String fieldName) throws Exception {
		ICompilationUnit unit= createCU(packageFragment, id);
		IField field= getField(unit, fieldName);
		assertNotNull(field);

		initializePreferences();

		ConvertToAtomicIntegerRefactoring refactoring= new ConvertToAtomicIntegerRefactoring(field);
		RefactoringStatus status= refactoring.checkAllConditions(new NullProgressMonitor());
		assertTrue(status.hasError());
		assertEquals(1, status.getEntries().length);
	}	
	
	private void initializePreferences() {
		Hashtable<String, String> options= new Hashtable<String, String>();
		options.put(JavaCore.CODEASSIST_FIELD_PREFIXES, ""); 
		options.put(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, ""); 
		options.put(JavaCore.CODEASSIST_FIELD_SUFFIXES, ""); 
		options.put(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES, ""); 
		JavaCore.setOptions(options);
	}
	
	private static IField getField(ICompilationUnit unit, String fieldName) throws Exception {
		
		IField result= null;
		IType[] types= unit.getAllTypes();
		for (int i= 0; i < types.length; i++) {
			IType type= types[i];
			result= type.getField(fieldName);
			if (result != null && result.exists())
				break;
		}
		return result;
	}

	private void objectTest(String fieldName) throws Exception {
		performTest(fgTestSetup.getObjectPackage(), getName(), "object_out", fieldName); 
	}
	
//	private void baseTest(String fieldName) throws Exception {
//		performTest(fgTestSetup.getBasePackage(), getName(), "base_out", fieldName); 
//	}
//	
//	private void invalidTest(String fieldName) throws Exception {
//		performInvalidTest(fgTestSetup.getInvalidPackage(), getName(), fieldName);
//	}
//	
//	private void existingTest(String fieldName) throws Exception {
//		performTest(fgTestSetup.getExistingMethodPackage(), getName(), "existingmethods_out", fieldName); 
//	}
	
	private void testWithWarning(String fieldName) throws Exception {
		performTestWithWarning(fgTestSetup.getObjectPackage(), getName(), "object_out", fieldName); 
	}
	
	//=====================================================================================
	// Basic Object Test
	//=====================================================================================
	
	public void testDemo() throws Exception {
		objectTest("counter");
	}
	
	public void testInfixExpressions() throws Exception {
		objectTest("i");
	}
	
	public void testInfixExpressions2() throws Exception {
		objectTest("i");
	}
	
	public void testInfixExpressions3() throws Exception {
		objectTest("i");
	}
	
	public void testInfixExpressions4() throws Exception {
		objectTest("i");
	}
	
	public void testInfixExpressions5() throws Exception {
		objectTest("i");
	}
	
	public void testMultipleFieldRefsInEnclosingStatementSynchBlock() throws Exception {
		objectTest("i"); 
	}

	public void testMultipleFieldRefsInEnclosingStatementSynchMethod() throws Exception {
		objectTest("i"); 
	}
	
	public void testMultipleInfixExpressionsWithReferenceToChosenField() throws Exception {
		objectTest("f"); 
	}
	
	public void testMultipleInfixExpWithMultipleRefsToChosenField() throws Exception {
		objectTest("i");
	}
	
	public void testReadAccess() throws Exception {
		objectTest("field"); 
	}
	
	public void testSynchronizedBlockOneSingleAccess() throws Exception {
		objectTest("f"); 
	}
	
	public void testSynchronizedBlockMultipleAccess() throws Exception {
		objectTest("f"); 
	}
	
	public void testIncrementPrefix() throws Exception {
		objectTest("f"); 
	}	

	public void testIncrementPostfix() throws Exception {
		objectTest("f"); 
	}
	
	public void testDecrementPrefix() throws Exception {
		objectTest("f"); 
	}	

	public void testDecrementPostfix() throws Exception {
		objectTest("f"); 
	}
	
	public void testMultipleInfixExpressionsAssignment() throws Exception {
		objectTest("f"); 
	}
	
	public void testMultipleInfixExpressionsAssignmentInSynchBlock() throws Exception {
		objectTest("f"); 
	}
	
	public void testMultipleInfixExpressionsAssignmentInSynchMethod() throws Exception {
		objectTest("f"); 
	}
	
	public void testRemoveSynchronizedBlockIncrement() throws Exception {
		objectTest("f"); 
	}
	
	public void testNotRemoveSynchronizedBlockMultipleAccess() throws Exception {
		objectTest("f"); 
	}
	
	public void testSynchronizedMethodSingleAccess() throws Exception {
		objectTest("f"); 
	}
	
	public void testSynchronizedMethodMultipleAccess() throws Exception {
		objectTest("f"); 
	}
	
	public void testSynchronizedMethodSingleAccessIncrement() throws Exception {
		objectTest("f"); 
	}
	
	public void testExistingImport() throws Exception {
		objectTest("f"); 
	}
	
	public void testIncrementByAdding() throws Exception {
		objectTest("f"); 
	}
	
	public void testAddAssign() throws Exception {
		objectTest("f"); 
	}
	
	public void testSubtract() throws Exception {
		objectTest("f"); 
	}
	
	public void testFieldAndOvershadowingVariable() throws Exception {
		objectTest("f"); 
	}
	
	public void testNoFieldReference() throws Exception {
		objectTest("f"); 
	}
	
	public void testThisDotMethod() throws Exception {
		objectTest("f"); 
	}
	
	public void testSuperDotMethod() throws Exception {
		objectTest("f"); 
	}
	
	public void testInnerClass() throws Exception {
		objectTest("f"); 
	}
	
	public void testCounterExample() throws Exception {
		objectTest("value"); 
	}
	
	public void testFieldModifier() throws Exception {
		objectTest("f"); 
	}
	
	public void testThisAccessWithInfixExpression() throws Exception {
		objectTest("f"); 
	}
	
	public void testThisAccessWithInfixExpressionOfOtherVariable() throws Exception {
		objectTest("f"); 
	}
	
	public void testReturnAssignment() throws Exception {
		objectTest("i");  
	}

	public void testAddingInReverse() throws Exception {
		objectTest("i");  
	}
	
	//------------------------- Cases below do meet preconditions - however should throw a warning
	
	public void testWarningDueToMultiplication() throws Exception {
		testWithWarning("f"); 
	}
	
	public void testWarningDueToMultiplicationAssignment() throws Exception {
		testWithWarning("f"); 
	}
	
	public void testWarningDueToDivisionAssignment() throws Exception {
		testWithWarning("f"); 
	}
	
	public void testWarningDueToTwoFieldsInSynchronizedBlock() throws Exception {
		testWithWarning("f"); 
	}
	
	public void testWarningDueToTwoFieldsInSynchronizedMethod() throws Exception {
		testWithWarning("f"); 
	}
	
	public void testWarningDuetoFieldAccessedTwiceInSynchronizedBlock() throws Exception {
		testWithWarning("f");
	}
	
	public void testWarningDueToFieldAccessedTwiceInSynchronizedMethod() throws Exception {
		testWithWarning("f"); 
	}
	
	public void testReturnAssignmentInSynchronizedMethod() throws Exception {
		testWithWarning("i"); 
	}
	
	public void testReturnAssignmentInSynchronizedBlock() throws Exception {
		testWithWarning("i"); 
	}
	
	//------------------------- Cases below do not meet preconditions - therefore refactoring should not proceed
	
}
