package object_in;

public class TestSubtraction_rightOperandFieldRefWithExtendedOperands {

	private int i;
	private int j;

	public void foo() {
		i = 12 - i - j - 3;
	}
}