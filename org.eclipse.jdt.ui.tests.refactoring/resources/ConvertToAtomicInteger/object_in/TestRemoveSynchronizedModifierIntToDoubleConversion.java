package object_in;

public class TestRemoveSynchronizedModifierIntToDoubleConversion {

	private int i;

	public synchronized double foo() {
		return Double.parseDouble(Integer.toString(i));
	}	
}