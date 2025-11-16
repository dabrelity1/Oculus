package kroppeb.stareval.element.tree;

import java.util.List;

import kroppeb.stareval.element.ExpressionElement;

public class FunctionCall implements ExpressionElement {
	private final String id;
	private final List<? extends ExpressionElement> args;

	public FunctionCall(String id, List<? extends ExpressionElement> args) {
		this.id = id;
		this.args = args;
	}

	public String getId() {
		return this.id;
	}

	public List<? extends ExpressionElement> getArgs() {
		return this.args;
	}

	@Override
	public String toString() {
		return "FunctionCall{" + this.id + " {" + this.args + "} }";
	}
}
