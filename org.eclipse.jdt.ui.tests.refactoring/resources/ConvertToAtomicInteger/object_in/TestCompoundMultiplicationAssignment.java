package object_in;

public class TestCompoundMultiplicationAssignment {

	private int i;
	
	public void foo() {
		i*= i + Integer.bitCount(i) * (i/3) - 12;
	}
}