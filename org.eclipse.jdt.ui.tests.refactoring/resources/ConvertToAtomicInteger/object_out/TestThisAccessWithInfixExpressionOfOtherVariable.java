package object_out;

import java.util.concurrent.atomic.AtomicInteger;

public class TestThisAccessWithInfixExpressionOfOtherVariable {

	AtomicInteger f = new AtomicInteger();

	void doAccess(int value) {
		// TODO The operations below cannot be executed atomically.
		this.f.set(value + value);
	}
}