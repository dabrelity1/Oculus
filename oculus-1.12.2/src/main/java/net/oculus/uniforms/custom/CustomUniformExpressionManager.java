package net.oculus.uniforms.custom;

import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import kroppeb.stareval.element.AccessibleExpressionElement;
import kroppeb.stareval.element.ExpressionElement;
import kroppeb.stareval.element.token.IdToken;
import kroppeb.stareval.element.token.NumberToken;
import kroppeb.stareval.element.tree.AccessExpressionElement;
import kroppeb.stareval.element.tree.BinaryExpressionElement;
import kroppeb.stareval.element.tree.FunctionCall;
import kroppeb.stareval.element.tree.UnaryExpressionElement;
import kroppeb.stareval.exception.ParseException;
import kroppeb.stareval.parser.BinaryOp;
import kroppeb.stareval.parser.Parser;
import kroppeb.stareval.parser.ParserOptions;
import kroppeb.stareval.parser.UnaryOp;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.oculus.Oculus;
import net.oculus.gl.program.ProgramUniforms;
import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.gl.state.MatrixState;
import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.layer.GbufferPrograms;
import net.oculus.shaderpack.CustomUniformDirective;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.materialmap.NamespacedId;
import net.oculus.uniforms.BuiltinReplacementUniforms;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.CelestialUniforms;
import net.oculus.uniforms.CompatibilityUniforms;
import net.oculus.uniforms.CustomUniforms;
import net.oculus.uniforms.GameplayUniforms;
import net.oculus.uniforms.IdMapUniforms;
import net.oculus.uniforms.ShadowUniforms;
import net.oculus.uniforms.SpecialEffectUniforms;
import net.oculus.uniforms.SystemTimeUniforms;
import net.oculus.uniforms.WorldInfoUniforms;
import net.oculus.uniforms.transforms.ExponentialSmoothing;

/**
 * Evaluates OptiFine-style scalar custom uniform and variable expressions.
 */
public final class CustomUniformExpressionManager {
    private static final BinaryOp OR = new BinaryOp("||", 80);
    private static final BinaryOp AND = new BinaryOp("&&", 70);
    private static final BinaryOp EQUAL = new BinaryOp("==", 60);
    private static final BinaryOp NOT_EQUAL = new BinaryOp("!=", 60);
    private static final BinaryOp LESS = new BinaryOp("<", 50);
    private static final BinaryOp LESS_EQUAL = new BinaryOp("<=", 50);
    private static final BinaryOp GREATER = new BinaryOp(">", 50);
    private static final BinaryOp GREATER_EQUAL = new BinaryOp(">=", 50);
    private static final BinaryOp ADD = new BinaryOp("+", 40);
    private static final BinaryOp SUBTRACT = new BinaryOp("-", 40);
    private static final BinaryOp MULTIPLY = new BinaryOp("*", 30);
    private static final BinaryOp DIVIDE = new BinaryOp("/", 30);
    private static final BinaryOp MODULO = new BinaryOp("%", 30);

    private static final UnaryOp NEGATE = new UnaryOp("-");
    private static final UnaryOp POSITIVE = new UnaryOp("+");
    private static final UnaryOp NOT = new UnaryOp("!");

    private static final ParserOptions PARSER_OPTIONS = createParserOptions();
    private static final double EPSILON = 1.0e-6;
    private static final int ABSENT_BIOME_BASE = -10_000;
    private static final int MATRIX_SIZE = 4;

    private static final CustomUniformExpressionManager EMPTY =
        new CustomUniformExpressionManager(Collections.emptyMap(), Collections.emptyMap(), true, null);

    private final Map<String, CompiledExpression> uniforms;
    private final Map<String, CompiledExpression> variables;
    private final boolean oldHandLight;
    private final Object2IntFunction<NamespacedId> itemIdMap;
    private final Set<String> missingSymbolsLogged = new HashSet<>();
    private final Set<String> missingFunctionsLogged = new HashSet<>();

    private long frameId;
    private long dynamicEvaluationId = Long.MIN_VALUE + 1L;

    private CustomUniformExpressionManager(Map<String, CompiledExpression> uniforms,
                                           Map<String, CompiledExpression> variables,
                                           boolean oldHandLight,
                                           Object2IntFunction<NamespacedId> itemIdMap) {
        this.uniforms = uniforms;
        this.variables = variables;
        this.oldHandLight = oldHandLight;
        this.itemIdMap = itemIdMap;
    }

    public static CustomUniformExpressionManager empty() {
        return EMPTY;
    }

    public static CustomUniformExpressionManager fromProperties(ShaderProperties properties) {
        return fromProperties(properties, null);
    }

    public static CustomUniformExpressionManager fromShaderPack(ShaderPack pack) {
        if (pack == null) {
            return empty();
        }
        return fromProperties(pack.getProperties(), pack.getIdMap().getItemIdMap());
    }

    static CustomUniformExpressionManager fromProperties(ShaderProperties properties,
            Object2IntFunction<NamespacedId> itemIdMap) {
        if (properties == null) {
            return empty();
        }

        boolean oldHandLight = properties.getOldHandLight().orElse(true);
        if (properties.getCustomUniforms().isEmpty() && properties.getCustomVariables().isEmpty()) {
            return itemIdMap == null && oldHandLight
                ? empty()
                : new CustomUniformExpressionManager(Collections.emptyMap(), Collections.emptyMap(),
                    oldHandLight, itemIdMap);
        }

        Map<String, CompiledExpression> variables = compileDirectives(properties.getCustomVariables(), "variable");
        Map<String, CompiledExpression> uniforms = compileDirectives(properties.getCustomUniforms(), "uniform");
        if (variables.isEmpty() && uniforms.isEmpty()) {
            return itemIdMap == null && oldHandLight
                ? empty()
                : new CustomUniformExpressionManager(Collections.emptyMap(), Collections.emptyMap(),
                    oldHandLight, itemIdMap);
        }

        resolveDynamicExpressions(uniforms, variables);
        Oculus.LOGGER.info("Loaded {} custom uniform expression(s) and {} custom variable expression(s)",
            uniforms.size(), variables.size());
        return new CustomUniformExpressionManager(uniforms, variables, oldHandLight, itemIdMap);
    }

