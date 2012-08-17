package object_in;

public class TestRemoveSynchronizedBlockIntToDoubleConversion {

	private int i;
	
	public double foo() {
		synchronized (this) {
			return Double.parseDouble(Integer.toString(i));
		}
	}
}