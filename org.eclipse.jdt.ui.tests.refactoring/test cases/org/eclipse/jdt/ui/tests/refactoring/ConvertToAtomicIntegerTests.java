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

		ConvertToAtomicIntegerRefactoring refactoring=
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

		ConvertToAtomicIntegerRefactoring refactoring=
				((Checks.checkAvailability(field).hasFatalError() ||
				!RefactoringAvailabilityTester.isConvertAtomicIntegerAvailable(field))
						? null : new ConvertToAtomicIntegerRefactoring(field));
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

		ConvertToAtomicIntegerRefactoring refactoring=
				((Checks.checkAvailability(field).hasFatalError() ||
				!RefactoringAvailabilityTester.isConvertAtomicIntegerAvailable(field))
						? null : new ConvertToAtomicIntegerRefactoring(field));
		RefactoringStatus status= refactoring.checkAllConditions(new NullProgressMonitor());
		assertTrue(status.hasError());
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

	@SuppressWarnings("unused")
	private void baseTest(String fieldName) throws Exception {
		performTest(fgTestSetup.getBasePackage(), getName(), "base_out", fieldName);
	}

	private void invalidTest(String fieldName) throws Exception {
		performInvalidTest(fgTestSetup.getInvalidPackage(), getName(), fieldName);
	}

	@SuppressWarnings("unused")
	private void existingTest(String fieldName) throws Exception {
		performTest(fgTestSetup.getExistingMethodPackage(), getName(), "existingmethods_out", fieldName);
	}

	private void testWithWarning(String fieldName) throws Exception {
		performTestWithWarning(fgTestSetup.getObjectPackage(), getName(), "object_out", fieldName);
	}

	//=====================================================================================
	// Basic Object Test
	//=====================================================================================

	public void testMultiplication_extendedOperands() throws Exception {
		objectTest("i");
	}

	public void testInfixExpressionWithMixedOperands() throws Exception {
		objectTest("i");
	}

	public void testMultiplication_extendedOperandsWithFieldRefs() throws Exception {
		objectTest("i");
	}

	public void testAddition_AllFieldRefs() throws Exception {
		objectTest("i");
	}

	public void testAddition_NestedFieldRefs() throws Exception {
		objectTest("i");
	}

	public void testAddition_FieldRefsInExtOperands() throws Exception {
		objectTest("i");
	}

	public void testAddition_NoFieldRefs() throws Exception {
		objectTest("i");
	}

	public void testCompoundSubtractionAssignment_normal() throws Exception {
		objectTest("i");
	}

	public void testCompoundSubtractionAssignment_extendedInfixWithExtraFieldRefs() throws Exception {
		objectTest("i");
	}

	public void testCompoundAdditionAssignment_normal() throws Exception {
		objectTest("f");
	}

	public void testCompoundAdditionAssignment_infixExpression() throws Exception {
		objectTest("f");
	}

	public void testCompoundAdditionAssignment_extendedInfixWithMultipleFieldRefs() throws Exception {
		objectTest("f");
	}

	public void testCompoundAdditionAssignment_parenthesizedExpression() throws Exception {
		objectTest("f");
	}


	public void testMultipleInfixExpressionsWithReferenceToChosenField() throws Exception {
		objectTest("f");
	}

	public void testMultipleInfixExpWithMultipleRefsToChosenField() throws Exception {
		objectTest("i");
	}

	public void testSynchronizedBlockOneSingleAccess() throws Exception {
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

	public void testRemoveSynchronizedBlockIncrement() throws Exception {
		objectTest("f");
	}

	public void testSynchronizedMethodSingleAccess() throws Exception {
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

	public void testSubtraction() throws Exception {
		objectTest("f");
	}

	public void testSubtraction_leftOperandFieldRefWithExtendedOperands() throws Exception {
		objectTest("i");
	}

	public void testSubtraction_rightOperandFieldRef() throws Exception {
		objectTest("i");
	}

	public void testSubtraction_rightOperandFieldRefWithExtendedOperands() throws Exception {
		objectTest("i");
	}

	public void testSubtraction_fieldRefInExtendedOperands() throws Exception {
		objectTest("i");
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

	public void testGetter() throws Exception {
		objectTest("value");
	}

	public void testSetter() throws Exception {
		objectTest("value");
	}

	public void testReturnPostfixIncrement() throws Exception {
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

	public void testReturnAssignment_extendedInfixWithFieldRefs() throws Exception {
		objectTest("i");
	}

	public void testReturnAssignment_compoundAdditionOperator() throws Exception {
		objectTest("i");
	}

	public void testReturnAssignment_compoundTimesOperator() throws Exception {
		objectTest("i");
	}

	public void testAddition() throws Exception {
		objectTest("i");
	}

	public void testForLoop() throws Exception {
		objectTest("i");
	}

	public void testReplaceIfStatementWithCompareAndSet() throws Exception {
		objectTest("i");
	}

	public void testReplaceIfStatementWithCompareAndSet_extendedSetExpression() throws Exception {
		objectTest("i");
	}

	public void testRemoveSynchronizedBlockAddAndGetAssignment() throws Exception {
		objectTest("counter");
	}

	public void testReplaceIfStatementWithCompareAndSet_extendedCompareExpression() throws Exception {
		objectTest("i");
	}

	public void testAssignmentInForLoop() throws Exception {
		objectTest("i");
	}

	public void testConvertIntToDouble() throws Exception {
		objectTest("i");
	}

	public void testPrimitiveTypeCastConversions_double() throws Exception {
		objectTest("i");
	}

	public void testPrimitiveTypeCastConversions_float() throws Exception {
		objectTest("i");
	}

	public void testPrimitiveTypeCastConversions_long() throws Exception {
		objectTest("i");
	}

	public void testPrimitiveTypeCastConversions_short() throws Exception {
		objectTest("i");
	}

	public void testPrimitiveTypeCastConversions_byte() throws Exception {
		objectTest("i");
	}

	public void testRemoveSynchronizedBlockPrimitiveTypeCastConversion() throws Exception {
		objectTest("i");
	}

	public void testRemoveSynchronizedBlockIntToDoubleConversion() throws Exception {
		objectTest("i");
	}

	public void testRemoveSynchronizedModifierPrimitiveTypeCastConversions() throws Exception {
		objectTest("i");
	}

	public void testRemoveSynchronizedModifierIntToDoubleConversion() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinSwitchCases() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinDoStatement() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinDoStatement_NoBlockBody() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinWhileStatement() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinWhileStatement_NoBlockBody() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinEnhancedForStatement() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinEnhancedForStatement_NoBlockBody() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinForStatement() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinIfStatement() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinIfStatement_NoBlockBody() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinIfStatement_elseStatement() throws Exception {
		objectTest("i");
	}

	public void testTodoCommentingWithinIfStatement_elseStatementNoBlockBody() throws Exception {
		objectTest("i");
	}

	public void testRemoveSynchronizedBlockForCompareAndSet() throws Exception {
		objectTest("i");
	}

	//------------------------- Cases below should throw a warning

	public void testSynchronizedMethodMultipleAccess() throws Exception {
		testWithWarning("f");
	}

	public void testMultipleInfixExpressionsAssignmentInSynchBlock() throws Exception {
		testWithWarning("f");
	}

	public void testMultipleInfixExpressionsAssignmentInSynchMethod() throws Exception {
		testWithWarning("f");
	}

	public void testMultipleFieldRefsInEnclosingStatementSynchBlock() throws Exception {
		testWithWarning("i");
	}

	public void testMultipleFieldRefsInEnclosingStatementSynchMethod() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockCompareAndSetExtraFieldRefs() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedModifierSetAndGetAssignment() throws Exception {
		testWithWarning("counter");
	}

	public void testWarningDueToMultiplication() throws Exception {
		testWithWarning("f");
	}

	public void testWarningDueToCompoundMultiplicationAssignment() throws Exception {
		testWithWarning("f");
	}

	public void testWarningDueToCompoundDivisionAssignment() throws Exception {
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

	public void testNotRemoveSynchronizedModifierReturnAssignment() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockReturnAssignment() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockForAnIfStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockForForStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockForWhileStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockForConditionalStatements() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockEnhancedForStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockDoStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockAssertStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockSwitchStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockTryStatement() throws Exception {
		testWithWarning("i");
	}

	public void testNotRemoveSynchronizedBlockMultipleAccess() throws Exception {
		testWithWarning("f");
	}

	public void testNotRemoveSynchronizedModifierAddAndGetAssignment_MultipleFieldRefs() throws Exception {
		testWithWarning("counter");
	}

	public void testSynchronizedBlockMultipleAccess() throws Exception {
		testWithWarning("f");
	}

	//------------------------- Cases below do not meet preconditions - should throw an error

	public void testPrefixSideEffectsOnIntFieldInAssignment() throws Exception {
		invalidTest("i");
	}

	public void testPostfixSideEffectsOnIntFieldInAssignment() throws Exception {
		invalidTest("i");
	}

	public void testAssignmentSideEffectsWithinAssignment() throws Exception {
		invalidTest("i");
	}
}