    private static Map<String, CompiledExpression> compileDirectives(Map<String, CustomUniformDirective> directives,
                                                                     String kind) {
        if (directives == null || directives.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, CompiledExpression> result = new LinkedHashMap<>();
        directives.forEach((name, directive) -> {
            try {
                ExpressionElement parsed = Parser.parse(directive.getExpression(), PARSER_OPTIONS);
                ExpressionNode node = compile(parsed);
                Set<String> dependencies = new LinkedHashSet<>();
                node.collectIdentifiers(dependencies);
                result.put(name, new CompiledExpression(directive, node, dependencies));
            } catch (ParseException | ExpressionCompileException ex) {
                Oculus.LOGGER.warn("Failed to parse custom {} {} = {}", kind, name, directive.getExpression(), ex);
            }
        });
        return result;
    }

    private static void resolveDynamicExpressions(Map<String, CompiledExpression> uniforms,
                                                  Map<String, CompiledExpression> variables) {
        Set<CompiledExpression> visiting = new HashSet<>();

        for (CompiledExpression expression : variables.values()) {
            resolveDynamicExpression(expression, uniforms, variables, visiting);
        }
        for (CompiledExpression expression : uniforms.values()) {
            resolveDynamicExpression(expression, uniforms, variables, visiting);
        }
    }

    private static boolean resolveDynamicExpression(CompiledExpression expression,
                                                    Map<String, CompiledExpression> uniforms,
                                                    Map<String, CompiledExpression> variables,
                                                    Set<CompiledExpression> visiting) {
        if (expression.dynamicResolved) {
            return expression.dynamic;
        }

        if (!visiting.add(expression)) {
            return false;
        }

        boolean dynamic = false;
        Set<String> dynamicDependencies = new LinkedHashSet<>();
        for (String dependency : expression.dependencies) {
            CompiledExpression variable = variables.get(dependency);
            if (variable != null) {
                if (resolveDynamicExpression(variable, uniforms, variables, visiting)) {
                    dynamic = true;
                    dynamicDependencies.addAll(variable.dynamicDependencies);
                }
                continue;
            }

            CompiledExpression uniform = uniforms.get(dependency);
            if (uniform != null) {
                if (resolveDynamicExpression(uniform, uniforms, variables, visiting)) {
                    dynamic = true;
                    dynamicDependencies.addAll(uniform.dynamicDependencies);
                }
                continue;
            }

            String dynamicDependency = BuiltinSymbols.dynamicDependencyName(dependency);
            if (dynamicDependency != null) {
                dynamic = true;
                dynamicDependencies.add(dynamicDependency);
            }
        }

        visiting.remove(expression);
        expression.dynamic = dynamic;
        expression.dynamicDependencies = Collections.unmodifiableSet(dynamicDependencies);
        expression.dynamicResolved = true;
        return dynamic;
    }

    public boolean isEmpty() {
        return uniforms.isEmpty() && variables.isEmpty();
    }

    public boolean hasUniform(String name) {
        return uniforms.containsKey(name);
    }

    public boolean addUniform(String uniformName, ProgramUniforms.Builder builder) {
        CompiledExpression expression = uniforms.get(uniformName);
        if (expression == null) {
            return false;
        }

        ValueUpdateNotifier notifier = expression.dynamic ? createDynamicNotifier(expression) : null;
        switch (expression.getType()) {
            case BOOL:
                builder.addInt(uniformName, () -> truthy(evaluateUniform(uniformName)) ? 1 : 0, notifier);
                return true;
            case INT:
                builder.addInt(uniformName, () -> (int) evaluateUniform(uniformName), notifier);
                return true;
            case FLOAT:
                builder.addFloatSupplier(uniformName, () -> (float) evaluateUniform(uniformName), notifier);
                return true;
            case VEC2:
                builder.addVec2(uniformName, () -> evaluateUniformVector(uniformName, 2), notifier);
                return true;
            case VEC3:
                builder.addVec3(uniformName, () -> evaluateUniformVector(uniformName, 3), notifier);
                return true;
            case VEC4:
                builder.addVec4(uniformName, () -> evaluateUniformVector(uniformName, 4), notifier);
                return true;
            default:
                return false;
        }
    }

    public void beginFrame() {
        if (isEmpty()) {
            return;
        }

        frameId++;
        EvaluationContext context = newFrameContext();

        for (CompiledExpression expression : variables.values()) {
            if (!expression.dynamic) {
                expression.evaluateValue(context);
            }
        }
        for (CompiledExpression expression : uniforms.values()) {
            if (!expression.dynamic) {
                expression.evaluateValue(context);
            }
        }
    }

    private double evaluateUniform(String name) {
        return evaluateUniformValue(name).scalar();
    }

    private float[] evaluateUniformVector(String name, int size) {
        return evaluateUniformValue(name).toFloatVector(size);
    }

    private ExpressionValue evaluateUniformValue(String name) {
        CompiledExpression expression = uniforms.get(name);
        if (expression == null) {
            return ExpressionValue.ZERO;
        }
        return expression.evaluateValue(expression.dynamic ? newDynamicContext() : newFrameContext());
    }

    double evaluateUniformForTesting(String name) {
        return evaluateUniform(name);
    }

    float[] evaluateUniformVectorForTesting(String name, int size) {
        return evaluateUniformVector(name, size);
    }

    boolean isUniformDynamicForTesting(String name) {
        CompiledExpression expression = uniforms.get(name);
        return expression != null && expression.dynamic;
    }

    Set<String> dynamicDependenciesForTesting(String name) {
        CompiledExpression expression = uniforms.get(name);
        return expression == null ? Collections.emptySet() : expression.dynamicDependencies;
    }

    Set<String> dependenciesForTesting(String name) {
        CompiledExpression expression = uniforms.get(name);
        return expression == null ? Collections.emptySet() : expression.dependencies;
    }

    Object2IntFunction<NamespacedId> itemIdMapForTesting() {
        return itemIdMap;
    }

    boolean isOldHandLightForTesting() {
        return oldHandLight;
    }

    public int getHeldItemIdMain() {
        return IdMapUniforms.getHeldItemIdMain(itemIdMap);
    }

    public int getHeldItemIdOff() {
        return IdMapUniforms.getHeldItemIdOff(itemIdMap);
    }

    public int getCurrentRenderedItemId() {
        return IdMapUniforms.getCurrentRenderedItemId(itemIdMap);
    }

    private ExpressionValue evaluateVariableValue(String name, EvaluationContext context) {
        CompiledExpression expression = variables.get(name);
        if (expression == null) {
            return null;
        }
        return expression.evaluateValue(expression.dynamic ? dynamicContextFor(context) : newFrameContext());
    }

    private ExpressionValue evaluateReferencedUniformValue(String name, EvaluationContext context) {
        CompiledExpression expression = uniforms.get(name);
        if (expression == null) {
            return null;
        }
        return expression.evaluateValue(expression.dynamic ? dynamicContextFor(context) : newFrameContext());
    }

    private EvaluationContext newFrameContext() {
        return new EvaluationContext(this, frameId, frameId);
    }

    private EvaluationContext newDynamicContext() {
        long cacheId = dynamicEvaluationId++;
        if (dynamicEvaluationId == 0L) {
            dynamicEvaluationId = Long.MIN_VALUE + 1L;
        }
        return new EvaluationContext(this, frameId, cacheId);
    }

    private EvaluationContext dynamicContextFor(EvaluationContext context) {
        return context.cacheId < 0L ? context : newDynamicContext();
    }

    private static ValueUpdateNotifier createDynamicNotifier(CompiledExpression expression) {
        List<ValueUpdateNotifier> notifiers = new ArrayList<>();
        for (String dependency : expression.dynamicDependencies) {
            ValueUpdateNotifier notifier = BuiltinSymbols.dynamicNotifier(dependency);
            if (notifier != null) {
                notifiers.add(notifier);
            }
        }

        if (notifiers.isEmpty()) {
            return null;
        }
        if (notifiers.size() == 1) {
            return notifiers.get(0);
        }
        return new CombinedValueUpdateNotifier(notifiers);
    }

    private void logMissingSymbol(String symbol) {
        if (missingSymbolsLogged.add(symbol)) {
            Oculus.LOGGER.warn("Unknown custom uniform expression symbol '{}'; using 0", symbol);
        }
    }

    private void logMissingFunction(String function) {
        if (missingFunctionsLogged.add(function)) {
            Oculus.LOGGER.warn("Unknown custom uniform expression function '{}'; using 0", function);
        }
    }

    private static ParserOptions createParserOptions() {
        ParserOptions.Builder builder = new ParserOptions.Builder();
        builder.addUnaryOp("-", NEGATE);
        builder.addUnaryOp("+", POSITIVE);
        builder.addUnaryOp("!", NOT);

        builder.addBinaryOp("||", OR);
        builder.addBinaryOp("&&", AND);
        builder.addBinaryOp("==", EQUAL);
        builder.addBinaryOp("!=", NOT_EQUAL);
        builder.addBinaryOp("<", LESS);
        builder.addBinaryOp("<=", LESS_EQUAL);
        builder.addBinaryOp(">", GREATER);
        builder.addBinaryOp(">=", GREATER_EQUAL);
        builder.addBinaryOp("+", ADD);
        builder.addBinaryOp("-", SUBTRACT);
        builder.addBinaryOp("*", MULTIPLY);
        builder.addBinaryOp("/", DIVIDE);
        builder.addBinaryOp("%", MODULO);
        return builder.build();
    }

    private static ExpressionNode compile(ExpressionElement element) throws ExpressionCompileException {
        if (element instanceof NumberToken) {
            return new ConstantNode(parseNumber(((NumberToken) element).getNumber()));
        }
        if (element instanceof IdToken) {
            return new IdentifierNode(((IdToken) element).getId());
        }
        if (element instanceof AccessExpressionElement) {
            AccessPath path = flattenAccess((AccessExpressionElement) element);
            return new AccessNode(path.baseName, path.accesses);
        }
        if (element instanceof UnaryExpressionElement) {
            UnaryExpressionElement unary = (UnaryExpressionElement) element;
            return new UnaryNode(unary.getOp(), compile(unary.getInner()));
        }
        if (element instanceof BinaryExpressionElement) {
            BinaryExpressionElement binary = (BinaryExpressionElement) element;
            return new BinaryNode(binary.getOp(), compile(binary.getLeft()), compile(binary.getRight()));
        }
        if (element instanceof FunctionCall) {
            FunctionCall call = (FunctionCall) element;
            List<ExpressionNode> args = new ArrayList<>();
            for (ExpressionElement arg : call.getArgs()) {
                args.add(compile(arg));
            }
            if ("smooth".equalsIgnoreCase(call.getId())) {
                return new SmoothNode(args);
            }
            return new FunctionNode(call.getId(), args);
        }
        throw new ExpressionCompileException("Unsupported expression element " + element);
    }

    private static double parseNumber(String number) throws ExpressionCompileException {
        if (number == null || number.isEmpty()) {
            throw new ExpressionCompileException("Empty numeric literal");
        }

        String normalized = number;
        char last = normalized.charAt(normalized.length() - 1);
        if (last == 'f' || last == 'F' || last == 'd' || last == 'D') {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ex) {
            throw new ExpressionCompileException("Invalid numeric literal " + number, ex);
        }
    }

    private static AccessPath flattenAccess(AccessExpressionElement access) throws ExpressionCompileException {
        List<String> accesses = new ArrayList<>();
        AccessibleExpressionElement current = access;
        while (current instanceof AccessExpressionElement) {
            AccessExpressionElement currentAccess = (AccessExpressionElement) current;
            accesses.add(0, currentAccess.getIndex());
            current = currentAccess.getBase();
        }

        if (!(current instanceof IdToken)) {
            throw new ExpressionCompileException("Unsupported access expression " + access);
        }
        return new AccessPath(((IdToken) current).getId(), accesses);
    }

    private static boolean truthy(double value) {
        return Math.abs(value) > EPSILON;
    }

    private static boolean truthy(ExpressionValue value) {
        return value != null && truthy(value.scalar());
    }

    private static boolean same(double left, double right) {
        return Math.abs(left - right) <= EPSILON;
    }

    private interface ExpressionNode {
        ExpressionValue evaluate(EvaluationContext context);

        default void collectIdentifiers(Set<String> identifiers) {
        }
    }

    private static final class ExpressionValue {
        private static final ExpressionValue ZERO = scalar(0.0);

        private final double[] components;

        private ExpressionValue(double[] components) {
            this.components = components;
        }

        private static ExpressionValue scalar(double value) {
            return new ExpressionValue(new double[] {value});
        }

        private static ExpressionValue vector(double[] values) {
            if (values == null || values.length == 0) {
                return ZERO;
            }
            return new ExpressionValue(Arrays.copyOf(values, values.length));
        }

        private double scalar() {
            return components.length == 0 ? 0.0 : components[0];
        }

        private int size() {
            return components.length;
        }

        private double component(int index) {
            if (components.length == 0) {
                return 0.0;
            }
            if (components.length == 1) {
                return components[0];
            }
            return index >= 0 && index < components.length ? components[index] : 0.0;
        }

        private ExpressionValue map(DoubleUnaryFunction function) {
            double[] result = new double[components.length];
            for (int i = 0; i < result.length; i++) {
                result[i] = function.apply(components[i]);
            }
            return vector(result);
        }

        private ExpressionValue zip(ExpressionValue other, DoubleBinaryFunction function) {
            int resultSize = Math.max(size(), other.size());
            double[] result = new double[resultSize];
            for (int i = 0; i < result.length; i++) {
                result[i] = function.apply(component(i), other.component(i));
            }
            return vector(result);
        }

        private double[] toVector(int size) {
            double[] result = new double[size];
            if (components.length == 0) {
                return result;
            }
            if (components.length == 1) {
                Arrays.fill(result, components[0]);
                return result;
            }
            System.arraycopy(components, 0, result, 0, Math.min(size, components.length));
            return result;
        }

        private float[] toFloatVector(int size) {
            double[] values = toVector(size);
            float[] result = new float[size];
            for (int i = 0; i < size; i++) {
                result[i] = (float) values[i];
            }
            return result;
        }
    }

    private static final class ConstantNode implements ExpressionNode {
        private final double value;

        private ConstantNode(double value) {
            this.value = value;
        }

        @Override
        public ExpressionValue evaluate(EvaluationContext context) {
            return ExpressionValue.scalar(value);
        }

        private boolean isInteger() {
            return Math.rint(value) == value;
        }
    }

    private static final class IdentifierNode implements ExpressionNode {
        private final String name;

        private IdentifierNode(String name) {
            this.name = name;
        }

        @Override
        public ExpressionValue evaluate(EvaluationContext context) {
            ExpressionValue variable = context.manager.evaluateVariableValue(name, context);
            if (variable != null) {
                return variable;
            }

            ExpressionValue uniform = context.manager.evaluateReferencedUniformValue(name, context);
            if (uniform != null) {
                return uniform;
            }

            Double builtin = BuiltinSymbols.resolve(name, context.manager.oldHandLight, context.manager.itemIdMap);
            if (builtin != null) {
                return ExpressionValue.scalar(builtin);
            }

            double[] builtinVector = BuiltinSymbols.resolveVector(name);
            if (builtinVector != null) {
                return ExpressionValue.vector(builtinVector);
            }

            context.manager.logMissingSymbol(name);
            return ExpressionValue.ZERO;
        }

        @Override
        public void collectIdentifiers(Set<String> identifiers) {
            identifiers.add(name);
        }
    }

    private static final class AccessNode implements ExpressionNode {
        private final String baseName;
        private final List<String> accesses;

        private AccessNode(String baseName, List<String> accesses) {
            this.baseName = baseName;
            this.accesses = accesses;
        }

        @Override
        public ExpressionValue evaluate(EvaluationContext context) {
            if (accesses.size() == 1) {
                return ExpressionValue.scalar(evaluateVectorAccess(context));
            }
            if (accesses.size() == 2) {
                return ExpressionValue.scalar(evaluateMatrixAccess(context));
            }

            context.manager.logMissingSymbol(baseName + "." + String.join(".", accesses));
            return ExpressionValue.ZERO;
        }

        @Override
        public void collectIdentifiers(Set<String> identifiers) {
            identifiers.add(baseName);
        }

        private double evaluateVectorAccess(EvaluationContext context) {
            String access = accesses.get(0);
            int index = componentIndex(access);
            if (index < 0) {
                context.manager.logMissingSymbol(baseName + "." + access);
                return 0.0;
            }

            ExpressionValue customValue = context.manager.evaluateVariableValue(baseName, context);
            if (customValue == null) {
                customValue = context.manager.evaluateReferencedUniformValue(baseName, context);
            }
            double[] vector = customValue != null && customValue.size() > 1
                ? customValue.toVector(customValue.size())
                : BuiltinSymbols.resolveVector(baseName);
            if (vector == null || index >= vector.length) {
                context.manager.logMissingSymbol(baseName + "." + access);
                return 0.0;
            }
            return vector[index];
        }

        private double evaluateMatrixAccess(EvaluationContext context) {
            int row = matrixIndex(accesses.get(0));
            int column = matrixIndex(accesses.get(1));
            if (row < 0 || column < 0) {
                context.manager.logMissingSymbol(baseName + "." + String.join(".", accesses));
                return 0.0;
            }

            float[] matrix = BuiltinSymbols.resolveMatrix(baseName);
            if (matrix == null || matrix.length < MATRIX_SIZE * MATRIX_SIZE) {
                context.manager.logMissingSymbol(baseName + "." + String.join(".", accesses));
                return 0.0;
            }

            return matrix[column * MATRIX_SIZE + row];
        }

        private static int matrixIndex(String access) {
            if (access == null || access.length() != 1) {
                return -1;
            }
            char c = access.charAt(0);
            return c >= '0' && c <= '3' ? c - '0' : -1;
        }

        private static int componentIndex(String access) {
            if (access == null || access.isEmpty()) {
                return -1;
            }

            switch (access.charAt(0)) {
                case 'x':
                case 'r':
                case 's':
                case '0':
                    return 0;
                case 'y':
                case 'g':
                case 't':
                case '1':
                    return 1;
                case 'z':
                case 'b':
                case 'p':
                case '2':
                    return 2;
                case 'w':
                case 'a':
                case 'q':
                case '3':
                    return 3;
                default:
                    return -1;
            }
        }
    }

    private static final class UnaryNode implements ExpressionNode {
        private final UnaryOp op;
        private final ExpressionNode inner;

        private UnaryNode(UnaryOp op, ExpressionNode inner) {
            this.op = op;
            this.inner = inner;
        }

        @Override
        public ExpressionValue evaluate(EvaluationContext context) {
            ExpressionValue value = inner.evaluate(context);
            if (op == NEGATE) {
                return value.map(v -> -v);
            }
            if (op == POSITIVE) {
                return value;
            }
            if (op == NOT) {
                return ExpressionValue.scalar(truthy(value) ? 0.0 : 1.0);
            }
            return ExpressionValue.ZERO;
        }

        @Override
        public void collectIdentifiers(Set<String> identifiers) {
            inner.collectIdentifiers(identifiers);
        }
    }

    private static final class BinaryNode implements ExpressionNode {
        private final BinaryOp op;
        private final ExpressionNode left;
        private final ExpressionNode right;

        private BinaryNode(BinaryOp op, ExpressionNode left, ExpressionNode right) {
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override
        public ExpressionValue evaluate(EvaluationContext context) {
            if (op == AND) {
                return ExpressionValue.scalar(truthy(left.evaluate(context)) && truthy(right.evaluate(context)) ? 1.0 : 0.0);
            }
            if (op == OR) {
                return ExpressionValue.scalar(truthy(left.evaluate(context)) || truthy(right.evaluate(context)) ? 1.0 : 0.0);
            }

            ExpressionValue l = left.evaluate(context);
            ExpressionValue r = right.evaluate(context);

            if (op == ADD) {
                return l.zip(r, (a, b) -> a + b);
            }
            if (op == SUBTRACT) {
                return l.zip(r, (a, b) -> a - b);
            }
            if (op == MULTIPLY) {
                return l.zip(r, (a, b) -> a * b);
            }
            if (op == DIVIDE) {
                return l.zip(r, (a, b) -> Math.abs(b) <= EPSILON ? 0.0 : a / b);
            }
            if (op == MODULO) {
                return l.zip(r, (a, b) -> Math.abs(b) <= EPSILON ? 0.0 : a % b);
            }
            if (op == EQUAL) {
                return l.zip(r, (a, b) -> same(a, b) ? 1.0 : 0.0);
            }
            if (op == NOT_EQUAL) {
                return l.zip(r, (a, b) -> same(a, b) ? 0.0 : 1.0);
            }
            if (op == LESS) {
                return l.zip(r, (a, b) -> a < b ? 1.0 : 0.0);
            }
            if (op == LESS_EQUAL) {
                return l.zip(r, (a, b) -> a <= b ? 1.0 : 0.0);
            }
            if (op == GREATER) {
                return l.zip(r, (a, b) -> a > b ? 1.0 : 0.0);
            }
            if (op == GREATER_EQUAL) {
                return l.zip(r, (a, b) -> a >= b ? 1.0 : 0.0);
            }
            return ExpressionValue.ZERO;
        }

        @Override
        public void collectIdentifiers(Set<String> identifiers) {
            left.collectIdentifiers(identifiers);
            right.collectIdentifiers(identifiers);
        }
    }

    private static final class FunctionNode implements ExpressionNode {
        private final String name;
        private final List<ExpressionNode> args;

        private FunctionNode(String name, List<ExpressionNode> args) {
            this.name = name.toLowerCase(Locale.ROOT);
            this.args = args;
        }

        @Override
        public ExpressionValue evaluate(EvaluationContext context) {
            switch (name) {
                case "vec2":
                    return constructVector(context, 2);
                case "vec3":
                    return constructVector(context, 3);
                case "vec4":
                    return constructVector(context, 4);
                case "if":
                    return evaluateIf(context);
                case "in":
                    return ExpressionValue.scalar(evaluateIn(context));
                case "abs":
                    return unary(context, Math::abs);
                case "min":
                    return aggregate(context, Math::min);
                case "max":
                    return aggregate(context, Math::max);
                case "clamp":
                    return args.size() < 3
                        ? ExpressionValue.ZERO
                        : clamp(args.get(0).evaluate(context), args.get(1).evaluate(context), args.get(2).evaluate(context));
                case "sqrt":
                    return unary(context, value -> Math.sqrt(Math.max(0.0, value)));
                case "pow":
                    return args.size() < 2 ? ExpressionValue.ZERO : args.get(0).evaluate(context).zip(args.get(1).evaluate(context), Math::pow);
                case "floor":
                    return unary(context, Math::floor);
                case "ceil":
                    return unary(context, Math::ceil);
                case "round":
                    return unary(context, value -> (double) Math.round(value));
                case "sin":
                    return unary(context, Math::sin);
                case "cos":
                    return unary(context, Math::cos);
                case "tan":
                    return unary(context, Math::tan);
                case "asin":
                    return unary(context, Math::asin);
                case "acos":
                    return unary(context, Math::acos);
                case "atan":
                    return args.size() >= 2
                        ? args.get(0).evaluate(context).zip(args.get(1).evaluate(context), Math::atan2)
                        : unary(context, Math::atan);
                case "atan2":
                    return args.size() < 2 ? ExpressionValue.ZERO : args.get(0).evaluate(context).zip(args.get(1).evaluate(context), Math::atan2);
                case "exp":
                    return unary(context, Math::exp);
                case "exp2":
                    return unary(context, value -> Math.pow(2.0, value));
                case "exp10":
                    return unary(context, value -> Math.pow(10.0, value));
                case "log":
                    return args.size() >= 2
                        ? args.get(0).evaluate(context).zip(args.get(1).evaluate(context), FunctionNode::log)
                        : unary(context, Math::log);
                case "log2":
                    return unary(context, value -> log(2.0, value));
                case "log10":
                    return unary(context, Math::log10);
                case "frac":
                case "fract":
                    return unary(context, value -> value - Math.floor(value));
                case "sign":
                case "signum":
                    return unary(context, Math::signum);
                case "torad":
                case "radians":
                    return unary(context, Math::toRadians);
                case "todeg":
                case "degrees":
                    return unary(context, Math::toDegrees);
                case "mix":
                    return args.size() < 3
                        ? ExpressionValue.ZERO
                        : mix(args.get(0).evaluate(context), args.get(1).evaluate(context), args.get(2).evaluate(context));
                case "edge":
                    return args.size() < 2
                        ? ExpressionValue.ZERO
                        : args.get(0).evaluate(context).zip(args.get(1).evaluate(context), (k, x) -> x < k ? 0.0 : 1.0);
                case "fmod":
                    return args.size() < 2 ? ExpressionValue.ZERO : args.get(0).evaluate(context).zip(args.get(1).evaluate(context), FunctionNode::floorMod);
                case "between":
                    return args.size() < 3
                        ? ExpressionValue.ZERO
                        : between(args.get(0).evaluate(context), args.get(1).evaluate(context), args.get(2).evaluate(context));
                case "equals":
                    return args.size() < 3
                        ? ExpressionValue.ZERO
                        : equals(args.get(0).evaluate(context), args.get(1).evaluate(context), args.get(2).evaluate(context));
                case "random":
                    return evaluateRandom(context);
                case "randomint":
                    return evaluateRandomInt(context);
                default:
                    context.manager.logMissingFunction(name);
                    return ExpressionValue.ZERO;
            }
        }

        @Override
        public void collectIdentifiers(Set<String> identifiers) {
            for (ExpressionNode arg : args) {
                arg.collectIdentifiers(identifiers);
            }
        }

        private ExpressionValue constructVector(EvaluationContext context, int size) {
            if (args.isEmpty()) {
                return ExpressionValue.vector(new double[size]);
            }

            List<Double> values = new ArrayList<>();
            for (ExpressionNode arg : args) {
                ExpressionValue value = arg.evaluate(context);
                if (value.size() == 1 && args.size() == 1) {
                    double[] filled = new double[size];
                    Arrays.fill(filled, value.scalar());
                    return ExpressionValue.vector(filled);
                }
                for (int i = 0; i < value.size(); i++) {
                    values.add(value.component(i));
                    if (values.size() == size) {
                        break;
                    }
                }
                if (values.size() == size) {
                    break;
                }
            }

            double[] result = new double[size];
            for (int i = 0; i < values.size() && i < size; i++) {
                result[i] = values.get(i);
            }
            return ExpressionValue.vector(result);
        }

        private ExpressionValue evaluateIf(EvaluationContext context) {
            if (args.size() < 2) {
                return ExpressionValue.ZERO;
            }

            int index = 0;
            while (index + 1 < args.size()) {
                if (truthy(args.get(index).evaluate(context))) {
                    return args.get(index + 1).evaluate(context);
                }
                index += 2;
            }

            return index < args.size() ? args.get(index).evaluate(context) : ExpressionValue.ZERO;
        }

        private double evaluateIn(EvaluationContext context) {
            if (args.size() < 2) {
                return 0.0;
            }

            double value = args.get(0).evaluate(context).scalar();
            for (int i = 1; i < args.size(); i++) {
                if (same(value, args.get(i).evaluate(context).scalar())) {
                    return 1.0;
                }
            }
            return 0.0;
        }

        private ExpressionValue aggregate(EvaluationContext context, DoubleBinaryFunction function) {
            if (args.isEmpty()) {
                return ExpressionValue.ZERO;
            }

            ExpressionValue result = args.get(0).evaluate(context);
            for (int i = 1; i < args.size(); i++) {
                result = result.zip(args.get(i).evaluate(context), function);
            }
            return result;
        }

        private ExpressionValue unary(EvaluationContext context, DoubleUnaryFunction function) {
            return args.isEmpty() ? ExpressionValue.ZERO : args.get(0).evaluate(context).map(function);
        }

        private ExpressionValue evaluateRandom(EvaluationContext context) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (args.size() >= 2) {
                double min = args.get(0).evaluate(context).scalar();
                double max = args.get(1).evaluate(context).scalar();
                return ExpressionValue.scalar(max <= min ? min : random.nextDouble(min, max));
            }
            return ExpressionValue.scalar(random.nextDouble());
        }

        private ExpressionValue evaluateRandomInt(EvaluationContext context) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            if (args.size() >= 2) {
                int min = (int) args.get(0).evaluate(context).scalar();
                int max = (int) args.get(1).evaluate(context).scalar();
                return ExpressionValue.scalar(max <= min ? min : random.nextInt(min, max));
            }
            return ExpressionValue.scalar(random.nextInt());
        }

        private static ExpressionValue clamp(ExpressionValue value, ExpressionValue min, ExpressionValue max) {
            return value.zip(min, Math::max).zip(max, Math::min);
        }

        private static ExpressionValue between(ExpressionValue value, ExpressionValue min, ExpressionValue max) {
            int resultSize = Math.max(Math.max(value.size(), min.size()), max.size());
            double[] result = new double[resultSize];
            for (int i = 0; i < result.length; i++) {
                double v = value.component(i);
                result[i] = v >= min.component(i) && v <= max.component(i) ? 1.0 : 0.0;
            }
            return ExpressionValue.vector(result);
        }

        private static ExpressionValue equals(ExpressionValue left, ExpressionValue right, ExpressionValue epsilon) {
            return left.zip(right, (a, b) -> Math.abs(a - b)).zip(epsilon, (diff, eps) -> diff <= eps ? 1.0 : 0.0);
        }

        private static ExpressionValue mix(ExpressionValue x, ExpressionValue y, ExpressionValue amount) {
            int resultSize = Math.max(Math.max(x.size(), y.size()), amount.size());
            double[] result = new double[resultSize];
            for (int i = 0; i < result.length; i++) {
                double a = amount.component(i);
                result[i] = x.component(i) * (1.0 - a) + y.component(i) * a;
            }
            return ExpressionValue.vector(result);
        }

        private static double floorMod(double value, double divisor) {
            if (Math.abs(divisor) <= EPSILON) {
                return 0.0;
            }
            return value - divisor * Math.floor(value / divisor);
        }

        private static double log(double base, double value) {
            if (base <= 0.0 || same(base, 1.0) || value <= 0.0) {
                return 0.0;
            }
            return Math.log(value) / Math.log(base);
        }

    }

