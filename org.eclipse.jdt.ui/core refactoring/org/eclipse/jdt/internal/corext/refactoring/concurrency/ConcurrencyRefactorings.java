package org.eclipse.jdt.internal.corext.refactoring.concurrency;

import org.eclipse.osgi.util.NLS;

/**
 * Helper class to get NLSed messages.
 */
public final class ConcurrencyRefactorings extends NLS {

	private static final String BUNDLE_NAME= ConcurrencyRefactorings.class.getName();

	private ConcurrencyRefactorings() {
		// Do not instantiate
	}
	
	public static String ConcurrencyRefactorings_update_imports;
	public static String ConcurrencyRefactorings_type_error;
	public static String ConcurrencyRefactorings_empty_string;
	public static String ConcurrencyRefactorings_program_name;
	public static String ConcurrencyRefactorings_field_compile_error;
	public static String ConcurrencyRefactorings_read_access;
	public static String ConcurrencyRefactorings_write_access;
	public static String ConcurrencyRefactorings_postfix_access;
	public static String ConcurrencyRefactorings_prefix_access;
	public static String ConcurrencyRefactorings_remove_synch_mod;
	public static String ConcurrencyRefactorings_remove_synch_block;
	public static String ConcurrencyRefactorings_unsafe_op_error_1;
	public static String ConcurrencyRefactorings_unsafe_op_error_2;
	public static String ConcurrencyRefactorings_unsafe_op_error_3;
	
	public static String ConvertToAtomicInteger_check_preconditions;
	public static String ConvertToAtomicInteger_task_name;
	public static String ConvertToAtomicInteger_analyze_error;
	public static String ConvertToAtomicInteger_name_user;
	public static String ConvertToAtomicInteger_create_changes;
	public static String ConvertToAtomicInteger_name_official;
	public static String ConvertToAtomicInteger_compile_error;
	
	public static String ConvertToAtomicLong_check_preconditions;
	public static String ConvertToAtomicLong_task_name;
	public static String ConvertToAtomicLong_analyze_error;
	public static String ConvertToAtomicLong_name_user;
	public static String ConvertToAtomicLong_create_changes;
	public static String ConvertToAtomicLong_name_official;
	public static String ConvertToAtomicLong_compile_error;
	
	public static String ConvertToConcurrentHashMapRefactoring_check_preconditions;
	public static String ConvertToConcurrentHashMapRefactoring_task_name;
	public static String ConvertToConcurrentHashMapRefactoring_program_name;
	public static String ConvertToConcurrentHashMapRefactoring_type_error;
	public static String ConvertToConcurrentHashMapRefactoring_analyze_error;
	public static String ConvertToConcurrentHashMapRefactoring_name_user;
	public static String ConvertToConcurrentHashMapRefactoring_create_changes;
	public static String ConvertToConcurrentHashMapRefactoring_name_official;
	public static String ConvertToConcurrentHashMapRefactoring_compile_error;
	
	public static String ConvertToFJTaskRefactoring_check_preconditions;
	public static String ConvertToFJTaskRefactoring_task_name;
	public static String ConvertToFJTaskRefactoring_recursive_method;
	public static String ConvertToFJTaskRefactoring_recursive_action;
	public static String ConvertToFJTaskRefactoring_generate_compute;
	public static String ConvertToFJTaskRefactoring_recursion_error_1;
	public static String ConvertToFJTaskRefactoring_recursion_error_2;
	public static String ConvertToFJTaskRefactoring_scenario_error;
	public static String ConvertToFJTaskRefactoring_analyze_error;
	public static String ConvertToFJTaskRefactoring_compile_error;
	public static String ConvertToFJTaskRefactoring_compile_error_update;
	public static String ConvertToFJTaskRefactoring_name_user;
	public static String ConvertToFJTaskRefactoring_create_changes;
	public static String ConvertToFJTaskRefactoring_name_official;
	public static String ConvertToFJTaskRefactoring_sequential_req;
	
	public static String AccessAnalyzerForAtomicInteger_access_error_1;
	public static String AccessAnalyzerForAtomicInteger_access_error_2;
	
	public static String AccessAnalyzerForConcurrentHashMap_method_invocation;
	public static String AccessAnalyzerForConcurrentHashMap_init;
	public static String AccessAnalyzerForConcurrentHashMap_replace_with_absent;
	public static String AccessAnalyzerForConcurrentHashMap_remove_statement;
	public static String AccessAnalyzerForConcurrentHashMap_then_clause_error;
	public static String AccessAnalyzerForConcurrentHashMap_method_error;
	public static String AccessAnalyzerForConcurrentHashMap_code_error;
	public static String AccessAnalyzerForConcurrentHashMap_clone_error;
	public static String AccessAnalyzerForConcurrentHashMap_synch_block_error;
	public static String AccessAnalyzerForConcurrentHashMap_synch_method_error;
	
	static {
		NLS.initializeMessages(BUNDLE_NAME, ConcurrencyRefactorings.class);
	}
}