package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestConvertIntToDouble {

	private AtomicInteger i = new AtomicInteger();
	
	public void foo() {
		double d= i.doubleValue();
	}
}