    private static final class SmoothNode implements ExpressionNode {
        private final List<ExpressionNode> args;
        private final boolean hasExplicitId;
        // The local target packs reuse explicit smooth IDs, so the ID only selects
        // the value argument until a reference implementation proves keyed state.
        private long lastFrame = Long.MIN_VALUE;
        private boolean hasValue;
        private ExpressionValue accumulator = ExpressionValue.ZERO;

        private SmoothNode(List<ExpressionNode> args) {
            this.args = args;
            this.hasExplicitId = args.size() >= 2 && args.get(0) instanceof ConstantNode && ((ConstantNode) args.get(0)).isInteger();
        }

        @Override
        public ExpressionValue evaluate(EvaluationContext context) {
            if (lastFrame == context.frameId) {
                return accumulator;
            }
            lastFrame = context.frameId;

            int valueIndex = hasExplicitId ? 1 : 0;
            if (args.size() <= valueIndex) {
                accumulator = ExpressionValue.ZERO;
                hasValue = true;
                return accumulator;
            }

            ExpressionValue target = args.get(valueIndex).evaluate(context);
            ExpressionValue halfLifeUp = args.size() > valueIndex + 1
                ? args.get(valueIndex + 1).evaluate(context)
                : ExpressionValue.scalar(1.0);
            ExpressionValue halfLifeDown = args.size() > valueIndex + 2
                ? args.get(valueIndex + 2).evaluate(context)
                : halfLifeUp;

            if (!hasValue) {
                accumulator = target;
                hasValue = true;
                return accumulator;
            }

            int resultSize = Math.max(accumulator.size(), target.size());
            double[] result = new double[resultSize];
            for (int i = 0; i < result.length; i++) {
                double current = accumulator.component(i);
                double next = target.component(i);
                double halfLife = next > current ? halfLifeUp.component(i) : halfLifeDown.component(i);
                double decay = computeDecay(halfLife * 0.1);
                double smoothingFactor = 1.0 - Math.exp(-decay * SystemTimeUniforms.TIMER.getLastFrameTime());
                result[i] = current + (next - current) * smoothingFactor;
            }
            accumulator = ExpressionValue.vector(result);
            return accumulator;
        }

