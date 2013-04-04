package invalid;

public class TestPrefixSideEffectsOnIntFieldInAssignment {

	private int i;

	public void foo() {
		i = --i;
	}
}
