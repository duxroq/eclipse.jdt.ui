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
	
	public static String AtomicIntegerRefactoring_descriptor_description;
	public static String AtomicIntegerRefactoring_field_pattern;
	
	static {
		NLS.initializeMessages(BUNDLE_NAME, ConcurrencyRefactorings.class);
	}
}