        @Override
        public void collectIdentifiers(Set<String> identifiers) {
            for (ExpressionNode arg : args) {
                arg.collectIdentifiers(identifiers);
            }
        }

        private static double computeDecay(double halfLifeSeconds) {
            return ExponentialSmoothing.decayFromHalfLifeSeconds(halfLifeSeconds);
        }
    }

    private static final class CompiledExpression {
        private final CustomUniformDirective directive;
        private final ExpressionNode node;
        private final Set<String> dependencies;
        private long cachedFrame = Long.MIN_VALUE;
        private boolean evaluating;
        private ExpressionValue cachedValue = ExpressionValue.ZERO;
        private boolean dynamic;
        private boolean dynamicResolved;
        private Set<String> dynamicDependencies = Collections.emptySet();

        private CompiledExpression(CustomUniformDirective directive, ExpressionNode node, Set<String> dependencies) {
            this.directive = directive;
            this.node = node;
            this.dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
        }

        private CustomUniformDirective.ValueType getType() {
            return directive.getType();
        }

        private ExpressionValue evaluateValue(EvaluationContext context) {
            if (cachedFrame == context.cacheId) {
                return cachedValue;
            }

            if (evaluating) {
                Oculus.LOGGER.warn("Detected recursive custom expression while evaluating {}", directive.getName());
                return ExpressionValue.ZERO;
            }

            evaluating = true;
            try {
                cachedValue = node.evaluate(context);
                cachedFrame = context.cacheId;
                return cachedValue;
            } catch (RuntimeException ex) {
                Oculus.LOGGER.warn("Failed to evaluate custom expression {}", directive.getName(), ex);
                cachedValue = ExpressionValue.ZERO;
                cachedFrame = context.cacheId;
                return cachedValue;
            } finally {
                evaluating = false;
            }
        }
    }

