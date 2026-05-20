package kroppeb.stareval.element.tree.partial;

import java.util.ArrayList;
import java.util.List;

import kroppeb.stareval.element.ExpressionElement;

public class UnfinishedArgsExpression extends PartialExpression {
	public final List<ExpressionElement> tokens = new ArrayList<>();

	@Override
	public String toString() {
		return "UnfinishedArgs{" + this.tokens + "}";
	}
}