    private static final class EvaluationContext {
        private final CustomUniformExpressionManager manager;
        private final long frameId;
        private final long cacheId;

        private EvaluationContext(CustomUniformExpressionManager manager, long frameId, long cacheId) {
            this.manager = manager;
            this.frameId = frameId;
            this.cacheId = cacheId;
        }
    }

    private static final class CombinedValueUpdateNotifier implements ValueUpdateNotifier {
        private final List<ValueUpdateNotifier> delegates;

        private CombinedValueUpdateNotifier(List<ValueUpdateNotifier> delegates) {
            this.delegates = Collections.unmodifiableList(new ArrayList<>(delegates));
        }

        @Override
        public void setListener(Runnable listener) {
            Throwable failure = null;
            for (ValueUpdateNotifier delegate : delegates) {
                failure = runNotifierOperation(failure, () -> delegate.setListener(listener));
            }
            rethrowNotifierFailure(failure);
        }

        @Override
        public void removeListener(Runnable listener) {
            Throwable failure = null;
            for (ValueUpdateNotifier delegate : delegates) {
                failure = runNotifierOperation(failure, () -> delegate.removeListener(listener));
            }
            rethrowNotifierFailure(failure);
        }

        private static Throwable runNotifierOperation(Throwable failure, Runnable operation) {
            try {
                operation.run();
            } catch (RuntimeException | Error exception) {
                if (failure == null) {
                    return exception;
                }
                suppressNotifierFailure(failure, exception);
            }
            return failure;
        }

        private static void suppressNotifierFailure(Throwable failure, Throwable exception) {
            if (exception != failure) {
                failure.addSuppressed(exception);
            }
        }

        private static void rethrowNotifierFailure(Throwable failure) {
            if (failure == null) {
                return;
            }
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            throw new IllegalStateException("Unexpected dynamic notifier failure", failure);
        }
    }

    private static final class BuiltinSymbols {
        private static final Map<String, Integer> BIOME_CONSTANT_CACHE = new HashMap<>();
        private static final Map<String, String> BIOME_ALIASES = createBiomeAliases();
        private static final Map<String, Integer> ABSENT_BIOMES = createAbsentBiomes();
        private static final Map<String, Integer> UNBOOTSTRAPPED_BIOME_IDS = createUnbootstrappedBiomeIds();

        private BuiltinSymbols() {
        }

        private static Double resolve(String name, boolean oldHandLight,
                Object2IntFunction<NamespacedId> itemIdMap) {
            if (name == null) {
                return null;
            }

            switch (name) {
                case "true":
                    return 1.0;
                case "false":
                    return 0.0;
                case "PI":
                case "pi":
                    return Math.PI;
                case "frameCounter":
                    return (double) SystemTimeUniforms.COUNTER.getAsInt();
                case "frameTime":
                    return (double) SystemTimeUniforms.TIMER.getLastFrameTime();
                case "frameTimeCounter":
                    return (double) SystemTimeUniforms.TIMER.getFrameTimeCounter();
                case "systemTime":
                case "u_Time":
                    return GameDataSuppliers.systemTime().get().doubleValue();
                case "tickDelta":
                    return (double) CapturedRenderingState.INSTANCE.getTickDelta();
                case "renderStage":
                    return (double) GbufferPrograms.getCurrentPhase().ordinal();
                case "entityId":
                    return (double) CapturedRenderingState.INSTANCE.getCurrentEntity();
                case "blockEntityId":
                    return (double) CapturedRenderingState.INSTANCE.getCurrentBlockEntity();
                case "currentRenderedItemId":
                    return (double) IdMapUniforms.getCurrentRenderedItemId(itemIdMap);
                case "fogMode":
                case "u_FogMode":
                    return (double) GameDataSuppliers.fogMode().getAsInt();
                case "fogDensity":
                case "u_FogDensity":
                case "iris_FogDensity":
                    return GameDataSuppliers.fogDensity().get().doubleValue();
                case "fogStart":
                case "u_FogStart":
                case "iris_FogStart":
                    return GameDataSuppliers.fogStart().get().doubleValue();
                case "fogEnd":
                case "u_FogEnd":
                case "iris_FogEnd":
                    return GameDataSuppliers.fogEnd().get().doubleValue();
                case "framemod2":
                    return (double) (SystemTimeUniforms.COUNTER.getAsInt() % 2);
                case "framemod4":
                    return (double) (SystemTimeUniforms.COUNTER.getAsInt() % 4);
                case "framemod8":
                    return (double) (SystemTimeUniforms.COUNTER.getAsInt() % 8);
                case "frame_mod":
                    return (double) CustomUniforms.getFrameMod();
                case "worldTime":
                    return (double) GameplayUniforms.getWorldTime();
                case "worldDay":
                    return (double) GameplayUniforms.getWorldDay();
                case "moonPhase":
                    return (double) GameplayUniforms.getMoonPhase();
                case "rainStrength":
                    return (double) GameplayUniforms.getRainStrength();
                case "wetness":
                    return (double) GameplayUniforms.getWetness();
                case "thunderStrength":
                    return (double) GameplayUniforms.getThunderStrength();
                case "isEyeInWater":
                    return (double) GameplayUniforms.isEyeInWater();
                case "eyeAltitude":
                    return (double) CapturedRenderingState.INSTANCE.getEyeAltitude();
                case "blindness":
                    return (double) GameplayUniforms.getBlindness();
                case "nightVision":
                    return (double) GameplayUniforms.getNightVision();
                case "darknessFactor":
                case "darknessLightFactor":
                    return 0.0;
                case "biome":
                    return (double) getCurrentBiomeId();
                case "biome_precipitation":
                    return (double) getCurrentBiomePrecipitation();
                case "screenBrightness":
                    return (double) GameplayUniforms.getScreenBrightness();
                case "viewWidth":
                case "u_ViewWidth":
                    return GameDataSuppliers.viewWidth().get().doubleValue();
                case "viewHeight":
                case "u_ViewHeight":
                    return GameDataSuppliers.viewHeight().get().doubleValue();
                case "aspectRatio":
                    return GameDataSuppliers.aspectRatio().get().doubleValue();
                case "near":
                    return (double) CapturedRenderingState.INSTANCE.getNearPlane();
                case "far":
                    return (double) CapturedRenderingState.INSTANCE.getFarPlane();
                case "sunAngle":
                    return (double) CelestialUniforms.getSunAngle();
                case "shadowAngle":
                    return (double) CelestialUniforms.getShadowAngle();
                case "pixel_size_x":
                    return (double) CustomUniforms.getPixelSizeX();
                case "pixel_size_y":
                    return (double) CustomUniforms.getPixelSizeY();
                case "inv_aspect_ratio":
                    return (double) CustomUniforms.getInverseAspectRatio();
                case "day_moment":
                    return (double) CustomUniforms.getDayMoment();
                case "day_mixer":
                    return (double) CustomUniforms.getDayMixer();
                case "night_mixer":
                    return (double) CustomUniforms.getNightMixer();
                case "vol_mixer":
                    return (double) CustomUniforms.getVolumeMixer();
                case "light_mix":
                    return (double) CustomUniforms.getLightMix();
                case "dither_shift":
                    return (double) CustomUniforms.getDitherShift();
                case "fov_y_inv":
                    return (double) CustomUniforms.getFovYInverse();
                case "iris_ModelOffset":
                    return 0.0;
                case "u_ModelScale":
                    return (double) GameplayUniforms.getTerrainModelScale();
                case "u_TextureScale":
                    return (double) GameplayUniforms.getTerrainTextureScale();
                case "iris_LineWidth":
                    return 1.0;
                case "timeAngle":
                    return (double) CompatibilityUniforms.getTimeAngle();
                case "timeBrightness":
                    return (double) CompatibilityUniforms.getTimeBrightness();
                case "moonBrightness":
                    return (double) CompatibilityUniforms.getMoonBrightness();
                case "shadowFade":
                    return (double) CompatibilityUniforms.getShadowFade();
                case "shdFade":
                    return (double) CompatibilityUniforms.getShdFade();
                case "blindFactor":
                    return (double) CompatibilityUniforms.getBlindFactor();
                case "rainStrengthS":
                    return (double) CompatibilityUniforms.getRainStrengthS();
                case "rainStrengthShiningStars":
                    return (double) CompatibilityUniforms.getRainStrengthShiningStars();
                case "rainStrengthS2":
                    return (double) CompatibilityUniforms.getRainStrengthS2();
                case "inDry":
                    return (double) CompatibilityUniforms.getInDry();
                case "inRainy":
                    return (double) CompatibilityUniforms.getInRainy();
                case "inSnowy":
                    return (double) CompatibilityUniforms.getInSnowy();
                case "isDry":
                    return (double) CompatibilityUniforms.getIsDry();
                case "isRainy":
                    return (double) CompatibilityUniforms.getIsRainy();
                case "isSnowy":
                    return (double) CompatibilityUniforms.getIsSnowy();
                case "isEyeInCave":
                    return (double) CompatibilityUniforms.getIsEyeInCave();
                case "velocity":
                    return (double) CompatibilityUniforms.getVelocity();
                case "starter":
                    return (double) CompatibilityUniforms.getStarter();
                case "frameTimeSmooth":
                    return (double) CompatibilityUniforms.getFrameTimeSmooth();
                case "eyeBrightnessM":
                    return (double) CompatibilityUniforms.getEyeBrightnessMUniform();
                case "eyeBrightnessM2":
                    return (double) CompatibilityUniforms.getEyeBrightnessM2();
                case "rainFactor":
                    return (double) CompatibilityUniforms.getRainFactor();
                case "inSwamp":
                    return (double) CompatibilityUniforms.getInSwamp();
                case "inBasaltDeltas":
                    return (double) CompatibilityUniforms.getInBasaltDeltas();
                case "inCrimsonForest":
                    return (double) CompatibilityUniforms.getInCrimsonForest();
                case "inNetherWastes":
                    return (double) CompatibilityUniforms.getInNetherWastes();
                case "inSoulValley":
                    return (double) CompatibilityUniforms.getInSoulValley();
                case "inWarpedForest":
                    return (double) CompatibilityUniforms.getInWarpedForest();
                case "inPaleGarden":
                    return (double) CompatibilityUniforms.getInPaleGarden();
                case "BiomeTemp":
                    return (double) CompatibilityUniforms.getBiomeTemperature();
                case "day":
                    return (double) CompatibilityUniforms.getDay();
                case "night":
                    return (double) CompatibilityUniforms.getNight();
                case "dawnDusk":
                    return (double) CompatibilityUniforms.getDawnDusk();
                case "isPrecipitationRain":
                    return (double) CompatibilityUniforms.getIsPrecipitationRain();
                case "touchmybody":
                    return (double) CompatibilityUniforms.getTouchMyBody();
                case "sneakSmooth":
                    return (double) CompatibilityUniforms.getSneakSmooth();
                case "burningSmooth":
                    return (double) CompatibilityUniforms.getBurningSmooth();
                case "effectStrength":
                    return (double) CompatibilityUniforms.getEffectStrength();
                case "hideGUI":
                    return (double) GameplayUniforms.hideGui();
                case "firstPersonCamera":
                    return (double) GameplayUniforms.isFirstPersonCamera();
                case "isSpectator":
                    return (double) GameplayUniforms.isSpectator();
                case "is_sneaking":
                    return (double) GameplayUniforms.isSneaking();
                case "is_sprinting":
                    return (double) GameplayUniforms.isSprinting();
                case "is_hurt":
                    return (double) GameplayUniforms.isHurt();
                case "is_invisible":
                    return (double) GameplayUniforms.isInvisible();
                case "is_burning":
                    return (double) GameplayUniforms.isBurning();
                case "is_on_ground":
                    return (double) GameplayUniforms.isOnGround();
                case "currentPlayerHealth":
                    return (double) GameplayUniforms.getCurrentPlayerHealth();
                case "maxPlayerHealth":
                    return (double) GameplayUniforms.getMaxPlayerHealth();
                case "currentPlayerHunger":
                    return (double) GameplayUniforms.getCurrentPlayerHunger();
                case "maxPlayerHunger":
                    return (double) GameplayUniforms.getMaxPlayerHunger();
                case "currentPlayerAir":
                    return (double) GameplayUniforms.getCurrentPlayerAir();
                case "maxPlayerAir":
                    return (double) GameplayUniforms.getMaxPlayerAir();
                case "currentColorSpace":
                    return (double) GameplayUniforms.getCurrentColorSpace();
                case "heavyFog":
                    return (double) GameplayUniforms.isHeavyFog();
                case "playerMood":
                    return (double) GameplayUniforms.getPlayerMood();
                case "maxBlindnessDarkness":
                    return (double) GameplayUniforms.getMaxBlindnessDarkness();
                case "heldItemId":
                    return (double) IdMapUniforms.getHeldItemIdMain(itemIdMap);
                case "heldItemId2":
                    return (double) IdMapUniforms.getHeldItemIdOff(itemIdMap);
                case "heldBlockLightValue":
                    return (double) IdMapUniforms.getHeldBlockLightValueMain(oldHandLight);
                case "heldBlockLightValue2":
                    return (double) IdMapUniforms.getHeldBlockLightValueOff();
                case "bedrockLevel":
                    return (double) WorldInfoUniforms.getBedrockLevel();
                case "cloudHeight":
                    return (double) WorldInfoUniforms.getCloudHeight();
                case "heightLimit":
                    return (double) WorldInfoUniforms.getHeightLimit();
                case "logicalHeightLimit":
                    return (double) WorldInfoUniforms.getLogicalHeightLimit();
                case "hasCeiling":
                    return (double) WorldInfoUniforms.hasCeiling();
                case "hasSkylight":
                    return (double) WorldInfoUniforms.hasSkylight();
                case "ambientLight":
                    return (double) WorldInfoUniforms.getAmbientLight();
                default:
                    if (name.startsWith("BIOME_")) {
                        return (double) resolveBiomeConstant(name.substring("BIOME_".length()));
                    }
                    return null;
            }
        }

        private static double[] resolveVector(String name) {
            switch (name) {
                case "cameraPosition":
                    return CapturedRenderingState.INSTANCE.getCameraPosition();
                case "u_CameraPosition":
                case "iris_CameraTranslation":
                    return toDouble(CapturedRenderingState.INSTANCE.getCameraPositionVec());
                case "previousCameraPosition":
                    return CapturedRenderingState.INSTANCE.getPreviousCameraPosition();
                case "cameraPositionInt":
                    return toDouble(CapturedRenderingState.INSTANCE.getCameraPositionInt());
                case "previousCameraPositionInt":
                    return toDouble(CapturedRenderingState.INSTANCE.getPreviousCameraPositionInt());
                case "cameraPositionFract":
                    return toDouble(CapturedRenderingState.INSTANCE.getCameraPositionFract());
                case "previousCameraPositionFract":
                    return toDouble(CapturedRenderingState.INSTANCE.getPreviousCameraPositionFract());
                case "eyeBrightness":
                    return toDouble(GameplayUniforms.getEyeBrightness());
                case "eyeBrightnessSmooth":
                    return toDouble(GameplayUniforms.getEyeBrightnessSmooth());
                case "skyColor":
                    return toDouble(GameplayUniforms.getSkyColor());
                case "sunPosition":
                    return toDouble(CelestialUniforms.getSunPosition());
                case "moonPosition":
                    return toDouble(CelestialUniforms.getMoonPosition());
                case "shadowLightPosition":
                    return toDouble(CelestialUniforms.getShadowLightPosition());
                case "upPosition":
                    return toDouble(CelestialUniforms.getUpPosition());
                case "fogColor":
                case "u_FogColor":
                    return toDouble(CapturedRenderingState.INSTANCE.getFogColor());
                case "iris_FogColor":
                    return toDouble(CapturedRenderingState.INSTANCE.getFogColorVec4());
                case "gtextureSize":
                    return toDouble(GameplayUniforms.getGtextureSizeFloat());
                case "blendFunc":
                    return toDouble(GameplayUniforms.getBlendFuncFloat());
                case "entityColor":
                case "iris_entityColor":
                    return toDouble(GameplayUniforms.getEntityColor());
                case "iris_ChunkOffset":
                    return toDouble(BuiltinReplacementUniforms.getChunkOffset());
                case "iris_ColorModulator":
                    return toDouble(BuiltinReplacementUniforms.getColorModulator());
                case "screenSize":
                case "iris_ScreenSize":
                    return toDouble(GameplayUniforms.getScreenSize());
                case "atlasSize":
                    return toDouble(GameplayUniforms.getAtlasSizeFloat());
                case "eyePosition":
                    return toDouble(GameplayUniforms.getEyePosition());
                case "relativeEyePosition":
                    return toDouble(SpecialEffectUniforms.getRelativeEyePosition());
                case "lightningBoltPosition":
                    return toDouble(SpecialEffectUniforms.getLightningBoltPosition());
                case "playerLookVector":
                    return toDouble(GameplayUniforms.getPlayerLookVector());
                case "playerBodyVector":
                    return toDouble(GameplayUniforms.getPlayerBodyVector());
                case "u_ModelScale":
                    return toDouble(GameplayUniforms.getTerrainModelScaleVec3());
                case "u_TextureScale":
                    return toDouble(GameplayUniforms.getTerrainTextureScaleVec2());
                case "taa_offset":
                    return toDouble(CustomUniforms.getTaaOffset());
                default:
                    return null;
            }
        }

        private static boolean isDynamic(String name) {
            switch (name) {
                case "renderStage":
                case "entityId":
                case "blockEntityId":
                case "currentRenderedItemId":
                case "fogMode":
                case "u_FogMode":
                case "fogDensity":
                case "u_FogDensity":
                case "iris_FogDensity":
                case "fogStart":
                case "u_FogStart":
                case "iris_FogStart":
                case "fogEnd":
                case "u_FogEnd":
                case "iris_FogEnd":
                case "fogColor":
                case "u_FogColor":
                case "iris_FogColor":
                case "gtextureSize":
                case "atlasSize":
                case "blendFunc":
                case "entityColor":
                case "iris_entityColor":
                case "iris_ColorModulator":
                case "gbufferModelView":
                case "iris_ModelViewMatrix":
                case "gbufferPreviousModelView":
                case "gbufferModelViewInverse":
                case "modelViewMatrix":
                case "u_ModelViewMatrix":
                case "gbufferProjection":
                case "iris_ProjectionMatrix":
                case "gbufferPreviousProjection":
                case "gbufferProjectionInverse":
                case "projectionMatrix":
                case "u_ProjectionMatrix":
                case "shadowModelView":
                case "shadowModelViewInverse":
                case "shadowProjection":
                case "shadowProjectionMatrix":
                case "shadowProjectionInverse":
                case "shadowProjectionMatrixInverse":
                case "iris_ModelViewMat":
                case "iris_ProjMat":
                case "iris_TextureMat":
                case "u_ModelViewProjectionMatrix":
                case "iris_ModelViewProjectionMatrix":
                case "iris_NormalMatrix":
                case "iris_LightmapTextureMatrix":
                    return true;
                default:
                    return false;
            }
        }

        private static String dynamicDependencyName(String name) {
            if (name == null) {
                return null;
            }
            if (isDynamic(name)) {
                return name;
            }

            String candidate = name;
            int dotIndex = candidate.lastIndexOf('.');
            while (dotIndex > 0) {
                candidate = candidate.substring(0, dotIndex);
                if (isDynamic(candidate)) {
                    return candidate;
                }
                dotIndex = candidate.lastIndexOf('.');
            }
            return null;
        }

        private static ValueUpdateNotifier dynamicNotifier(String name) {
            switch (name) {
                case "renderStage":
                    GbufferPrograms.init();
                    return StateUpdateNotifiers.phaseChangeNotifier;
                case "entityId":
                    return CapturedRenderingState.INSTANCE.getEntityIdNotifier();
                case "blockEntityId":
                    return CapturedRenderingState.INSTANCE.getBlockEntityIdNotifier();
                case "currentRenderedItemId":
                    return IdMapUniforms.getCurrentRenderedItemIdNotifier();
                case "fogMode":
                case "u_FogMode":
                    return StateUpdateNotifiers.fogModeNotifierWithToggle();
                case "fogDensity":
                case "u_FogDensity":
                case "iris_FogDensity":
                    return StateUpdateNotifiers.fogDensityNotifierWithToggle();
                case "fogStart":
                case "u_FogStart":
                case "iris_FogStart":
                    return StateUpdateNotifiers.fogStartNotifierWithToggle();
                case "fogEnd":
                case "u_FogEnd":
                case "iris_FogEnd":
                    return StateUpdateNotifiers.fogEndNotifierWithToggle();
                case "fogColor":
                case "u_FogColor":
                case "iris_FogColor":
                    return CapturedRenderingState.INSTANCE.getFogColorNotifier();
                case "gtextureSize":
                case "atlasSize":
                    return StateUpdateNotifiers.bindTextureNotifier;
                case "blendFunc":
                    return StateUpdateNotifiers.blendFuncNotifier;
                case "entityColor":
                case "iris_entityColor":
                    return GameplayUniforms.getEntityColorNotifier();
                case "iris_ColorModulator":
                    return BuiltinReplacementUniforms.getColorModulatorNotifier();
                default:
                    return null;
            }
        }

        private static float[] resolveMatrix(String name) {
            switch (name) {
                case "gbufferModelView":
                case "iris_ModelViewMatrix":
                    return CapturedRenderingState.INSTANCE.getGbufferModelView();
                case "modelViewMatrix":
                case "u_ModelViewMatrix":
                case "iris_ModelViewMat":
                    return currentMatrixOrFallback(
                        MatrixState::updateModelViewMatrix,
                        CapturedRenderingState.INSTANCE.getGbufferModelView());
                case "gbufferPreviousModelView":
                    return CapturedRenderingState.INSTANCE.getPreviousModelView();
                case "gbufferModelViewInverse":
                    return CapturedRenderingState.INSTANCE.getModelViewInverse();
                case "gbufferProjection":
                case "iris_ProjectionMatrix":
                    return CapturedRenderingState.INSTANCE.getGbufferProjection();
                case "projectionMatrix":
                case "u_ProjectionMatrix":
                case "iris_ProjMat":
                    return currentMatrixOrFallback(
                        MatrixState::updateProjectionMatrix,
                        CapturedRenderingState.INSTANCE.getGbufferProjection());
                case "gbufferPreviousProjection":
                    return CapturedRenderingState.INSTANCE.getPreviousProjection();
                case "gbufferProjectionInverse":
                    return CapturedRenderingState.INSTANCE.getProjectionInverse();
                case "shadowModelView":
                    return ShadowUniforms.getShadowModelView();
                case "shadowModelViewInverse":
                    return ShadowUniforms.getShadowModelViewInverse();
                case "shadowProjection":
                case "shadowProjectionMatrix":
                    return ShadowUniforms.getShadowProjection();
                case "shadowProjectionInverse":
                case "shadowProjectionMatrixInverse":
                    return ShadowUniforms.getShadowProjectionInverse();
                case "u_ModelViewProjectionMatrix":
                case "iris_ModelViewProjectionMatrix":
                    return CapturedRenderingState.INSTANCE.getModelViewProjection();
                case "iris_NormalMatrix":
                    return CapturedRenderingState.INSTANCE.getNormalMatrix();
                case "iris_LightmapTextureMatrix":
                    return BuiltinReplacementUniforms.getLightmapTextureMatrix();
                case "iris_TextureMat":
                    return currentMatrixOrFallback(
                        MatrixState::updateTextureMatrix,
                        BuiltinReplacementUniforms.getIdentityTextureMatrix());
                default:
                    return null;
            }
        }

        private static float[] currentMatrixOrFallback(Supplier<FloatBuffer> matrixSupplier, float[] fallback) {
            try {
                FloatBuffer matrix = matrixSupplier.get();
                if (matrix == null || matrix.limit() < MATRIX_SIZE * MATRIX_SIZE) {
                    return fallback;
                }

                float[] result = new float[MATRIX_SIZE * MATRIX_SIZE];
                for (int i = 0; i < result.length; i++) {
                    result[i] = matrix.get(i);
                }
                return result;
            } catch (RuntimeException | LinkageError exception) {
                return fallback;
            }
        }

        private static double[] toDouble(float[] values) {
            if (values == null) {
                return null;
            }

            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = values[i];
            }
            return result;
        }

        private static double[] toDouble(int[] values) {
            if (values == null) {
                return null;
            }

            double[] result = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                result[i] = values[i];
            }
            return result;
        }

        private static int getCurrentBiomePrecipitation() {
            Biome biome = getCurrentBiome();
            if (biome == null) {
                return 0;
            }
            if (biome.getEnableSnow()) {
                return 2;
            }
            return biome.canRain() ? 1 : 0;
        }

        private static int getCurrentBiomeId() {
            Biome biome = getCurrentBiome();
            return biome == null ? -1 : Biome.getIdForBiome(biome);
        }

        private static Biome getCurrentBiome() {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return null;
            }

            World world = minecraft.world;
            Entity camera = minecraft.getRenderViewEntity();
            if (world == null || camera == null) {
                return null;
            }
            return world.getBiome(new BlockPos(camera));
        }

        private static int resolveBiomeConstant(String biomeName) {
            Integer cached = BIOME_CONSTANT_CACHE.get(biomeName);
            if (cached != null) {
                return cached;
            }

            Integer absent = ABSENT_BIOMES.get(biomeName);
            if (absent != null) {
                BIOME_CONSTANT_CACHE.put(biomeName, absent);
                return absent;
            }

            String fieldName = BIOME_ALIASES.getOrDefault(biomeName, biomeName);
            int resolved = resolveBiomeField(fieldName);
            BIOME_CONSTANT_CACHE.put(biomeName, resolved);
            return resolved;
        }

        private static int resolveBiomeField(String fieldName) {
            if (!isMinecraftBootstrapped()) {
                Integer id = UNBOOTSTRAPPED_BIOME_IDS.get(fieldName);
                if (id != null) {
                    return id;
                }
                return ABSENT_BIOME_BASE - BIOME_CONSTANT_CACHE.size();
            }

            try {
                Field field = Biomes.class.getField(fieldName);
                Object value = field.get(null);
                if (value instanceof Biome) {
                    return Biome.getIdForBiome((Biome) value);
                }
            } catch (ReflectiveOperationException ignored) {
                return ABSENT_BIOME_BASE - BIOME_CONSTANT_CACHE.size();
            } catch (LinkageError ignored) {
                return ABSENT_BIOME_BASE - BIOME_CONSTANT_CACHE.size();
            }
            return ABSENT_BIOME_BASE - BIOME_CONSTANT_CACHE.size();
        }

        private static boolean isMinecraftBootstrapped() {
            try {
                return Bootstrap.isRegistered();
            } catch (LinkageError ignored) {
                return false;
            }
        }

        private static Map<String, String> createBiomeAliases() {
            Map<String, String> aliases = new HashMap<>();
            aliases.put("NETHER", "HELL");
            aliases.put("NETHER_WASTES", "HELL");
            aliases.put("THE_END", "SKY");
            aliases.put("END", "SKY");
            aliases.put("SWAMP", "SWAMPLAND");
            aliases.put("SNOWY_TUNDRA", "ICE_PLAINS");
            aliases.put("SNOWY_MOUNTAINS", "ICE_MOUNTAINS");
            aliases.put("DARK_FOREST", "ROOFED_FOREST");
            aliases.put("GIANT_TREE_TAIGA", "REDWOOD_TAIGA");
            aliases.put("GIANT_TREE_TAIGA_HILLS", "REDWOOD_TAIGA_HILLS");
            aliases.put("WOODED_MOUNTAINS", "EXTREME_HILLS_WITH_TREES");
            aliases.put("BADLANDS", "MESA");
            aliases.put("WOODED_BADLANDS_PLATEAU", "MESA_ROCK");
            aliases.put("BADLANDS_PLATEAU", "MESA_CLEAR_ROCK");
            return aliases;
        }

        private static Map<String, Integer> createAbsentBiomes() {
            Map<String, Integer> biomes = new HashMap<>();
            biomes.put("SOUL_SAND_VALLEY", ABSENT_BIOME_BASE - 1);
            biomes.put("CRIMSON_FOREST", ABSENT_BIOME_BASE - 2);
            biomes.put("WARPED_FOREST", ABSENT_BIOME_BASE - 3);
            biomes.put("BASALT_DELTAS", ABSENT_BIOME_BASE - 4);
            biomes.put("PALE_GARDEN", ABSENT_BIOME_BASE - 5);
            return biomes;
        }

        private static Map<String, Integer> createUnbootstrappedBiomeIds() {
            Map<String, Integer> biomes = new HashMap<>();
            biomes.put("OCEAN", 0);
            biomes.put("DEFAULT", 1);
            biomes.put("PLAINS", 1);
            biomes.put("DESERT", 2);
            biomes.put("EXTREME_HILLS", 3);
            biomes.put("FOREST", 4);
            biomes.put("TAIGA", 5);
            biomes.put("SWAMPLAND", 6);
            biomes.put("RIVER", 7);
            biomes.put("HELL", 8);
            biomes.put("SKY", 9);
            biomes.put("FROZEN_OCEAN", 10);
            biomes.put("FROZEN_RIVER", 11);
            biomes.put("ICE_PLAINS", 12);
            biomes.put("ICE_MOUNTAINS", 13);
            biomes.put("MUSHROOM_ISLAND", 14);
            biomes.put("MUSHROOM_ISLAND_SHORE", 15);
            biomes.put("BEACH", 16);
            biomes.put("DESERT_HILLS", 17);
            biomes.put("FOREST_HILLS", 18);
            biomes.put("TAIGA_HILLS", 19);
            biomes.put("EXTREME_HILLS_EDGE", 20);
            biomes.put("JUNGLE", 21);
            biomes.put("JUNGLE_HILLS", 22);
            biomes.put("JUNGLE_EDGE", 23);
            biomes.put("DEEP_OCEAN", 24);
            biomes.put("STONE_BEACH", 25);
            biomes.put("COLD_BEACH", 26);
            biomes.put("BIRCH_FOREST", 27);
            biomes.put("BIRCH_FOREST_HILLS", 28);
            biomes.put("ROOFED_FOREST", 29);
            biomes.put("COLD_TAIGA", 30);
            biomes.put("COLD_TAIGA_HILLS", 31);
            biomes.put("REDWOOD_TAIGA", 32);
            biomes.put("REDWOOD_TAIGA_HILLS", 33);
            biomes.put("EXTREME_HILLS_WITH_TREES", 34);
            biomes.put("SAVANNA", 35);
            biomes.put("SAVANNA_PLATEAU", 36);
            biomes.put("MESA", 37);
            biomes.put("MESA_ROCK", 38);
            biomes.put("MESA_CLEAR_ROCK", 39);
            biomes.put("VOID", 127);
            biomes.put("MUTATED_PLAINS", 129);
            biomes.put("MUTATED_DESERT", 130);
            biomes.put("MUTATED_EXTREME_HILLS", 131);
            biomes.put("MUTATED_FOREST", 132);
            biomes.put("MUTATED_TAIGA", 133);
            biomes.put("MUTATED_SWAMPLAND", 134);
            biomes.put("MUTATED_ICE_FLATS", 140);
            biomes.put("MUTATED_JUNGLE", 149);
            biomes.put("MUTATED_JUNGLE_EDGE", 151);
            biomes.put("MUTATED_BIRCH_FOREST", 155);
            biomes.put("MUTATED_BIRCH_FOREST_HILLS", 156);
            biomes.put("MUTATED_ROOFED_FOREST", 157);
            biomes.put("MUTATED_TAIGA_COLD", 158);
            biomes.put("MUTATED_REDWOOD_TAIGA", 160);
            biomes.put("MUTATED_REDWOOD_TAIGA_HILLS", 161);
            biomes.put("MUTATED_EXTREME_HILLS_WITH_TREES", 162);
            biomes.put("MUTATED_SAVANNA", 163);
            biomes.put("MUTATED_SAVANNA_ROCK", 164);
            biomes.put("MUTATED_MESA", 165);
            biomes.put("MUTATED_MESA_ROCK", 166);
            biomes.put("MUTATED_MESA_CLEAR_ROCK", 167);
            return biomes;
        }
    }

    @FunctionalInterface
    private interface DoubleUnaryFunction {
        double apply(double value);
    }

    @FunctionalInterface
    private interface DoubleBinaryFunction {
        double apply(double left, double right);
    }

    private static final class AccessPath {
        private final String baseName;
        private final List<String> accesses;

        private AccessPath(String baseName, List<String> accesses) {
            this.baseName = baseName;
            this.accesses = Collections.unmodifiableList(new ArrayList<>(accesses));
        }
    }

    private static final class ExpressionCompileException extends Exception {
        private ExpressionCompileException(String message) {
            super(message);
        }

        private ExpressionCompileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
